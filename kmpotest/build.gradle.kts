System.setProperty("KOTEST_NO_ASP_HELPER","true")

plugins {
    id("at.asitplus.gradle.conventions")
    id("com.google.devtools.ksp") version libs.versions.kotlin.get() +"-"+libs.versions.ksp.get()
    id("io.kotest") version libs.versions.kotest.get()
    kotlin("multiplatform") version libs.versions.kotlin.get() apply false
    kotlin("plugin.serialization") version libs.versions.kotlin.get() apply false
    id("com.android.library") version "8.10.0" apply (false)
}
group = "at.asitplus.gradle"
