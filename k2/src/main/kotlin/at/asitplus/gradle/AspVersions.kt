package at.asitplus.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.util.*


/**
 * Helper class, which provides version information for various dependencies commonly used in A-SIT Plus projects.
 */
class AspVersions(private val project: Project) {
    val versions: Properties by lazy {
        javaClass.classLoader!!.getResourceAsStream("versions.properties").use { Properties().apply { load(it) } }

    }

    val versionCatalog: TomlParseResult? by lazy {
        kotlin.runCatching {
            Toml.parse(
                project.rootProject.layout.projectDirectory.dir("gradle")
                    .file("libs.versions.toml").asFile.inputStream()
            )
        }.getOrNull()
    }

    /**
     * Gets the version for the dependencies managed by shorthands. Can be overridden by `gradle/libs.versions.toml`
     */
    internal fun versionOf(dependency: String) =
        versionCatalog?.getTable("versions")?.getString(dependency) ?: versions[dependency] as String


    val kotlin get() = project.getKotlinPluginVersion()

    val ksp get() = kotlin + "-" + versions.getProperty("ksp")

    val serialization = versionOf("serialization")

    val datetime = versionOf("datetime")

    val kotest = versionOf("kotest")

    val ktor = versionOf("ktor")

    val dokka = versionOf("dokka")

    val coroutines = versionOf("coroutines")

    val napier = versionOf("napier")

    val nexus = versionOf("nexus")

    val kmmresult = versionOf("kmmresult")

    inner class Jvm {
        val defaultTarget = 17.toString()

        val bouncycastle get() = versionOf("bouncycastle")
    }

      object Android {
         private val androidJvmMap = linkedMapOf<Int, JavaVersion>(
             31 to JavaVersion.VERSION_1_8,
             32 to JavaVersion.VERSION_11,
             33 to JavaVersion.VERSION_11,
             34 to JavaVersion.VERSION_17,
         )

         fun jdkFor(sdkVersion:Int): JavaVersion {
             val lowerBound= androidJvmMap.entries.first().key
             val upperBound = androidJvmMap.entries.last().key
             return if(sdkVersion<=lowerBound) androidJvmMap.entries.first().value
             else if(sdkVersion>=upperBound) androidJvmMap.entries.last().value
             else androidJvmMap[sdkVersion]?:throw StopExecutionException("Unsupported SDK version $sdkVersion")
         }

    }



    val jvm = Jvm()
}
