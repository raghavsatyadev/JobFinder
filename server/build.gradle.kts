plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room3)
}

kotlin { jvmToolchain(21) }

room3 { schemaDirectory("$projectDir/schemas") }

dependencies {
  api(projects.shared)

  api(libs.androidx.room3.runtime)
  implementation(libs.androidx.sqlite.bundled)
  ksp(libs.androidx.room3.compiler)

  implementation(libs.bundles.ktor.server)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.jsoup)
  implementation(libs.pdfbox)
  implementation(libs.flexmark.html2md)
  implementation(libs.cron.utils)
  implementation(libs.logback.classic)
}
