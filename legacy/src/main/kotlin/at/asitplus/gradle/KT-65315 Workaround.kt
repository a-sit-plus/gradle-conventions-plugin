package at.asitplus.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

fun NamedDomainObjectContainer<KotlinSourceSet>.shiftResources() {
    kotlin.runCatching {
        getByName("commonMain") {
            println("\n  ${H}Working around KT-65315 by moving resources to platform targets${R}")
            val configuredRsrcs = resources.srcDirs

            this@shiftResources.filterNot { it.name == "commonMain" || it.name.endsWith("Test") }.forEach {
                println(
                    "   * SourceSet ${it.name} now now also contains ${
                        configuredRsrcs.joinToString {
                            it.canonicalPath.substring(project.projectDir.canonicalPath.length)
                        }
                    }"
                )
                it.resources.srcDirs(*configuredRsrcs.toTypedArray())
            }
            println("  Clearing commonMain srcSet")
            resources.setSrcDirs(emptyList<File>())
        }

    }
}
