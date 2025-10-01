import at.asitplus.gradle.kotest
import at.asitplus.gradle.publishVersionCatalog
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.util.*

System.setProperty("KOTEST_NO_ASP_HELPER", "true")

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("at.asitplus.gradle.conventions")
}
group = "at.asitplus.gradle"
version = Properties().apply {
    load(project.rootProject.layout.projectDirectory.dir("..").file("gradle.properties").asFile.inputStream())
}.getProperty("buildDate")


repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    google()
}

publishVersionCatalog = false

kotlin {
    jvm()
    androidTarget { publishLibraryVariants("release") }
    macosArm64()
    macosX64()
    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosDeviceArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()
    androidNativeX64()
    androidNativeX86()
    androidNativeArm32()
    androidNativeArm64()
    //wasmWasi(nodeJs())
    listOf(
        js().apply { browser { testTask { enabled = false } } },
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs().apply { browser { testTask { enabled = false } } }
    ).forEach {
        it.nodejs()
        it.browser()
    }

    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                api(kotest("property"))
                api("de.infix.testBalloon:testBalloon-framework-core:${libs.versions.testballoon.get()}")
                api("de.infix.testBalloon:testBalloon-integration-kotest-assertions:${libs.versions.testballoon.get()}")
            }
        }
    }
}

android {
    namespace = "at.asitplus.gradle.testballoonshim"
    packaging {
        listOf(
            "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
            "org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties",
            "org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties",
            "org/bouncycastle/x509/CertPathReviewerMessages_de.properties",
            "org/bouncycastle/x509/CertPathReviewerMessages.properties",
            "org/bouncycastle/pkix/CertPathReviewerMessages_de.properties",
            "org/bouncycastle/pkix/CertPathReviewerMessages.properties",
            "/META-INF/{AL2.0,LGPL2.1}",
            "win32-x86-64/attach_hotspot_windows.dll",
            "win32-x86/attach_hotspot_windows.dll",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "META-INF/licenses/*",
        ).forEach { resources.excludes.add(it) }
    }

}

// we don't have native android tests independent of our regular test suite.
// this task expect those and fails, since no tests are present, so we disable it.
project.gradle.taskGraph.whenReady {
    tasks.getByName("testDebugUnitTest") {
        enabled = false
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("Testballoon Shim")
                description.set("Testballoon FreeSpec Shim")
                url.set("https://github.com/a-sit-plus/gradle-conventions-plugin")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("JesusMcCloud")
                        name.set("Bernd Prünster")
                        email.set("bernd.pruenster@a-sit.at")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:a-sit-plus/gradle-conventions-plugin.git")
                    developerConnection.set("scm:git:git@github.com:a-sit-plus/gradle-conventions-plugin.git")
                    url.set("https://github.com/a-sit-plus/gradle-conventions-plugin")
                }
            }
        }
    }
    repositories {
        mavenLocal()
        maven {
            url = uri(rootProject.layout.projectDirectory.dir("..").dir("repo"))
            name = "GitHub"
        }
    }
}
