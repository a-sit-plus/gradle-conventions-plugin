@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import AspVersions
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

private const val H = "\u001b[7m\u001b[1m"
private const val R = "\u001b[0m"

private inline fun Project.supportLocalProperties() {
    println("  Adding support for storing extra project properties in local.properties")
    java.util.Properties().apply {
        kotlin.runCatching { load(java.io.FileInputStream(rootProject.file("local.properties"))) }
        forEach { (k, v) -> extra.set(k as String, v) }
    }
}

private inline fun Project.hasMrJar() = plugins.hasPlugin("me.champeau.mrjar")

inline val Project.jvmTarget: String get() = runCatching { extraProperties["jdk.version"] as String }.getOrElse { AspVersions.Jvm.defaultTarget }

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

    override fun apply(target: Project) {

        println(
            "\n ASP Conventions ${H}${AspVersions.kotlin}$R is using the following dependency versions for project ${
                if (target == target.rootProject) target.name
                else "${target.rootProject.name}:${target.name}"
            }:"
        )
        runCatching {
            AspVersions.versions.entries.filterNot { (k, _) -> k == "jvmTarget" }.sortedBy { (k, _) -> k.toString() }
                .forEach { (t, u) -> println("    ${String.format("%-14s", "$t:")} $u") }
            println()
        }


        println("  Adding Nexus Publish plugin ${AspVersions.nexus}")
        target.rootProject.plugins.apply("io.github.gradle-nexus.publish-plugin")

        target.supportLocalProperties()

        if (target == target.rootProject) {

            target.plugins.apply("idea")

            val mrJarModules = target.childProjects.filter { (_, p) -> p.hasMrJar() }
                .map { (name, _) -> name }
            if (mrJarModules.isEmpty()) { //MRJAR
                println("  ${H}Configuring IDEA to use Java ${target.jvmTarget}$R")
                target.extensions.getByType<IdeaModel>().project {
                    jdkName = target.jvmTarget
                }
            } else println(
                println("  MR Jar plugin detected in modules${
                    mrJarModules.joinToString(
                        prefix = "\n",
                        separator = "\n      * ",
                        postfix = "\n"
                    ) { it }
                }   Not setting IDEA Java version.\n")
            )



            println("  Adding repositories")
            println("    * serialization fork")
            println("    * dokka dev")
            println("    * maven central")
            println("    * google")
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
                    println("  Adding clean task to root project")

                    doFirst { println("Cleaning all build files") }

                    delete(target.rootProject.buildDir)
                    //delete(target.layout.projectDirectory.dir("repo"))
                    doLast { println("Clean done") }
                }
            }

            println("  Setting Nexus publishing URL to s01.oss.sonatype.org")
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
                println("  ${H}Using Kotlin version ${it.first().pluginVersion} for project ${target.name}$R")
            }
        }

        target.plugins.withType<KotlinMultiplatformPluginWrapper> {
            isMultiplatform = true
            println("  ${H}Multiplatform project detected$R")

            target.afterEvaluate {

                val kmpTargets =
                    extensions.getByType<KotlinMultiplatformExtension>().targets.filter { it.name != "metadata" }
                if (kmpTargets.isEmpty())
                    throw StopExecutionException("No buildable targets found! Declare at least a single one explicitly as per https://kotlinlang.org/docs/multiplatform-hierarchy.html#default-hierarchy-template")

                println("\n  This project will be built for the following targets:")
                kmpTargets.forEach { println("   * ${it.name}") }


                println("\n  Setting up Kotest multiplatform plugin ${AspVersions.kotest}")
                plugins.apply("io.kotest.multiplatform")

                extensions.getByType<KotlinMultiplatformExtension>().jvm {
                    println("  Setting jsr305=strict for JVM nullability annotations")
                    compilations.all {
                        kotlinOptions {
                            if (!hasMrJar()) { //MRJAR
                                println("  ${H}Setting jvmTarget to ${target.jvmTarget} for $name$R")
                                kotlinOptions.jvmTarget = target.jvmTarget
                            } else println("  MR Jar plugin detected. Not setting jvmTarget")
                            freeCompilerArgs = listOf(
                                "-Xjsr305=strict"
                            )
                        }
                    }

                    println("  Configuring Kotest JVM runner")
                    testRuns["test"].executionTask.configure {
                        useJUnitPlatform()
                    }
                }

                val kmp = extensions.getByType<KotlinMultiplatformExtension>()

                kmp.experimentalOptIns()

                @Suppress("UNUSED_VARIABLE")
                kmp.setupKotest()
                println() //to make it look less crammed
            }
        }


        runCatching {

            val kotlin = target.kotlinExtension

            if (target != target.rootProject) {
                if (!target.hasMrJar()) //MRJAR
                    kotlin.apply {
                        println("  ${H}Setting jvmToolchain to JDK ${target.jvmTarget}$R")
                        jvmToolchain {
                            languageVersion.set(JavaLanguageVersion.of(target.jvmTarget))
                        }
                    }
                else println("  MR Jar plugin detected. Not setting jvmToolchain")

                if (!isMultiplatform) /*TODO: actually check for JVM*/ {
                    println("  Assuming JVM-only Kotlin project")
                    target.afterEvaluate {
                        kotlin.apply {
                            sourceSets.getByName("test").dependencies {
                                addKotest("jvm")
                                addKotestJvmRunner()
                            }

                        }
                    }
                }
                println("  Adding maven publish plugin")
                target.plugins.apply("maven-publish")

                target.afterEvaluate {
                    println("  Configuring Test output format")
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
                }
            }
        }.getOrElse {
            println("\n> No Kotlin plugin detected for ${if (target == target.rootProject) "root " else ""}project ${target.name}")
            if (target != target.rootProject) println("   Make sure to load the kotlin jvm or multiplatform plugin before the ASP conventions plugin\n")
            else println("  This is usually fine.")
        }
    }
}