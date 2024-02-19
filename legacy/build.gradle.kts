import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
    idea
    `maven-publish`
}


private val versions = Properties().apply {
    kotlin.runCatching {
        FileInputStream(project.file("src/main/resources/versions.properties")).use { load(it) }
    }
}
val groupId: String by extra
val buildDate: String by extra

val kotlinVersion = versions["kotlin"] as String
val ksp = "$kotlinVersion-${versions["ksp"]}"

val dokka = versions["dokka"]
val nexus = versions["nexus"]
val kotest = versions["kotest"]
val ktor = versions["ktor"]

version = "$kotlinVersion+$buildDate"
group = groupId

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
    plugins.register("asp-conventions-legacy") {
        id = "$groupId.conventions"
        implementationClass = "at.asitplus.gradle.AspLegacyConventions"
    }
}

publishing {
    repositories {
        maven {
            url = uri(rootProject.layout.projectDirectory.dir("repo"))
            name = "GitHub"
        }
    }
}