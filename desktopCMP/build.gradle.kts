import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin { jvmToolchain(21) }

/** ProGuard for `packageRelease*`; the plugin's default (7.8) is older. */
val PROGUARD_VERSION = "7.10.0"

val isLinux = System.getProperty("os.name").startsWith("Linux")
val isMac = System.getProperty("os.name").startsWith("Mac")

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(projects.composeApp)
  // Material 3 and resources for the desktop-only composables (the add-search browser).
  implementation(libs.bundles.compose.mp)
  implementation(projects.server)
  // The demo tour (dev) hosts the screens without a window.
  implementation(libs.bundles.lifecycle.mp)
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
  when {
    isLinux -> runtimeOnly(libs.jcef.natives.linux.amd64)
    isMac -> runtimeOnly(libs.jcef.natives.macosx.arm64)
    else -> runtimeOnly(libs.jcef.natives.windows.amd64)
  }
}

configurations.runtimeClasspath {
  // JOGL only draws JCEF's off-screen browsers; EmbeddedBrowser and HiddenBrowser are windowed.
  exclude(group = "me.friwi", module = "jogl-all")
  exclude(group = "me.friwi", module = "gluegen-rt")
  // The API server speaks HTTP/1.1 on Netty: no HTTP/3 (QUIC). The kqueue and epoll jars stay:
  // Ktor checks `KQueue`/`Epoll.isAvailable()` on start.
  exclude(group = "io.netty", module = "netty-codec-http3")
  exclude(group = "io.netty", module = "netty-codec-classes-quic")
  exclude(group = "io.netty", module = "netty-codec-native-quic")
}

/**
 * JCEF reaches into JDK internals. Applied through `application.jvmArgs`, which both `run` and the
 * packaged app's launcher config use.
 */
val jcefJvmArgs =
  listOf(
    "--add-exports=java.base/java.lang=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
    // macOS: JCEF's windowed browser reaches into the Cocoa AWT peers.
    "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    // JNA and the bundled SQLite load native code.
    "--enable-native-access=ALL-UNNAMED",
  )

