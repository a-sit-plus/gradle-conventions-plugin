package at.asitplus.gradle
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import java.util.*
import kotlin.jvm.optionals.getOrNull


class AspVersions(private val project: Project) {
    val versions: Properties by lazy {
        javaClass.classLoader!!.getResourceAsStream("versions.properties").use { Properties().apply { load(it) } }

    }

    internal fun versionOf(dependency: String) =
        project.extensions.getByType(VersionCatalogsExtension::class).find("libs").getOrNull()?.findVersion(dependency)
            ?.getOrNull()?.displayName ?: versions[dependency] as String

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
