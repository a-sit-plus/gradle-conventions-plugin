package at.asitplus.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.io.File

fun NamedDomainObjectContainer<KotlinSourceSet>.shiftResources() {
    kotlin.runCatching {
        getByName("commonMain") {
            if (runCatching { project.extraProperties["kt65315.workaround"] as String }.getOrNull() != "false") {
                Logger.lifecycle("")
                Logger.lifecycle("  ${H}Working around KT-65315 by moving resources to platform targets${R}")
                if (runCatching { project.extraProperties["kt65315.workaround"] as String }.getOrNull() == null) {
                    Logger.warn("    ${H}This is what you want for libraries, but not for apps and service!${R}")
                    Logger.warn("    ${H}To silence this warning, add kt65315.workaround=true to gradle.properties${R}")
                    Logger.warn("    ${H}To disable the workaround, add kt65315.workaround=false to gradle.properties${R}")
                }
                val configuredRsrcs = resources.srcDirs

                this@shiftResources.filterNot {
                    it.name == "commonMain" || it.name.endsWith(
                        "Test"
                    )
                }.forEach {
                    Logger.info(
                        "   * SourceSet ${it.name} now now also contains ${
                            configuredRsrcs.joinToString {
                                it.canonicalPath.substring(project.projectDir.canonicalPath.length)
                            }
                        }"
                    )
                    it.resources.srcDirs(*configuredRsrcs.toTypedArray())
                }
                Logger.info("  Clearing commonMain srcSet")
                resources.setSrcDirs(emptyList<File>())
            }

        }
    }
}
