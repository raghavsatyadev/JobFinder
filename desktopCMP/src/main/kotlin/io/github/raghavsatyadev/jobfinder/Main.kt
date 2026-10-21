package io.github.raghavsatyadev.jobfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.raghavsatyadev.jobfinder.desktop.browser.AppPaths
import io.github.raghavsatyadev.jobfinder.desktop.browser.CefBrowserPool
import io.github.raghavsatyadev.jobfinder.desktop.browser.CefRuntime
import io.github.raghavsatyadev.jobfinder.desktop.browser.DevCommands
import io.github.raghavsatyadev.jobfinder.desktop.browser.ExtensionSignIns
import io.github.raghavsatyadev.jobfinder.desktop.browser.SingleInstanceLock
import io.github.raghavsatyadev.jobfinder.desktop.browser.SystemBrowserSignIn
import io.github.raghavsatyadev.jobfinder.desktop.dev.scanCommand
import io.github.raghavsatyadev.jobfinder.desktop.dev.serve
import io.github.raghavsatyadev.jobfinder.desktop.models.OllamaQuit
import io.github.raghavsatyadev.jobfinder.desktop.nativehost.BundledExtension
import io.github.raghavsatyadev.jobfinder.desktop.nativehost.NativeHostEntry
import io.github.raghavsatyadev.jobfinder.desktop.nativehost.NativeHostRegistration
import io.github.raghavsatyadev.jobfinder.desktop.scan.ScanServices
import io.github.raghavsatyadev.jobfinder.desktop.server.DevApprovals
import io.github.raghavsatyadev.jobfinder.desktop.server.EmbeddedServer
import io.github.raghavsatyadev.jobfinder.desktop.server.LanAnnouncer
import io.github.raghavsatyadev.jobfinder.desktop.server.serverModule
import io.github.raghavsatyadev.jobfinder.desktop.setup.DesktopOllamaSetup
import io.github.raghavsatyadev.jobfinder.desktop.setup.DesktopSetupAssistant
import io.github.raghavsatyadev.jobfinder.desktop.setup.NetworkSetup
import io.github.raghavsatyadev.jobfinder.desktop.setup.PowerShell
import io.github.raghavsatyadev.jobfinder.desktop.setup.SetupMemory
import io.github.raghavsatyadev.jobfinder.desktop.system.ProcessPriority
import io.github.raghavsatyadev.jobfinder.desktop.system.SleepBlocker
import io.github.raghavsatyadev.jobfinder.desktop.system.TrayIcon
import io.github.raghavsatyadev.jobfinder.desktop.system.TrayMenuItem
import io.github.raghavsatyadev.jobfinder.desktop.system.WindowMotion
import io.github.raghavsatyadev.jobfinder.desktop.ui.AppWindowFrame
import io.github.raghavsatyadev.jobfinder.desktop.ui.AppWindowIcon
import io.github.raghavsatyadev.jobfinder.desktop.ui.LoginBrowserState
import io.github.raghavsatyadev.jobfinder.desktop.ui.LoginBrowserWindow
import io.github.raghavsatyadev.jobfinder.desktop.ui.MainWindowRequests
import io.github.raghavsatyadev.jobfinder.desktop.ui.desktopUiModule
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_notify_text
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_notify_title
import io.github.raghavsatyadev.jobfinder.resources.restore_request_notify_text
import io.github.raghavsatyadev.jobfinder.resources.restore_request_notify_title
import io.github.raghavsatyadev.jobfinder.resources.tray_hidden_text
import io.github.raghavsatyadev.jobfinder.resources.tray_hidden_title
import io.github.raghavsatyadev.jobfinder.resources.tray_quit
import io.github.raghavsatyadev.jobfinder.resources.tray_setup
import io.github.raghavsatyadev.jobfinder.server.ai.AiService
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.http.ApiServerHost
import io.github.raghavsatyadev.jobfinder.server.http.PairedPhones
import io.github.raghavsatyadev.jobfinder.server.http.PairingRequests
import io.github.raghavsatyadev.jobfinder.server.http.RestoreRequests
import io.github.raghavsatyadev.jobfinder.server.http.ServerIdentity
import io.github.raghavsatyadev.jobfinder.server.mcp.McpServerHost
import io.github.raghavsatyadev.jobfinder.server.nativehost.ExtensionPageFallback
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostLink
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSitesRepository
import io.github.raghavsatyadev.jobfinder.server.scan.BrowserPool
import io.github.raghavsatyadev.jobfinder.server.scan.JobScanner
import io.github.raghavsatyadev.jobfinder.server.service.CustomBoards
import io.github.raghavsatyadev.jobfinder.server.service.JobRetention
import io.github.raghavsatyadev.jobfinder.server.service.NewJobsRecheck
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import io.github.raghavsatyadev.jobfinder.ui.pairing.PairingApprovalDialog
import io.github.raghavsatyadev.jobfinder.ui.pairing.RestoreApprovalDialog
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.theme.AppTheme
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.mp.KoinPlatform
import org.slf4j.LoggerFactory

