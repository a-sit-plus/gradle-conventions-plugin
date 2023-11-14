@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import AspVersions
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler


fun String?.toSuffix() = this?.let { "-$it" } ?: ""

internal inline fun KotlinDependencyHandler.addKotest(target: String? = null) {
    val targetInfo = target?.let { " ($it)" } ?: ""
    println("  Adding Kotest libraries:")
    println("   * Assertions$targetInfo")
    println("   * Property-based testing$targetInfo")
    println("   * Datatest$targetInfo")
    implementation(kotlin("reflect"))
    implementation(kotest("assertions-core", target))
    implementation(kotest("common", target))
    implementation(kotest("property", target))
    implementation(kotest("framework-engine", target))
    implementation(kotest("framework-datatest", target))
}

@JvmOverloads
 fun kotest(module: String, target: String? = null) =
    "io.kotest:kotest-$module${target.toSuffix()}:${AspVersions.kotest}"

internal inline fun KotlinDependencyHandler.addKotestJvmRunner() {
    println("  Adding Kotest JUnit runner")
    implementation(kotest("runner-junit5", "jvm"))
}

@JvmOverloads
 fun serialization(format: String, target: String? = null) =
    "org.jetbrains.kotlinx:kotlinx-serialization-$format${target.toSuffix()}:${AspVersions.serialization}"

@JvmOverloads
 fun ktor(module: String, target: String? = null) =
    "io.ktor:ktor-$module${target.toSuffix()}:${AspVersions.ktor}"

@JvmOverloads
 fun coroutines(target: String? = null) =
    "org.jetbrains.kotlinx:kotlinx-coroutines-core${target.toSuffix()}:${AspVersions.coroutines}"

@JvmOverloads
 fun napier(target: String? = null) =
    "io.github.aakira:napier${target.toSuffix()}:${AspVersions.napier}"

@JvmOverloads
 fun datetime(target: String? = null) =
    "org.jetbrains.kotlinx:kotlinx-datetime${target.toSuffix()}:${AspVersions.datetime}"

@JvmOverloads
 fun kmmresult(target: String? = null) =
    "at.asitplus:kmmresult${target.toSuffix()}:${AspVersions.kmmresult}"

@JvmOverloads
 fun bouncycastle(module: String, classifier: String = "jdk18on") =
    "org.bouncycastle:$module-$classifier:${AspVersions.Jvm.bouncycastle}"
