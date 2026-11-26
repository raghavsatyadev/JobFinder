// `packageMsix`: the Windows Store package (MSIX) of the release app image, which ProGuard has
// shrunk (`createReleaseDistributable`). Needs the Windows SDK (makepri, makeappx, signtool).
// Unsigned unless MSIX_CERT (a .pfx) is set, with MSIX_CERT_PASSWORD: the Store signs its own copy,
// a local install needs a certificate this computer trusts.

val msixVersion =
  rootProject.extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("versionName")
    .get()
    .requiredVersion + ".0"
val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/JobFinder")
val msixDir = layout.buildDirectory.dir("msix")

/** The newest Windows SDK tool, e.g. `makeappx.exe`. */
fun sdkTool(name: String): String {
  val bin =
    File(System.getenv("ProgramFiles(x86)") ?: "C:/Program Files (x86)", "Windows Kits/10/bin")
  return bin
    .listFiles()
    .orEmpty()
    .filter { it.name.startsWith("10.") }
    .sortedByDescending { dir ->
      dir.name.split('.').map { it.toIntOrNull() ?: 0 }.fold(0L) { a, n -> a * 100000 + n }
    }
    .map { it.resolve("x64/$name") }
    .firstOrNull { it.isFile }
    ?.absolutePath ?: error("$name not found: install the Windows SDK")
}

tasks.register("packageMsix") {
  group = "compose desktop"
  description = "Packages the ProGuard release app image as an MSIX."
  dependsOn("createReleaseDistributable")
  inputs.dir(appImage)
  inputs.dir("msix")
  outputs.dir(msixDir)
  doLast {
    val out = msixDir.get().asFile
    val root = out.resolve("layout")
    out.deleteRecursively()
    copy {
      from(appImage)
      into(root)
    }
    copy {
      from("msix/Assets")
      into(root.resolve("Assets"))
    }
    root
      .resolve("AppxManifest.xml")
      .writeText(file("msix/AppxManifest.xml").readText().replace("@VERSION@", msixVersion))
    fun run(vararg args: String) {
      val process = ProcessBuilder(*args).directory(out).redirectErrorStream(true).start()
      val log = process.inputStream.bufferedReader().readText()
      check(process.waitFor() == 0) { "${File(args[0]).name} failed:\n$log" }
    }
    val pri = out.resolve("priconfig.xml").absolutePath
    run(sdkTool("makepri.exe"), "createconfig", "/cf", pri, "/dq", "en-US", "/o")
    run(
      sdkTool("makepri.exe"),
      "new",
      "/pr",
      root.absolutePath,
      "/cf",
      pri,
      "/of",
      root.resolve("resources.pri").absolutePath,
      "/o",
    )
    val msix = out.resolve("JobFinder-$msixVersion.msix")
    run(sdkTool("makeappx.exe"), "pack", "/d", root.absolutePath, "/p", msix.absolutePath, "/o")
    val cert = System.getenv("MSIX_CERT")
    if (!cert.isNullOrBlank()) {
      run(
        sdkTool("signtool.exe"),
        "sign",
        "/fd",
        "SHA256",
        "/f",
        cert,
        "/p",
        System.getenv("MSIX_CERT_PASSWORD").orEmpty(),
        msix.absolutePath,
      )
    }
    logger.lifecycle("MSIX: ${msix.absolutePath}")
  }
}
