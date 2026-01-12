package at.asitplus.gradle.at.asitplus.gradle

import at.asitplus.gradle.AspVersions
import at.asitplus.gradle.Logger
import at.asitplus.gradle.forceApiVersion
import at.asitplus.gradle.kotest
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


private fun Project.testDeps(target: String?=null) :List<String> {
    val list= mutableListOf<String>(
        "org.jetbrains.kotlin:kotlin-reflect",
        kotest("assertions-core", target),
        kotest("property", target),
        "at.asitplus.gradle:testhelper" + (target?.let { "-$it" } ?: "") + ":20251114",
        )
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") {
        list.add(project.kotest("property"))
        list.add("de.infix.testBalloon:testBalloon-framework-core:${project.AspVersions.testballoon}")
    }
    if (System.getProperty("TESTBALLOON_NO_ASP_HELPER") != "true") {
        list.add("de.infix.testBalloon:testBalloon-framework-core:${project.AspVersions.testballoon}")
        list.add("at.asitplus.testballoon:fixturegen-freespec:${project.AspVersions.testballoonAddons}")
        list.add("at.asitplus.testballoon:datatest:${project.AspVersions.testballoonAddons}")
        list.add("at.asitplus.testballoon:property:${project.AspVersions.testballoonAddons}")
    }
    return list
}

 fun KotlinDependencyHandler.addTestExtensions(target: String? = null) {
    val targetInfo = target?.let { " ($it)" } ?: ""
    Logger.info("   * Assertions$targetInfo")
    Logger.info("   * Property-based testing$targetInfo")
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") Logger.info("   * Testballoon$targetInfo")

    project.testDeps(target).forEach {
        implementation(it)
    }
}


internal fun Project.setupTestExtensions() {
    val kmp = extensions.findByType<KotlinMultiplatformExtension>()
    if (kmp == null) setupJvmOnlyTestExtensions()
    else kmp.setupTestExtensions()
}

private fun KotlinMultiplatformExtension.setupTestExtensions() {
    sourceSets.whenObjectAdded {
        if (name.endsWith(("Test"))) {
            this.dependencies {
                addTestExtensions()
            }
        }
    }
}

private fun Project.setupJvmOnlyTestExtensions() {
    testDeps("jvm").forEach {
        dependencies.add("testImplementation",it)
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