import java.io.FileInputStream
import java.util.*

plugins {
    `kotlin-dsl`
    idea
    `maven-publish`
}
private val versions = Properties().apply {
    kotlin.runCatching { load(FileInputStream(rootProject.file("src/main/resources/versions.properties"))) }
}

val buildDate = "20230621"
group = "at.asitplus.gradle"
val kotlinVersion = versions["kotlin"] as String
version = "$kotlinVersion+$buildDate"

val dokka = versions["dokka"]
val nexus = versions["nexus"]
val kotest = versions["kotest"]
val jvmTarget = versions["jvmTarget"] as String


idea {
    project {
        jdkName = jvmTarget
    }
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    api("io.kotest:kotest-framework-multiplatform-plugin-gradle:$kotest")
    api("io.github.gradle-nexus:publish-plugin:$nexus")
    api("org.jetbrains.dokka:dokka-gradle-plugin:$dokka")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(jvmTarget))
    }
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