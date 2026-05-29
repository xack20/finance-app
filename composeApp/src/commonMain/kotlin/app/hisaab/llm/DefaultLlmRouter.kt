package app.hisaab.llm

import app.hisaab.data.CaptureConfigRepository
import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import app.hisaab.platform.SecureStorage

/** SecureStorage key prefix for BYO cloud API keys: "llm_api_key_<PROVIDER>". */
fun secureKeyFor(provider: CloudProvider): String = "llm_api_key_${provider.name}"

/**
 * Selects the active LLM provider from CaptureConfig with a cloud consent + key gate.
 *
 * - ON_DEVICE: the on-device provider if available, else null (template-only fallback).
 * - CLOUD: the selected cloud adapter only when consent is recorded AND its key is
 *   present (provider.isAvailable()). Otherwise null.
 *
 * Internal ctor exposes functional seams (configSource/keyLoader) for unit testing
 * without an actual SecureStorage; the production ctor adapts the repo + SecureStorage.
 */
class DefaultLlmRouter internal constructor(
    private val configSource: suspend () -> CaptureConfig,
    private val keyLoader: (CloudProvider) -> String?,
    private val onDeviceProvider: LlmProvider?,
    private val claude: LlmProvider,
    private val gemini: LlmProvider,
    private val openai: LlmProvider,
) : LlmRouter {

    constructor(
        configRepo: CaptureConfigRepository,
        secureStorage: SecureStorage,
        onDeviceProvider: LlmProvider?,
        claude: LlmProvider,
        gemini: LlmProvider,
        openai: LlmProvider,
    ) : this(
        configSource = { configRepo.get() },
        keyLoader = { provider -> secureStorage.loadString(secureKeyFor(provider)) },
        onDeviceProvider = onDeviceProvider,
        claude = claude,
        gemini = gemini,
        openai = openai,
    )

    override suspend fun active(): LlmProvider? {
        val cfg = configSource()
        return when (cfg.engineMode) {
            EngineMode.ON_DEVICE -> onDeviceProvider?.takeIf { it.isAvailable() }
            EngineMode.CLOUD -> activeCloud(cfg)
        }
    }

    private suspend fun activeCloud(cfg: CaptureConfig): LlmProvider? {
        if (cfg.cloudConsentAt == null) return null
        val provider = cfg.cloudProvider ?: return null
        if (keyLoader(provider).isNullOrBlank()) return null
        val adapter = when (provider) {
            CloudProvider.CLAUDE -> claude
            CloudProvider.GEMINI -> gemini
            CloudProvider.OPENAI -> openai
        }
        return adapter.takeIf { it.isAvailable() }
    }
}
