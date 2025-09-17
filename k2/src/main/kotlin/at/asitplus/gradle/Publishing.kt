package at.asitplus.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.catalog.CatalogPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.Sign
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import kotlin.jvm.optionals.getOrNull

/**
 * Configures Dokka to publish documentation to [outputDir]. Also add a `deleteDokkaOutputDirectory` task which is executed
 * before documentation is written, s.t. [outputDir] is always clean.
 *
 * @return a `Jar`-type task, which can directly be fed into a maven publication
 */
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

/**
 * Makes all publishing tasks depend on all signing tasks. Hampers parallelization but works around dodgy task dependencies
 * that (more often than anticipated) make the build process stumble over its own feet.
 */
internal fun Project.setupSignDependency() {

    Logger.lifecycle("")
    Logger.lifecycle("  Making signing tasks of project $H${name}$R run after publish tasks")
    Logger.info("")
    tasks.whenTaskAdded {
        if (this is Sign) {
            val publishTasks = tasks.withType<AbstractPublishToMaven>()
            publishTasks.forEach {
                it.dependsOn(this)
                it.mustRunAfter(this)
            }
        }

        if (this is AbstractPublishToMaven) {
            val signTasks = tasks.withType<Sign>()
            signTasks.forEach {
                this.dependsOn(it)
                this.mustRunAfter(it)
            }
        }
    }

    tasks.withType<Sign>().configureEach {
        val sign = this
        tasks.withType<AbstractPublishToMaven>().forEach {
            it.dependsOn(sign)
            it.mustRunAfter(sign)
        }

    }

    gradle.taskGraph.whenReady {
        Logger.info("")
        Logger.info("  Task Graph for project $name is ready. The following publish tasks are present:")
        tasks.withType<AbstractPublishToMaven>().forEach {
            Logger.info("    * ${it.name}")
        }
        Logger.info("\n  The following signing tasks are present:")
        tasks.withType<Sign>().forEach {
            Logger.info("    * ${it.name}")
        }
        tasks.withType<AbstractPublishToMaven>().forEach { publishTask ->
            val signingTasks = publishTask.dependsOn.filterIsInstance<Sign>()
            Logger.info("   * ${publishTask.name} must now run after")
            signingTasks.forEach {
                Logger.info("      * ${it.name}")
            }
        }
        logger.info("")
    }
}

/**
 * Adds the `version-catalog` plugin to the build to publish version catalogs alongside the project's other maven artefacts.
 */
internal fun Project.addVersionCatalogSupport() {
    Logger.lifecycle("  Adding version catalog plugin to project ${rootProject.name}:${project.name}")
    project.plugins.apply("version-catalog")
}

/**
 * Returns all **configured** dependencies (i.e. no transitive dependencies). Used to auto-compile a version catalog.
 */
private fun Project.getDependencies(type: String): List<Dependency> =
    configurations.asSequence().filterNot { it.name.contains("compilation", ignoreCase = true) }
        .filterNot { it.name.contains("test", ignoreCase = true) }.filter { it.name.endsWith(type, ignoreCase = true) }
        .flatMap { it.dependencies }.toList()


/**
 * This is the fat daddy of dependency collection and version catalog aggregation.
 *
 * Compiles a version catalog consisting of: (in descending order of priority):
 *  * Dependencies declared as part of `gradle/libs.versions.toml`
 *  * Dependencies added through shorthand functions (e.g. `kmmresult()`, `ktor("client-core")`, …)
 *      * These are collected when the respective shorthand functions are called.
 *      * Their versions can be overridden by adding versions to `gradle/libs.versions.toml` (see [AspVersions])
 *  * Dependencies declared directly in build.gradle.kts (except for test dependencies)
 *
 *  Once the version catalog is compiled, it is added to the maven publication
 */
