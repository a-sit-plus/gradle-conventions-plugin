# A-SIT Plus Gradle Conventions Plugin

[![Version](https://img.shields.io/badge/stable_version-1.9.10+20230911-blue.svg?style=flat)](CHANGELOG.md)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

**Note: This plugin is still in its early stages and may not work well for edge cases!
Tested against Gradle 7.6 and 8.1.1. Requires Java 11!**

This repository bundles conventions used inside A-SIT Plus into a plugin.
The main motivation to go beyond a versions catalogue was re-usability inside complex, nested composite builds.
Gradle version catalogues are great, they can only get you so far…

This plugin targets Kotlin JVM and multiplatform projects and provides the following functionality:

* Version management of core libraries, Dokka, Kotlin, certain Kotlin plugins, Gradle Ktor Plugin (and ktor libraries),
  ksp plugin, and JVM toolchain (can be overridden)
* Shorthands for various commonly-used dependencies
* Natural extension functions to ass common dependencies
* Autoconfiguration of Kotest for multiplatform projects
* Automatically adding of google and maven central repository
* Auto-setup of gradle-nexus-plugin (but no configuration of publishing)
* Experimental opt-ins for multiplatform projects
* Gitlab publishing shorthands
* Shorthand to publish an iOS Framework
* Support for storing extra properties in `local.properties` as known from the Android Gradle Plugin
* Compatible with Android Gradle Plugin
* Introduction of a `clean` task to the root project
* Auto-apply `idea` plugin to root project and set JDK name in accordance with JVM target
* Autoconfiguration of test output format
* Force dependency from publish tasks to sign tasks
* Shorthand for Dokka setup

This plugin is hosted on a public GitHub repo, because a) som of our publicly published projects depend on it and b)
sharing is caring!
We hope that this plugin can also help other seeking to streamline build processes across multiple projects following a
common set of conventions.

## Adding the Plugin

You can add this plugin to your project using one of two ways: either add it as part of a composite build or include the
maven repository hosted in this GitHub repo. Then apply the plugin in your Gradle root project.

### Adding the plugin as part of a composite build

Add this repository as a git submodule to your project, then add the following to `settings.gradle`:

```kotlin
pluginManagement {
    includeBuild("gradle-conventions-plugin")
}
```

The composite build approach provides more flexibility and makes sense when contributing to or this plugin.
Including plugins through composite builds works even in settings here the Kotlin Gradle plugin would otherwise fail to
handle
composite builds correctly (such as, when depending on a multiplatform library in an Android project).
If, for example, you are developing a large piece of software, consisting of many individual Gradle projects, that
justifies the creation of additional conventions based on this plugin,
simply add it as a composite build to the new conventions plugin, and you are good to go!
In most cases, however, you want to depend on a specific version of this plugin that maps to the Kotlin version you want
to use.

### Adding the plugin's maven repository

This plugin is directly published as a maven repository to GitHub (this repo, branch `mvn`).
To make this plugin available for your projects, simply add the following to your root project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://raw.githubusercontent.com/a-sit-plus/gradle-conventions-plugin/mvn/repo")
            name = "aspConventions"
        }
        maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Note that is is important to (at least) add `gradlePluginPortal()`. Otherwise, **no plugins at all** can be resolved!

## Applying the plugin

Before being able to add this plugin to your project's modules, add the following to your Gradle root project as show
below.

```kotlin
plugins {
    id("at.asitplus.gradle.conventions") version "1.8.21+20230622" //version can be omitted when using this plugin through composite builds
}
```

Versions of various Kotlin plugins will then be managed by this conventions plugin for each module the plugin is applied
to.
The plugin's version corresponds to the Kotlin version provided by this plugin, while the timestamp simply indicates the
build date.

The logic behind this scheme is rooted in the fact that it manages more than just the Kotlin version. Hence, updates to
other dependencies need to be captured as well.
A full list of all plugin and dependency versions can be found
in [versions.properties](src/main/resources/versions.properties).
Access to all declared versions inside a Gradle module is possible through
the [AspVersions](src/main/kotlin/AspVersions.kt) object.
<br>
**Please refer to the [changelog](CHANGELOG.md) for detailed version information on each build of this plugin.**

