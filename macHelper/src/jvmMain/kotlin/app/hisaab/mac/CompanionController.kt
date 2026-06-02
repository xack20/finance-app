package app.hisaab.mac

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hisaab.capture.RelayCaptureCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.UUID

/** UI stages of the Mac companion. */
sealed interface Stage {
    data object NeedsConfig : Stage              // no Supabase URL/key configured
    data object SignedOut : Stage                // need phone-OTP login
    data class AwaitingOtp(val phone: String) : Stage
    data object NeedsPairing : Stage             // signed in; need recovery phrase
    data object Running : Stage                  // paired; push loop active
}

/**
 * Orchestrates the helper: phone-OTP login → recovery-phrase pairing → a poll loop that reads new
 * forwarded SMS and pushes them E2EE. Holds Compose state for the UI. All the verified pieces
 * (ChatDbReader, RelayPusher, RelayCaptureCrypto, Pairing, FileCursorStore) are assembled here;
 * this layer is glue (exercised on a real Mac run).
 */
class CompanionController(
    private val config: CompanionConfig?,
    private val scope: CoroutineScope,
    private val reader: ChatDbReader = ChatDbReader(),
    private val cursorStore: FileCursorStore = FileCursorStore(),
    private val deviceId: String = defaultDeviceId(),
    private val pollIntervalMs: Long = 20_000L,
) {
    var stage by mutableStateOf<Stage>(if (config == null) Stage.NeedsConfig else Stage.SignedOut)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var totalPushed by mutableStateOf(0)
        private set
    var lastSyncNote by mutableStateOf<String?>(null)
        private set

    private val relay: SupabaseRelay? = config?.let { SupabaseRelay(it.supabaseUrl, it.supabaseAnonKey) }
    private val crypto = RelayCaptureCrypto()
    private var relayKey: ByteArray? = null
    private var loopJob: Job? = null

    fun sendOtp(phone: String) = launchAction("Couldn't send the code") {
        relay!!.sendOtp(phone.trim())
        stage = Stage.AwaitingOtp(phone.trim())
    }

    fun verifyOtp(code: String) {
        val awaiting = stage as? Stage.AwaitingOtp ?: return
        launchAction("Invalid code") {
            relay!!.verifyOtp(awaiting.phone, code.trim())
            stage = if (relay.currentUserId() != null) Stage.NeedsPairing else Stage.SignedOut
        }
    }

    fun pair(phrase: String) = launchAction("Invalid recovery phrase") {
        relayKey = Pairing.relayKeyFromPhrase(phrase) // throws on bad words/checksum → surfaced
        stage = Stage.Running
        startLoop()
    }

    private fun startLoop() {
        if (loopJob != null) return
        loopJob = scope.launch {
            while (isActive) {
                runCatching { pushOnce() }
                    .onSuccess { pushed -> error = null; lastSyncNote = if (pushed > 0) "Synced $pushed new SMS" else "Up to date" }
                    .onFailure { e -> error = e.message ?: "Sync error" }
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun pushOnce(): Int {
        val key = relayKey ?: return 0
        val pusher = RelayPusher(
            relayCrypto = crypto,
            relayKey = key,
            deviceId = deviceId,
            readSince = { cursor -> reader.readSince(cursor).let { it.captures to it.newCursor } },
            uploader = relay!!.uploader(),
            loadCursor = cursorStore::load,
            saveCursor = cursorStore::save,
        )
        return pusher.pushOnce().also { totalPushed += it }
    }

    private fun launchAction(errorPrefix: String, action: suspend () -> Unit) {
        if (relay == null) { stage = Stage.NeedsConfig; return }
        busy = true
        error = null
        scope.launch {
            runCatching { action() }.onFailure { e -> error = "$errorPrefix: ${e.message ?: "unknown"}" }
            busy = false
        }
    }

    companion object {
        private fun defaultDeviceId(): String =
            runCatching { "mac-" + InetAddress.getLocalHost().hostName }
                .getOrElse { "mac-" + UUID.randomUUID().toString().take(8) }
    }
}
