package at.asitplus.test


enum class Target {
    JVM,
    IOS_ARM64,
    IOS_IA64,
    IOS_SIMULATORARM64,
    ANDROID_ART,
    ANDROID_AMR32,
    ANDROID_AMR64,
    ANDROID_IA32,
    ANDROID_IA64,
    JS_BROWSER,
    JS_NODE,
    JS_WEBWORKER,
    JS_GENERIC,
    LINUX_ARM64,
    LINUX_IA64,
    MACOS_ARM64,
    MACOS_IA64,
    MINGW_IA64,
    TVOS_ARM64,
    TVOS_SIMULATORARM64,
    TVOS_IA64,
    WASMJS,
    WATCHOS_ARM32,
    WATCHOS_ARM64,
    WATCHOS_DEVICEARM64,
    WATCHOS_SIMULATORARM64,
    WATCHOS_IA64,

    ;

    companion object {
        val current: Target get() = target
    }
}

internal expect val target: Target

