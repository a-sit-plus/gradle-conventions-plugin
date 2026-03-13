package at.asitplus.gradle

import groovy.json.JsonSlurper
import org.cyclonedx.Version
import org.cyclonedx.generators.BomGeneratorFactory
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.gradle.CyclonedxPlugin
import org.cyclonedx.model.*
import org.cyclonedx.parsers.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.net.URL
import java.net.URLConnection
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

val Project.enableSbom: Boolean
    get() = envExtra["enableSbom"]?.toBooleanStrictOrNull() ?: false

val Project.licenseId get() = envExtra["licenseId"]?.trim()
val Project.licenseName get() = envExtra["licenseName"]?.trim()
val Project.licenseUrl get() = envExtra["licenseUrl"]?.trim()

private data class SupplierInfo(
    val name: String,
    val urls: List<String>,
    val contactName: String?,
    val email: String?,
)

private data class SupplierMapping(
    val prefixes: List<String>,
    val supplier: SupplierInfo,
)

private fun List<SupplierMapping>.findSupplierForGroup(group: String?): SupplierInfo? {
    if (group.isNullOrBlank()) return null

    return asSequence()
        .flatMap { mapping -> mapping.prefixes.asSequence().map { prefix -> prefix to mapping.supplier } }
        .filter { (prefix, _) -> group == prefix || group.startsWith("$prefix.") }
        .maxByOrNull { (prefix, _) -> prefix.length }
        ?.second
}

private fun openSupplierMappingConnection(urlString: String): URLConnection {
    val url = URL(urlString)
    require(url.protocol.lowercase(Locale.ROOT) != "http") {
        "Plain http is not allowed for supplier mapping URL: $urlString"
    }
    return url.openConnection().apply {
        connectTimeout = 5_000
        readTimeout = 10_000
    }
}

