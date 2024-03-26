@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import kotlin.random.Random

private val KEY_COLLECTED_DEPS = Random.nextBits(32).toString(16)

class CollectedDependencies(
    private val project: Project
) {
    val versions: MutableMap<String, String> = mutableMapOf()
    val libraries: MutableMap<String, Pair<String,String>> = mutableMapOf()

    fun add(module: String, versionRef: String): String {
        val split = module.indexOf(':')
        val name = module.substring(split + 1)
        versions[versionRef] = project.AspVersions.versionOf(versionRef)
        libraries[name] = module to versionRef
        return "$module:${versions[versionRef]}"
    }
}

internal val Project.collectedDependencies: CollectedDependencies
    get() {
        if (!extraProperties.has(KEY_COLLECTED_DEPS))
            extraProperties[KEY_COLLECTED_DEPS] = CollectedDependencies(this)
        return extraProperties[KEY_COLLECTED_DEPS] as CollectedDependencies
    }

private fun Project.addDependency(module: String, versionRef: String) = collectedDependencies.add(module, versionRef)

fun String?.toSuffix() = this?.let { "-$it" } ?: ""

inline fun KotlinDependencyHandler.addKotest(target: String? = null) {
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
    implementation(project.kotest("framework-datatest", target))
}

@JvmOverloads
fun Project.kotest(module: String, target: String? = null) =
    addDependency("io.kotest:kotest-$module${target.toSuffix()}", "kotest")

inline fun KotlinDependencyHandler.addKotestJvmRunner() {
    Logger.info("  Adding Kotest JUnit runner")
    implementation(project.kotest("runner-junit5", "jvm"))
}

@JvmOverloads
fun Project.serialization(format: String, target: String? = null) =
    addDependency("org.jetbrains.kotlinx:kotlinx-serialization-$format${target.toSuffix()}", "serialization")

@JvmOverloads
fun Project.ktor(module: String, target: String? = null) =
    addDependency("io.ktor:ktor-$module${target.toSuffix()}", "ktor")

@JvmOverloads
fun Project.coroutines(target: String? = null) =
    addDependency("org.jetbrains.kotlinx:kotlinx-coroutines-core${target.toSuffix()}", "coroutines")

@JvmOverloads
fun Project.napier(target: String? = null) =
    addDependency("io.github.aakira:napier${target.toSuffix()}", "napier")

@JvmOverloads
fun Project.datetime(target: String? = null) =
    addDependency("org.jetbrains.kotlinx:kotlinx-datetime${target.toSuffix()}", "datetime")

@JvmOverloads
fun Project.kmmresult(target: String? = null) =
    addDependency("at.asitplus:kmmresult${target.toSuffix()}", "kmmresult")

@JvmOverloads
fun Project.bouncycastle(module: String, classifier: String = "jdk18on") =
    addDependency("org.bouncycastle:$module-$classifier", "bouncycastle")
