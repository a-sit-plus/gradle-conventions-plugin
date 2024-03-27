import at.asitplus.gradle.kmmresult
import at.asitplus.gradle.napier
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("multiplatform")

    id("com.android.application")
    id("at.asitplus.gradle.conventions")
}


group = "org.example"
version = "1.0-SNAPSHOT"

android {
    namespace = "at.asitplus.cryptotest"
    compileSdk = 34

    defaultConfig {
        minSdk = 33
        targetSdk = 34

        applicationId = "at.asitplus.cryptotest.androidApp"
        versionCode = 1
        versionName = "1.0.0"
    }
    sourceSets["main"].apply {
     //   manifest.srcFile("src/androidMain/AndroidManifest.xml")
      //  res.srcDirs("src/androidMain/resources")
      //  resources.srcDirs("src/commonMain/resources")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}


publishing {
    repositories { mavenLocal() }
}

kotlin{
    jvm()
    androidTarget()
    iosArm64()


}



catalog {
  versionCatalog {
      plugin("ksp", "com.google.devtools.ksp").versionRef("ksp")
  }
}