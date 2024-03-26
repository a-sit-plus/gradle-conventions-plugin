package at.asitplus.gradle

import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class AspConventions : AspLegacyConventions() {

    /*TODO
        init {
            kotlin.runCatching {
                javaClass.classLoader!!.getResourceAsStream("k2versions.properties").use { AspVersions.versions.load(it) }
            }

        }*/

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