### Multiplatform

Configuring a multiplatform module relying on serialization and dokka, for example, can be achieved by the
following `plugins` block:

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("io.ktor.plugin")
    id("at.asitplus.gradle.conventions")
}
```

**Be sure to add the conventions plugin last!**

### JVM-Only

The same approach show before also works for jvm-only projects:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("at.asitplus.gradle.conventions")
}
```

**Be sure to add the conventions plugin last!**

### Usage with other Kotlin Plugins

Although adding this plugin to the root project completely removes the need to manage Kotlin plugin versions in the
modules, it does
come with a caveat:
Only the following Gradle plugins are directly supported with implicit versions:

* Kotlin
    * multiplatform
    * jvm
    * serialization
* Ktor
* Kotest
* Dokka
* Nexus publishing

If, for example, you rely on other plugins, which must be in sync with the Kotlin version used by your project, you need
to identify the plugin's artefact coordinates.
You can usually obtain a plugin's coordinates through the Gradle plugin portal by search for it, and then checking the "
legacy" way of adding plugins.

If you rely on `kotlin("plugin.spring")` and `kotlin("plugin.jpa")` as typical for a Spring + hibernate webservice, add
the following to your **root project's** `build.gradle.kts`:

```kotlin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen") //Spring
        classpath("org.jetbrains.kotlin:kotlin-noarg") //JPA
    }

}
```

You can then simply add `kotlin("plugin.spring")` and `kotlin("plugin.jpa")` to your module's `plugins` block and the
version will be in sync with the core Kotlin Gradle plugin.
The reason why you cannot access `AspVersions.kotlin` inside the plugins-block to have it match the Kotlin version is
that this property is not a compile-time constant.

## Extensions and Shorthands

