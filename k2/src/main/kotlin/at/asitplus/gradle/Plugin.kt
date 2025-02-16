@file:Suppress("NOTHING_TO_INLINE")

package at.asitplus.gradle

import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.JavaVersion
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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
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

/**
 * System environment delegate to fetch system values.
 */
object EnvDelegate {
    /**
     * gets a system environment [property] if present
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? =
        System.getenv(property.name)
}

/**
 * Chained system environment / Extra properties delegate
 */
class EnvExtraDelegate(private val project: Project) {
    /**
     * Gets a [property] value
     * * from the system environment properties, if present.
     * * If not: gets it from extra properties (`gradle.properties`, can be overridden by `local.properties`)
     * * Returns `null` if the property is not set.
     *
     */
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
private val KEY_VERSION_CATALOG_PUBLISH = Random.nextBits(32).toString(36)

/**
 * Toggle automagic version catalog publishing as `${artefactName}-versionCatalog`
 */
var Project.publishVersionCatalog: Boolean
    get() = kotlin.runCatching { extraProperties[KEY_VERSION_CATALOG_PUBLISH] as Boolean }.getOrElse { false }
    set(value) {
        extraProperties[KEY_VERSION_CATALOG_PUBLISH] = value
    }

/**
 * access to [at.asitplus.gradle.AspVersions]
 */
val Project.AspVersions: AspVersions get() = rootProject.extraProperties[KEY_ASP_VERSIONS] as AspVersions

/**
 * Use as: `val propertyToGet by env` to get the desired system environment property, if present
 */
val Project.env: EnvDelegate get() = EnvDelegate

/**
 * Get a system environment [property] if present
 */
fun Project.env(property: String): String? = System.getenv(property)


/**
 * Use as: `val propertyToGet by envExtra` to get the desired property:
 * * from the system environment properties, if present.
 * * If not: gets it from extra properties (`gradle.properties`, can be overridden by `local.properties`)
 * * Returns `null` if the property is not set.
 *
 */
val Project.envExtra: EnvExtraDelegate get() = EnvExtraDelegate(this)

private inline fun Project.hasMrJar() = plugins.hasPlugin("me.champeau.mrjar")

/**
 * The JVM version to target. This sets `jvmToolchain` and applies to the JVM Kotlin target
 */
val Project.jvmTarget: String get() = runCatching { extraProperties["jdk.version"] as String }.getOrElse { AspVersions.jvm.defaultTarget }

/**
 * Minimum Android SDK version read from the `android.minSdk` property. **This property must be set, if you are targeting Android!**.
 */
val Project.androidMinSdk: Int?
    get() = runCatching { (extraProperties["android.minSdk"] as String).toInt() }.getOrNull()

/**
 * Android compile SDK version read from the `android.compileSdk` property. If unset, specify it manually in side the `android` DSL.
 */
val Project.androidCompileSdk: Int?
    get() = runCatching { (extraProperties["android.compileSdk"] as String).toInt() }.getOrNull()

/**
 * The Android JVM version to target. Automagically set for the `defaultConfig` matching `android.minSdk`.
 * May be overridden by specifying `android.jvmTarget`.
 */
val Project.androidJvmTarget: String?
    get() = runCatching { extraProperties["android.jvmTarget"] as String }.getOrElse {
        androidMinSdk?.let { at.asitplus.gradle.AspVersions.Android.jdkFor(it).toString() }
    }

open class K2Conventions : Plugin<Project> {

    protected open fun KotlinMultiplatformExtension.setupKotest() {
        sourceSets {
            commonTest {
                dependencies {
                    addKotest()
                }
            }
            jvmTest {
                dependencies {
                    addKotestJvmRunner()
                }
            }
        }
    }

