package io.github.raghavsatyadev.jobfinder.desktop.dev

import androidx.compose.runtime.Composable
import io.github.raghavsatyadev.jobfinder.server.ai.PromptBook
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiKeyUsage
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import io.github.raghavsatyadev.jobfinder.shared.model.FormAnswer
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovedResume
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementState
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementStatus
import io.github.raghavsatyadev.jobfinder.shared.model.LinkedInSummary
import io.github.raghavsatyadev.jobfinder.shared.model.MakeResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTrial
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeSetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobFilterStore
import io.github.raghavsatyadev.jobfinder.ui.jobs.NoJobFilterStore
import io.github.raghavsatyadev.jobfinder.ui.links.LinksViewStore
import io.github.raghavsatyadev.jobfinder.ui.links.NoLinksViewStore
import io.github.raghavsatyadev.jobfinder.ui.platform.AddKeyOutcome
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentEndpoint
import io.github.raghavsatyadev.jobfinder.ui.platform.AgyStatus
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeysState
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.github.raghavsatyadev.jobfinder.ui.platform.OpenCodeStatus
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.PickedFile
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/** The jobs sort and the searches filter start fresh and are not saved on this computer. */
fun demoStoresModule(): Module = module {
  single<JobFilterStore> { NoJobFilterStore }
  single<LinksViewStore> { NoLinksViewStore }
}

/** The desktop-only parts of the app on sample data, for [DemoTour]. Nothing leaves the process. */
fun demoDesktopModule(setup: FakeSetupAssistant): Module = module {
  single<DesktopFeatures> { DemoDesktopFeatures }
  single<AiKeys> { DemoAiKeys }
  single<AgentAccess> { DemoAgentAccess }
  single<AiPromptsRepository> { DemoPrompts }
  single<FormAnswersRepository> { DemoFormAnswers }
  single<ResumeImproveRepository> { DemoImprove }
  single<SetupAssistant> { setup }
  single<ResumePdfRepository> { DemoPdfs }
  single<FilePicker> { DemoPicker }
}

private object DemoPdfs : ResumePdfRepository {
  private var status =
    ResumePdfStatus(
      linkedIn = LinkedInSummary(SampleData.NOW - 26 * 3_600_000, "Sam Sample", 4, 38, 3, 1, 2),
      hasResume = true,
    )

  override suspend fun status() = status

  override suspend fun savePrefs(prefs: ResumePdfPrefs) =
    status.copy(prefs = prefs).also { status = it }

  override suspend fun importLinkedIn(fileName: String, bytes: ByteArray) = status

  override suspend fun deleteLinkedIn() = status

  override suspend fun makePdf(request: MakeResumePdf) =
    ResumePdf("Sam Sample Resume.pdf", "", pages = 1, match = 91, baseMatch = 69)
}

/** Opens nothing and saves nothing. */
private object DemoPicker : FilePicker {
  override suspend fun pickResume(): PickedFile? = null

  override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray) = true
}

private object DemoDesktopFeatures : DesktopFeatures {
  override val pairing =
    MutableStateFlow<PairingInfo?>(PairingInfo("http://192.168.1.20:47821", "demo-pairing-code"))

  override suspend fun rotatePairingToken() = Unit

  override fun openLoginBrowser(url: String?) = Unit

  override suspend fun copyBrowserSignIns(): SignInCopy = SignInCopy.Copied(12, emptyList())

  @Composable
  override fun AddLinkBrowser(
    onPicked: (url: String, title: String) -> Unit,
    onCancel: () -> Unit,
  ) = Unit
}

private object DemoAiKeys : AiKeys {
  private val keys =
    listOf(
      AiKey("groq", AiProvider.GROQ, requestsPerMinute = 30, requestsPerDay = 1_000),
      AiKey("nvidia", AiProvider.NVIDIA, requestsPerMinute = 40),
      AiKey("openrouter", AiProvider.OPENROUTER, requestsPerMinute = 20, requestsPerDay = 50),
    )

  override suspend fun load() =
    AiKeysState(
      keys = keys,
      usage = listOf(AiKeyUsage("groq", 214, 61_800), AiKeyUsage("nvidia", 87, 40_200)),
      now = SampleData.NOW,
      openCode = OpenCodeStatus(true, "v2.0.31"),
      agy = AgyStatus(false),
    )

  override suspend fun add(secret: String, expiresAt: Long?) = AddKeyOutcome.Unknown

  override suspend fun setExpiry(id: String, expiresAt: Long?) = Unit

  override suspend fun remove(id: String) = Unit

  override suspend fun update(key: AiKey) = Unit

  override suspend fun models(id: String) = emptyList<String>()

  override suspend fun setRouting(routing: AiRouting) = Unit

  override suspend fun backupKeys() = emptyList<BackupApiKey>()

  override suspend fun restoreKeys(keys: List<BackupApiKey>) = 0
}

private object DemoAgentAccess : AgentAccess {
  override val endpoint = MutableStateFlow(AgentEndpoint("http://127.0.0.1:47822/mcp"))

  override fun token() = "demo-token"

  override fun rotateToken() = Unit
}

private object DemoPrompts : AiPromptsRepository {
  override suspend fun listPrompts() =
    PromptId.entries.map { AiPrompt(it, PromptBook.defaultText(it), PromptBook.defaultText(it)) }

  override suspend fun savePrompt(id: PromptId, text: String) =
    AiPrompt(id, text, PromptBook.defaultText(id))

  override suspend fun resetPrompt(id: PromptId) =
    AiPrompt(id, PromptBook.defaultText(id), PromptBook.defaultText(id))

  override suspend fun tryPrompt(id: PromptId, text: String) =
    PromptTrial(1, "Senior Android Engineer", "Northwind", excluded = false)
}

private object DemoFormAnswers : FormAnswersRepository {
  private val answers =
    listOf(
      answer(1, "boards.greenhouse.io", "Years of Android experience", "7"),
      answer(2, "boards.greenhouse.io", "Notice period", "30 days"),
      answer(3, "jobs.lever.co", "Why do you want to work here?", DemoData.WHY_US),
      answer(4, "jobs.lever.co", "LinkedIn profile", "https://www.linkedin.com/in/sample"),
      answer(5, "myworkdayjobs.com", "Expected salary (INR)", "48,00,000"),
    )

  override suspend fun listAnswers() = answers

  override suspend fun updateAnswer(id: Long, value: String) =
    answers.first { it.id == id }.copy(value = value)

  override suspend fun deleteAnswer(id: Long) = Unit

  private fun answer(id: Long, site: String, label: String, value: String) =
    FormAnswer(
      id = id,
      jobId = 1,
      site = site,
      url = "https://$site/apply",
      label = label,
      inputType = if (value.length > 40) "textarea" else "text",
      questionKey = label.lowercase(),
      value = value,
      createdAt = SampleData.NOW - id * 3_600_000,
      updatedAt = SampleData.NOW - id * 3_600_000,
      jobTitle = "Senior Android Engineer",
      companyName = "Northwind",
    )
}

private object DemoImprove : ResumeImproveRepository {
  override suspend fun requestImprovement(jobId: Long, write: Boolean) =
    DemoData.improvement(jobId)?.let { ImprovementStatus(ImprovementState.READY, it) }
      ?: ImprovementStatus(ImprovementState.NO_REVIEW)

  override suspend fun improvedResume(jobId: Long, edits: List<Int>) =
    ImprovedResume(DemoData.RESUME_TEXT)
}
