package at.asitplus.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.util.*


class AspVersions(private val project: Project) {
    val versions: Properties by lazy {
        javaClass.classLoader!!.getResourceAsStream("versions.properties").use { Properties().apply { load(it) } }

    }

    val versionCatalog: TomlParseResult by lazy {
        Toml.parse(
            project.rootProject.layout.projectDirectory.dir("gradle").file("libs.versions.toml").asFile.inputStream()
        )
    }

    internal fun versionOf(dependency: String) =
        versionCatalog.getTable("versions")?.getString(dependency) ?: versions[dependency] as String

    val kotlin get() = project.getKotlinPluginVersion()

    val ksp get() = versionOf("kotlin") + "-" + versionOf("ksp")

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
        val defaultTarget = 11.toString()

        val bouncycastle get() = versionOf("bouncycastle")
    }

    val jvm = Jvm()
}