/*
Expected JSON shape:

[
  {
    "prefixes": ["org.jetbrains.kotlin", "org.jetbrains"],
    "supplier": {
      "name": "JetBrains",
      "urls": ["https://www.jetbrains.com"],
      "contactName": "JetBrains",
      "email": "support@jetbrains.com"
    }
  }
]
*/
private fun loadSupplierMappings(urlString: String): List<SupplierMapping> {
    val connection = openSupplierMappingConnection(urlString)
    val parsed = connection.getInputStream().bufferedReader().use { reader ->
        JsonSlurper().parse(reader)
    }

    require(parsed is List<*>) {
        "Supplier mapping JSON must be a top-level array: $urlString"
    }

    return parsed.mapIndexed { index, rawEntry ->
        require(rawEntry is Map<*, *>) {
            "Supplier mapping entry #$index must be an object"
        }

        val prefixes = (rawEntry["prefixes"] as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        require(prefixes.isNotEmpty()) {
            "Supplier mapping entry #$index must contain a non-empty 'prefixes' list"
        }

        val rawSupplier = rawEntry["supplier"] as? Map<*, *>
            ?: error("Supplier mapping entry #$index must contain a 'supplier' object")

        val name = rawSupplier["name"]?.toString()?.trim().orEmpty()
        require(name.isNotBlank()) {
            "Supplier mapping entry #$index has supplier with missing/blank 'name'"
        }

        val urls = when (val rawUrls = rawSupplier["urls"]) {
            is List<*> -> rawUrls.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
            null -> emptyList()
            else -> error("Supplier mapping entry #$index has supplier 'urls' that is not a list")
        }

        val contactName = rawSupplier["contactName"]?.toString()?.trim()?.ifBlank { null }
        val email = rawSupplier["email"]?.toString()?.trim()?.ifBlank { null }

        SupplierMapping(
            prefixes = prefixes,
            supplier = SupplierInfo(
                name = name,
                urls = urls,
                contactName = contactName,
                email = email,
            ),
        )
    }
}

private fun Project.supplierMappingsUrlFromEnvExtra(): String? =
    envExtra["supplierMappingsUrl"]?.trim()?.ifBlank { null }

private fun Bom.patchLicenseMetadata(
    licenseId: String,
    licenseName: String?,
    licenseUrl: String?,
) {
    val license = License().apply {
        id = licenseId
        name = licenseName
        url = licenseUrl
    }
    val choice = LicenseChoice().apply {
        addLicense(license)
    }

    if (metadata == null) {
        metadata = Metadata()
    }

    metadata!!.component?.licenses = choice
}

private fun Bom.patchFirstPartyComponentLicenses(
    licenseId: String?,
    licenseName: String?,
    licenseUrl: String?,
) {
    val rootGroup = metadata?.component?.group ?: return
    components?.forEach { component ->
        if (component.group == rootGroup) {
            component.licenses = LicenseChoice().apply {
                addLicense(
                    License().apply {
                        id = licenseId
                        name = licenseName
                        url = licenseUrl
                    }
                )
            }
        }
    }
}

private fun Project.supplierInfoFromEnvExtra(): SupplierInfo? {
    val name = envExtra["supplierName"]?.trim().orEmpty()
    if (name.isBlank()) return null

    val urls = envExtra["supplierUrls"]
        ?.trim()
        .orEmpty()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    val contactName = envExtra["supplierContactName"]?.trim()?.ifBlank { null }
    val email = envExtra["supplierEmail"]?.trim()?.ifBlank { null }

    return SupplierInfo(
        name = name,
        urls = urls,
        contactName = contactName,
        email = email,
    )
}

private fun SupplierInfo.toOrganizationalEntity(): OrganizationalEntity =
    OrganizationalEntity().apply {
        name = this@toOrganizationalEntity.name
        urls = this@toOrganizationalEntity.urls
        if (this@toOrganizationalEntity.contactName != null || this@toOrganizationalEntity.email != null) {
            addContact(
                OrganizationalContact().apply {
                    name = this@toOrganizationalEntity.contactName
                    email = this@toOrganizationalEntity.email
                }
            )
        }
    }

internal fun Project.setupSbomSupport() {
    if (!enableSbom) {
        Logger.lifecycle("  > SBOM generation disabled for project $path")
        return
    }

    val supplierInfo = supplierInfoFromEnvExtra()

    pluginManager.apply(CyclonedxPlugin::class.java)

    tasks.matching { it.name == "cyclonedxDirectBom" }.configureEach {
        enabled = false
        description = "Disabled in favor of publication-specific CycloneDX SBOM tasks."
    }
    tasks.matching { it.name == "cyclonedxBom" }.configureEach {
        enabled = false
        description = "Disabled in favor of publication-specific CycloneDX SBOM tasks."
    }

    pluginManager.withPlugin("maven-publish") {
        afterEvaluate {
            val publishing = extensions.findByType<PublishingExtension>() ?: return@afterEvaluate
            val publicationSbomTasks = mutableListOf<String>()

            publishing.publications.withType<MavenPublication>().configureEach {
                val publication = this
                val publicationConfigNames = cyclonedxConfigsForPublication(publication.name)
                if (
                    publication.name == "relocation" ||
                    publication.artifactId.endsWith("-versionCatalog") ||
                    publicationConfigNames.isEmpty()
                ) {
                    return@configureEach
                }

                val cyclonedxTaskName = cyclonedxTaskNameForPublication(publication.name)
                val cyclonedxTask = tasks.register<CyclonedxDirectTask>(cyclonedxTaskName) {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Generates CycloneDX SBOM for Maven publication '${publication.name}'."
                    includeConfigs.set(publicationConfigNames)
                    projectType.set(Component.Type.LIBRARY)
                    schemaVersion.set(Version.VERSION_16)
                    includeMetadataResolution.set(true)
                    includeBuildSystem.set(true)
                    includeBomSerialNumber.set(true)
                    componentGroup.set(project.group.toString())
                    componentName.set(publication.artifactId)
                    componentVersion.set(project.version.toString())
                    jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx-publications/${publication.name}/bom.raw.json"))
                    xmlOutput.set(layout.buildDirectory.file("reports/cyclonedx-publications/${publication.name}/bom.raw.xml"))

                    supplierInfo?.let {
                        organizationalEntity.set(it.toOrganizationalEntity())
                    }
                }

                val normalizeTask = tasks.register<NormalizeCyclonedxBomTask>("${cyclonedxTaskName}Normalized") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Normalizes CycloneDX package types for Maven publication '${publication.name}'."
                    dependsOn(cyclonedxTask)
                    dependsOn(tasks.matching {
                        it.name == "generatePomFileFor${
                            publication.name.replaceFirstChar { ch ->
                                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                            }
                        }Publication"
                    })
                    publicationName.set(publication.name)
                    includeConfigs.set(publicationConfigNames)
                    inputJson.set(cyclonedxTask.flatMap { it.jsonOutput })
                    outputJson.set(layout.buildDirectory.file("reports/cyclonedx-publications/${publication.name}/bom.json"))
                    outputXml.set(layout.buildDirectory.file("reports/cyclonedx-publications/${publication.name}/bom.xml"))

                    supplierName.set(supplierInfo?.name.orEmpty())
                    supplierUrls.set(supplierInfo?.urls ?: emptyList())
                    supplierContactName.set(supplierInfo?.contactName.orEmpty())
                    supplierEmail.set(supplierInfo?.email.orEmpty())
                    supplierMappingsUrl.set(project.supplierMappingsUrlFromEnvExtra().orEmpty())
                }

                if (publication.name == "kotlinMultiplatform") {
                    registerRootKmpSbomVariants(normalizeTask)
                }

                val verifyTask = tasks.register<VerifyCyclonedxBomConsistencyTask>("${cyclonedxTaskName}Consistency") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description =
                        "Verifies CycloneDX SBOM graph consistency for Maven publication '${publication.name}'."
                    dependsOn(normalizeTask)
                    inputJson.set(normalizeTask.flatMap { it.outputJson })
                }

                val compareTask =
                    tasks.register<VerifyCyclonedxBomDirectDependenciesTask>("${cyclonedxTaskName}DirectDependencies") {
                        group = LifecycleBasePlugin.VERIFICATION_GROUP
                        description =
                            "Verifies CycloneDX SBOM direct dependencies for Maven publication '${publication.name}' against the publication POM."
                        dependsOn(normalizeTask)
                        publicationName.set(publication.name)
                        inputJson.set(normalizeTask.flatMap { it.outputJson })
                    }

                publicationSbomTasks += normalizeTask.name
                publicationSbomTasks += verifyTask.name
                publicationSbomTasks += compareTask.name

                if (publication.name != "kotlinMultiplatform") {
                    publication.artifact(normalizeTask.flatMap { it.outputJson }) {
                        classifier = "cyclonedx"
                        extension = "json"
                        builtBy(normalizeTask)
                    }
                    publication.artifact(normalizeTask.flatMap { it.outputXml }) {
                        classifier = "cyclonedx"
                        extension = "xml"
                        builtBy(normalizeTask)
                    }
                }
            }

            if (publicationSbomTasks.isEmpty()) {
                return@afterEvaluate
            }

            val aggregateTask = tasks.register("cyclonedxPublishedBom") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Generates CycloneDX SBOMs for all published Maven publications in $path."
                dependsOn(publicationSbomTasks)
            }

            tasks.withType<AbstractPublishToMaven>().configureEach {
                dependsOn(aggregateTask)
            }
        }
    }
}

