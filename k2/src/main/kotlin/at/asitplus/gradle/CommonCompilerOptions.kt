@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.text.get

internal inline fun KotlinMultiplatformExtension.experimentalOptIns() {
    Logger.lifecycle("  Adding opt ins")
    Logger.info("   * Serialization")
    Logger.info("   * Coroutines")
    Logger.info("   * kotlinx.datetime")
    Logger.info("   * RequiresOptIn\n")

    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesAp")
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.RequiresOptIn")

        freeCompilerArgs.addAll(
            listOf(
                "-Xexpect-actual-classes",
            )
        )
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}