package at.asitplus.testballoon

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestExecutionScope
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.disable
import io.kotest.property.*

context(suite: TestSuite)
operator fun String.invoke(nested: suspend TestExecutionScope.() -> Unit) {
    if (this.startsWith("!"))
        suite.testWithLimits(this) {
            nested()
        }
    else suite.testWithLimits(this) { nested() }
}

context(suite: TestSuite)
infix operator fun String.minus(suiteBody: TestSuite.() -> Unit) {
    suite.testSuiteWithLimits(name = limited()) {
        suiteBody()
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withData(vararg parameters: Data, action: suspend (Data) -> Unit) {
    for (data in parameters) {
        testWithLimits("$data") {
            action(data)
        }
    }
}

fun <Data> TestSuite.withData(data: Iterable<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        testWithLimits("$d") { action(d) }
    }
}

fun <Data> TestSuite.withData(map: Map<String, Data>, action: suspend (Data) -> Unit) {
    for (d in map) {
        testWithLimits(d.key) { action(d.value) }
    }
}

fun <Data> TestSuite.withData(nameFn: (Data) -> String, data: Iterable<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        testWithLimits(nameFn(d)) { action(d) }
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withData(nameFn: (Data) -> String, vararg arguments: Data, action: suspend (Data) -> Unit) {
    for (d in arguments) {
        testWithLimits(nameFn(d)) { action(d) }
    }
}

fun <Data> TestSuite.withData(data: Sequence<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        testWithLimits("$d") { action(d) }
    }
}

fun <Data> TestSuite.withData(nameFn: (Data) -> String, data: Sequence<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        testWithLimits(nameFn(d)) { action(d) }
    }
}


@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withDataSuites(
    vararg parameters: Data,
    action: TestSuite.(Data) -> Unit
) {
    for (d in parameters) {
        testSuiteWithLimits(name = d.toString()) {
            action(d)
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    data: Iterable<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuiteWithLimits(name = d.toString()) {
            action(d)
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    map: Map<String, Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in map) {
        testSuiteWithLimits(d.key) {
            action(d.value)
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    data: Sequence<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuiteWithLimits(name = d.toString()) {
            action(d)
        }
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withDataSuites(
    nameFn: (Data) -> String,
    vararg arguments: Data,
    action: TestSuite.(Data) -> Unit
) = withDataSuites(nameFn, arguments.asIterable(), action)

fun <Data> TestSuite.withDataSuites(
    nameFn: (Data) -> String,
    data: Iterable<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuiteWithLimits(nameFn(d)) {
            action(d)
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    nameFn: (Data) -> String,
    data: Sequence<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuiteWithLimits(nameFn(d)) {
            action(d)
        }
    }
}

fun <Value> TestSuite.checkAllTests(
    iterations: Int,
    genA: Gen<Value>,
    content: suspend context(PropertyContext) TestExecutionScope.(Value) -> Unit
) {
    var count = 0
    checkAllSeries(iterations, genA) { value, context ->
        count++
        testWithLimits("$count of $iterations ${if (value == null) "null" else value::class.simpleName}: $value") {
            with(context) {
                content(value)
            }
        }
    }
}

fun <Value> TestSuite.checkAllSuites(
    iterations: Int,
    genA: Gen<Value>,
    content: context(PropertyContext) TestSuite.(Value) -> Unit
) {

    var count = 0
    checkAllSeries(iterations, genA) { value, context ->
        count++
        val prefix = if (value == null) "null" else value::class.simpleName
        testSuiteWithLimits(name = "$count-${iterations}_${prefix}_${value.toString()}") {
            with(context) {
                content(value)
            }
        }
    }
}

fun <A> TestSuite.checkAllSuites(
    genA: Gen<A>,
    content: context(PropertyContext) TestSuite.(A) -> Unit
) = checkAllSuites(PropertyTesting.defaultIterationCount, genA, content)

private inline fun <Value> checkAllSeries(iterations: Int, genA: Gen<Value>, series: (Value, PropertyContext) -> Unit) {
    val constraints = Constraints.iterations(iterations)

    @Suppress("OPT_IN_USAGE")
    val config = PropTestConfig(constraints = constraints)
    val context = PropertyContext(config)
    genA.generate(RandomSource.default(), config.edgeConfig)
        .takeWhile { constraints.evaluate(context) }
        .forEach { sample ->
            context.markEvaluation()
            series(sample.value, context)
            context.markSuccess()
        }
}

@TestDiscoverable
private fun TestSuite.testSuiteWithLimits(
    name: String,
    testConfig: TestConfig = TestConfig,
    content: TestSuite.() -> Unit
) =
    testSuite(name.limited(), testConfig = testConfig, content = content)

@TestDiscoverable
private fun TestSuite.testWithLimits(
    name: String,
    testConfig: TestConfig = TestConfig,
    action: suspend TestExecutionScope.() -> Unit
) =
    test(name.limited(), testConfig = testConfig.disableByName(name), action = action)

private fun String.limited() = this.take(30)

private fun TestConfig.disableByName(name: String) =
    if (name.startsWith("!")) TestConfig.disable() else this
