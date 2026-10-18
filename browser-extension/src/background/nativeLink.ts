// Owns the native messaging port: connect, handshake, reconnect with backoff, publish state.
import { HOST_NAME, PROTOCOL_VERSION, RECONNECT } from '../shared/constants';
import { errorMsg, newId, type ExtToHost, type HostToExt } from '../shared/protocol';
import { setConnection, type ConnectionInfo } from '../shared/state';

export class NativeLink {
  private port: chrome.runtime.Port | null = null;
  private retryMs = RECONNECT.minMs;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;
  private info: ConnectionInfo = { state: 'connecting' };
  private waiters: Array<(info: ConnectionInfo) => void> = [];
  /** Called whenever the open port goes away, on purpose or not. */
  onClosed: (() => void) | undefined;

  constructor(private readonly onMessage: (msg: HostToExt) => void) {}

  get isOpen(): boolean {
    return this.port !== null;
  }

  /** Connects unless a port is already open. */
  start(): void {
    this.clearRetry();
    if (!this.port) this.connect();
  }

  stop(): void {
    this.clearRetry();
    this.drop();
    this.publish({ state: 'off' });
  }

  /** Drops any port and connects again now; resolves once the attempt settles or times out. */
  restart(timeoutMs = 5_000): Promise<ConnectionInfo> {
    this.retryMs = RECONNECT.minMs;
    this.clearRetry();
    this.drop();
    this.connect();
    if (this.info.state !== 'connecting') return Promise.resolve(this.info);
    return new Promise((resolve) => {
      const timer = setTimeout(() => resolve(this.info), timeoutMs);
      this.waiters.push((info) => {
        clearTimeout(timer);
        resolve(info);
      });
    });
  }

  /** Returns false when there is no open port; callers treat that as "not connected". */
  send(msg: ExtToHost): boolean {
    if (!this.port) return false;
    try {
      this.port.postMessage(msg);
      return true;
    } catch {
      return false;
    }
  }

  private connect(): void {
    this.publish({ state: 'connecting' });
    const port = chrome.runtime.connectNative(HOST_NAME);
    this.port = port;
    port.onMessage.addListener((raw: unknown) => this.handle(raw));
    port.onDisconnect.addListener(() => {
      const reason = chrome.runtime.lastError?.message ?? '';
      if (this.port !== port) return; // A port we dropped on purpose.
      this.port = null;
      this.onClosed?.();
      this.publish(stateFromDisconnect(reason));
      this.scheduleRetry();
    });
    this.send({
      v: PROTOCOL_VERSION,
      type: 'hello',
      id: newId(),
      extensionVersion: chrome.runtime.getManifest().version,
      browser: navigator.userAgent.includes(' Edg/') ? 'edge' : 'chrome',
    });
  }

  private handle(raw: unknown): void {
    const msg = raw as (Partial<HostToExt> & { id?: unknown }) | null;
    if (typeof msg !== 'object' || msg === null || typeof msg.type !== 'string') {
      this.send(errorMsg('bad_request', 'A message must be a JSON object with a string "type".'));
      return;
    }
    if (msg.v !== PROTOCOL_VERSION) {
      // Never answer an error with an error, or two mismatched peers would loop forever.
      if (msg.type !== 'error') {
        const id = typeof msg.id === 'string' ? msg.id : undefined;
        this.send(errorMsg('unsupported_version', `Protocol version ${String(msg.v)} is not supported; use 1.`, id));
      }
      return;
    }
    if (msg.type === 'hello') {
      this.retryMs = RECONNECT.minMs;
      this.publish(appState(msg.appRunning !== false, msg.appVersion));
    } else if (msg.type === 'status') {
      this.publish(appState(msg.appRunning === true, msg.appVersion ?? this.info.appVersion));
    } else {
      this.onMessage(msg as HostToExt);
    }
  }

  private publish(info: ConnectionInfo): void {
    this.info = info;
    void setConnection(info);
    if (info.state === 'connecting') return;
    const waiters = this.waiters;
    this.waiters = [];
    waiters.forEach((resolve) => resolve(info));
  }

  private drop(): void {
    const port = this.port;
    this.port = null;
    port?.disconnect();
    if (port) this.onClosed?.();
  }

  private scheduleRetry(): void {
    this.clearRetry();
    this.retryTimer = setTimeout(() => this.start(), this.retryMs);
    this.retryMs = Math.min(this.retryMs * 2, RECONNECT.maxMs);
  }

  private clearRetry(): void {
    clearTimeout(this.retryTimer);
    this.retryTimer = undefined;
  }
}

function appState(appRunning: boolean, appVersion: string | undefined): ConnectionInfo {
  const state = appRunning ? 'connected' : 'app_not_running';
  return appVersion ? { state, appVersion } : { state };
}

/** Maps Chrome's onDisconnect error text to a state the UI can explain. */
function stateFromDisconnect(reason: string): ConnectionInfo {
  if (/not found|failed to start/i.test(reason)) return { state: 'host_not_installed', detail: reason };
  if (/forbidden/i.test(reason)) return { state: 'forbidden', detail: reason };
  return { state: 'app_not_running', detail: reason };
}
