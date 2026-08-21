System.setProperty("TESTBALLOON_NO_ASP_HELPER","true")

plugins {
    id("at.asitplus.gradle.conventions")
    kotlin("multiplatform") version libs.versions.kotlin.get() apply false
    kotlin("plugin.serialization") version libs.versions.kotlin.get() apply false
    id("com.android.kotlin.multiplatform.library") version libs.versions.agp.get() apply false
}
group = "at.asitplus.gradle"
