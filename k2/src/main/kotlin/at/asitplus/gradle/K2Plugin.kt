package at.asitplus.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class AspConventions : AspLegacyConventions() {


    override fun versionOverrides(aspVersions: AspVersions) {
        super.versionOverrides(aspVersions)
        kotlin.runCatching {
            javaClass.classLoader!!.getResourceAsStream("k2versions.properties").use { aspVersions.versions.load(it) }
        }
    }

    override fun Project.addKotestPlugin(isMultiplatform: Boolean) {

        Logger.info("\n  Setting up Kotest multiplatform plugin")
        plugins.apply("io.kotest.multiplatform")
    }

    override fun KotlinMultiplatformExtension.setupKotest() {
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
}
