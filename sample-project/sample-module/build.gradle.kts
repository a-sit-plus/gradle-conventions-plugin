import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm")
    id("at.asitplus.gradle.conventions")
}


group = "org.example"
version = "1.0-SNAPSHOT"


publishing {
    repositories { mavenLocal() }
}

catalog {
  versionCatalog {
      plugin("ksp", "com.google.devtools.ksp").versionRef("ksp")
  }
}