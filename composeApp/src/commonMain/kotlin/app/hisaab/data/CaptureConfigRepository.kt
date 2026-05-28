package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.CaptureConfig
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class CaptureConfigRepository(private val db: HisaabDatabase) {

    private val queries get() = db.captureConfigQueriesQueries

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    // on_device_model setter deferred to M3-4

    private suspend fun ensure() = withContext(Dispatchers.Default) { queries.ensureSingleton(now()) }

    fun observe(): Flow<CaptureConfig> = flow {
        ensure()
        emitAll(queries.observeConfig().asFlow().mapToOne(Dispatchers.Default).map { it.toDomain() })
    }

    suspend fun get(): CaptureConfig {
        ensure()
        return queries.getConfig().executeAsOne().toDomain()
    }

    suspend fun setEngineMode(mode: EngineMode) {
        ensure()
        queries.setEngineMode(mode.name, now())
    }

    suspend fun setCloudProvider(provider: CloudProvider?, model: String?) {
        ensure()
        queries.setCloudProvider(provider?.name, model, now())
    }

    suspend fun setRedaction(enabled: Boolean) {
        ensure()
        queries.setRedaction(if (enabled) 1L else 0L, now())
    }

    suspend fun setAlwaysReview(enabled: Boolean) {
        ensure()
        queries.setAlwaysReview(if (enabled) 1L else 0L, now())
    }

    suspend fun setAutoPostThreshold(threshold: Double) {
        ensure()
        queries.setAutoPostThreshold(threshold, now())
    }

    suspend fun setCaptureEnabled(enabled: Boolean) {
        ensure()
        queries.setCaptureEnabled(if (enabled) 1L else 0L, now())
    }

    suspend fun recordConsent(ts: Long) {
        ensure()
        queries.recordConsent(ts, now())
    }

    suspend fun clearConsent() {
        ensure()
        queries.clearConsent(now())
    }

    suspend fun setCursor(ts: Long) {
        ensure()
        queries.setCursor(ts, now())
    }

    private fun migrations.Capture_config.toDomain(): CaptureConfig = CaptureConfig(
        captureEnabled = capture_enabled == 1L,
        engineMode = EngineMode.valueOf(engine_mode),
        onDeviceModel = on_device_model,
        cloudProvider = cloud_provider?.let { CloudProvider.valueOf(it) },
        cloudModel = cloud_model,
        redactionEnabled = redaction_enabled == 1L,
        alwaysReview = always_review == 1L,
        autoPostThreshold = auto_post_threshold,
        cloudConsentAt = cloud_consent_at,
        retainRawBody = retain_raw_body == 1L,
        lastSmsCursor = last_sms_cursor,
        updatedAt = updated_at,
    )
}
