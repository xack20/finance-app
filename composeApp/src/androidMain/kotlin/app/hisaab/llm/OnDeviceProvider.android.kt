package app.hisaab.llm

import android.content.Context

/**
 * Process-global Application context and singleton on-device provider cache.
 *
 * [appContext] is set once from AppContainerAndroid before the router is built.
 * The app context outlives all lock/unlock cycles.
 *
 * [cachedProvider] memoizes the AndroidOnDeviceProvider so the ~900MB MediaPipe
 * LlmInference engine is created at most once per process — calling
 * [createOnDeviceProvider] on every SMS no longer causes per-SMS OOM.
 */
object AndroidLlmContext {
    @Volatile var appContext: Context? = null

    @Volatile private var cachedProvider: AndroidOnDeviceProvider? = null

    /**
     * Returns the cached [AndroidOnDeviceProvider], creating it on first call.
     * The provider's [redact] lambda is stored at construction time so the caller
     * can supply a live config read without the provider holding a DB reference.
     */
    fun getOrCreateProvider(
        ctx: Context,
        redact: suspend () -> Boolean,
    ): AndroidOnDeviceProvider {
        cachedProvider?.let { return it }
        return synchronized(this) {
            cachedProvider ?: AndroidOnDeviceProvider(ctx, ModelManager(ctx), redact)
                .also { cachedProvider = it }
        }
    }
}

actual fun createOnDeviceProvider(): LlmProvider? {
    val ctx = AndroidLlmContext.appContext ?: return null
    val manager = ModelManager(ctx)
    // Only expose the provider when an on-device model is actually usable.
    return if (manager.isAnyModelAvailable()) {
        AndroidLlmContext.getOrCreateProvider(ctx, redact = { true })
    } else {
        null
    }
}
