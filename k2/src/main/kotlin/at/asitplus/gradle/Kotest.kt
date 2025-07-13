package at.asitplus.gradle.at.asitplus.gradle

import at.asitplus.gradle.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

private val tempDir = SystemTemporaryDirectory
private val kotestReportDir = Path(tempDir, "kotest-report")

internal fun Project.registerKotestCopyTask() {
    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") afterEvaluate {
        //cannot filter for test instance, since kmp tests do not inherit Test
        tasks.matching { it.name.endsWith("Test") }.forEach {
            it.doLast {
                runCatching {
                    logger.lifecycle("  >> Copying tests from $kotestReportDir")
                    val source = File(kotestReportDir.toString())
                    source.copyRecursively(layout.buildDirectory.asFile.get(), overwrite = true)
                }.getOrElse {
                    val source = project.layout.projectDirectory.dir("kotest-report").asFile
                    if (source.exists()) {
                        logger.lifecycle("  >> Copying tests from kotest-report")
                        source.copyRecursively(layout.buildDirectory.asFile.get(), overwrite = true)
                    } else Logger.warn(" >> Copying tests from $kotestReportDir failed: ${it.message}")
                }
            }
        }
    }
}

fun Project.getBuildableTargets() =
    project.extensions.getByType<KotlinMultiplatformExtension>().targets.filter { target ->
        when {
            // Non-native targets are always buildable
            target.platformType != org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.native -> true
            else -> {
                runCatching {
                    val konanTarget = (target as? KotlinNativeTarget)
                    konanTarget?.publishable == true
                }.getOrElse { false }
            }
        }
    }

internal fun KotlinMultiplatformExtension.defaultSetupKotest() {
    sourceSets {
        commonTest {
            dependencies {
                addKotestExtensions()
            }
        }
        if (hasJvmTarget()) jvmTest {
            dependencies {
                addKotestJvmRunner()
            }
        }
    }
}

internal fun KotlinMultiplatformExtension.wireKotestKsp() {
    if (!project.rootProject.pluginManager.hasPlugin("com.google.devtools.ksp")) throw StopExecutionException("KSP not found in root project, please add 'com.google.devtools.ksp' to the root project's plugins")

    project.pluginManager.apply("com.google.devtools.ksp")

    project.configurations.whenObjectAdded {
        if (name.startsWith("ksp") && name.endsWith("Test")) {
            val target = name.substring(3, name.length - 4).replaceFirstChar { it.lowercase() }
            if (project.getBuildableTargets().firstOrNull { target == it.name } != null) {
                project.logger.lifecycle("  >>[${project.name}] Adding Kotest symbol processor dependency to $name")
                project.dependencies.add(
                    name,
                    "io.kotest:kotest-framework-symbol-processor-jvm:${project.AspVersions.kotest}"
                )
            } else {
                project.logger.lifecycle("  >>[${project.name}] Not wiring Kotest symbol processor dependency to non-buildable configuration $name")
            }
        }

    }
}


/**
 * Adds Kotest (to test dependencies, as it is called there)
 * * assertions-core
 * * common
 * * property
 * * datatest
 *
 * Also adds `kotlin-reflect` to make kotest work smoothly with IDEA
 */
inline fun KotlinDependencyHandler.addKotestExtensions(target: String? = null) {
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

    if (System.getProperty("KOTEST_NO_ASP_HELPER") != "true") {
        implementation("at.asitplus.gradle:kmpotest" + (target?.let { "-$it" } ?: "") + ":$buildDate")
    }
}


inline fun KotlinDependencyHandler.addKotestJvmRunner() {
    Logger.info("  Adding Kotest JUnit runner")
    implementation(project.kotest("runner-junit5", "jvm"))
}