private fun Project.registerRootKmpSbomVariants(
    normalizeTask: org.gradle.api.tasks.TaskProvider<NormalizeCyclonedxBomTask>,
) {
    val kotlin = extensions.findByType<KotlinMultiplatformExtension>()
        ?: error("kotlinMultiplatform publication exists in $path, but KotlinMultiplatformExtension was not found")

    val jsonElements = configurations.maybeCreate("kotlinMultiplatformSbomJsonElements").apply {
        isCanBeConsumed = true
        isCanBeResolved = false
        isVisible = false
        description = "Documentation-only CycloneDX JSON SBOM variant for the kotlinMultiplatform publication"

        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("sbom-cyclonedx-json"))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        }

        outgoing.artifacts.clear()
        outgoing.artifact(normalizeTask.flatMap { it.outputJson }) {
            classifier = "cyclonedx"
            extension = "json"
            builtBy(normalizeTask)
        }
    }

    val xmlElements = configurations.maybeCreate("kotlinMultiplatformSbomXmlElements").apply {
        isCanBeConsumed = true
        isCanBeResolved = false
        isVisible = false
        description = "Documentation-only CycloneDX XML SBOM variant for the kotlinMultiplatform publication"

        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("sbom-cyclonedx-xml"))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        }

        outgoing.artifacts.clear()
        outgoing.artifact(normalizeTask.flatMap { it.outputXml }) {
            classifier = "cyclonedx"
            extension = "xml"
            builtBy(normalizeTask)
        }
    }

    kotlin.publishing.adhocSoftwareComponent.addVariantsFromConfiguration(jsonElements) {}
    kotlin.publishing.adhocSoftwareComponent.addVariantsFromConfiguration(xmlElements) {}
}

