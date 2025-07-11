@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

internal inline fun KotlinMultiplatformExtension.experimentalOptIns() {
    Logger.lifecycle("  Adding opt ins")
    Logger.info("   * Serialization")
    Logger.info("   * Coroutines")
    Logger.info("   * kotlinx.datetime")
    Logger.info("   * RequiresOptIn\n")


    //work around IDEA bug
    forceApiVersion()
    compilerOptions {

        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
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

internal fun KotlinMultiplatformExtension.forceApiVersion() {
    val kotlinVer = coreLibrariesVersion.split(".").let { it.first() + "." + it[1] }
    Logger.info("  [ForceApi] Forcing Api Version: $kotlinVer")
    compilerOptions {
        apiVersion.set(KotlinVersion.fromVersion(kotlinVer))
        languageVersion.set(KotlinVersion.fromVersion(kotlinVer))
    }
}

internal fun KotlinJvmExtension.forceApiVersion() {
    val kotlinVer = coreLibrariesVersion.split(".").let { it.first() + "." + it[1] }
    Logger.info("  [ForceApi] Forcing Api Version: $kotlinVer")
    compilerOptions {
        apiVersion.set(KotlinVersion.fromVersion(kotlinVer))
        languageVersion.set(KotlinVersion.fromVersion(kotlinVer))
    }
}