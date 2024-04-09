# Dual-Version (Kotlin 1.9.10 and Kotlin 1.9.22)
* Modularise into two plugins, such that Kotlin 1.9.10 and 1.9.22 are supported in parallel.
  This is required, since working with source differs syntactically between Kotlin < 1.9.20
  and Kotlin 1.9.20 upwards.


## 20240409+1
* Fix gathering of declared versions in absence of version catalog

 
## 20240409
  * **Breaking Change:** Groovy build scripts are no longer supported
  * Auto-Generate Gradle version catalog for maven publication
  * Make it possible to override versions for dependency-shorthands through version catalog
  * Minimum supported gradle version: 8.5

## 20240319+1
* Make it possible to export static XCFrameworks

## 20240319
* Make it possible to export static XCFrameworks

## 20240318
* Make it possible to disable workaround for KT-65315

## 20240316
* Add workaround for KT-65315
* Update dependencies:
  * Kotlin 1.9.23 (K2 only)
  * Kotest 5.8.1
  * Ktor 2.3.9
  * Dokka 1.9.20
  * Coroutines 1.8.0

## 20240301
* Introduce `env` shorthands to access system properties (environment variables)
* rename file `Plugin.kt` -> `K2Plugin.kt` in k2 plugin sources

## 20240216
* First dual-version release

# 1.9.22 (Kotlin 1.9.22)
* Kotlin 1.9.22

## 20240213
* Dependency Updated
  * KSP 1.9.22-1.0.17
  * Ktor 2.3.8

## 20240115
* Dependency Updates:
  * KSP 1.9.22-1.0.16
  * Ktor 2.3.7
  * kotlinx.datetime 0.5.0
  * Napier 2.7.1
  * Bouncy Castle 1.77
  * kotlinx.serialization (fork with COSE support) 1.6.3-SNAPSHOT

# 1.9.20 (Kotlin 1.9.20)
* Kotlin 1.9.20

## 20231114
* Add KmmResult shorthand (version 1.5.4)

## 20231107
* Ktor 2.3.6


## 20231106
* Initial Kotlin 1.9.20 build
* Requires explicit declaration of targets as per the [default hierarchy template](https://kotlinlang.org/docs/multiplatform-hierarchy.html?utm_campaign=hierarchy-template#default-hierarchy-template)
  * As a consequence, `exportIosFramework` can only be called **after** the `koltin` block declaring the targets
* Kotest 5.8.0
* KSP 1.0.14

# 1.9.10 (Kotlin 1.9.10)
* New Dokka helper
* Make all publish tasks depend on all sign tasks
* Serialization fork with COSE features
* Print versions used
* Make embedding bitcode into xcframework configurable

## 20231030
* add `kotlin-reflect` to test dependencies (fixes inability to run single Kotest-tests under certain conditions)
* Dependency updates:
    * Ktor 2.3.5
    * Kotest 5.7.2
    * Dokka 1.9.10

## 20230922
* make it possible to override jdk version using the property `jdk.version`

## 20230911
* Manage ksp version
* optimize loading of versions from properties
* drop `inline` modifiers from all public functions

## 20230908
* Dependency Updates:
  * ktor 2.3.4
  * Kotest 5.7.1
  * Dokka 1.9.0 Release
  * kotlinx.datetime 0.4.1
  * kotlinx.coroutines 1.7.3
  * Bouncy Castle 1.7.6

## 202308238
* Initial Kotlin 1.9 Release
* Dependency Updates:
  * Dokka 1.9
  * kotlinx.serialization 1.6.0-RC
  * Bouncy Castle 1.75
  * kotlinx.coroutines 1.7.2
  * ktor 2.3.3


# 1.8.21 (Kotlin 1.8.21)

## 20230622
* support ktor plugin

## 20230621
* Be agnostic of MR Jar Plugin
* Bouncy Castle 1.74
* Ktor 2.3.1
* Dokka 1.8.20

# 20230620
* Kotest 5.6.2
* Ktor 2.3.0
* Nexus publishing plugin 1.3.0
* Dokka 1.8.10
* kotlinx.serialization 1.5.1
* kotlinx.datetime 0.4.0
* kotlinx.coroutines 1.7.1
* Napier 2.6.1
* JDK 11
* Bouncy Castle 1.73
 