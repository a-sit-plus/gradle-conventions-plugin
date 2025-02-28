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

project.file("src/main/kotlin/BuildDate.kt").bufferedWriter().use { writer ->
    writer.write("package at.asitplus.gradle\nval buildDate = \"$buildDate\"")
}

val kotlinVersion = versions["kotlin"] as String
val ksp = "$kotlinVersion-${versions["ksp"]}"

val dokka = versions["dokka"]
val nexus = versions["nexus"]
val kotest = versions["kotest-plugin"]
val ktor = versions["ktor"]
val agp = versions["agp"]

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
    implementation("org.tomlj:tomlj:1.1.1")
    compileOnly("com.android.tools.build:gradle:$agp")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

gradlePlugin {
    plugins.register("asp-conventions") {
        id = "$groupId.conventions"
        implementationClass = "at.asitplus.gradle.K2Conventions"
    }
}

publishing {
    repositories {
        mavenLocal()
        maven {
            url = uri(rootProject.layout.projectDirectory.dir("repo"))
            name = "GitHub"
        }
    }
}

kotlin {
    jvmToolchain(17)
}