private fun Project.cyclonedxConfigsForPublication(publicationName: String): List<String> {
    if (publicationName == "kotlinMultiplatform") {
        return listOf("allSourceSetsCompileDependenciesMetadata")
    }

    val orderedCandidates = listOf(
        "${publicationName}RuntimeClasspath",
        "${publicationName}CompileClasspath",
        "${publicationName}CompileKlibraries",
        "${publicationName}CompilationDependenciesMetadata",
        "${publicationName}MainResolvableDependenciesMetadata",
        "${publicationName}MainImplementationDependenciesMetadata",
    )

    return orderedCandidates.mapNotNull { configurations.findByName(it)?.name }.distinct()
}

private fun cyclonedxTaskNameForPublication(publicationName: String): String =
    "cyclonedx${publicationName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }}PublicationBom"

private data class SbomComponentCoordinates(
    val group: String,
    val name: String,
    val version: String,
)

private data class PublishedCoordinates(
    val coordinates: SbomComponentCoordinates,
    val packaging: String,
    val directDependencies: List<SbomComponentCoordinates>,
)

private data class SbomNormalizationPlan(
    val exactArtifactTypes: Map<SbomComponentCoordinates, String>,
    val coordinateAliases: Map<SbomComponentCoordinates, SbomComponentCoordinates>,
)

private data class SemanticComponentRef(
    val group: String,
    val name: String,
    val version: String,
    val type: String,
) {
    override fun toString(): String = "$group:$name:$version ($type)"
}

abstract class NormalizeCyclonedxBomTask : DefaultTask() {
    @get:Input
    abstract val publicationName: Property<String>

    @get:Input
    abstract val includeConfigs: ListProperty<String>

    @get:Input
    abstract val supplierName: Property<String>

    @get:Input
    abstract val supplierUrls: ListProperty<String>

    @get:Input
    abstract val supplierContactName: Property<String>

    @get:Input
    abstract val supplierEmail: Property<String>

    @get:Input
    abstract val supplierMappingsUrl: Property<String>

    @get:InputFile
    abstract val inputJson: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val outputJson: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val outputXml: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun normalize() {
        val normalizationPlan = project.buildNormalizationPlan(publicationName.get(), includeConfigs.get())
        val publicationCoordinates = project.projectPublicationCoordinates(publicationName.get())
            ?: error("Missing publication metadata for ${project.path}:${publicationName.get()}")

        val supplierInfo = supplierName.orNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                SupplierInfo(
                    name = it,
                    urls = supplierUrls.get().filter { url -> url.isNotBlank() },
                    contactName = supplierContactName.orNull?.trim()?.ifBlank { null },
                    email = supplierEmail.orNull?.trim()?.ifBlank { null },
                )
            }

