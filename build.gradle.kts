import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
    idea
    `maven-publish`
}
private val versions = Properties().apply {
    kotlin.runCatching { FileInputStream(rootProject.file("src/main/resources/versions.properties")).use { load(it) } }
}

val buildDate = "20231114"
group = "at.asitplus.gradle"
val kotlinVersion = versions["kotlin"] as String
version = "$kotlinVersion+$buildDate"

val dokka = versions["dokka"]
val nexus = versions["nexus"]
val kotest = versions["kotest"]
val ktor = versions["ktor"]
val ksp = "$kotlinVersion-${versions["ksp"]}"

dependencies {
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    api("io.ktor.plugin:plugin:$ktor")
    api("io.kotest:kotest-framework-multiplatform-plugin-gradle:$kotest")
    api("io.github.gradle-nexus:publish-plugin:$nexus")
    api("org.jetbrains.dokka:dokka-gradle-plugin:$dokka")
    api("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$ksp")
}

repositories {
    maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins.register("asp-conventions") {
        id = "at.asitplus.gradle.conventions"
        implementationClass = "at.asitplus.gradle.AspConventions"
    }
}

publishing {
    repositories {
        maven {
            url = uri(layout.projectDirectory.dir("repo"))
            name = "GitHub"
        }
    }
}