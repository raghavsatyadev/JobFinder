package io.github.raghavsatyadev.jobfinder.desktop.setup

import com.sun.jna.Platform
import io.github.raghavsatyadev.jobfinder.server.http.ServerEndpoint
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupProblem
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStep
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepKind
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepState
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * The desktop's [SetupAssistant]. Checks run on start, and every [pollEvery] while the assistant is
 * on screen; it shows itself on start when a step is not done and the user never closed it. Fixes
 * run in [scope] in the background: the network and firewall in one elevated script, Ollama and
 * then the model one after the other. A step being fixed is not overwritten by a check, and a
 * failed step keeps its reason until it is done or tried again.
 */
class DesktopSetupAssistant(
  private val endpoint: StateFlow<ServerEndpoint?>,
  private val portInUse: StateFlow<Int?>,
  private val network: WindowsNetwork,
  private val ollama: OllamaSetup,
  private val searchPages: suspend () -> List<String>,
  private val phonePaired: StateFlow<Boolean>,
  private val memory: SetupMemory,
  override val computerName: String,
  private val openSignIn: suspend (pages: List<String>) -> Unit,
  private val openSearches: () -> Unit,
  /** A browser extension is connected now. */
  private val extensionConnected: () -> Boolean = { false },
  /** Shows the extension folder (and the browser's extensions page) so the user can load it. */
  private val openExtension: () -> Unit = {},
  private val scope: CoroutineScope,
  private val pollEvery: Duration = 5.seconds,
  /** The AI choice: with an online model, Ollama and its model are not needed. */
  private val aiSettings: suspend () -> AiSettings = { AiSettings() },
  /**
   * Runs when Ollama and the app's model are ready: the AI work that failed without them retries.
   */
  private val onModelReady: suspend () -> Unit = {},
) : SetupAssistant, AutoCloseable {
  private val log = LoggerFactory.getLogger(DesktopSetupAssistant::class.java)
  private val _visible = MutableStateFlow(false)
  override val visible: StateFlow<Boolean> = _visible.asStateFlow()
  private val _steps =
    MutableStateFlow(SetupStepKind.entries.map { SetupStep(it, SetupStepState.CHECKING) })
  override val steps: StateFlow<List<SetupStep>> = _steps.asStateFlow()
  override val modelBytes: Long = AppModel.BYTES

  /** Steps a fix is running on. */
  private val working: MutableSet<SetupStepKind> = ConcurrentHashMap.newKeySet()
  private val checking = Mutex()
  private var networkJob: Job? = null
  private var ollamaJob: Job? = null
  private var signInJob: Job? = null

  init {
    scope.launch {
      checkNow()
      if (
        !memory.seen && _steps.value.any { !it.kind.optional && it.state != SetupStepState.DONE }
      ) {
        _visible.value = true
      }
      _visible.collectLatest { shown ->
        while (shown) {
          delay(pollEvery)
          checkNow()
        }
      }
    }
    scope.launch { phonePaired.collect { report(SetupStepKind.PHONE, done = it) } }
    // The extension may connect only while the assistant is closed: noticed within a minute.
    scope.launch {
      while (!memory.extensionLinked) {
        delay(EXTENSION_POLL)
        if (extensionConnected()) {
          memory.extensionLinked = true
          report(SetupStepKind.EXTENSION, true)
        }
      }
    }
  }

  override fun show(show: Boolean) {
    _visible.value = show
    if (show) check() else memory.seen = true
  }

  override fun close() {
    scope.cancel()
  }

  override fun check() {
    scope.launch { checkNow() }
  }

  override fun fix(kinds: Set<SetupStepKind>) {
    val todo =
      _steps.value
        .filter { it.kind in kinds && it.kind.automatic && it.state != SetupStepState.DONE }
        .map { it.kind }
    fixNetwork(SetupStepKind.NETWORK in todo, SetupStepKind.FIREWALL in todo)
    if (SetupStepKind.OLLAMA in todo || SetupStepKind.MODEL in todo) fixOllama()
  }

  override fun markSignedIn() {
    memory.signedIn = true
    check()
  }

  override fun act(kind: SetupStepKind) {
    when (kind) {
      SetupStepKind.SERVER,
      SetupStepKind.PHONE -> check()
      SetupStepKind.EXTENSION -> {
        openExtension()
        check()
      }
      SetupStepKind.NETWORK -> fixNetwork(makePrivate = true, openFirewall = false)
      SetupStepKind.FIREWALL -> fixNetwork(makePrivate = false, openFirewall = true)
      SetupStepKind.OLLAMA,
      SetupStepKind.MODEL -> fixOllama()
      SetupStepKind.SIGN_IN -> signIn()
      SetupStepKind.SEARCHES -> {
        show(false)
        openSearches()
      }
    }
  }

  /** Checks every step that is not being fixed; a second call while one runs waits for it. */
  private suspend fun checkNow() = checking.withLock {
    try {
      val at = endpoint.value
      report(
        SetupStepKind.SERVER,
        at != null,
        if (portInUse.value != null) SetupProblem.PORT_IN_USE else SetupProblem.NO_NETWORK,
      )
      if (at == null) {
        report(SetupStepKind.NETWORK, false, SetupProblem.NEEDS_SERVER)
        report(SetupStepKind.FIREWALL, false, SetupProblem.NEEDS_SERVER)
      } else if (!Platform.isWindows()) {
        // The network and firewall fixes are Windows scripts; elsewhere the user's own settings.
        report(SetupStepKind.NETWORK, true)
        report(SetupStepKind.FIREWALL, true)
      } else {
        // Unknown counts as done: there is nothing the fix could change.
        val category = network.category(at.host)
        report(
          SetupStepKind.NETWORK,
          category != NetworkCategory.PUBLIC,
          SetupProblem.PUBLIC_NETWORK,
        )
        val firewall = network.firewall(at.port)
        report(
          SetupStepKind.FIREWALL,
          firewall == FirewallState.OPEN,
          if (firewall == FirewallState.BLOCKED) SetupProblem.BLOCKED_BY_FIREWALL
          else SetupProblem.NO_FIREWALL_RULE,
        )
      }
      val ai = runCatching { aiSettings() }.getOrDefault(AiSettings())
      if (ai.provider.online) {
        notNeeded(SetupStepKind.OLLAMA)
        notNeeded(SetupStepKind.MODEL)
      } else {
        val running = ollama.running()
        report(
          SetupStepKind.OLLAMA,
          running,
          if (ollama.installed()) SetupProblem.OLLAMA_STOPPED else SetupProblem.OLLAMA_MISSING,
        )
        val models = if (running) ollama.models() else null
        report(SetupStepKind.MODEL, models != null && hasModel(models, ai.model))
      }
      val searched = searchPages().isNotEmpty()
      report(SetupStepKind.SEARCHES, searched)
      report(
        SetupStepKind.SIGN_IN,
        memory.signedIn,
        if (searched) null else SetupProblem.NEEDS_SEARCH,
      )
      report(SetupStepKind.PHONE, phonePaired.value)
      if (extensionConnected()) memory.extensionLinked = true
      report(SetupStepKind.EXTENSION, memory.extensionLinked)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.warn("A setup check failed", e)
    }
  }

  private fun fixNetwork(makePrivate: Boolean, openFirewall: Boolean) {
    if (!makePrivate && !openFirewall || networkJob?.isActive == true) return
    val at = endpoint.value ?: return
    val kinds =
      listOfNotNull(
        SetupStepKind.NETWORK.takeIf { makePrivate },
        SetupStepKind.FIREWALL.takeIf { openFirewall },
      )
    networkJob = scope.launch {
      val problems = kinds.associateWith { step(it).problem }
      kinds.forEach { begin(it) }
      val result =
        try {
          network.fix(at.host, at.port, makePrivate, openFirewall)
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          log.warn("The network fix failed", e)
          Elevated.FAILED
        }
      log.info("Network fix for {}: {}", kinds, result)
      for (kind in kinds) {
        working -= kind
        when (result) {
          Elevated.OK -> set(SetupStep(kind, SetupStepState.CHECKING))
          Elevated.DECLINED -> fail(kind, SetupProblem.PERMISSION_DECLINED)
          Elevated.FAILED -> fail(kind, problems[kind] ?: SetupProblem.NO_FIREWALL_RULE)
        }
      }
      if (result == Elevated.OK) checkNow()
    }
  }

  /**
   * Opens the sign-in browser on one search page per site; the step is done once the user closes
   * it. Nothing happens before a search is saved.
   */
  private fun signIn() {
    if (signInJob?.isActive == true) return
    signInJob = scope.launch {
      val pages = searchPages()
      if (pages.isEmpty())
        return@launch report(SetupStepKind.SIGN_IN, false, SetupProblem.NEEDS_SEARCH)
      begin(SetupStepKind.SIGN_IN)
      try {
        openSignIn(pages)
        memory.signedIn = true
        finish(SetupStepKind.SIGN_IN)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.warn("Opening the sign-in browser failed", e)
        working -= SetupStepKind.SIGN_IN
        report(SetupStepKind.SIGN_IN, memory.signedIn)
      }
    }
  }

  private fun fixOllama() {
    if (ollamaJob?.isActive == true) return
    ollamaJob = scope.launch {
      var kind = SetupStepKind.OLLAMA
      try {
        if (!ollama.running()) {
          begin(kind)
          if (!ollama.installed()) {
            ollama.install { done, total ->
              set(SetupStep(SetupStepKind.OLLAMA, SetupStepState.WORKING, null, done, total))
            }
            set(SetupStep(kind, SetupStepState.WORKING))
          }
          if (!ollama.start()) throw SetupFailure(SetupProblem.OLLAMA_STOPPED)
          finish(kind)
        }
        kind = SetupStepKind.MODEL
        val models = ollama.models() ?: throw SetupFailure(SetupProblem.OLLAMA_STOPPED)
        if (!AppModel.isIn(models)) {
          begin(kind)
          if (!AppModel.baseIsIn(models)) {
            ollama.pull(AppModel.BASE) { done, total ->
              set(
                SetupStep(
                  SetupStepKind.MODEL,
                  SetupStepState.WORKING,
                  null,
                  done,
                  total ?: modelBytes,
                )
              )
            }
            set(SetupStep(kind, SetupStepState.WORKING))
          }
          ollama.create(AppModel.NAME, AppModel.BASE, AppModel.CONTEXT_SIZE)
          finish(kind)
        }
        onModelReady()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.warn("Setting up {} failed", kind, e)
        working -= kind
        val problem =
          (e as? SetupFailure)?.problem
            ?: if (kind == SetupStepKind.OLLAMA) SetupProblem.INSTALL_FAILED
            else SetupProblem.DOWNLOAD_FAILED
        fail(kind, problem)
      }
    }
  }

  private fun step(kind: SetupStepKind): SetupStep = _steps.value.first { it.kind == kind }

  private fun set(step: SetupStep) {
    _steps.update { list -> list.map { if (it.kind == step.kind) step else it } }
  }

  private fun begin(kind: SetupStepKind) {
    working += kind
    set(SetupStep(kind, SetupStepState.WORKING))
  }

  private fun finish(kind: SetupStepKind) {
    working -= kind
    set(SetupStep(kind, SetupStepState.DONE))
  }

  private fun fail(kind: SetupStepKind, problem: SetupProblem) {
    set(SetupStep(kind, SetupStepState.FAILED, problem))
  }

  /** The chosen model, or the app's own when that is chosen, is in [models]. */
  private fun hasModel(models: List<String>, chosen: String): Boolean {
    val name = chosen.trim().removeSuffix(":latest")
    return if (name == AppModel.NAME) AppModel.isIn(models)
    else models.any { it.removeSuffix(":latest") == name }
  }

  /** [kind] needs nothing: an online model answers. */
  private fun notNeeded(kind: SetupStepKind) {
    if (kind in working) return
    set(SetupStep(kind, SetupStepState.DONE, SetupProblem.NOT_NEEDED_ONLINE))
  }

  /** A check's answer, unless a fix runs on the step or it failed and is still not done. */
  private fun report(kind: SetupStepKind, done: Boolean, problem: SetupProblem? = null) {
    if (kind in working) return
    _steps.update { list ->
      list.map {
        when {
          it.kind != kind -> it
          done -> SetupStep(kind, SetupStepState.DONE)
          it.state == SetupStepState.FAILED -> it
          else -> SetupStep(kind, SetupStepState.TODO, problem)
        }
      }
    }
  }
}

/** How often a closed assistant looks for the browser extension. */
private val EXTENSION_POLL = 30.seconds
