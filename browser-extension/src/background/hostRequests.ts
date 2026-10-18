// Requests to the host that expect one reply (the form messages), matched by `id`, with a timeout.
import { PROTOCOL_VERSION } from '../shared/constants';
import { newId, type ErrorCode, type ErrorMsg, type HostReplies, type HostToExt } from '../shared/protocol';
import type { NativeLink } from './nativeLink';
import { fakeHostReply } from './e2eFakeHost';

/** A reply, or why there is none: `not_connected` and `timeout` are local, never on the wire. */
export type HostResult<T> = { ok: true; reply: T } | { ok: false; code: ErrorCode | 'not_connected' | 'timeout'; message: string };

type Pending = (msg: HostToExt | null) => void;

export class HostRequests {
  private readonly pending = new Map<string, Pending>();

  constructor(private readonly link: NativeLink) {}

  async request<T extends keyof HostReplies>(type: T, body: Record<string, unknown>, timeoutMs: number): Promise<HostResult<HostReplies[T]>> {
    const id = newId();
    if (__E2E__) {
      const fake = await fakeHostReply(type, id, body);
      if (fake) return { ok: true, reply: fake as HostReplies[T] };
    }
    const reply = new Promise<HostToExt | null>((resolve) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        resolve(null);
      }, timeoutMs);
      this.pending.set(id, (msg) => {
        clearTimeout(timer);
        this.pending.delete(id);
        resolve(msg);
      });
    });
    if (!this.link.send({ v: PROTOCOL_VERSION, type, id, ...body } as never)) {
      this.pending.get(id)?.(null);
      return { ok: false, code: 'not_connected', message: 'No open port to the JobFinder app.' };
    }
    const msg = await reply;
    if (msg === null && !this.link.isOpen) return { ok: false, code: 'not_connected', message: 'The JobFinder app went away.' };
    if (msg === null) return { ok: false, code: 'timeout', message: `No ${type} reply in ${timeoutMs} ms.` };
    if (msg.type === 'error') return { ok: false, code: (msg as ErrorMsg).code, message: (msg as ErrorMsg).message };
    return { ok: true, reply: msg as HostReplies[T] };
  }

  /** The port closed: every waiting request ends as not answered. */
  failAll(): void {
    [...this.pending.values()].forEach((waiter) => waiter(null));
  }

  /** Takes a reply to one of its requests; false for anything else. */
  onReply(msg: HostToExt): boolean {
    const id = (msg as { id?: unknown }).id;
    if (typeof id !== 'string') return false;
    const waiter = this.pending.get(id);
    if (!waiter) return false;
    waiter(msg);
    return true;
  }
}
