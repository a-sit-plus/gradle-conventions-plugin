import at.asitplus.gradle.env

plugins {
    id("at.asitplus.gradle.conventions")
}
val foo by env
group = "org.example"
version = "1.0-SNAPSHOT"

