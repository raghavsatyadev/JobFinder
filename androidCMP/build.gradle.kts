import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.screenshot)
}

kotlin { jvmToolchain(21) }

// Release signing, from the untracked secret.properties at the repo root. Without it the release
// build is left unsigned rather than failing.
val signingProps =
  Properties().apply {
    rootProject.file("secret.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
  }

android {
  experimentalProperties["android.experimental.enableScreenshotTest"] = true
  namespace = libs.versions.nameSpace.get()
  compileSdk {
    version =
      release(libs.versions.compileSdk.get().toInt()) {
        minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
      }
  }
  buildToolsVersion = libs.versions.buildTools.get()

  defaultConfig {
    applicationId = libs.versions.appIdProd.get()
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = libs.versions.versionCode.get().toInt()
    versionName = libs.versions.versionName.get()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    resValue("string", "app_name", libs.versions.releaseAppName.get())
  }
  buildFeatures { resValues = true }
  signingConfigs {
    create("release") {
      signingProps.getProperty("storeFile")?.let { storeFile = file(it) }
      storePassword = signingProps.getProperty("storePassword")
      keyAlias = signingProps.getProperty("keyAlias")
      keyPassword = signingProps.getProperty("keyPassword")
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (signingProps.getProperty("storeFile") != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(projects.composeApp)
  implementation(libs.compose.activity)
  implementation(libs.compose.foundation.mp)
  implementation(libs.compose.ui.preview)
  implementation(libs.koin.android)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.play.services.code.scanner)
  implementation(libs.compose.components.resources.mp)
  implementation(libs.androidx.room3.runtime)
  debugImplementation(libs.compose.ui.tooling)

  // Testing
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.ext)
  androidTestImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)

  screenshotTestImplementation(libs.screenshot.validation.api)
  screenshotTestImplementation(libs.compose.ui.tooling)
  screenshotTestImplementation(libs.bundles.compose.mp)
  screenshotTestImplementation(libs.koin.compose.mp)
}

// Rendering every screenshot preview needs more than the test JVM's 512 MB default.
tasks.withType<Test>().configureEach { maxHeapSize = "2g" }
