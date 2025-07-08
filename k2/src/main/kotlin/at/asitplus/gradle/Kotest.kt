package at.asitplus.gradle.at.asitplus.gradle

import at.asitplus.gradle.AspVersions
import at.asitplus.gradle.Logger
import at.asitplus.gradle.hasJvmTarget
import at.asitplus.gradle.kotest
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler



internal fun KotlinMultiplatformExtension.defaultSetupKotest() {
    sourceSets {
        commonTest {
            dependencies {
                addKotestExtensions()
            }
        }
        if (hasJvmTarget()) jvmTest {
            dependencies {
                addKotestJvmRunner()
            }
        }
    }
}

/**
 * Adds Kotest (to test dependencies, as it is called there)
 * * assertions-core
 * * common
 * * property
 * * datatest
 *
 * Also adds `kotlin-reflect` to make kotest work smoothly with IDEA
 */
inline fun KotlinDependencyHandler.addKotestExtensions(target: String? = null) {
    val targetInfo = target?.let { " ($it)" } ?: ""
    Logger.lifecycle("  Adding Kotest libraries")
    Logger.info("   * Assertions$targetInfo")
    Logger.info("   * Property-based testing$targetInfo")
    Logger.info("   * Datatest$targetInfo")
    implementation(kotlin("reflect"))
    implementation(project.kotest("assertions-core", target))
    implementation(project.kotest("common", target))
    implementation(project.kotest("property", target))
    implementation(project.kotest("framework-engine", target))
}



inline fun KotlinDependencyHandler.addKotestJvmRunner() {
    Logger.info("  Adding Kotest JUnit runner")
    implementation(project.kotest("runner-junit5", "jvm"))
}
