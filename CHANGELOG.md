# No-Kotlin-Version

Starting with May 2025, the conventions plugin does not include a Kotlin version anymore. Instead, it lets any consuming
project set it, just as with the Android Gradle Plugin. **This is a breaking change, as there is no fallback version specified.**

## TBA
* Allow nested typealias

## 20251023
* auto-setup configured JDK matching JVM target
* allow optionally wiring shared Android/JVM sources even when no Android plugin is enabled

## 20251017
* Fully integrated test configs

## 20251003
* Migrations thanks to [@Oliver02](https://github.com/OliverO2)
  * Migrate from Kotest to [TestBalloon](https://github.com/infix-de/testBalloon)
  * Migrate to [new Android Library KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin)

## 20250729
* Add `android.raiseTestToJdkTarget` property to toggle raising Android test compile target JVM version to `jdk.version`.  
Previously this defaulted to true, now it defaults to false.

## 20250728
* Fix Kotest KSP wiring for good

## 20250727
* Kotest 6.0.0.M6
* Removed https://s01.oss.sonatype.org/content/repositories/snapshots from maven repos
* `XmlReportingProjectConfig` Kotest abstract project config shim to enable KMP JUnit XML reporting

## 20250723+1
* fix order of XCF export arguments

## 20250723
* Upgrade nexus publish

## 20250722
* Ktor 3.2.2
* fix XCF export

## 20250714
* fix signing dependency workaround
* add ASP repo to main repos too
* fix linux test report dir
* Serialization 1.9.0

## 20250713
* Don't wire Kotest KMP processor to non-buildable targets

## 20250712
* Kotest KMP test report generator

## 20250711
* Kotlin 2.2.0
* KSP-based Kotest 6.0.0.M5
* Allow overriding Kotest version through environment variable `KOTEST_VERSION_OVERRIDE`, which takes precedence over version catalog


## 20250708+1
* fix XCF export

## 20250708
* breaking change: kotlinx.datetime 0.7.1
* Rollback to Kotest 6.0.0.M1 by default

## 20250628
* Kotest Multiplatform must be manually added to the root project, as it is now an optional dependency
* Publish to new maven Central endpoint
* Dependency Updated:
  * Bouncy Castle 1.81!!
  * Ktor 3.2.0
  * KmmResult 1.9.3

## 20250506
* First version not to ship with a fixed-version Kotlin plugin
* Drop bundling KSP as it makes no sense without a bundled Kotlin version
* Dependency updates
  * Bouncy Castle 1.80!!
  * Serialization 1.8.1
  * Coroutines 1.10.2
  * Ktor 3.1.2
  * Datetime 0.6.2
  * Kotest 6.0.0.M4
  * Kotest Plugin 6.0.0.M3

# Single-Version Kotlin 1.9.10 / 2+

# 2.1.20

## 20250409
* Fail on misconfigured Android Projects
* Fix opt-in typo

## 20250324
* Kotlin 2.1.20
* KSP 1.0.31

# 2.0.20

## 20250311 
* Don't assume a JVM target is always present
  * If both JVM and Android targets are present, automagically create the shared `androidJvmMain` **and also make it available right away!**
  * Simply use `val androidJvmMain by getting {…}` to declare any shared dependencies inside the regular source sets block and you're golden. No need to put it into `afterEvaluate` anymore

## 20250219+1
* Fix Kotest plugin version mishap
* Update kotest plugin to 6.0.0 M2

## 20250219
* KmmResult 1.9.1
* Fix Android SDK version parsing

## 20250218
* **Drop Kotlin 1.9 support**
* **Drop MRJAR support**, since it was flaky anyway
* Add new property `android.minSdk`
* Add new property `android.compileSdk`
* Add new property `android.jvmTarget`
* Allow separate JVM targets for Android and JVM. To use it:
  * Add the AGP to your project
  * Set `android.minSdk`
  * Set `android.compileSdk`
  * Add at least one `androidTaget` and configure at least one variant to publish
  * Configure the toplevel `android` block (minSdk, and compileSdk can be omitted, as they are read from properties)
  * Once this is done
    * A shared `androidJvmMain` source set automagically available to Android and JVM targets
    * Compile for JVM target using `jdk.version`
    * Compile for Android target using the JDK version matching `android.minSdk` (can be overridden by `android.jvmTarget`)
  * **The `jvmToolchain` will always be set to `jdk.version`**


# Dual-Version Kotlin 1.9.10 / 2.0.20+

## 20250107
* Serialization 1.8.0
* Kotlin 2.1.20-Beta1

# 2.1.0
* Kotlin 2.1.0

## 20241219
* Ktor 3.0.3
* Improve XCF export
  * Add `NativeBinary` customization options
  * Remove deprecated alias `exportIosFramework`
  * Explicitly require specifying `static` **and** `transitiveExports`

## 20241204
* KSP 1.0.29

# 2.0.21
* Kotlin 2.0.21 with XCode 16 Support

## 20241118
* KSP 1.0.28
* Bouncy Castle 1.79!! **with PQC support**
* Ktor 3.0.1

## 20241030
* Export XCFramework to all targets

## 20241029
* KmmResult 1.9.0
* Only add Clean task when it does not yet exist

## 20241024
* Kotest 6.0.0.M1
* KmmResult 1.8.0

## 20240930
* kotlinx.serialization 1.7.3
* Add flag to disable version Catalog publishing

## 20240920
* Kotest plugin 6.0.0-20240918.002009-71

## 20240919
* KSP 1.0.25 (K2 only)
* allow fine-grained XCodeFramework export-config with shorthand for transitive dependency export

## 20240917
* Coroutines 1.9.0
* Kotest plugin 6.0.0-20240905.065253-61
* Kotest dependencies 5.9.1
* Print out build date in plugin info

## 20240905
* Pin Kotest Snapshot version to 6.0.0-20240905.065253-61

## 20240904
* Pin Kotest Snapshot version to 6.0.0.1564-20240722.131423-1

## 20240829
* Kotest 6.0 Snapshot
* KSP 2.0.20-2.0.24 (K2 only)
* kotlinx.serialization 1.7.2 with full COSE support
* kotlinx-datetime 0.6.1

## 20240725

* Don't depend on SNAPSHOT serialization by default. If you need COSE features that were previously pulled in form the snapshot, simply add `serialization = 1.8.0-SNAPSHOT` (or: even safer: `serialization = 1.8.0-SNAPSHOT!!`) to your project' `gradle/libs.verions.toml`. This changes comes with a huge advantage: It does not add this snapshot dependency virally to all projects that rely on this conventions plugin.

## 20240717
* XCFramework: Disable bitcode embedding by default
* Dependency Updates:
  * KmmResult 1.7.0
  * Ktor 2.3.12
  * KSP 2.0.0-2.0.23 (K2 only)

## 20240619
* Fix Multiplatform builds without JVM target (i.e. compose mobile, without desktop target)
* Fix version catalog version reading

## 20240610
* KmmResult 1.6.1
* Maven central snapshot repo added by default

## 20240609
* KSP 1.0.22 for K2

## 20240607
* Kotest 5.9.1 for K2
* kotlinx.serialization 1.7.1-SNAPSHOT for K2

## 20240604+1
* Fix Kotest multiplatform plugin loading for K2

## 20240604
* Fix Kotest version for K2

## 20240603
* Gradle 8.8
* Default to Kotlin 1.9.10 (legacy) / 2.0.0 (k2)
* JVM 17 by default
* Bouncy Castle 1.78.1
* KSP 2.0.0-1.0.21 (K2 only)
* Kotest 5.9.0 (K2 only)
* Ktor 2.3.11
* kotlinx.datetime 0.6.0
* kotlinx.coroutines 1.8.1
* KmmResult 1.6.0

# Dual-Version (Kotlin 1.9.10 and Kotlin 1.9.22+)
* Modularise into two plugins, such that Kotlin 1.9.10 and 1.9.22 are supported in parallel.
  This is required, since working with source differs syntactically between Kotlin < 1.9.20
  and Kotlin 1.9.20 upwards.

## 20240531
* Explicitly use `jvmToolchain(17)` in plugin build scripts

## 20240501
* Work around Gradle bug [26091](https://github.com/gradle/gradle/issues/26091) in yet another ways

## 20240430
* more version catalog publishing workarounds

## 20240425+1
* Add missing name to version catalog maven metadata to jvm-only version catalog

## 20240425
* Add maven metadata to jvm-only version catalog

## 20240424
* Publish version catalog to separate publication with classifier "versionCatalog"

## 20240422 (broken publishing!)
* Fix version catalog building issue for version-less dependencies

## 20240410 (broken publishing!)
* Fix missing sign-publish task dependency
* Make adding versionRef-managed versions extensible (for VcLib conventions plugin)
* ktor 2.3.10
* K2: KSP 1.0.20

## 20240409+1 (broken publishing!)
* Fix gathering of declared versions in absence of version catalog

 
## 20240409 (broken publishing!)
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
 
