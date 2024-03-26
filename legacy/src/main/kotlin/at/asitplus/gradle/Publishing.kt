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
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.tomlj.Toml
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
            Logger.info("    * Adding version alias '$alias = $version' to version catalog 'libs'")
            version(alias, version)
            setVersions += alias
        }

        userDefinedCatalog?.versionAliases?.filterNot { setVersions.contains(it) }
            ?.forEach {
                val requiredVersion = AspVersions.versionCatalog.getTable("versions")?.getString(it)!!
                Logger.info("    * Adding version alias '$it = $requiredVersion' to version catalog 'libs'")
                version(it, requiredVersion)
            }

        version("kotlin", AspVersions.kotlin)
        version("ksp", AspVersions.ksp)

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

        userDefinedCatalog?.let { udf ->

            AspVersions.versionCatalog.getTable("libraries")?.let { libs ->
                libs.keySet().forEach { alias ->
                    val versionRef = libs.getTable(alias)!!.getString("version.ref")
                    val version = if (versionRef == null) libs.getTable(alias)!!.getString("version") else null

                    val fromCatalog = udf.findLibrary(alias).get().get()
                    val dep = library(alias, fromCatalog.group, fromCatalog.name)
                    Logger.info(
                        "    * Adding library alias '$alias = {group = \"${fromCatalog.group}\", name = \"${fromCatalog.name}\", version${versionRef?.let { ".ref" } ?: ""}=\"${
                            versionRef ?: version
                        }\"}' to version catalog 'libs'"
                    )
                    versionRef?.also { dep.versionRef(it) } ?: version?.also { dep.version(it) } ?: dep.withoutVersion()
                }
            }

            var isKMP=false
            plugins.withType<KotlinMultiplatformPluginWrapper>{
                plugin("kotlin-multiplatform", "org.jetbrains.kotlin.multiplatform").versionRef("kotlin")
                isKMP=true
            }
            if(!isKMP){
                plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").versionRef("kotlin")
            }


            val pluginDeclarations = AspVersions.versionCatalog.getTable("plugins")
            pluginDeclarations?.keySet()?.forEach { alias ->
                val currentPlugin = pluginDeclarations.getTable(alias)!!
                val versionRef = currentPlugin.getString("version.ref")
                val version = if (versionRef == null) currentPlugin.getString("version") else null
                val dep = this.plugin(alias, currentPlugin.getString("id")!!)
                versionRef?.also { dep.versionRef(it) } ?: dep.version(version ?: "")
            }

            val bundleDeclarations = AspVersions.versionCatalog.getTable("bundles")
            bundleDeclarations?.keySet()?.forEach { alias ->
                bundle(alias, bundleDeclarations!!.getArray(alias)!!.toList().map { it.toString() })
            }

        }
    }

    project.extensions.findByType(PublishingExtension::class)?.let {
        it.publications.create<MavenPublication>("maven").from(components["versionCatalog"])
    }

}