plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

kotlin { jvmToolchain(21) }

application { mainClass = "io.github.raghavsatyadev.jobfinder.migrator.MainKt" }

dependencies {
  implementation(projects.server)
  implementation(libs.postgresql)
  implementation(libs.logback.classic)
}
