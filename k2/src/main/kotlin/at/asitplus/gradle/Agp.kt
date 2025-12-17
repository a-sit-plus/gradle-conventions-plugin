package at.asitplus.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.androidLibrary
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.PluginAware
import org.gradle.api.tasks.StopExecutionException
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

val PluginAware.isAndroidApplication get() = pluginManager.findPlugin("com.android.application") != null
val PluginAware.isAndroidLibrary get() = pluginManager.findPlugin("com.android.library") != null
val PluginAware.isNewAndroidLibrary get() = pluginManager.findPlugin("com.android.kotlin.multiplatform.library") != null

internal val PluginAware.hasOldAgp get() = isAndroidApplication || isAndroidLibrary

val PluginAware.agpVersion get() = if (hasOldAgp || isNewAndroidLibrary) com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION else null

/**
 * A project extension property that determines whether to keep Android-specific JVM main source sets.
 *
 * This property is used when working with shared source sets between Android and JVM platforms,
 * allowing the shared Android/JVM sources to persist even when no Android Plugin is applied.
 * By default, this property is `false`
 */
var Project.keepAndroidJvmTarget: Boolean
    set(value) {
        extensions.extraProperties["keepAndroidJvmTarget"] = value
        logger.info("Keeping shared Android JVM main sources")
    }
    get() = extensions.extraProperties.has("keepAndroidJvmTarget") &&
            (extensions.extraProperties["keepAndroidJvmTarget"] as? Boolean ?: false)

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

/**
 * Switch to raise the JVM target used for Android test compile tasks to `jdk.version`. Defaults to `false`.
 * Can be specified using `android.raiseTestToJdkTarget`
 */
val Project.raiseAndroidTestToJdkTarget: Boolean
    get() = runCatching { extraProperties["android.raiseTestToJdkTarget"] as String }.getOrElse {
        "false"
    }.toBoolean()

internal fun Project.setAndroidOptions() {
    if (hasOldAgp) extensions.getByType<com.android.build.gradle.BaseExtension>().apply {
        compileOptions {
            if (androidMinSdk == null)
                throw StopExecutionException("Android Gradle Plugin found, but no android.minSdk set in properties! To fix this add android.minSdk=<sdk-version> to gradle.properties")
            else {
                val compat = androidJvmTarget
                Logger.lifecycle("  ${H}Setting Android source and target compatibility to ${compat}.$R")
                sourceCompatibility = JavaVersion.toVersion(compat!!)
                targetCompatibility = JavaVersion.toVersion(compat)
            }
        }
        Logger.lifecycle("  ${H}Setting Android defaultConfig minSDK to ${androidMinSdk}$R")
        defaultConfig.minSdk = androidMinSdk!!
        androidCompileSdk?.let {
            Logger.lifecycle("  ${H}Setting Android compileSDK to ${it}$R")
            compileSdkVersion(it)
        }
    } else if (isNewAndroidLibrary) {
        val compat = androidJvmTarget
        Logger.lifecycle("  ${H}Setting Android source and target compatibility to ${compat}.$R")
        extensions.getByType<KotlinMultiplatformExtension>().apply {
            androidLibrary {
                compileSdk = androidCompileSdk
                minSdk = androidMinSdk
                compilations.configureEach {
                    if (name.contains("test", ignoreCase = true)) {
                        if (raiseAndroidTestToJdkTarget) compilerOptions.configure {
                            jvmTarget.set(JvmTarget.fromTarget(project.jvmTarget))
                        }
                    } else compilerOptions.configure {
                        jvmTarget.set(JvmTarget.fromTarget(compat!!))
                    }
                }
            }
            sourceSets.whenObjectAdded {
                if (this.name == "androidDeviceTest") {
                    dependencies {
                        implementation("de.infix.testBalloon:testBalloon-framework-core:${AspVersions.testballoon}")
                        implementation("androidx.test:runner:${AspVersions.androidTestRunner}")
                    }
                }
            }
            sourceSets.findByName("androidDeviceTest")?.dependencies {
                implementation("de.infix.testBalloon:testBalloon-framework-core:${AspVersions.testballoon}")
                implementation("androidx.test:runner:${AspVersions.androidTestRunner}")
            }
        }
    }
}