/** Exit code when another instance already holds the profile. */
private const val EXIT_ALREADY_RUNNING = 3

/** The data folder, for `logback.xml`. */
private const val DATA_DIR_PROPERTY = "jobfinder.dataDir"

fun main(args: Array<String>) {
  // Started by Chrome or Edge as the extension's native host: no lock, no window, no logger.
  if (NativeHostEntry.handles(args)) exitProcess(NativeHostEntry.run())
  // Before the first logger: this may move the old first2apply folder, and logback writes into it.
  val paths = AppPaths.default()
  System.setProperty(DATA_DIR_PROPERTY, paths.root.path)
  val log = LoggerFactory.getLogger("jobfinder")
  Thread.setDefaultUncaughtExceptionHandler { thread, error ->
    log.error("Uncaught exception in {}", thread.name, error)
  }
  val lock = SingleInstanceLock.tryAcquire(paths.instanceLock)
  if (lock == null) {
    System.err.println("$APP_NAME is already running (lock ${paths.instanceLock}).")
    exitProcess(EXIT_ALREADY_RUNNING)
  }
  val runtime = CefRuntime(paths)
  val devCommands = DevCommands(runtime)
  val exitCode =
    try {
      when {
        devCommands.handles(args.toList()) -> devCommands.run(args.toList())
        args.firstOrNull() == "serve" -> {
          initKoin(
            extraModules =
              listOf(desktopModule(runtime, paths.root), serverModule(paths.root), desktopUiModule)
          )
          serve(args.getOrNull(1)?.toLongOrNull())
        }
        args.firstOrNull() == "scan" -> {
          initKoin(
            extraModules =
              listOf(desktopModule(runtime, paths.root), serverModule(paths.root), desktopUiModule)
          )
          scanCommand(args.getOrNull(1)?.toLongOrNull())
        }
        else -> {
          initKoin(
            extraModules =
              listOf(desktopModule(runtime, paths.root), serverModule(paths.root), desktopUiModule)
          )
          runApp(runtime, startHidden = START_HIDDEN_ARG in args)
          0
        }
      }
    } finally {
      runtime.shutdown()
      lock.close()
    }
  // CEF and AWT leave non-daemon threads behind.
  exitProcess(exitCode)
}

