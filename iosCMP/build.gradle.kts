plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

// The iOS app's Kotlin half: the iOS implementations of the platform interfaces and the entry
// point. It builds the `JobFinderKit` framework the Xcode project in `iosApp/` links.
kotlin {
  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "JobFinderKit"
      isStatic = true
    }
  }

  sourceSets {
    iosMain.dependencies {
      implementation(projects.composeApp)
      implementation(libs.bundles.compose.mp)
      implementation(libs.bundles.koin.mp)
      implementation(libs.bundles.lifecycle.mp)
      implementation(libs.ktor.client.darwin)
      implementation(libs.ktor.network)
      implementation(libs.multiplatform.settings)
      implementation(libs.androidx.room3.runtime)
    }
  }
}