        val thirdPartySupplierMappings = supplierMappingsUrl.orNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::loadSupplierMappings)
            .orEmpty()

        val refRewrites = LinkedHashMap<String, String>()
        val jsonBom = JsonParser().parse(inputJson.get().asFile)
            .normalizeBom(
                normalizationPlan = normalizationPlan,
                publicationCoordinates = publicationCoordinates,
                refRewrites = refRewrites,
                supplierInfo = supplierInfo,
                thirdPartySupplierMappings = thirdPartySupplierMappings,
            )

        outputJson.get().asFile.apply {
            parentFile.mkdirs()
            writeText(BomGeneratorFactory.createJson(Version.VERSION_16, jsonBom).toJsonString())
        }
        outputXml.get().asFile.apply {
            parentFile.mkdirs()
            writeText(BomGeneratorFactory.createXml(Version.VERSION_16, jsonBom).toXmlString())
        }
    }

    private fun Bom.normalizeBom(
        normalizationPlan: SbomNormalizationPlan,
        publicationCoordinates: PublishedCoordinates,
        refRewrites: MutableMap<String, String>,
        supplierInfo: SupplierInfo?,
        thirdPartySupplierMappings: List<SupplierMapping>,
    ): Bom {
        metadata?.component?.rewriteComponent(normalizationPlan, refRewrites)
        components?.forEach { component -> component.rewriteComponent(normalizationPlan, refRewrites) }
        dependencies = dependencies?.map { it.rewrittenDependency(refRewrites) }?.toMutableList()
        alignRootDependenciesToPublicationPom(publicationCoordinates)
        patchSupplierMetadata(supplierInfo, thirdPartySupplierMappings)

        project.licenseId?.let {
            patchLicenseMetadata(it, project.licenseName, project.licenseUrl)
            patchFirstPartyComponentLicenses(it, project.licenseName, project.licenseUrl)
        }
        return this
    }

    private fun Bom.patchSupplierMetadata(
        supplierInfo: SupplierInfo?,
        thirdPartySupplierMappings: List<SupplierMapping>,
    ) {
        if (supplierInfo == null && thirdPartySupplierMappings.isEmpty()) return

        if (metadata == null) {
            metadata = Metadata()
        }

        val ownGroup = metadata!!.component?.group
        val firstPartySupplier = supplierInfo?.toOrganizationalEntity()

        if (firstPartySupplier != null) {
            metadata!!.supplier = firstPartySupplier
            metadata!!.component?.supplier = firstPartySupplier
        }

        components?.forEach { component ->
            val group = component.group ?: return@forEach

            when {
                ownGroup != null && group == ownGroup -> {
                    if (firstPartySupplier != null) {
                        component.supplier = firstPartySupplier
                    }
                }

                else -> {
                    val thirdPartySupplier = thirdPartySupplierMappings
                        .findSupplierForGroup(group)
                        ?.toOrganizationalEntity()

                    if (thirdPartySupplier != null) {
                        component.supplier = thirdPartySupplier
                    }
                }
            }
        }
    }

    private fun Bom.alignRootDependenciesToPublicationPom(publicationCoordinates: PublishedCoordinates) {
        val rootRef = metadata?.component?.bomRef ?: return
        val rootDependency = dependencies?.firstOrNull { it.ref == rootRef } ?: return
        val byCoordinates = linkedMapOf<SbomComponentCoordinates, String>()
        metadata?.component?.let { component ->
            val group = component.group
            val name = component.name
            val version = component.version
            val bomRef = component.bomRef
            if (group != null && name != null && version != null && bomRef != null) {
                byCoordinates[SbomComponentCoordinates(group, name, version)] = bomRef
            }
        }
        components?.forEach { component ->
            val group = component.group
            val name = component.name
            val version = component.version
            val bomRef = component.bomRef
            if (group != null && name != null && version != null && bomRef != null) {
                byCoordinates[SbomComponentCoordinates(group, name, version)] = bomRef
            }
        }
        rootDependency.dependencies = publicationCoordinates.directDependencies.mapNotNull { coordinates ->
            byCoordinates[coordinates]?.let(::Dependency)
        }
    }

    private fun Component.rewriteComponent(
        normalizationPlan: SbomNormalizationPlan,
        refRewrites: MutableMap<String, String>,
    ) {
        val group = group ?: return
        val name = name ?: return
        val version = version ?: return
        val originalCoordinates = SbomComponentCoordinates(group, name, version)
        val targetCoordinates = normalizationPlan.coordinateAliases[originalCoordinates] ?: originalCoordinates
        val artifactType = normalizationPlan.exactArtifactTypes[targetCoordinates]
            ?: normalizationPlan.exactArtifactTypes[originalCoordinates]
            ?: return
        val oldBomRef = bomRef
        val oldPurl = purl
        this.group = targetCoordinates.group
        this.name = targetCoordinates.name
        this.version = targetCoordinates.version
        val newBomRef = oldBomRef?.let { rewritePurl(it, targetCoordinates, artifactType) }
        val newPurl = oldPurl?.let { rewritePurl(it, targetCoordinates, artifactType) }
        if (oldBomRef != null && newBomRef != null && oldBomRef != newBomRef) {
            bomRef = newBomRef
            refRewrites[oldBomRef] = newBomRef
        }
        if (oldPurl != null && newPurl != null && oldPurl != newPurl) {
            purl = newPurl
        }
    }

    private fun Dependency.rewrittenDependency(refRewrites: Map<String, String>): Dependency =
        Dependency(refRewrites[ref] ?: ref).also { rewritten ->
            rewritten.dependencies = dependencies?.map { it.rewrittenDependency(refRewrites) }
        }

    private fun rewritePurl(
        purl: String,
        coordinates: SbomComponentCoordinates,
        artifactType: String,
    ): String {
        val rewrittenCoordinates = when {
            !purl.startsWith("pkg:maven/") -> purl
            else -> {
                val queryIndex = purl.indexOf('?').let { if (it < 0) purl.length else it }
                buildString {
                    append("pkg:maven/")
                    append(coordinates.group)
                    append('/')
                    append(coordinates.name)
                    append('@')
                    append(coordinates.version)
                    append(purl.substring(queryIndex))
                }
            }
        }
        return withPurlType(rewrittenCoordinates, artifactType)
    }

    private fun withPurlType(purl: String, artifactType: String): String {
        val queryIndex = purl.indexOf('?')
        if (queryIndex < 0) {
            return "$purl?type=$artifactType"
        }
        val base = purl.substring(0, queryIndex)
        val qualifiers = purl.substring(queryIndex + 1)
            .split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { qualifier ->
                val separatorIndex = qualifier.indexOf('=')
                if (separatorIndex < 0) null else qualifier.substring(0, separatorIndex) to qualifier.substring(
                    separatorIndex + 1
                )
            }
            .toMutableList()
        val existingTypeIndex = qualifiers.indexOfFirst { it.first == "type" }
        if (existingTypeIndex >= 0) qualifiers[existingTypeIndex] = "type" to artifactType
        else qualifiers += "type" to artifactType
        return buildString {
            append(base)
            append('?')
            append(qualifiers.joinToString("&") { "${it.first}=${it.second}" })
        }
    }
}

