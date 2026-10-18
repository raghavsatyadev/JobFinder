import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(projects.composeApp)
  // Material 3 and resources for the desktop-only composables (the add-search browser).
  implementation(libs.bundles.compose.mp)
  implementation(projects.server)
  implementation(libs.kotlinx.coroutines.swing)
  implementation(libs.koin.core)
  implementation(libs.logback.classic)
  implementation(libs.jcefmaven)
  // Windows calls: keep the PC awake during scans, and the notification-area icon.
  implementation(libs.jna.platform)
  // Announces the API server on the LAN so phones find it without typing an address.
  implementation(libs.jmdns)
  // Bundled CEF natives for the OS that builds the package: jcefmaven extracts them from the
  // classpath, so there is no download. jpackage builds only for its own OS anyway.
  if (System.getProperty("os.name").startsWith("Linux")) {
    runtimeOnly(libs.jcef.natives.linux.amd64)
  } else {
    runtimeOnly(libs.jcef.natives.windows.amd64)
  }
}

/**
 * JCEF (and JOGL, which jcefmaven pulls in) reach into JDK internals. Applied through
 * `application.jvmArgs`, which both `run` and the packaged app's launcher config use.
 */
val jcefJvmArgs =
  listOf(
    "--add-exports=java.base/java.lang=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
    // JNA and the bundled SQLite load native code.
    "--enable-native-access=ALL-UNNAMED",
  )

compose.desktop {
  application {
    mainClass = "io.github.raghavsatyadev.jobfinder.MainKt"
    jvmArgs += jcefJvmArgs
    // Without a cap the JVM may grow its heap to a quarter of the PC's memory.
    jvmArgs += "-Xmx1g"
    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Deb)
      // `common/browser-extension`: the built extension, for "Load unpacked" (BundledExtension).
      appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
      packageName = libs.versions.releaseAppName.get()
      packageVersion = libs.versions.versionName.get()
      windows {
        iconFile.set(project.file("icons/app.ico"))
        menuGroup = libs.versions.releaseAppName.get()
        shortcut = true
        // Installs for the current user only, with no admin prompt; the data stays in
        // %LOCALAPPDATA%\jobfinder. One fixed id so each new version upgrades the old one.
        perUserInstall = true
        dirChooser = false
        upgradeUuid = "07242878-9a13-4a90-b785-2138acca2b4e"
      }
      linux {
        iconFile.set(project.file("icons/app.png"))
        packageName = "jobfinder"
        menuGroup = "Office"
        shortcut = true
        debMaintainer = "raghavsatyadev@gmail.com"
        appCategory = "office"
      }
      // From `./gradlew :desktopCMP:suggestRuntimeModules`, plus java.logging (jcefmaven logs with
      // java.util.logging).
      modules("java.instrument", "java.logging", "java.net.http", "java.sql", "jdk.unsupported")
    }
  }
}

// Dev commands (`--args="capture <url> <out.html>"`) take paths relative to where Gradle started.
tasks
  .withType<JavaExec>()
  .matching { it.name == "run" }
  .configureEach {
    workingDir = gradle.startParameter.currentDir
    // Dev runs only: `-Pf2aDevAutoApprove=true` allows phone requests without a click
    // (DevApprovals).
    providers.gradleProperty("f2aDevAutoApprove").orNull?.let {
      systemProperty("jobfinder.dev.autoApprove", it)
    }
  }

// Dev tool: replays the AI filter and ATS review on each online model, one task per request and
// merged (`--args="<report.md> [jobs]"`). Reads a copy of the app's database.
tasks.register<JavaExec>("aiReplay") {
  group = "verification"
  mainClass = "io.github.raghavsatyadev.jobfinder.desktop.dev.AiReplayKt"
  classpath = sourceSets["main"].runtimeClasspath
  workingDir = gradle.startParameter.currentDir
  jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx1g")
}

tasks.register<JavaExec>("readPageProbe") {
  group = "verification"
  mainClass = "io.github.raghavsatyadev.jobfinder.desktop.dev.ReadPageProbeKt"
  classpath = sourceSets["main"].runtimeClasspath
  workingDir = gradle.startParameter.currentDir
  jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// The browser extension, built with npm and bundled into the app's resources.
val extensionDir = rootProject.layout.projectDirectory.dir("browser-extension")
val buildExtension by
  tasks.registering(Exec::class) {
    group = "build"
    workingDir = extensionDir.asFile
    val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
    commandLine(npm, "run", "build")
    inputs.dir(extensionDir.dir("src"))
    inputs.dir(extensionDir.dir("static"))
    inputs.file(extensionDir.file("build.mjs"))
    outputs.dir(extensionDir.dir("dist"))
  }
val extensionResources by
  tasks.registering(Sync::class) {
    dependsOn(buildExtension)
    from(extensionDir.dir("dist"))
    into(layout.buildDirectory.dir("appResources/common/browser-extension"))
  }

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(extensionResources) }
