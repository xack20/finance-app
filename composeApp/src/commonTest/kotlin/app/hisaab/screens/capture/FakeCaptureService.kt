package app.hisaab.screens.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.Category
import app.hisaab.domain.RawCapture
import app.hisaab.llm.LlmParseResult
import app.hisaab.llm.LlmProvider
import app.hisaab.llm.LlmRouter
import app.hisaab.llm.ParseRequest
import app.hisaab.llm.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Hand fake of M3-2's CaptureService for ViewModel unit tests.
 * NOTE: CaptureService is an `expect class`, so we cannot subclass it. Instead the
 * AutoCaptureViewModel takes the operations it needs behind small functional seams
 * (see AutoCaptureViewModel constructor): `hasSmsPermission`, `requestSmsPermission`,
 * `backfillSince`. This fake exposes those as overridable lambdas.
 */
class FakeCaptureGateway(
    var permissionGranted: Boolean = false,
    var backfillResult: List<RawCapture> = emptyList(),
) {
    var requestCalled = false
    var lastBackfillCursor: Long? = null

    suspend fun hasSmsPermission(): Boolean = permissionGranted
    suspend fun requestSmsPermission(): Boolean {
        requestCalled = true
        permissionGranted = true
        return true
    }
    fun observeIncoming(): Flow<RawCapture> = emptyFlow()
    suspend fun backfillSince(cursorMs: Long): List<RawCapture> {
        lastBackfillCursor = cursorMs
        return backfillResult
    }
    fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.SMS)
    fun openNotificationAccessSettings() {}
}

/** Fake LlmProvider used to assert key-validation paths. */
class FakeLlmProvider(
    override val id: ProviderId,
    private val available: Boolean,
) : LlmProvider {
    override suspend fun isAvailable(): Boolean = available
    override suspend fun parse(req: ParseRequest): LlmParseResult =
        LlmParseResult(null, null, null, null, null, null, 0.0, false)
    override suspend fun categorize(merchant: String, categories: List<Category>): String? = null
}

/** Fake router returning a configurable active provider. */
class FakeLlmRouter(var provider: LlmProvider? = null) : LlmRouter {
    override suspend fun active(): LlmProvider? = provider
}
