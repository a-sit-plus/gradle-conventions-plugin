package at.asitplus.gradle.at.asitplus.gradle

import at.asitplus.gradle.*
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

/**
 * Adds Kotest (to test dependencies, as it is called there)
 * * assertions-core
 * * property
 * * datatest
 * * Testballoon Addons
 *
 * Also adds `kotlin-reflect` to make kotest work smoothly with IDEA
 */
inline fun KotlinDependencyHandler.addTestExtensions(target: String? = null) {
    val targetInfo = target?.let { " ($it)" } ?: ""
    Logger.info("   * Assertions$targetInfo")
    Logger.info("   * Property-based testing$targetInfo")
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") Logger.info("   * Testballoon$targetInfo")
    implementation(kotlin("reflect"))
    implementation(project.kotest("assertions-core", target))
    implementation(project.kotest("property", target))
    implementation("at.asitplus.gradle:testhelper" + (target?.let { "-$it" } ?: "") + ":$buildDate")
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") {
        implementation(project.kotest("property"))
        implementation("de.infix.testBalloon:testBalloon-framework-core:${project.AspVersions.testballoon}")
    }
    if (System.getProperty("TESTBALLOON_NO_ASP_HELPER") != "true") {
        implementation("de.infix.testBalloon:testBalloon-framework-core:${project.AspVersions.testballoon}")
        implementation("at.asitplus.testballoon:fixturegen-freespec:${project.AspVersions.testballoonAddons}")
        implementation("at.asitplus.testballoon:datatest:${project.AspVersions.testballoonAddons}")
        implementation("at.asitplus.testballoon:property:${project.AspVersions.testballoonAddons}")
    }
}


internal fun Project.setupTestExtensions() {
    val kmp = extensions.findByType<KotlinMultiplatformExtension>()
    if (kmp == null) setupJvmOnlyTestExtensions()
    else kmp.setupTestExtensions()
}

private fun KotlinMultiplatformExtension.setupTestExtensions() {
    sourceSets.whenObjectAdded {
        if (this.name.endsWith(("Test"))) {
            this.dependencies {
                addTestExtensions()
            }
        }
    }
}

private fun Project.setupJvmOnlyTestExtensions() {
    extensions.findByType<KotlinJvmExtension>()?.let { jvmExt ->
        jvmExt.forceApiVersion()
        jvmExt.sourceSets.whenObjectAdded { if (name == "test") dependencies { addTestExtensions("jvm") } }
    }
}

internal fun Project.setupTestReportFormat() {
    Logger.info("  Configuring Test output format")
    tasks.withType<Test> {
        if (name != "testReleaseUnitTest") {
            filter {
                isFailOnNoMatchingTests = false
            }
            testLogging {
                showExceptions = true
                showStandardStreams = true
                events = setOf(
                    TestLogEvent.FAILED,
                    TestLogEvent.PASSED
                )
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}