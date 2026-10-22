import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.multiplatform.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room3)
}

// The phone's offline copy (PhoneCacheDatabase); any client platform can keep one.
room3 { schemaDirectory("$projectDir/schemas") }

kotlin {
  android {
    namespace = libs.versions.sharedAndroidId.get()
    compileSdk {
      version =
        release(libs.versions.compileSdk.get().toInt()) {
          minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    minSdk = libs.versions.minSdk.get().toInt()
    androidResources.enable = true
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
  }

  // Same bytecode level as :server and :desktopCMP (toolchain 21), which load these classes.
  jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

  // The iOS app; :iosCMP builds the framework Xcode links.
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    androidMain.dependencies {
      implementation(libs.compose.ui.preview)
      implementation(libs.compose.activity)
    }

    commonMain.dependencies {
      api(projects.shared)
      implementation(libs.bundles.compose.mp)
      implementation(libs.bundles.koin.mp)
      implementation(libs.bundles.lifecycle.mp)
      implementation(libs.bundles.navigation.mp)
      implementation(libs.kotlinx.collections.immutable)
      implementation(libs.markdown.renderer.m3)
      implementation(libs.reorderable)
      implementation(libs.atomicfu)
      implementation(libs.coil.compose.mp)
      implementation(libs.coil.network.ktor.mp)
      implementation(libs.qrose)
      implementation(libs.multiplatform.settings.no.arg)
      implementation(libs.androidx.room3.runtime)
      implementation(libs.androidx.sqlite.bundled)
    }
  }

  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}

compose.resources {
  packageOfResClass = "io.github.raghavsatyadev.jobfinder.resources"
  publicResClass = true
}

dependencies {
  androidRuntimeClasspath(libs.compose.ui.tooling)
  add("kspAndroid", libs.androidx.room3.compiler)
  add("kspJvm", libs.androidx.room3.compiler)
  add("kspIosArm64", libs.androidx.room3.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}