/**
 * The app: the API server, the scan services (schedule, tray icon and notifications), the main
 * window and, when asked for, the sign-in window. Windows are normal windows, never always-on-top.
 * Closing the main window fades it out to the tray icon while scans go on, and the icon fades it
 * back in; "Quit" in the icon's menu ends the app. With [startHidden] the window starts in the
 * tray.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun runApp(runtime: CefRuntime, startHidden: Boolean) {
  val log = LoggerFactory.getLogger("jobfinder")
  val koin = KoinPlatform.getKoin()
  ProcessPriority.lower()
  val server = koin.get<EmbeddedServer>()
  val scans = koin.get<ScanServices>()
  val windows = koin.get<MainWindowRequests>()
  val login = koin.get<LoginBrowserState>()
  val tray = koin.get<TrayIcon>()
  val pairing = koin.get<PairingRequests>()
  val restores = koin.get<RestoreRequests>()
  server.start()
  scans.start()
  Thread(
      {
        NativeHostRegistration.register(AppPaths.default().root)
        BundledExtension.sync(AppPaths.default().root)
      },
      "native-host-registration",
    )
    .apply { isDaemon = true }
    .start()
  val devScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  koin.get<JobRetention>().start(devScope)
  koin.get<NewJobsRecheck>().start(devScope)
  val mcp = koin.get<McpServerHost>()
  devScope.launch { mcp.start() }
  devScope.launch { runCatching { koin.get<CustomBoards>().splitCatchAll() } }
  if (DevApprovals.enabled()) DevApprovals.start(devScope, pairing, restores)
  try {
    // Compose would end the process here, before the clean-up below runs; main() ends it instead.
    application(exitProcessOnExit = false) {
      var visible by remember { mutableStateOf(!startHidden || !tray.available) }
      var toldAboutTray by remember { mutableStateOf(false) }
      // Fades the main window to and from the tray; a new request cancels the running one.
      val scope = rememberCoroutineScope()
      var motion by remember { mutableStateOf<WindowMotion?>(null) }
      var transition by remember { mutableStateOf<Job?>(null) }
      LaunchedEffect(Unit) { windows.quits.collect { exitApplication() } }
      // Compose's default shows a modal error dialog, which can open behind the window and then
      // blocks every click, the close button included. Log the error and quit instead.
      val crashes = WindowExceptionHandlerFactory { _ ->
        WindowExceptionHandler { error ->
          log.error("A window failed; quitting", error)
          exitApplication()
        }
      }
      CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides crashes) {
        Window(
          onCloseRequest = {
            val fader = motion
            if (tray.available && fader != null) {
              transition?.cancel()
              transition = scope.launch {
                fader.hide()
                visible = false
                if (!toldAboutTray) {
                  toldAboutTray = true
                  tray.notify(trayHiddenTitle, trayHiddenText, sound = false)
                }
              }
            } else {
              exitApplication()
            }
          },
          state =
            rememberWindowState(
              size = DpSize(MAIN_WIDTH.dp, MAIN_HEIGHT.dp),
              position = WindowPosition(Alignment.Center),
            ),
          visible = visible,
          title = APP_NAME,
        ) {
          AppWindowIcon()
          val windowMotion = remember(window) { WindowMotion(window).also { motion = it } }
          LaunchedEffect(windowMotion) {
            windows.shows.collect {
              transition?.cancel()
              // Undispatched: the window is on screen, still transparent, before `visible` changes.
              transition = scope.launch(start = CoroutineStart.UNDISPATCHED) { windowMotion.show() }
              visible = true
            }
          }
          AppWindowFrame(APP_NAME, windowMotion) { App(navigation = windows.destinations) }
          // Outside App(): give the approval dialogs the app's theme, dark included.
          AppTheme {
            PairingApproval(pairing, windows, tray)
            RestoreApproval(restores, windows, tray)
          }
        }
        val loginRequest by login.request.collectAsState()
        loginRequest?.let { LoginBrowserWindow(runtime, it, onDone = login::close) }
      }
    }
  } finally {
    // A stuck clean-up must never keep the app running after the windows are gone.
    Thread(
        {
          Thread.sleep(SHUTDOWN_LIMIT_MS)
          log.warn("Shutdown took over {} ms; stopping now", SHUTDOWN_LIMIT_MS)
          Runtime.getRuntime().halt(0)
        },
        "shutdown-watchdog",
      )
      .apply { isDaemon = true }
      .start()
    devScope.cancel()
    scans.close()
    koin.get<CefBrowserPool>().close()
    mcp.stop()
    server.close()
    // Last: no scan or API call can load a model again after this.
    OllamaQuit.unloadModels()
  }
}

/**
 * Shows a phone's pairing request over the main window: brings the window forward, tells the user
 * through the tray, and expires the request once nobody answers in time.
 */
@Composable
private fun PairingApproval(pairing: PairingRequests, windows: MainWindowRequests, tray: TrayIcon) {
  val request by pairing.pending.collectAsState()
  val current = request ?: return
  LaunchedEffect(current.id) {
    windows.show()
    tray.notify(
      getString(Res.string.pairing_request_notify_title),
      getString(Res.string.pairing_request_notify_text, current.deviceName),
      sound = true,
    )
    while (true) {
      delay(PAIRING_EXPIRY_CHECK_MS)
      pairing.expireOld()
    }
  }
  PairingApprovalDialog(
    current.deviceName,
    current.code,
    onAllow = { pairing.approve(current.id) },
    onDeny = { pairing.deny(current.id) },
  )
}

/** Shows a phone's request to replace everything here with its backup, like a pairing request. */
@Composable
private fun RestoreApproval(
  restores: RestoreRequests,
  windows: MainWindowRequests,
  tray: TrayIcon,
) {
  val request by restores.pending.collectAsState()
  val current = request ?: return
  LaunchedEffect(current.id) {
    windows.show()
    tray.notify(
      getString(Res.string.restore_request_notify_title),
      getString(Res.string.restore_request_notify_text),
      sound = true,
    )
  }
  RestoreApprovalDialog(
    current.manifest,
    onAllow = { restores.approve(current.id) },
    onDeny = { restores.deny(current.id) },
  )
}