abstract class VerifyCyclonedxBomConsistencyTask : DefaultTask() {
    @get:InputFile
    abstract val inputJson: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun verify() {
        val bom = JsonParser().parse(inputJson.get().asFile)
        val knownRefs = linkedSetOf<String>()
        bom.metadata?.component?.bomRef?.let(knownRefs::add)
        bom.components?.mapNotNullTo(knownRefs) { it.bomRef }

        val danglingRefs = mutableListOf<String>()
        bom.dependencies?.forEach { dependency ->
            if (dependency.ref !in knownRefs) {
                danglingRefs += "Missing component for dependency ref: ${dependency.ref}"
            }
            dependency.dependencies?.forEach { dependsOn ->
                if (dependsOn.ref !in knownRefs) {
                    danglingRefs += "Missing component for dependsOn ref: ${dependsOn.ref} (from ${dependency.ref})"
                }
            }
        }

        check(danglingRefs.isEmpty()) {
            buildString {
                appendLine("CycloneDX BOM graph is inconsistent for ${inputJson.get().asFile}:")
                danglingRefs.sorted().forEach(::appendLine)
            }
        }
    }
}

abstract class VerifyCyclonedxBomDirectDependenciesTask : DefaultTask() {
    @get:Input
    abstract val publicationName: Property<String>

