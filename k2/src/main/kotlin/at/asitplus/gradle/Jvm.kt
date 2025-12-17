package at.asitplus.gradle.at.asitplus.gradle

import at.asitplus.gradle.H
import at.asitplus.gradle.Logger
import at.asitplus.gradle.R
import at.asitplus.gradle.hasJvmTarget
import at.asitplus.gradle.jvmTarget
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.assign
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

internal fun KotlinMultiplatformExtension.setupJvmOpts() {
    Logger.lifecycle("  ${H}[JVM] Setting jvmTarget to ${project.jvmTarget} for ${project.name}$R")
    Logger.info("  [JVM] Setting jsr305=strict for JVM nullability annotations")
    targets.whenObjectAdded {
        if (this@setupJvmOpts.hasJvmTarget()) runCatching {
            this@setupJvmOpts.jvm {
                compilerOptions {
                    freeCompilerArgs = listOf(
                        "-Xjsr305=strict"
                    )
                    jvmTarget = JvmTarget.Companion.fromTarget(project.jvmTarget)
                }
            }
        }
    }
}

internal fun KotlinMultiplatformExtension.setJvmToolchain() {
    Logger.lifecycle("  ${H}Setting jvmToolchain to JDK ${project.jvmTarget} for ${project.name}$R")
    targets.whenObjectAdded {
        if (this@setJvmToolchain.hasJvmTarget()) {
            project.kotlinExtension.apply {
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(project.jvmTarget))
                }
            }
        }
    }
}