package at.asitplus.test

internal actual val target: Target = when {
    js("typeof window !== 'undefined'") as Boolean -> Target.JS_BROWSER
    js("typeof global !== 'undefined'") as Boolean -> Target.JS_NODE
    js("typeof self !== 'undefined'") as Boolean -> Target.JS_WEBWORKER
    else -> Target.JS_GENERIC
}