    @get:InputFile
    abstract val inputJson: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun verify() {
        val publicationCoordinates = project.projectPublicationCoordinates(publicationName.get())
            ?: error("Missing publication metadata for ${project.path}:${publicationName.get()}")
        val rootSemantic = SemanticComponentRef(
            publicationCoordinates.coordinates.group,
            publicationCoordinates.coordinates.name,
            publicationCoordinates.coordinates.version,
            publicationCoordinates.packaging,
        )
        val expectedDirectDependencies = publicationCoordinates.directDependencies.mapTo(linkedSetOf()) { dependency ->
            val type = project.packagingForCoordinates(dependency)
                ?: error("Missing cached POM packaging for ${dependency.group}:${dependency.name}:${dependency.version}")
            SemanticComponentRef(dependency.group, dependency.name, dependency.version, type)
        }

        val bom = JsonParser().parse(inputJson.get().asFile)
        val bomRefToComponent = linkedMapOf<String, SemanticComponentRef>()
        fun register(ref: String?, component: Component?) {
            if (ref == null || component == null) return
            val group = component.group ?: return
            val name = component.name ?: return
            val version = component.version ?: return
            val type = component.bomRef?.let(::typeFromPurl) ?: component.purl?.let(::typeFromPurl) ?: "unknown"
            bomRefToComponent[ref] = SemanticComponentRef(group, name, version, type)
        }
        register(bom.metadata?.component?.bomRef, bom.metadata?.component)
        bom.components?.forEach { register(it.bomRef, it) }

        val rootDependencyEntry = bom.dependencies?.firstOrNull { it.ref == bom.metadata?.component?.bomRef }
            ?: error("Missing dependency entry for root BOM component in ${inputJson.get().asFile}")
        val actualDirectDependencies = rootDependencyEntry.dependencies.orEmpty().mapTo(linkedSetOf()) { dependsOn ->
            bomRefToComponent[dependsOn.ref]
                ?: error("Dependency ref ${dependsOn.ref} missing component mapping in ${inputJson.get().asFile}")
        }

        val missingDirectDependencies = expectedDirectDependencies - actualDirectDependencies
        val unexpectedDirectDependencies = actualDirectDependencies - expectedDirectDependencies

        check(missingDirectDependencies.isEmpty() && unexpectedDirectDependencies.isEmpty()) {
            buildString {
                appendLine("CycloneDX BOM direct dependencies do not match the publication POM for ${project.path}:${publicationName.get()}")
                appendLine("Root component: $rootSemantic")
                if (missingDirectDependencies.isNotEmpty()) {
                    appendLine("Missing direct dependencies:")
                    missingDirectDependencies.sortedBy { "${it.group}:${it.name}:${it.version}:${it.type}" }
                        .forEach { appendLine("  - $it") }
                }
                if (unexpectedDirectDependencies.isNotEmpty()) {
                    appendLine("Unexpected direct dependencies:")
                    unexpectedDirectDependencies.sortedBy { "${it.group}:${it.name}:${it.version}:${it.type}" }
                        .forEach { appendLine("  - $it") }
                }
            }
        }
    }
}

private fun Project.buildNormalizationPlan(
    publicationName: String,
    includeConfigs: List<String>,
): SbomNormalizationPlan {
    val artifactTypes = linkedMapOf<SbomComponentCoordinates, MutableSet<String>>()
    val pomPackagings = linkedMapOf<SbomComponentCoordinates, MutableSet<String>>()
    val coordinateAliases = linkedMapOf<SbomComponentCoordinates, SbomComponentCoordinates>()

    projectPublicationCoordinates(publicationName)?.let { publicationCoordinates ->
        pomPackagings.getOrPut(publicationCoordinates.coordinates) { linkedSetOf() }
            .add(publicationCoordinates.packaging)
        publicationCoordinates.directDependencies.forEach { dependencyCoordinates ->
            packagingFromCachedPom(
                dependencyCoordinates.group,
                dependencyCoordinates.name,
                dependencyCoordinates.version
            )?.let { packaging ->
                pomPackagings.getOrPut(dependencyCoordinates) { linkedSetOf() }.add(packaging)
            }
        }
    }

    includeConfigs.forEach { configurationName ->
        val configuration = configurations.getByName(configurationName)
        val resolvedArtifacts = configuration.incoming.artifactView { isLenient = true }
            .artifacts.artifacts.filterIsInstance<ResolvedArtifactResult>()

        resolvedArtifacts.forEach { artifact ->
            val extension = artifact.file.extension.ifBlank { return@forEach }
            val coordinates = when (val id = artifact.id.componentIdentifier) {
                is ModuleComponentIdentifier -> {
                    val c = SbomComponentCoordinates(id.group, id.module, id.version)
                    packagingFromCachedPom(id.group, id.module, id.version)?.let { packaging ->
                        pomPackagings.getOrPut(c) { linkedSetOf() }.add(packaging)
                    }
                    c
                }

                is ProjectComponentIdentifier -> {
                    val dependencyProject = rootProject.findProject(id.projectPath) ?: return@forEach
                    val fallbackCoordinates = SbomComponentCoordinates(
                        group = dependencyProject.group.toString(),
                        name = dependencyProject.name,
                        version = dependencyProject.version.toString(),
                    )
                    dependencyProject.projectPublicationCoordinates(publicationName)?.let { publicationCoordinates ->
                        coordinateAliases[fallbackCoordinates] = publicationCoordinates.coordinates
                        pomPackagings.getOrPut(publicationCoordinates.coordinates) { linkedSetOf() }
                            .add(publicationCoordinates.packaging)
                        publicationCoordinates.coordinates
                    } ?: fallbackCoordinates
                }

                else -> return@forEach
            }
            artifactTypes.getOrPut(coordinates) { linkedSetOf() }.add(extension)
        }
    }

    val exactExtensions = artifactTypes.filterValues { it.size == 1 }.mapValues { it.value.single() }.toMutableMap()
    pomPackagings.filterValues { it.size == 1 }.forEach { (coordinates, packagings) ->
        exactExtensions[coordinates] = packagings.single()
    }

    return SbomNormalizationPlan(exactArtifactTypes = exactExtensions, coordinateAliases = coordinateAliases)
}

