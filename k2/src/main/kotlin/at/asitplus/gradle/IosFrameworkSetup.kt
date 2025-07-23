package at.asitplus.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig

/**
 * Export an XCode framework for all configured apple targets
 */
fun Project.exportXCFramework(
    name: String,
    transitiveExports: Boolean,
    static: Boolean = false,
    vararg additionalExports: Any,
    bundleIdentifier: String = "$group.$name",
    additionalConfig: XCFrameworkConfig.() -> Unit = {},
    nativeBinaryOpts: NativeBinary.() -> Unit = {}
) {
    val appleTargets = kotlinExtension.let {
        if (it is KotlinMultiplatformExtension) {
            it.targets.filterIsInstance<KotlinNativeTarget>().filter {
                it.name.startsWith("ios") ||
                        it.name.startsWith("tvos") ||
                        it.name.startsWith("macos")

            }
        } else throw StopExecutionException("No Apple Targets found! Declare them explicitly before calling exportXCFramework!")
    }

    extensions.getByType<KotlinMultiplatformExtension>().apply {
        XCFrameworkConfig(project, name).also { xcf ->
            Logger.lifecycle("  \u001B[1mXCFrameworks with bundle identifier '$bundleIdentifier' will be exported for the following iOS targets: ${appleTargets.joinToString { it.name }}\u001B[0m")
            appleTargets.forEach { target ->
                target.binaries.framework {
                    baseName = name
                    isStatic = static
                    transitiveExport = transitiveExports
                    additionalExports.forEach { export(it) }
                    binaryOption("bundleId", bundleIdentifier)
                    nativeBinaryOpts()
                    xcf.add(this)
                }
            }
            xcf.additionalConfig()
        }
    }
}