plugins {
    kotlin("jvm")
    id("at.asitplus.gradle.conventions")
}


group = "org.example"
version = "1.0-SNAPSHOT"


publishing {
    repositories { mavenLocal() }
}