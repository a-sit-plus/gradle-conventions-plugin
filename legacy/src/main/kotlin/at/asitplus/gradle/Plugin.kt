@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import kotlin.random.Random
import kotlin.reflect.KProperty

internal const val H = "\u001b[7m\u001b[1m"
internal const val R = "\u001b[0m"

lateinit var Logger: org.gradle.api.logging.Logger

private inline fun Project.supportLocalProperties() {
    Logger.lifecycle("  Adding support for storing extra project properties in local.properties and System Environment")
    java.util.Properties().apply {
        kotlin.runCatching { load(java.io.FileInputStream(rootProject.file("local.properties"))) }
        forEach { (k, v) -> extra.set(k as String, v) }
    }
}

object EnvDelegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? =
        System.getenv(property.name)
}

class EnvExtraDelegate(private val project: Project) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? =
        System.getenv(property.name)
            ?.also { Logger.lifecycle("  > Property ${property.name} set to $it from environment") }
            ?: runCatching {
                (project.extraProperties[property.name] as String).also {
                    Logger.lifecycle("  > Property ${property.name} set to $it from extra properties")
                }
            }.getOrElse {
                Logger.lifecycle("")
                Logger.warn("w: Property ${property.name} could could be read from neither environment nor extra")
                Logger.lifecycle("")
                null
            }

}

private val KEY_ASP_VERSIONS = Random.nextBits(32).toString(36)

val Project.AspVersions: AspVersions get() = rootProject.extraProperties[KEY_ASP_VERSIONS] as AspVersions
val Project.env: EnvDelegate get() = EnvDelegate

fun Project.env(property: String): String? = System.getenv(property)

val Project.envExtra: EnvExtraDelegate get() = EnvExtraDelegate(this)

private inline fun Project.hasMrJar() = plugins.hasPlugin("me.champeau.mrjar")

val Project.jvmTarget: String get() = runCatching { extraProperties["jdk.version"] as String }.getOrElse { AspVersions.jvm.defaultTarget }

open class AspLegacyConventions : Plugin<Project> {

    protected open fun KotlinMultiplatformExtension.setupKotest() {
        sourceSets {
            val commonTest by getting {
                dependencies {
                    addKotest()
                }
            }
            val jvmTest by getting {
                dependencies {
                    addKotestJvmRunner()
                }
            }
        }
    }

    protected open fun versionOverrides(aspVersions: AspVersions) = Unit

