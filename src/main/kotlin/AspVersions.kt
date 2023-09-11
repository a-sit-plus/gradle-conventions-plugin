import java.util.*


object AspVersions {
    @JvmStatic
    internal val versions by lazy {
        javaClass.classLoader!!.getResourceAsStream("versions.properties").use { Properties().apply { load(it) } }
    }

    @JvmStatic
    private fun versionOf(dependency: String) = versions[dependency] as String

    @JvmStatic
    val kotlin = versionOf("kotlin")

    @JvmStatic
    val ksp = versionOf("kotlin") + "-" + versionOf("ksp")

    @JvmStatic
    val serialization = versionOf("serialization")

    @JvmStatic
    val datetime = versionOf("datetime")

    @JvmStatic
    val kotest = versionOf("kotest")

    @JvmStatic
    val ktor = versionOf("ktor")

    @JvmStatic
    val dokka = versionOf("dokka")

    @JvmStatic
    val coroutines = versionOf("coroutines")

    @JvmStatic
    val napier = versionOf("napier")

    @JvmStatic
    val nexus = versionOf("nexus")

    object Jvm {
        @JvmStatic
        val target = versionOf("jvmTarget")

        @JvmStatic
        val bouncycastle = versionOf("bouncycastle")
    }

    @JvmStatic
    val jvm = Jvm //groovy workaround
}