compose.desktop {
  application {
    mainClass = "io.github.raghavsatyadev.jobfinder.MainKt"
    // macOS: Android Studio's runtime has no jpackage, so packaging uses a full JDK 21 (Temurin,
    // fetched once by the foojay toolchain resolver).
    if (isMac) {
      javaHome =
        javaToolchains
          .launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
          }
          .get()
          .metadata
          .installationPath
          .asFile
          .absolutePath
    }
    jvmArgs += jcefJvmArgs
    // Without a cap the JVM may grow its heap to a quarter of the PC's memory.
    jvmArgs += "-Xmx1g"
    // The menu bar and Dock name in dev runs; the packaged app takes its bundle's name.
    if (isMac) jvmArgs += listOf("-Xdock:name=JobFinder", "-Dapple.awt.application.name=JobFinder")
    // `packageReleaseMsi`: ProGuard drops unused classes (most of the extended icons). Shrink
    // only: obfuscation and optimization break the libraries that load classes by name.
    buildTypes.release.proguard {
      version.set(PROGUARD_VERSION)
      configurationFiles.from(project.file("proguard-rules.pro"))
      obfuscate.set(false)
      optimize.set(false)
    }
    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
      // `common/browser-extension`: the built extension, for "Load unpacked" (BundledExtension).
      appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
      packageName = libs.versions.releaseAppName.get()
      packageVersion = libs.versions.versionName.get()
      windows {
        iconFile.set(project.file("icons/app.ico"))
        menuGroup = libs.versions.releaseAppName.get()
        shortcut = true
        // Installs for the current user only, with no admin prompt; the data stays in
        // %LOCALAPPDATA%\jobfinder-data. One fixed id so each new version upgrades the old one.
        perUserInstall = true
        dirChooser = false
        upgradeUuid = "07242878-9a13-4a90-b785-2138acca2b4e"
      }
      macOS {
        iconFile.set(project.file("icons/app.icns"))
        bundleID = libs.versions.appIdProd.get()
        appCategory = "public.app-category.productivity"
        // macOS needs 1.x.y or later; the app's 0.0.x maps to 1.0.x.
        packageVersion = "1.0." + libs.versions.versionName.get().substringAfterLast('.')
        // Phones find the computer over Bonjour; macOS asks the user once (Local Network).
        infoPlist {
          extraKeysRawXml =
            """
            <key>NSLocalNetworkUsageDescription</key>
            <string>JobFinder lets your phone find and reach this Mac on your Wi-Fi.</string>
            <key>NSBonjourServices</key>
            <array><string>_jobfinder._tcp</string></array>
            """
              .trimIndent()
        }
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

/**
 * npm from the PATH, else from where macOS installs put it (Homebrew, a user install in
 * `~/.local/node`): a Gradle daemon started from an IDE has no shell PATH.
 */
fun npm(): String {
  if (System.getProperty("os.name").startsWith("Windows")) return "npm.cmd"
  val home = System.getProperty("user.home")
  val dirs =
    System.getenv("PATH").orEmpty().split(File.pathSeparator) +
      listOf("/opt/homebrew/bin", "/usr/local/bin", "$home/.local/node/bin")
  return dirs.map { File(it, "npm") }.firstOrNull { it.canExecute() }?.path ?: "npm"
}

// The browser extension, built with npm and bundled into the app's resources.
val extensionDir = rootProject.layout.projectDirectory.dir("browser-extension")
val buildExtension by
  tasks.registering(Exec::class) {
    group = "build"
    workingDir = extensionDir.asFile
    commandLine(npm(), "run", "build")
    // npm runs node from its own folder, which a Gradle daemon's PATH may not hold.
    environment("PATH", File(npm()).parent + File.pathSeparator + System.getenv("PATH").orEmpty())
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

// Dev tool: records the app on sample data for the README (DemoTour). `-PtourScript=<file>
// -PtourOut=<dir>`; needs ffmpeg on the PATH.
tasks.register<JavaExec>("demoTour") {
  group = "documentation"
  mainClass = "io.github.raghavsatyadev.jobfinder.desktop.dev.DemoTourKt"
  classpath = sourceSets["main"].runtimeClasspath
  workingDir = gradle.startParameter.currentDir
  jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
  args(
    providers.gradleProperty("tourScript").orNull ?: "tour.txt",
    providers.gradleProperty("tourOut").orNull ?: "tour",
  )
}

// Resume PDFs from a made-up resume and LinkedIn export (or a real export: -PlinkedInZip=...).
tasks.register<JavaExec>("resumePdfProbe") {
  group = "verification"
  mainClass = "io.github.raghavsatyadev.jobfinder.desktop.dev.ResumePdfProbeKt"
  classpath = sourceSets["main"].runtimeClasspath
  workingDir = gradle.startParameter.currentDir
  args(
    listOfNotNull(
      providers.gradleProperty("probeOut").orNull ?: "tmp/resume-probe",
      providers.gradleProperty("linkedInZip").orNull,
    )
  )
}

/**
 * ProGuard cannot read the Kotlin 2.5 stdlib's metadata yet (proguard-core 9.4 fails on a new
 * contract effect). It only reads metadata when `kotlin.Metadata` is kept, and Compose's default
 * rules keep all of `kotlin.**`, so the release build uses a copy of those rules without it. The
 * app does no full Kotlin reflection, so the stripped metadata is not missed.
 */
val composeRulesWithoutMetadata by tasks.registering {
  val unpacked =
    tasks.named("unpackDefaultComposeDesktopJvmApplicationResources").map {
      it.outputs.files.asFileTree.matching { include("**/default-compose-desktop-rules.pro") }
    }
  val out = layout.buildDirectory.file("compose/proguard/compose-rules.pro")
  inputs.files(unpacked)
  outputs.file(out)
  doLast {
    val original = unpacked.get().singleFile
    val text = original.readText()
    val keepKotlin = "-keep class kotlin.** { *; }"
    check(keepKotlin in text) { "Compose's ProGuard rules changed: recheck $original" }
    out
      .get()
      .asFile
      .writeText(text.replace(keepKotlin, "-keep class !kotlin.Metadata,kotlin.** { *; }"))
  }
}

// After the Compose plugin, which sets its own rules file in afterEvaluate.
afterEvaluate {
  tasks
    .withType<org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask>()
    .configureEach {
      defaultComposeRulesFile.set(
        composeRulesWithoutMetadata.map { RegularFile { it.outputs.files.singleFile } }
      )
    }
}
