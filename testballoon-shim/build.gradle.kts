System.setProperty("KOTEST_NO_ASP_HELPER","true")

plugins {
    id("at.asitplus.gradle.conventions")
    kotlin("multiplatform") version libs.versions.kotlin.get() apply false
    kotlin("plugin.serialization") version libs.versions.kotlin.get() apply false
    id("com.android.library") version "8.10.0" apply (false)
}
group = "at.asitplus.gradle"