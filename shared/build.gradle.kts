import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.multiplatform.library)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = libs.versions.sharedModuleAndroidId.get()
    compileSdk {
      version =
        release(libs.versions.compileSdk.get().toInt()) {
          minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    minSdk = libs.versions.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
  }

  // Same bytecode level as :server and :desktopCMP (toolchain 21), which load these classes.
  jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.serialization.json)
      api(libs.kotlinx.coroutines.core)
      api(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.client.auth)
      implementation(libs.ktor.serialization.kotlinx.json)
      implementation(libs.cryptography.core)
    }
    jvmMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.cryptography.provider.jdk)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.cryptography.provider.jdk)
    }
  }

  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}