private val trayHiddenTitle by lazy { runBlocking { getString(Res.string.tray_hidden_title) } }
private val trayHiddenText by lazy { runBlocking { getString(Res.string.tray_hidden_text) } }

/** Desktop-only bindings: the JCEF runtime, the scan browsers and the scan pipeline on them. */
private fun desktopModule(runtime: CefRuntime, dataDir: File) = module {
  single { runtime }
  single { CefBrowserPool(get()) } bind BrowserPool::class
  single {
    JobScanner(get(), get(), get(), get(), fallback = ExtensionPageFallback(get<NativeHostLink>()))
  }
  single { LanAnnouncer(get<ApiServerHost>().endpoint, get()) }
  single { EmbeddedServer(get(), get(), get(), get()) }
  single { MainWindowRequests() }
  single { SystemBrowserSignIn(get()) } onClose { it?.close() }
  single {
    val sites = get<LocalSitesRepository>()
    ExtensionSignIns(get(), get()) { sites.listSites().flatMap { it.urls } }
  }
  single {
    val windows = get<MainWindowRequests>()
    val menu = runBlocking {
      listOf(TopLevel.Jobs, TopLevel.Links, TopLevel.Settings).map { destination ->
        TrayMenuItem(getString(destination.label)) { windows.show(destination) }
      } +
        TrayMenuItem(getString(Res.string.tray_setup)) {
          windows.show()
          get<SetupAssistant>().show(true)
        } +
        TrayMenuItem(getString(Res.string.tray_quit)) { windows.quit() }
    }
    TrayIcon.create(APP_NAME, menu) { windows.show() }
  }
  single { SetupMemory(dataDir.resolve(SETUP_FILE).toPath()) }
  single<SetupAssistant> {
    val scope = this
    val windows = get<MainWindowRequests>()
    DesktopSetupAssistant(
      endpoint = get<ApiServerHost>().endpoint,
      portInUse = get<ApiServerHost>().portInUse,
      network = NetworkSetup(PowerShell()),
      ollama = DesktopOllamaSetup(LlmClient.defaultHttpClient()),
      searchPages = {
        scope.get<LinksRepository>().listLinks().distinctBy { it.siteId }.map { it.url }
      },
      phonePaired = get<PairedPhones>().paired,
      memory = get(),
      computerName = get<ServerIdentity>().name,
      openSignIn = { pages -> signInTo(pages, scope.get(), scope.get()) },
      openSearches = { windows.show(TopLevel.Links) },
      extensionConnected = { get<NativeHostLink>().isConnected },
      openExtension = { BundledExtension.open(dataDir) },
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
      aiSettings = { scope.get<AiService>().settings() },
    )
  } onClose { (it as? AutoCloseable)?.close() }
  single {
    ScanServices(
      scanner = get(),
      settings = get(),
      events = get(),
      db = get(),
      pool = get(),
      sleep = SleepBlocker(),
      tray = get(),
      backups = get(),
    )
  }
}

/**
 * Opens [pages] in the installed Chrome or Edge, one tab each, and waits until the user closes it.
 * Without one, or when it does not start, the embedded sign-in window opens the first page.
 */
private suspend fun signInTo(
  pages: List<String>,
  browser: SystemBrowserSignIn,
  features: DesktopFeatures,
) {
  if (browser.isAvailable) {
    try {
      pages.forEach { browser.open(it) }
      browser.awaitClosed()
      return
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      LoggerFactory.getLogger("jobfinder.setup").warn("The sign-in browser did not open", e)
    }
  }
  features.openLoginBrowser(pages.first())
}

/** What the setup assistant remembers, in the data folder. */
private const val SETUP_FILE = "setup"

/** The main window's first size, in dp. */
private const val MAIN_WIDTH = 1280
private const val MAIN_HEIGHT = 860

/** How often an unanswered pairing request is checked for expiry. */
private const val PAIRING_EXPIRY_CHECK_MS = 5_000L

/** How long clean-up may take after the last window closes. */
private const val SHUTDOWN_LIMIT_MS = 20_000L

/** Starts the app with its window hidden in the tray, as at sign-in. */
private const val START_HIDDEN_ARG = "--minimized"

private const val APP_NAME = "JobFinder"
