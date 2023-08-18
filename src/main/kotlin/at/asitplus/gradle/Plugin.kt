@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import AspVersions
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
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

private inline fun Project.extraProps() {
    println("  Adding support for storing extra project properties in local.properties")
    java.util.Properties().apply {
        kotlin.runCatching { load(java.io.FileInputStream(rootProject.file("local.properties"))) }
        forEach { (k, v) -> extra.set(k as String, v) }
    }
}

class AspConventions : Plugin<Project> {
    override fun apply(target: Project) {

        println("\n ASP Conventions is using the following dependency versions:")
        runCatching {
            AspVersions.versions.entries.sortedBy { (k, _) -> k.toString() }
                .forEach { (t, u) -> println("    ${String.format("%-14s", "$t:")} $u") }
            println()
        }


        println("  Adding Nexus Publish plugin ${AspVersions.nexus}")
        target.rootProject.plugins.apply("io.github.gradle-nexus.publish-plugin")

        target.extraProps()

        if (target == target.rootProject) {

            target.plugins.apply("idea")
            target.extensions.getByType<IdeaModel>().project {
                jdkName = AspVersions.Jvm.target
            }


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
                println("  Using Kotlin version ${it.first().pluginVersion} for project ${target.name}")
            }
        }

        target.plugins.withType<KotlinMultiplatformPluginWrapper> {
            isMultiplatform = true
            println("  Multiplatform project detected")
            println("  Setting up Kotest multiplatform plugin ${AspVersions.kotest}")
            target.plugins.apply("io.kotest.multiplatform")

            target.extensions.getByType<KotlinMultiplatformExtension>().jvm {
                println("  Setting jsr305=strict for JVM nullability annotations")
                compilations.all {
                    kotlinOptions {
                        jvmTarget = AspVersions.Jvm.target
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

            target.afterEvaluate {


                val kmp = extensions.getByType<KotlinMultiplatformExtension>()

                kmp.experimentalOptIns()

                @Suppress("UNUSED_VARIABLE")
                kmp.sourceSets {
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
                println() //to make it look less crammed
            }
        }



        runCatching {

            val kotlin = target.kotlinExtension


            if (target != target.rootProject) {
                if (!target.plugins.hasPlugin("me.champeau.mrjar")) //MRJAR
                    kotlin.apply {
                        println("  Setting jvmToolchain to JDK 11")
                        jvmToolchain {
                            languageVersion.set(JavaLanguageVersion.of(AspVersions.Jvm.target))
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
                        if (name == "testReleaseUnitTest") return@withType
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
                    target.setupSignDependency()
                }

            }
        }.getOrElse {
            println("No Kotlin plugin detected for ${if (target == target.rootProject) "root " else ""}project ${target.name}")
            if (target != target.rootProject) println("   Make sure to load the kotlin jvm or multiplatform plugin before the ASP conventions plugin\n")
            else println("  This is usually fine.")
        }

    }
}