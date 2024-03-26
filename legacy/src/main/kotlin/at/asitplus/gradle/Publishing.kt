package at.asitplus.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.catalog.CatalogPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial


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
    extensions.getByType(CatalogPluginExtension::class).versionCatalog {
        collectedDependencies.versions.forEach { (alias, version) ->
            version(alias, version)
        }
        collectedDependencies.libraries.forEach { (alias, module) ->
            val split = module.first.indexOf(':')
            library(alias, module.first.substring(0, split), module.first.substring(split + 1)).versionRef(module.second)
        }
    }

    project.extensions.findByType(PublishingExtension::class)?.let {
        it.publications.create<MavenPublication>("maven").from(components["versionCatalog"])
    }

}