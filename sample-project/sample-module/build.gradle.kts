import at.asitplus.gradle.kmmresult
import at.asitplus.gradle.napier
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

dependencies {
    api(kmmresult())
    implementation(napier())
    implementation("at.asitplus.signum:indispensable:3.16.1")
}



catalog {
  versionCatalog {
      plugin("ksp", "com.google.devtools.ksp").versionRef("ksp")
  }
}