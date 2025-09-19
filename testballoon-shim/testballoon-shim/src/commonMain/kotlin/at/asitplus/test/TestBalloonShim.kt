package at.asitplus.testballoon

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.TestCoroutineScope
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.disable
import io.kotest.property.*

context(suite: TestSuite)
operator fun String.invoke(nested: suspend TestCoroutineScope.() -> Unit) {
    if (this.startsWith("!"))
        suite.test(this, TestConfig.disable()) { nested() }
    else suite.test(this) { nested() }
}

context(suite: TestSuite)
infix operator fun String.minus(suiteBody: TestSuite.() -> Unit) {
    suite.testSuite(this) {
        if (this@minus.startsWith("!")) testConfig = TestConfig.disable()
        suiteBody()
        if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withData(vararg parameters: Data, action: suspend (Data) -> Unit) {
    for (data in parameters) {
        test("$data") {
            action(data)
        }
    }
}

fun <Data> TestSuite.withData(data: Iterable<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        test("$d") { action(d) }
    }
}

fun <Data> TestSuite.withData(map: Map<String, Data>, action: suspend (Data) -> Unit) {
    for (d in map) {
        test(d.key) { action(d.value) }
    }
}

fun <Data> TestSuite.withData(nameFn: (Data) -> String, data: Iterable<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        test(nameFn(d)) { action(d) }
    }
}
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withData(nameFn: (Data) -> String, vararg arguments: Data, action: suspend (Data) -> Unit) {
    for (d in arguments) {
        test(nameFn(d)) { action(d) }
    }
}

fun <Data> TestSuite.withData(data: Sequence<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        test("$d") { action(d) }
    }
}

fun <Data> TestSuite.withData(nameFn: (Data) -> String, data: Sequence<Data>, action: suspend (Data) -> Unit) {
    for (d in data) {
        test(nameFn(d)) { action(d) }
    }
}


@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
fun <Data> TestSuite.withDataSuites(
    vararg parameters: Data,
    action: TestSuite.(Data) -> Unit
) {
    for (d in parameters) {
        testSuite(d.toString()) {
            action(d)
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    data: Iterable<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuite(d.toString()) {
            action(d)
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    map: Map<String, Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in map) {
        testSuite(d.key) {
            action(d.value)
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    data: Sequence<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuite(d.toString()) {
            action(d)
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
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
        testSuite(nameFn(d)) {
            action(d)
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
        }
    }
}

fun <Data> TestSuite.withDataSuites(
    nameFn: (Data) -> String,
    data: Sequence<Data>,
    action: TestSuite.(Data) -> Unit
) {
    for (d in data) {
        testSuite(nameFn(d)) {
            action(d)
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
        }
    }
}

fun <Value> TestSuite.checkAllTests(
    iterations: Int,
    genA: Gen<Value>,
    content: suspend context(PropertyContext) TestCoroutineScope.(Value) -> Unit
) {
    var count = 0
    checkAllSeries(iterations, genA) { value, context ->
        count++
        test("$count/$iterations ${if (value == null) "null" else value::class.simpleName}: $value") {
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
        testSuite("$count/$iterations ${if (value == null) "null" else value::class.simpleName}: $value") {
            with(context) {
                content(value)
            }
            if (this.testElementChildren.none()) throw IllegalStateException("Test suite $testElementName is empty!")
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