internal val KotlinMultiplatformExtension.hasAndroidTarget get() = targets.firstOrNull { it is KotlinAndroidTarget || it is KotlinMultiplatformAndroidLibraryTarget } != null


internal fun Project.createAndroidJvmSharedSources() {
    var sharedAdded = false
    val kmp = extensions.getByType<KotlinMultiplatformExtension>()

    kmp.targets.whenObjectAdded {
        if (sharedAdded) return@whenObjectAdded
        kmp.applyDefaultHierarchyTemplate()
        if ((hasOldAgp || isNewAndroidLibrary || keepAndroidJvmTarget) && kmp.hasJvmTarget()) kmp.apply {
            if (hasAndroidTarget || keepAndroidJvmTarget) {
                sharedAdded = true
                Logger.lifecycle("  ${H}Creating androidJvmMain shared source set$R")
                sourceSets.apply {
                    val androidJvmMain by creating {
                        dependsOn(get("commonMain"))
                    }
                }
            }
        }
    }
}

val NamedDomainObjectContainer<KotlinSourceSet>.androidJvmMain: KotlinSourceSet
    get() = findByName("androidJvmMain")
        ?: throw IllegalStateException("No androidJvmMain source set found!")

fun NamedDomainObjectContainer<KotlinSourceSet>.androidJvmMain(configure: KotlinSourceSet.() -> Unit): KotlinSourceSet =
    findByName("androidJvmMain")?.apply {
        configure()
    } ?: throw IllegalStateException("No androidJvmMain source set found!")

internal fun KotlinMultiplatformExtension.linkAgpJvmSharedSources() {
    targets.whenObjectAdded {
        if ((project.hasOldAgp || project.isNewAndroidLibrary || project.keepAndroidJvmTarget) && this@linkAgpJvmSharedSources.hasJvmTarget()) {
            Logger.lifecycle("  ${H}Linking androidJvmMain shared source set$R")
            this@linkAgpJvmSharedSources.sourceSets.apply {
                val androidJvmMain by getting
                if (this@linkAgpJvmSharedSources.hasAndroidTarget) get("androidMain").dependsOn(androidJvmMain)
                get("jvmMain").dependsOn(androidJvmMain)
            }
            this@linkAgpJvmSharedSources.setupAndroidTarget()
        }
    }
}

private fun KotlinMultiplatformExtension.setupAndroidTarget() {
    if (project.hasOldAgp) androidTarget {
        if (project.isAndroidLibrary) publishLibraryVariants.let {
            if (it == null || it.isEmpty())
                throw StopExecutionException("Android target found, but no publishing variant set. Setting publishing variants is mandatory for Android libraries! Otherwise no Android library artefact will be created.")

        }
        Logger.info("  [AND] Setting jsr305=strict for JVM nullability annotations")

        compilerOptions {
            if (project.androidJvmTarget == null)
                throw StopExecutionException("Android target found, but neither android.minSdk set nor android.jvmTarget override set in properties! To fix this add at least android.minSdk=<sdk-version> to gradle.properties")
            else {
                Logger.lifecycle(
                    "  ${H}[AND] Setting $name ${
                        String.format(
                            "%-14s",
                            "main sources"
                        )
                    } jvmTarget to ${project.androidJvmTarget}$R"
                )
                jvmTarget = JvmTarget.fromTarget(project.androidJvmTarget!!)
            }
            freeCompilerArgs = listOf(
                "-Xjsr305=strict"
            )
        }
        if (project.raiseAndroidTestToJdkTarget) compilations.filter {
            it.name.contains(
                "test",
                ignoreCase = true
            )
        }.forEach {
            Logger.lifecycle(
                "  ${H}[AND] Setting $name ${
                    String.format("%-14s", it.name)
                } jvmTarget to ${project.jvmTarget}$R"
            )
            it.target.compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(project.jvmTarget))
            }
        }
        //TODO test runner setup
    }
}