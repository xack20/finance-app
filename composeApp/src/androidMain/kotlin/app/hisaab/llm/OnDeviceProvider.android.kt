package app.hisaab.llm

import android.content.Context

/**
 * Process-global Application context for the on-device provider. Set once from
 * AppContainerAndroid before the router is built (the app context outlives all
 * lock/unlock cycles, so a static reference is safe and matches how other Android
 * platform deps receive their Context).
 */
object AndroidLlmContext {
    @Volatile var appContext: Context? = null
}

actual fun createOnDeviceProvider(): LlmProvider? {
    val ctx = AndroidLlmContext.appContext ?: return null
    val manager = ModelManager(ctx)
    // Only expose the provider when an on-device model is actually usable.
    return if (manager.isAnyModelAvailable()) {
        AndroidOnDeviceProvider(ctx, manager)
    } else {
        null
    }
}
