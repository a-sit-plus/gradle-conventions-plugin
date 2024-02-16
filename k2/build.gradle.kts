import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
    idea
    `maven-publish`
}

private val versions = Properties().apply {
    kotlin.runCatching {
        FileInputStream(rootProject.file("legacy/src/main/resources/versions.properties")).use { load(it) }
        FileInputStream(project.file("src/main/resources/k2versions.properties")).use { load(it) }
    }
}

val groupId: String by extra
val buildDate: String by extra

val kotlinVersion = versions["kotlin"] as String
val ksp = "$kotlinVersion-${versions["ksp"]}"

version = "$kotlinVersion+$buildDate"
group = groupId


dependencies {
    api(project(":legacy"))
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$ksp")
}

repositories {
    maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins.register("asp-conventions") {
        id = "$groupId.conventions"
        implementationClass = "at.asitplus.gradle.AspConventions"
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