package at.asitplus.gradle.at.asitplus.gradle

import at.asitplus.gradle.*
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

/**
 * Adds Kotest (to test dependencies, as it is called there)
 * * assertions-core
 * * property
 * * datatest
 * * Testballoon Shim
 *
 * Also adds `kotlin-reflect` to make kotest work smoothly with IDEA
 */
inline fun KotlinDependencyHandler.addTestExtensions(target: String? = null) {
    val targetInfo = target?.let { " ($it)" } ?: ""
    Logger.info("   * Assertions$targetInfo")
    Logger.info("   * Property-based testing$targetInfo")
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true")     Logger.info("   * Testballoon$targetInfo")
    implementation(kotlin("reflect"))
    implementation(project.kotest("assertions-core", target))
    implementation(project.kotest("property", target))
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") {
        implementation("at.asitplus.gradle:testballoon-shim" + (target?.let { "-$it" } ?: "") + ":$buildDate")
    }
}
