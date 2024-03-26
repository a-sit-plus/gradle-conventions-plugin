package at.asitplus.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.catalog.CatalogPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import kotlin.jvm.optionals.getOrNull


fun Project.setupDokka(
    outputDir: String = layout.buildDirectory.dir("dokka").get().asFile.canonicalPath,
    baseUrl: String,
    multiModuleDoc: Boolean = false,
    remoteLineSuffix: String = "#L"
): TaskProvider<Jar> {
    val dokkaHtml = (tasks["dokkaHtml"] as DokkaTask).apply { outputDirectory.set(file(outputDir)) }

    val deleteDokkaOutput = tasks.register<Delete>("deleteDokkaOutputDirectory") {
        delete(outputDir)
    }
    val sourceLinktToConfigure = if (multiModuleDoc) (tasks["dokkaHtmlPartial"] as DokkaTaskPartial) else dokkaHtml
    sourceLinktToConfigure.dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/$name/kotlin"))
            remoteUrl.set(uri("$baseUrl/${project.name}/src/$name/kotlin").toURL())
            this@sourceLink.remoteLineSuffix.set(remoteLineSuffix)
        }
    }

    return tasks.register<Jar>("javadocJar") {
        dependsOn(deleteDokkaOutput, dokkaHtml)
        archiveClassifier.set("javadoc")
        from(outputDir)
    }
}

internal fun Project.setupSignDependency() {
    val signTasks = tasks.filter { it.name.startsWith("sign") }
    if (signTasks.isNotEmpty()) {
        Logger.lifecycle("")
        Logger.lifecycle("  Making sign tasks depend on publish tasks")
        tasks.filter { it.name.startsWith("publish") }.forEach {
            Logger.info("   * ${it.name} now depends on ${signTasks.joinToString { it.name }}")
            it.dependsOn(*signTasks.toTypedArray())
        }
    }
}

internal fun Project.addVersionCatalogSupport() {
    Logger.lifecycle("  Adding version catalog plugin to project ${rootProject.name}:${project.name}")
    project.plugins.apply("version-catalog")
}

internal fun Project.compileVersionCatalog() {
    Logger.lifecycle("  Compiling version catalog of project ${rootProject.name}:${project.name}")
    val userDefinedCatalog = extensions.getByType(VersionCatalogsExtension::class).find("libs").getOrNull()
    extensions.getByType(CatalogPluginExtension::class).versionCatalog {
        val setVersions = mutableSetOf<String>()
        collectedDependencies.versions.forEach { (alias, version) ->
            val setVersion = (userDefinedCatalog?.findVersion(alias)?.getOrNull()?.let { it.requiredVersion }
                ?: version)
            Logger.info("    * Adding version alias '$alias = $setVersion' to version catalog 'libs'")
            version(alias, setVersion)
            setVersions += alias
        }

        userDefinedCatalog?.versionAliases?.filterNot { setVersions.contains(it) }
            ?.forEach {
                val requiredVersion = userDefinedCatalog.findVersion(it).get().requiredVersion
                Logger.info("    * Adding version alias '$it = $requiredVersion' to version catalog 'libs'")
                version(it, requiredVersion)
            }

        collectedDependencies.libraries.forEach { (alias, module) ->
            val split = module.first.indexOf(':')
            if (userDefinedCatalog?.libraryAliases?.contains(alias) == true) {
                Logger.error("  Conflicting library declaration in version catalog: '$alias' has already been added as dependency by the ASP Conventions plugin")
                throw RuntimeException("Conflicting library declaration in version catalog: $alias")
            }
            val group = module.first.substring(0, split)
            val name = module.first.substring(split + 1)
            Logger.info("    * Adding library alias '$alias = {group = \"$group\", name = \"$name\", version.ref=\"${module.second}\"}' to version catalog 'libs'")
            library(
                alias,
                group,
                name
            ).versionRef(module.second)
        }

        //TODO maybe use TOML parser to get version ref?
        userDefinedCatalog?.libraryAliases?.forEach {
            val dependency = userDefinedCatalog.findLibrary(it).get().get()
            val dep = library(it, dependency.group, dependency.name)
            Logger.info("    * Adding library alias '$it = {group = \"${dependency.group}\", name = \"${dependency.name}\", version=\"${dependency.version}\"}' to version catalog 'libs'")

            dependency.version?.also { dep.version(it) } ?: dep.withoutVersion()
        }
    }

    project.extensions.findByType(PublishingExtension::class)?.let {
        it.publications.create<MavenPublication>("maven").from(components["versionCatalog"])
    }

}