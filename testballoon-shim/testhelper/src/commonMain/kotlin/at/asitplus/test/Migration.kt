package de.infix.testBalloon.framework

import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestDisplayName
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import de.infix.testBalloon.framework.core.testScope as tc

@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.TestConfig"))
typealias TestConfig = de.infix.testBalloon.framework.core.TestConfig

@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.testScope"))
public fun TestConfig.testScope(isEnabled: Boolean, timeout: Duration = 60.seconds): TestConfig = tc(isEnabled, timeout)

@de.infix.testBalloon.framework.shared.TestRegistering
@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.testSuite"))
public fun testSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    testConfig: de.infix.testBalloon.framework.core.TestConfig = de.infix.testBalloon.framework.core.TestConfig,
    content: TestSuite.() -> Unit
) = de.infix.testBalloon.framework.core.testSuite(name, displayName, testConfig, content)

@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.TestSession"))
typealias TestSession = de.infix.testBalloon.framework.core.TestSession

@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.TestSession.DefaultConfiguration"))
val TestSession.DefaultConfiguration get() = de.infix.testBalloon.framework.core.TestSession.DefaultConfiguration


@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.TestInvocation"))
typealias TestInvocation = de.infix.testBalloon.framework.core.TestInvocation

@Deprecated("old 0.6 package name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.core.invocation"))
public fun TestConfig.invocation(mode: TestInvocation): TestConfig =
    de.infix.testBalloon.framework.core.TestConfig.invocation(mode)

@Deprecated("old 0.6 name", replaceWith = ReplaceWith("de.infix.testBalloon.framework.shared.TestRegistering"))
typealias TestDiscoverable = TestRegistering