Common compiler options (opt-ins for serialization, coroutines, datetime, and `RequiresOptIn`), and Kotest are applied
by default.
`jvmToolchain(11)` is also applied, **unless**
the [multi-release jar plugin ("me.champeau.mrjar")](https://melix.github.io/mrjar-gradle-plugin/0.1/index.html) is
applied as well.
Note that no version management is in place for the multi-release jar plugin, as we rarely need it internally.
In addition, shorthand for dependencies and other extensions are available to streamline project setup.

### Dependency Shorthands

The following shorthands are available to declare dependencies:

* `kotest(module: String, target: String? = null)`, where module is something like `datatest`, dor example and target
  can be blank for multiplatform or `jvm` to target the JVM.
  <br>Note that the following kotest modules are available by default, and require no separate declaration:
    * `kotest-assertions-core`
    * `kotest-common`
    * `kotest-property`
    * `kotest-framework-engine`
    * `kotest-framework-datatest`
* `serialization(format: String, target: String? = null)` declares a dependency for kotlinx-serialization
  format `format` (i.e. `json`, `cbor`, …) to target `target` (leave blank for multiplatform)
* `ktor(module: String, target: String? = null)` declares a dependency to a ktor module `module` for target `target` (
  leave blank for multiplatform)
* `coroutines(target: String? = null)` declares a dependency to kotlinx.coroutines-core for target `target`
* `napier(target: String? = null)` declares a dependency to Napier
* `datetime` declares a dependency to kotlinx.datetime
* `bouncycastle(module: String, classifier: String = "jdk18on")` declares a dependency to a bouncy castle module (
  e.g. `bcpkix`) with classifier `classifier` (e.g. `jdk18on`) (JVM-only!)

Any of them can as regular dependency declarations would be. Version declarations are not possible, since all versions
are managed by this plugin.
Fhe following two examples illustrates valid dependency declarations for multiplatform and JVM-only respectively.

```kotlin
//mutliplatform example
sourceSets {
    /* Main source sets */
    val commonMain by getting {
        dependencies {
            api(serialization("cbor"))
            api(ktor("client-core"))
            api(ktor("client-logging"))
            api(ktor("client-serialization"))
            api(ktor("client-content-negotiation"))
            api(ktor("serialization-kotlinx-json"))
        }
    }

    val iosMain by getting {
        dependencies {
            api(ktor("client-darwin"))
        }
    }

    val jvmMain by getting {
        dependencies {
            api(bouncycastle("bcpkix"))
            api(ktor("client-okhttp"))
        }
    }
}
```

```kotlin
//JVM example
dependencies {
    testImplementation(ktor("client-java"))
}
```

### Building iOS Framework

This plugin adds a shorthand for building iOS frameworks form multiplatform projects. It targets `ios`
and `iosSimulatorArm64`.
Simply invoke `exportIosFramework(name: String, vararg additionalExport: Any)` in your module's build script as
illustrated by the example below:

```kotlin
plugins {
    kotlin("multiplatform") //version managed by conventions plugin
    kotlin("plugin.serialization") //version managed by conventions plugin
}

exportIosFramework(
    "MyAwesomeCustomVcFramework", //name of the resulting framework
    BitcodeEmbeddingMode.BITCODE,  //this is the optional default value
    "at.asitplus:kmmresult:1.5.1", //with KMM result goodness
    "at.asitplus.wallet:vclib-openid:2.0.0", //and (in our opinion) the best KMM VC library ever built)
    datetime(), //and KMM datetime awesomeness (version managed by conventions plugin)
    napier() // and elegant KMM-powered logging (version managed by conventions plugin)
)

kotlin {
    //other kotlin multiplatform plugin config (dependencies, targets, etc
}
//whatever else needs to be configured
```

### Dokka Setup Shorthands
A shorthand to setup dokka is available as `setupDokka` which takes the following parameters:

 * `outputDir` defaults to `"$buildDir/dokka"`
 * `baseUrl` of the remote repository to configure source links
 * `multiModuleDoc` to indicate whether multi-module documentation needs to be configured (defaults to `false`)
 * `remoteLineSuffix` as per Dokka the manual; defaults to `#L`

### GitLab Repository Shorthands

In addition to GitHub, we use two GitLab instances for hosting various stuff internally.
This plugin also provides a shorthand for that, handling HTTP-header-based auth and integrating with GitLab's CI
runners.
These are called `gitlab()` and `extgit()` respectively and are available for both declaring maven repos for fetching
dependencies and for publishing.

#### GitLab Dependencies

Use the `gitlab(gitlabGroupId)` or `extgit(extgitGroupId)` shorthands inside a maven repository block as follows:

```kotlin
repositories {
    gitlab(groupId = 42) accessTokenFrom extra
    //alternatively you can use gitlab(groupId = 1337) withAccessToken "xPlat-WUMBO_FizzBuzz"
    mavenCentral()
}
```

The name of the repository defaults to _gitlab_ or _extgit_, respectively and can be overridden by optionally specifying
the `nameOverride` parameter.

#### Publishing to GitLab

Publishing to Gitlab works similarly and assumes this happens from a GitLab CI job. Hence, it requires not access token,
Publishing local builds in intentionally not supported.
Note that this time the project id is required, not the group id and that the extension is invoked directly in the
publishing block:

```kotlin
publishing {
    gitLab(projectId = 1337)
}
```

Again, the name of the repository defaults to _gitlab_ or _extgit_, respectively and can be overridden by optionally
specifying the `nameOverride` parameter.

## Showcase Projects

A sample project, which demonstrates automatic availability of property-based testing using kotest, is
provided [here](sample-project).
It includes this plugin through its maven repository.

In addition, thic plugin is also used in the following production projects:
 * [VC KMM Library](https://github.com/a-sit-plus/kmm-vc-library) adds this repo as submodule and includes the plugin as part of a compoite build to add a more specialised custom plugin on top
 * [Android Attestation Library](https://github.com/a-sit-plus/android-attestation) uses the prebuilt plugin from the maven repo
 * [Server-Side Mobile Client Attestation Library](https://github.com/a-sit-plus/attestation-service) uses the prebuilt plugin from the maven repo

## Additional Notes

This Plugin also works with Groovy but is not really tested against it.

Some improvements are already planned:

* Add a default detekt configuration that prevents using inline classes for API function exposed to iOS (e.g.
  use `KmmResult` instead of `Result` provided by the Kotlin stdlib)
* Decouple the versions of plugins used to build this plugin from the ones applied when using the plugin in projects.

**Outside Contributions are welcome!**