    protected open fun Project.addKotestPlugin(isMultiplatform: Boolean) {
        Logger.info("\n  Setting up Kotest multiplatform plugin")
        plugins.apply("io.kotest.multiplatform")
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
        target.publishVersionCatalog = true
        Logger.lifecycle(
            "\n ASP Conventions ${H}${target.AspVersions.versions["kotlin"]}+$buildDate$R is using the following dependency versions for project ${
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
            Logger.info("    * maven snapshots")
            Logger.info("    * maven central")
            Logger.info("    * google")
            target.allprojects {
                repositories {
                    maven("https://oss.sonatype.org/content/repositories/snapshots")
                    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
                    google()
                    mavenCentral()
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
        }
        target.addKotestPlugin(isMultiplatform)
        val hasAgp =
            ((target.pluginManager.findPlugin("com.android.library")
                ?: target.pluginManager.findPlugin("com.android.application")) != null)

        if (hasAgp) target.extensions.getByType<com.android.build.gradle.BaseExtension>().apply {
            compileOptions {
                if (target.androidMinSdk == null)
                    throw StopExecutionException("Android Gradle Plugin found, but no android.minSdk set in properties! To fix this add android.minSdk=<sdk-version> to gradle.properties")
                else {
                    val compat = target.androidJvmTarget
                    Logger.lifecycle("  ${H}Setting Android source and target compatibility to ${compat}.$R")
                    sourceCompatibility = JavaVersion.toVersion(compat!!)
                    targetCompatibility = JavaVersion.toVersion(compat)
                }
            }
            Logger.lifecycle("  ${H}Setting Android defaultConfig minSDK to ${target.androidMinSdk}$R")
            defaultConfig.minSdk = target.androidMinSdk!!
            target.androidCompileSdk?.let {
                Logger.lifecycle("  ${H}Setting Android compileSDK to ${it}$R")
                compileSdkVersion = it.toString()
            }
        }

        target.plugins.withType<KotlinMultiplatformPluginWrapper> {
            target.extensions.getByType<KotlinMultiplatformExtension>().applyDefaultHierarchyTemplate()
            target.afterEvaluate {

                val kmpTargets =
                    extensions.getByType<KotlinMultiplatformExtension>().targets.filter { it.name != "metadata" }
                if (kmpTargets.isEmpty())
                    throw StopExecutionException("No buildable targets found! Declare at least a single one explicitly as per https://kotlinlang.org/docs/multiplatform-hierarchy.html#default-hierarchy-template")


                val kmp = extensions.getByType<KotlinMultiplatformExtension>()

                Logger.lifecycle("\n  This project will be built for the following targets:")
                kmpTargets.forEach { Logger.lifecycle("   * ${it.name}") }

                runCatching {
                    kmp.jvm {
                        Logger.info("  [JVM] Setting jsr305=strict for JVM nullability annotations")
                        compilerOptions {
                            freeCompilerArgs = listOf(
                                "-Xjsr305=strict"
                            )
                            Logger.lifecycle("  ${H}[JVM] Setting jvmTarget to ${target.jvmTarget} for $name$R")
                            jvmTarget = JvmTarget.Companion.fromTarget(target.jvmTarget)
                        }

                        Logger.info("  [JVM] Configuring Kotest JVM runner")
                        testRuns["test"].executionTask.configure {
                            useJUnitPlatform()
                        }
                    }
                }

                val hasAndroidTarget = kmp.targets.firstOrNull { it is KotlinAndroidTarget } != null
                if (hasAndroidTarget) {
                    kmp.androidTarget {
                        Logger.info("  [AND] Setting jsr305=strict for JVM nullability annotations")
                        compilerOptions {
                            if (androidJvmTarget == null)
                                throw StopExecutionException("Android target configured found, but neither android.minSdk set nor android.jvmTarget override set in properties! To fix this add at least android.minSdk=<sdk-version> to gradle.properties")
                            else {
                                Logger.lifecycle("  ${H}[AND] Setting jvmTarget to $androidJvmTarget for $name$R")
                                jvmTarget = JvmTarget.fromTarget(androidJvmTarget!!)
                            }
                            freeCompilerArgs = listOf(
                                "-Xjsr305=strict"
                            )
                        }
                        //TODO test runner setup
                    }
                }



                kmp.experimentalOptIns()

                @Suppress("UNUSED_VARIABLE")
                kmp.setupKotest()
                if (hasAgp) kmp.apply {
                    val hasAndroidTarget = kmp.targets.firstOrNull { it is KotlinAndroidTarget } != null
                    if (hasAndroidTarget) {
                        Logger.lifecycle("  ${H}Creating androidJvmMain shared source set$R")
                        sourceSets.apply {
                            val androidJvmMain by creating {
                                dependsOn(get("commonMain"))
                            }
                            get("androidMain").dependsOn(androidJvmMain)
                            get("jvmMain").dependsOn(androidJvmMain)
                        }
                    }
                }
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
                    runCatching {
                        if (target.tasks.findByName(("clean")) == null)
                            target.tasks.register<Delete>("clean") {
                                Logger.lifecycle("  Adding clean task to root project")

                                doFirst { Logger.lifecycle("> Cleaning all build files") }

                                delete(target.rootProject.layout.buildDirectory.get().asFile)
                                doLast { Logger.lifecycle("> Clean done") }
                            }
                    }


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
                    target.compileVersionCatalog()
                    target.setupSignDependency()
                }
            }
        }.getOrElse {
            Logger.warn("\n> No Kotlin plugin detected for ${if (target == target.rootProject) "root " else ""}project ${target.name}")
            if (target != target.rootProject) Logger.warn("   Make sure to load the kotlin jvm or multiplatform plugin before the ASP conventions plugin\n")
            else Logger.lifecycle("  This is usually fine.")
        }
    }
}