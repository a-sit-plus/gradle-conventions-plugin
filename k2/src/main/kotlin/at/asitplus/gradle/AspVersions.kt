package at.asitplus.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
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


    /**
     * Kotlin version. Cannot be overridden
     */
    val kotlin get() = project.getKotlinPluginVersion()

    /**
     * kotlinx.serialization version (libraries, not plugin!). Override by adding setting a `serialization` in `libs.versions.toml`.
     */
    val serialization = versionOf("serialization")

    /**
     * kotlinx.dateteime version.  Override by adding setting a `datetime` in `libs.versions.toml`.
     */
    val datetime = versionOf("datetime")

    /**
     *  Kotest version (libraries and plugin). Override by adding setting a `kotest` in `libs.versions.toml`.
     */
    val kotest = System.getenv("KOTEST_VERSION_OVERRIDE")?.ifBlank { null } ?:versionOf("kotest")

    /**
     *  Testballoon version (libraries and plugin). Override by adding setting a `testballoon` in `libs.versions.toml`.
     */
    val testballoon = System.getenv("TESTBALLOON_VERSION_OVERRIDE")?.ifBlank { null } ?:versionOf("testballoon")

    /**
     * Ktor version.  Override by adding setting a `ktor` in `libs.versions.toml`.
     */
    val ktor = versionOf("ktor")

    /**
     * Dokka version. Should not be overridden!
     */
    val dokka = versionOf("dokka")

    /**
     * kotlinx.coroutines version. Override by adding setting a `coroutines` in `libs.versions.toml`.
     */
    val coroutines = versionOf("coroutines")

    /**
     * Napier version. Override by adding setting a `napier` in `libs.versions.toml`.
     */
    val napier = versionOf("napier")

    /**
     * Gradle-nexux-publish-plugin version.
     */
    val nexus = versionOf("nexus")

    /**
     * KmmResult version.  Override by adding setting a `kmmresult` in `libs.versions.toml`.
     */
    val kmmresult = versionOf("kmmresult")

    inner class Jvm {
        val defaultTarget = 17.toString()

        /**
         * Bouncy Castle version.  Override by adding setting a `bouncycastle` in `libs.versions.toml`.
         */
        val bouncycastle get() = versionOf("bouncycastle")
    }

    internal object Android {
        private val androidJvmMap = linkedMapOf<Int, JavaVersion>(
            31 to JavaVersion.VERSION_1_8,
            32 to JavaVersion.VERSION_11,
            33 to JavaVersion.VERSION_11,
            34 to JavaVersion.VERSION_17,
        )

        fun jdkFor(sdkVersion: Int): JavaVersion {
            val lowerBound = androidJvmMap.entries.first().key
            val upperBound = androidJvmMap.entries.last().key
            return if (sdkVersion <= lowerBound) androidJvmMap.entries.first().value
            else if (sdkVersion >= upperBound) androidJvmMap.entries.last().value
            else androidJvmMap[sdkVersion] ?: throw StopExecutionException("Unsupported SDK version $sdkVersion")
        }

    }


    val jvm = Jvm()
}