    override fun apply(target: Project) {
        Logger = target.logger
        target.supportLocalProperties()
        if (target.rootProject == target)
            AspVersions(target).let {
                target.extraProperties[KEY_ASP_VERSIONS] = it
                versionOverrides(it)
            }
        Logger.lifecycle(
            "\n ASP Conventions ${H}${target.AspVersions.versions["kotlin"]}$R is using the following dependency versions for project ${
                if (target == target.rootProject) target.name
                else "${target.rootProject.name}:${target.name}"
            }:"
        )
        runCatching {
            target.AspVersions.versions.entries.filterNot { (k, _) -> k == "jvmTarget" }
                .sortedBy { (k, _) -> k.toString() }
                .forEach { (t, _) ->
                    Logger.lifecycle(
                        "    ${
                            String.format(
                                "%-14s",
                                "$t:"
                            )
                        } ${target.AspVersions.versionOf(t as String)}"
                    )
                }
            Logger.lifecycle("")
        }

        if (target != target.rootProject) {
            target.addVersionCatalogSupport()
        }

        if (target == target.rootProject) {
            Logger.lifecycle("  Adding Nexus Publish plugin ${target.AspVersions.nexus}")
            target.plugins.apply("io.github.gradle-nexus.publish-plugin")

            target.plugins.apply("idea")

            val mrJarModules = target.childProjects.filter { (_, p) -> p.hasMrJar() }
                .map { (name, _) -> name }
            if (mrJarModules.isEmpty()) { //MRJAR
                Logger.lifecycle("  ${H}Configuring IDEA to use Java ${target.jvmTarget}$R")
                target.extensions.getByType<IdeaModel>().project {
                    jdkName = target.jvmTarget
                }
            } else Logger.lifecycle(
                "  MR Jar plugin detected in modules${
                    mrJarModules.joinToString(
                        prefix = "\n",
                        separator = "\n      * ",
                        postfix = "\n"
                    ) { it }
                }   Not setting IDEA Java version.\n")




            Logger.lifecycle("  Adding maven repositories")
            Logger.info("    * serialization fork")
            Logger.info("    * dokka dev")
            Logger.info("    * maven central")
            Logger.info("    * google")
            target.allprojects {
                repositories {
                    maven(uri("https://raw.githubusercontent.com/a-sit-plus/kotlinx.serialization/mvn/repo"))
                    maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
                    google()
                    mavenCentral()
                }
            }

            runCatching {
                target.tasks.register<Delete>("clean") {
                    Logger.lifecycle("  Adding clean task to root project")

                    doFirst { Logger.lifecycle("> Cleaning all build files") }

                    delete(target.rootProject.layout.buildDirectory.get().asFile)
                    //delete(target.layout.projectDirectory.dir("repo"))
                    doLast { Logger.lifecycle("> Clean done") }
                }
            }

            Logger.info("  Setting Nexus publishing URL to s01.oss.sonatype.org")
            target.extensions.getByType<NexusPublishExtension>().apply {
                repositories {
                    sonatype {
                        nexusUrl.set(java.net.URI("https://s01.oss.sonatype.org/service/local/"))
                        snapshotRepositoryUrl.set(java.net.URI("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
                    }
                }
            }
        }

        var isMultiplatform = false
        runCatching {
            target.plugins.withType<KotlinBasePlugin>().let {
                Logger.lifecycle("  ${H}Using Kotlin version ${it.first().pluginVersion} for project ${target.name}$R")
            }
        }

        target.plugins.withType<KotlinMultiplatformPluginWrapper> {
            isMultiplatform = true
            Logger.lifecycle("  ${H}Multiplatform project detected$R")

            target.afterEvaluate {

                val kmpTargets =
                    extensions.getByType<KotlinMultiplatformExtension>().targets.filter { it.name != "metadata" }
                if (kmpTargets.isEmpty())
                    throw StopExecutionException("No buildable targets found! Declare at least a single one explicitly as per https://kotlinlang.org/docs/multiplatform-hierarchy.html#default-hierarchy-template")


                val kmp = extensions.getByType<KotlinMultiplatformExtension>()
                kmp.sourceSets.shiftResources()

                Logger.lifecycle("\n  This project will be built for the following targets:")
                kmpTargets.forEach { Logger.lifecycle("   * ${it.name}") }


                Logger.info("\n  Setting up Kotest multiplatform plugin ${AspVersions.kotest}")
                plugins.apply("io.kotest.multiplatform")

                extensions.getByType<KotlinMultiplatformExtension>().jvm {
                    Logger.info("  Setting jsr305=strict for JVM nullability annotations")
                    compilations.all {
                        kotlinOptions {
                            if (!hasMrJar()) { //MRJAR
                                Logger.lifecycle("  ${H}Setting jvmTarget to ${target.jvmTarget} for $name$R")
                                kotlinOptions.jvmTarget = target.jvmTarget
                            } else Logger.lifecycle("  MR Jar plugin detected. Not setting jvmTarget")
                            freeCompilerArgs = listOf(
                                "-Xjsr305=strict"
                            )
                        }
                    }

                    Logger.info("  Configuring Kotest JVM runner")
                    testRuns["test"].executionTask.configure {
                        useJUnitPlatform()
                    }
                }


                kmp.experimentalOptIns()

                @Suppress("UNUSED_VARIABLE")
                kmp.setupKotest()
                Logger.lifecycle("") //to make it look less crammed
            }
        }


        runCatching {

            val kotlin = target.kotlinExtension

            if (target != target.rootProject) {
                if (!target.hasMrJar()) //MRJAR
                    kotlin.apply {
                        Logger.lifecycle("  ${H}Setting jvmToolchain to JDK ${target.jvmTarget} for ${target.name}$R")
                        jvmToolchain {
                            languageVersion.set(JavaLanguageVersion.of(target.jvmTarget))
                        }
                    }
                else Logger.lifecycle("  MR Jar plugin detected. Not setting jvmToolchain")

                if (!isMultiplatform) /*TODO: actually check for JVM*/ {
                    Logger.lifecycle("  Assuming JVM-only Kotlin project")
                    target.afterEvaluate {
                        kotlin.apply {
                            sourceSets.getByName("test").dependencies {
                                addKotest("jvm")
                                addKotestJvmRunner()
                            }

                        }
                    }
                }
                Logger.lifecycle("  Adding maven publish plugin\n")
                target.plugins.apply("maven-publish")

                target.afterEvaluate {
                    Logger.info("  Configuring Test output format")
                    target.tasks.withType<Test> {
                        if (name != "testReleaseUnitTest") {
                            useJUnitPlatform()
                            filter {
                                isFailOnNoMatchingTests = false
                            }
                            testLogging {
                                showExceptions = true
                                showStandardStreams = true
                                events = setOf(
                                    TestLogEvent.FAILED,
                                    TestLogEvent.PASSED
                                )
                                exceptionFormat = TestExceptionFormat.FULL
                            }
                        }
                    }
                    target.setupSignDependency()
                    target.compileVersionCatalog()
                }
            }
        }.getOrElse {
            Logger.warn("\n> No Kotlin plugin detected for ${if (target == target.rootProject) "root " else ""}project ${target.name}")
            if (target != target.rootProject) Logger.warn("   Make sure to load the kotlin jvm or multiplatform plugin before the ASP conventions plugin\n")
            else Logger.lifecycle("  This is usually fine.")
        }
    }
}