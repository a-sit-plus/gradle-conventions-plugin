import org.gradle.configurationcache.problems.PropertyTrace
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
val kotest = versions["kotest-plugin"]

version = "$kotlinVersion+$buildDate"
group = groupId

dependencies {
    api(project(":legacy")){
        exclude(group = "io.kotest", module = "kotest-framework-multiplatform-plugin-gradle")
    }
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$ksp")
    api("io.kotest:kotest-framework-multiplatform-plugin-gradle:$kotest")
}

repositories {
    maven {
        url = uri("https://raw.githubusercontent.com/a-sit-plus/gradle-conventions-plugin/mvn/repo")
        name = "aspConventions"
    }  //KOTEST snapshot
    mavenCentral()
    gradlePluginPortal()
}


if(System.getProperty("at.asitplus.gradle") == "legacy")
    logger.lifecycle("  NOT registering A-SIT Plus K2 Conventions Plugin")
else gradlePlugin {
    println()
    plugins.register("asp-conventions") {
        id = "$groupId.conventions"
        implementationClass = "at.asitplus.gradle.AspConventions"
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