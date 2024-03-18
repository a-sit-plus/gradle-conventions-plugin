@file:Suppress("NOTHING_TO_INLINE")
package at.asitplus.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.kpm.external.ExternalVariantApi
import org.jetbrains.kotlin.gradle.kpm.external.project

@OptIn(ExternalVariantApi::class)
internal inline fun KotlinMultiplatformExtension.experimentalOptIns() {
    Logger.lifecycle("  Adding opt ins")
    Logger.info("   * Serialization")
    Logger.info("   * Coroutines")
    Logger.info("   * kotlinx.datetime")
    Logger.info("   * RequiresOptIn\n")

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf(
                    "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlin.time.ExperimentalTime",
                    "-opt-in=kotlin.RequiresOptIn",
                )
            }
        }
    }
}