package at.asitplus.test

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension

/**
 * Multiplatform Kotest project configuration that records
 *  • every finished TestCase
 *  • every single invocation of those tests
 */
abstract class XmlReportingProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf<Extension>(JUnitXmlReporter)
}