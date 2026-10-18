package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheckMiss
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheckState
import io.github.raghavsatyadev.jobfinder.ui.platform.HfQuant
import io.github.raghavsatyadev.jobfinder.ui.platform.HfRepo
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModel
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import io.github.raghavsatyadev.jobfinder.ui.settings.AddModelState
import io.github.raghavsatyadev.jobfinder.ui.settings.AddTab
import io.github.raghavsatyadev.jobfinder.ui.settings.AiModelContent
import io.github.raghavsatyadev.jobfinder.ui.settings.AiNotice
import io.github.raghavsatyadev.jobfinder.ui.settings.AiUiState
import io.github.raghavsatyadev.jobfinder.ui.settings.LocalModelsSection
import io.github.raghavsatyadev.jobfinder.ui.settings.LocalModelsUiState

/** Settings → AI model on the desktop: the local model, saved. */
@PreviewTest
@StatePreviews
@Composable
fun AiModelLocalScreenshot() {
  ScreenshotHost { AiModelContent(AiUiState(loading = false), onEvent = {}) }
}

/** An online provider with a saved key, after a test that worked. */
@PreviewTest
@StatePreviews
@Composable
fun AiModelOnlineScreenshot() {
  ScreenshotHost {
    AiModelContent(
      AiUiState(
        loading = false,
        saved = AiSettings(AiProvider.ANTHROPIC, "claude-haiku-5-5"),
        provider = AiProvider.ANTHROPIC,
        model = "claude-haiku-5-5",
        keysSaved = setOf(AiProvider.ANTHROPIC),
        notice = AiNotice.Works,
      ),
      onEvent = {},
    )
  }
}

/** A custom server that didn't answer. */
@PreviewTest
@StatePreviews
@Composable
fun AiModelFailedScreenshot() {
  ScreenshotHost {
    AiModelContent(
      AiUiState(
        loading = false,
        provider = AiProvider.CUSTOM,
        model = "qwen3-8b",
        baseUrl = "http://localhost:1234/v1",
        notice = AiNotice.Failed("The AI server failed twice: Connection refused"),
      ),
      onEvent = {},
    )
  }
}

/** The models on this computer: the PC's tier, three models, and a partial check. */
@PreviewTest
@ScreenPreviews
@Composable
fun LocalModelsScreenshot() {
  ScreenshotHost {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
      LocalModelsSection(
        LocalModelsUiState(
          loading = false,
          profile = PcProfile(15.9, "NVIDIA GeForce RTX 4060", 8.6, 212.0),
          models = MODELS,
          result = PARTIAL,
        ),
        active = "jobfinder-qwen:latest",
        onEvent = {},
        onUse = {},
      )
    }
  }
}

/** A 64 GB PC with a 16 GB card: the app's model runs, but partly on the processor. */
@PreviewTest
@StatePreviews
@Composable
fun LocalModelsSplitScreenshot() {
  ScreenshotHost {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
      LocalModelsSection(
        LocalModelsUiState(
          loading = false,
          profile = PcProfile(63.6, "NVIDIA GeForce RTX 5080", 15.9, 243.0),
          models = MODELS.take(1),
        ),
        active = "jobfinder-qwen:latest",
        onEvent = {},
        onUse = {},
      )
    }
  }
}

/** Adding from Hugging Face: a search, a repository picked and its sizes. */
@PreviewTest
@StatePreviews
@Composable
fun LocalModelsAddScreenshot() {
  ScreenshotHost {
    LocalModelsSection(
      LocalModelsUiState(
        loading = false,
        models = MODELS.take(1),
        add =
          AddModelState(
            tab = AddTab.HUGGING_FACE,
            hfQuery = "qwen3 8b",
            hfResults =
              listOf(
                HfRepo("unsloth/Qwen3-8B-GGUF", 412_000, 90),
                HfRepo("Qwen/Qwen3-8B-GGUF", 98_000, 40),
              ),
            hfRepo = "unsloth/Qwen3-8B-GGUF",
            quants = listOf(HfQuant("Q3_K_M", 4_120_000_000), HfQuant("Q4_K_M", 5_030_000_000)),
            quant = "Q4_K_M",
          ),
      ),
      active = "jobfinder-qwen:latest",
      onEvent = {},
      onUse = {},
    )
  }
}

/** A model downloading, and Ollama not running on another PC. */
@PreviewTest
@StatePreviews
@Composable
fun LocalModelsDownloadingScreenshot() {
  ScreenshotHost {
    Column(Modifier.padding(16.dp)) {
      LocalModelsSection(
        LocalModelsUiState(
          loading = false,
          profile = PcProfile(7.8, null, null, 40.0),
          models = MODELS.take(1),
          progress = ModelProgress.Downloading(2_100_000_000, 5_030_000_000),
        ),
        active = "jobfinder-qwen:latest",
        onEvent = {},
        onUse = {},
      )
      LocalModelsSection(
        LocalModelsUiState(loading = false, ollamaRunning = false),
        active = "jobfinder-qwen:latest",
        onEvent = {},
        onUse = {},
      )
    }
  }
}

private val PARTIAL =
  ModelCheck(
    model = "jobfinder-qwen3-8b-q4_k_m",
    state = ModelCheckState.PARTIAL,
    percent = 85,
    agreed = 12,
    checks = 14,
    failedAnswers = 1,
    misses =
      listOf(
        ModelCheckMiss(
          ModelCheckMiss.Kind.MATCHING,
          "Senior Android Engineer",
          "Air Arabia",
          "kept",
          "excluded",
        ),
        ModelCheckMiss(
          ModelCheckMiss.Kind.ATS,
          "Android SDE 2",
          "Primetrace",
          "7.9, shortlisted",
          null,
        ),
      ),
    secondsPerJob = 0.4,
    secondsPerReview = 22.0,
    memoryGb = 7.3,
    gpuMemoryGb = 7.3,
    checkedAt = 0,
  )

private val MODELS =
  listOf(
    LocalModel("jobfinder-qwen:latest", 22_500_000_000),
    LocalModel("jobfinder-qwen3-8b-q4_k_m:latest", 5_030_000_000, PARTIAL),
    LocalModel(
      "jobfinder-qwen3-4b-instruct-2507-q4_k_m:latest",
      2_500_000_000,
      PARTIAL.copy(model = "jobfinder-qwen3-4b", state = ModelCheckState.MATCH, percent = 100),
    ),
  )
