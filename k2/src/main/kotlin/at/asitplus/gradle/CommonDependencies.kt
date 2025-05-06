@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import kotlin.random.Random

private val KEY_COLLECTED_DEPS = Random.nextBits(32).toString(16)

/**
 * Collects version information for all dependencies managed by shorthand functions (e.g. ktor, kmmresult)
 */
class CollectedDependencies(
    private val project: Project
) {
    val versions: MutableMap<String, String> = mutableMapOf()
    val libraries: MutableMap<String, Pair<String,String>> = mutableMapOf()

    fun add(module: String, versionRef: String): String {
        val components= module.split(':')
        val name = components[1]
        versions[versionRef] = project.AspVersions.versionOf(versionRef)
        libraries[name] = module to versionRef
        return "$module:${versions[versionRef]}"
    }
}

/**
 * Well, we do need some way to access collected dependencies. Here it is…
 */
internal val Project.collectedDependencies: CollectedDependencies
    get() {
        if (!extraProperties.has(KEY_COLLECTED_DEPS))
            extraProperties[KEY_COLLECTED_DEPS] = CollectedDependencies(this)
        return extraProperties[KEY_COLLECTED_DEPS] as CollectedDependencies
    }

fun Project.addDependency(module: String, versionRef: String) = collectedDependencies.add(module, versionRef)

private fun String?.toSuffix() = this?.let { "-$it" } ?: ""

/**
 * Adds Kotest (to test dependencies, as it is called there)
 * * assertions-core
 * * common
 * * property
 * * datatest
 *
 * Also adds `kotlin-reflect` to make kotest work smoothly with IDEA
 */
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
}

/**
 * Shorthand function to get the coordinates for a specific kotest dependency (*NOT added to the version catalog*)
 */
@JvmOverloads
fun Project.kotest(module: String, target: String? = null) =
    "io.kotest:kotest-$module${target.toSuffix()}:${AspVersions.kotest}"

inline fun KotlinDependencyHandler.addKotestJvmRunner() {
    Logger.info("  Adding Kotest JUnit runner")
    implementation(project.kotest("runner-junit5", "jvm"))
}

/**
 * get coordinates for kotlinx-serialization-[format]. Version can be overridden using `serialization` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.serialization(format: String, target: String? = null) =
    addDependency("org.jetbrains.kotlinx:kotlinx-serialization-$format${target.toSuffix()}", "serialization")

/**
 * get coordinates for ktor-[module]. Version can be overridden using `ktor` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.ktor(module: String, target: String? = null) =
    addDependency("io.ktor:ktor-$module${target.toSuffix()}", "ktor")

/**
 * get coordinates for kotlinx-coroutines-core. Version can be overridden using `coroutines` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.coroutines(target: String? = null) =
    addDependency("org.jetbrains.kotlinx:kotlinx-coroutines-core${target.toSuffix()}", "coroutines")

/**
 * get coordinates for AAkira Napier. Version can be overridden using `napier` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.napier(target: String? = null) =
    addDependency("io.github.aakira:napier${target.toSuffix()}", "napier")

/**
 * get coordinates for kotlinx-datetime. Version can be overridden using `datetime` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.datetime(target: String? = null) =
    addDependency("org.jetbrains.kotlinx:kotlinx-datetime${target.toSuffix()}", "datetime")

/**
 * get coordinates for kmmresult. Version can be overridden using `kmmresult` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.kmmresult(target: String? = null) =
    addDependency("at.asitplus:kmmresult${target.toSuffix()}", "kmmresult")

/**
 * get coordinates for org.bouncycastle:[module]-[classifier]. Version can be overridden using `bouncycastle` version alias in `gradle/libs.versions.toml`
 */
@JvmOverloads
fun Project.bouncycastle(module: String, classifier: String = "jdk18on") =
    addDependency("org.bouncycastle:$module-$classifier", "bouncycastle")
