package at.asitplus.gradle

import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class AspConventions : AspLegacyConventions() {


    override fun versionOverrides(aspVersions: AspVersions) {
        super.versionOverrides(aspVersions)
        kotlin.runCatching {
            javaClass.classLoader!!.getResourceAsStream("k2versions.properties").use { aspVersions.versions.load(it) }
        }

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