internal fun Project.compileVersionCatalog() {
    if (!publishVersionCatalog) {
        Logger.lifecycle(("\n  NOT publishing version catalog for project $project"))
        return
    }
    Logger.lifecycle("\n  Compiling version catalog of project ${rootProject.name}:${project.name}")

    //Get `libs` version catalog, which is the default and only supported one
    val userDefinedCatalog = extensions.getByType(VersionCatalogsExtension::class).find("libs").getOrNull()
    extensions.getByType(CatalogPluginExtension::class).versionCatalog {


        //gathers dependency aliases from invoked shorthand functions
        val setVersions = collectedDependencies.versions.map { (alias, version) ->
            Logger.info("    * Adding version alias '$alias = $version' to version catalog 'libs'")
            version(alias, version)
            alias
        }

        //we did the magic already for those special dependencies we used shorthands for, so we skip 'em here
        userDefinedCatalog?.versionAliases?.filterNot { setVersions.contains(it) }
            ?.forEach {
                //we can take the risk of using !! here, because userDefineCatalog and AspVersions.versionCatalog point to the same source file
                //and `versionAliases` contains the versions defined in the `versions` table
                val requiredVersion =
                    AspVersions.versionCatalog!!.getTable("versions")?.getString(it.replace(".", "-"))!!
                Logger.info("    * Adding version alias '$it = $requiredVersion' to version catalog 'libs'")
                version(it, requiredVersion)
            }

        //always add kotlin version, as they need to be aligned!
        version("kotlin", AspVersions.kotlin)

        //this sanity check makes sure that `gradle/libs.versions.toml` does not define a dependency handled by a called shorthand function.
        //If no shorthand functions are used, no problem!
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

        // get manually defined dependencies (i.e. those not defined in `gradle/libs.versions.toml` and not declared through shorthands provided by the conventions plugin
        var declaredDeps = getDependencies("api") + getDependencies("implementation") + getDependencies("ksp")

        AspVersions.versionCatalog?.getTable("libraries")?.let { libs ->
            libs.keySet().forEach { alias ->
                //the version catalog can uses versionRefs and/or plain versions strings
                val versionRef = libs.getTable(alias)!!.getString("version.ref")
                val version = if (versionRef == null) libs.getTable(alias)!!.getString("version") else null

                userDefinedCatalog?.let { udf ->
                    val fromCatalog = udf.findLibrary(alias).get().get()
                    declaredDeps =
                        declaredDeps.filterNot { it.group == fromCatalog.group && it.name == fromCatalog.name }
                    val dep = library(alias, fromCatalog.group, fromCatalog.name)
                    Logger.info(
                        "    * Adding library alias '$alias = {group = \"${fromCatalog.group}\", name = \"${fromCatalog.name}\", version${versionRef?.let { ".ref" } ?: ""}=\"${
                            versionRef ?: version
                        }\"}' to version catalog 'libs'"
                    )
                    versionRef?.also { dep.versionRef(it) } ?: version?.also { dep.version(it) } ?: dep.withoutVersion()
                }
            }
        }

        declaredDeps.filterNot { dep -> collectedDependencies.libraries.values.firstOrNull { it.first == dep.group + ":" + dep.name } != null }
            .forEach {

                it.version?.let { version ->
                    library(it.name, it.group + ":" + it.name + ":" + version)
                } ?: run {
                    if (it !is FileCollectionDependency)
                        library(it.name, it.group!!, it.name).withoutVersion()
                }
            }

        // add kotlin plugin to version catalog
        var isKMP = false
        plugins.withType<KotlinMultiplatformPluginWrapper> {
            plugin("kotlin-multiplatform", "org.jetbrains.kotlin.multiplatform").versionRef("kotlin")
            isKMP = true
        }
        if (!isKMP) plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").versionRef("kotlin")

        //also add all plugins to the version catalog.
        val pluginDeclarations = AspVersions.versionCatalog?.getTable("plugins")
        pluginDeclarations?.keySet()?.forEach { alias ->
            val currentPlugin = pluginDeclarations.getTable(alias)!!
            val versionRef = currentPlugin.getString("version.ref")
            val version = if (versionRef == null) currentPlugin.getString("version") else null
            val dep = this.plugin(alias, currentPlugin.getString("id")!!)
            versionRef?.also { dep.versionRef(it) } ?: dep.version(version ?: "")
        }

        val bundleDeclarations = AspVersions.versionCatalog?.getTable("bundles")
        bundleDeclarations?.keySet()?.forEach { alias ->
            bundle(alias, bundleDeclarations.getArray(alias)!!.toList().map { it.toString() })
        }
        library(project.name, project.group.toString() + ":" + project.name + ":" + project.version.toString())


    }

    project.extensions.getByType<PublishingExtension>().let { publishingExtension ->

        val configured = publishingExtension.publications.filterIsInstance<DefaultMavenPublication>()
            .firstOrNull { it.pom?.scm != null }

        if (project.kotlinExtension is KotlinMultiplatformExtension)
            publishingExtension.publications.create<MavenPublication>("versions") {
                this.artifactId += "-versionCatalog"
                Logger.lifecycle("  Creating publication 'version' with artifact $artifactId for version catalog publishing")
                from(project.components.getByName("versionCatalog"))
            }
        else
            publishingExtension.publications.register<MavenPublication>("versions") {
                this.artifactId += "-versionCatalog"
                Logger.lifecycle("  Creating publication 'version' with artifact $artifactId for version catalog publishing")
                from(project.components.getByName("versionCatalog"))
            }


        val newlyRegistered = publishingExtension.publications.getByName("versions") as DefaultMavenPublication
        newlyRegistered.isAlias = true

        if (!newlyRegistered.pom.name.isPresent) {
            newlyRegistered.pom.name.set(configured?.pom?.name?.get() + " Version Catalog")
        }

        configured?.pom?.description?.get()?.also {
            newlyRegistered.pom.description.set("$it version catalog")
        }

        if (!newlyRegistered.pom.url.isPresent)
            configured?.pom?.url?.get()?.also {
                newlyRegistered.pom.url.set(it)
            }

        if (newlyRegistered.pom.licenses.isEmpty()) {
            configured?.pom?.licenses?.forEach {
                newlyRegistered.pom.licenses += it
            }
        }
        if (newlyRegistered.pom.developers.isEmpty()) {
            configured?.pom?.developers?.forEach {
                newlyRegistered.pom.developers += it
            }
        }

        if (newlyRegistered.pom.scm == null) {
            newlyRegistered.pom {
                scm {
                    configured?.pom?.scm?.connection?.get()?.also {
                        connection.set(it)
                    }
                    configured?.pom?.scm?.url?.get()?.also {
                        url.set(it)
                    }
                    configured?.pom?.scm?.developerConnection?.get()?.also {
                        developerConnection.set(it)
                    }
                }

            }
        }
    }
}