private fun packagingFromCachedPom(group: String, module: String, version: String): String? {
    val cacheRoot = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
    val moduleDir = cacheRoot.resolve(group).resolve(module).resolve(version)
    val pomFile =
        moduleDir.takeIf(File::exists)?.walkTopDown()?.firstOrNull { it.isFile && it.extension == "pom" } ?: return null
    val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }
    val document = documentBuilderFactory.newDocumentBuilder().parse(pomFile)
    val packagingElements = document.getElementsByTagName("packaging")
    return if (packagingElements.length == 0) "jar" else packagingElements.item(0).textContent.trim().ifBlank { "jar" }
}

private fun Project.packagingForCoordinates(coordinates: SbomComponentCoordinates): String? =
    packagingFromCachedPom(coordinates.group, coordinates.name, coordinates.version)
        ?: rootProject.allprojects.asSequence()
            .mapNotNull { candidateProject ->
                candidateProject.layout.buildDirectory.dir("publications").get().asFile.takeIf(File::exists)
                    ?.listFiles()
                    ?.asSequence()
                    ?.mapNotNull { publicationDir ->
                        publicationDir.resolve("pom-default.xml").takeIf(File::exists)?.let(::readPomCoordinates)
                    }
                    ?.firstOrNull { it.coordinates == coordinates }
            }
            .firstOrNull()
            ?.packaging

private fun Project.projectPublicationCoordinates(publicationName: String): PublishedCoordinates? {
    val pomFile =
        layout.buildDirectory.file("publications/$publicationName/pom-default.xml").get().asFile.takeIf(File::exists)
            ?: return null
    return readPomCoordinates(pomFile)
}

private fun readPomCoordinates(pomFile: File): PublishedCoordinates {
    val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }
    val document = documentBuilderFactory.newDocumentBuilder().parse(pomFile)
    fun first(tagName: String): String = document.getElementsByTagName(tagName).item(0).textContent.trim()
    fun directDependencies(): List<SbomComponentCoordinates> {
        val dependencyNodes = document.getElementsByTagName("dependency")
        return buildList {
            for (index in 0 until dependencyNodes.length) {
                val dependencyNode = dependencyNodes.item(index)
                val children = dependencyNode.childNodes
                var group: String? = null
                var name: String? = null
                var version: String? = null
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex)
                    when (child.nodeName) {
                        "groupId" -> group = child.textContent.trim()
                        "artifactId" -> name = child.textContent.trim()
                        "version" -> version = child.textContent.trim()
                    }
                }
                if (group != null && name != null && version != null) {
                    add(SbomComponentCoordinates(group, name, version))
                }
            }
        }
    }

    val packagingNodes = document.getElementsByTagName("packaging")
    val packaging =
        if (packagingNodes.length == 0) "jar" else packagingNodes.item(0).textContent.trim().ifBlank { "jar" }
    return PublishedCoordinates(
        coordinates = SbomComponentCoordinates(first("groupId"), first("artifactId"), first("version")),
        packaging = packaging,
        directDependencies = directDependencies(),
    )
}

private fun typeFromPurl(purl: String): String? =
    purl.substringAfter('?', "").split('&').firstOrNull { it.startsWith("type=") }?.substringAfter('=')