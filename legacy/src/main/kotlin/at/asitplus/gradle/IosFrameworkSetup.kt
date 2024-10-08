package at.asitplus.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig


fun Project.exportIosFramework(
    name: String,
    transitiveExports: Boolean,
    vararg additionalExports: Any
) = exportIosFramework(
    name,
    transitiveExports = transitiveExports,
    static = false,
    additionalExports = additionalExports
)

fun Project.exportIosFramework(
    name: String,
    transitiveExports: Boolean,
    static: Boolean,
    vararg additionalExports: Any,
    additionalConfig: XCFrameworkConfig.() -> Unit = {}
) {
    val iosTargets = kotlinExtension.let {
        if (it is KotlinMultiplatformExtension) {
            it.targets.filterIsInstance<KotlinNativeTarget>().filter { it.name.startsWith("ios") }
        } else throw StopExecutionException("No iOS Targets found! Declare them explicitly before calling exportIosFramework!")
    }

    extensions.getByType<KotlinMultiplatformExtension>().apply {
        XCFrameworkConfig(project, name).also { xcf ->
            Logger.lifecycle("  \u001B[1mXCFrameworks will be exported for the following iOS targets: ${iosTargets.joinToString { it.name }}\u001B[0m")
            iosTargets.forEach {
                it.binaries.framework {
                    baseName = name
                    isStatic = static
                    transitiveExport = transitiveExports
                    embedBitcode(BitcodeEmbeddingMode.DISABLE)
                    additionalExports.forEach { export(it) }
                    xcf.add(this)
                }
            }
            xcf.additionalConfig()
        }
    }
}