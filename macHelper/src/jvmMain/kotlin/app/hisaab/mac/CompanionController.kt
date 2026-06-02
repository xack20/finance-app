package app.hisaab.mac

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hisaab.capture.ImapConfig
import app.hisaab.capture.ImapHosts
import app.hisaab.capture.RelayCaptureCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
 * forwarded SMS/email and pushes them E2EE. Holds Compose state for the UI.
 *
 * The Supabase session and the derived relay key are persisted to the macOS Keychain, so [resume]
 * can restore them on launch — the user verifies their phone + enters the recovery phrase ONCE, not
 * on every relaunch. [signOut] forgets both to switch accounts.
 *
 * ALL work runs on [Dispatchers.IO]: the injected [scope] comes from the Compose UI (Main) thread,
 * and the Keychain (`security` CLI), IMAP, chat.db, and Supabase calls all block — running them on
 * the UI thread freezes the window. Compose state writes are snapshot-safe from any thread.
 */
@OptIn(ExperimentalEncodingApi::class)
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

    /** Email (IMAP) connected + included in the push loop (optional, in addition to SMS). */
    var emailConnected by mutableStateOf(false)
        private set

    /** The connected mailbox address, if any — lets the UI prefill the field for re-entry. */
    var connectedEmail by mutableStateOf<String?>(null)
        private set

    private val relay: SupabaseRelay? = config?.let { SupabaseRelay(it.supabaseUrl, it.supabaseAnonKey) }
    private val crypto = RelayCaptureCrypto()
    private var relayKey: ByteArray? = null
    private var loopJob: Job? = null
    private var imapConfig: ImapConfig? = null
    private val emailCursorStore = FileCursorStore(appSupport("email_cursor"))
    private val emailAccountFile = appSupport("email_account")

    /**
     * On launch, restore a persisted session + pairing so the user skips OTP + recovery phrase.
     * No-op (stays on the sign-in step) when nothing is stored or the session can't be restored.
     */
    fun resume() {
        val r = relay ?: return
        scope.launch(Dispatchers.IO) {
            val sessionJson = MacKeychain.loadItem(ACCOUNT_SESSION) ?: return@launch
            val signedIn = runCatching { r.importSessionJson(sessionJson) }.getOrDefault(false)
            if (!signedIn) return@launch
            val key = MacKeychain.loadItem(ACCOUNT_RELAY_KEY)?.let { runCatching { Base64.decode(it) }.getOrNull() }
            if (key != null) {
                relayKey = key
                restoreEmail()
                stage = Stage.Running
                startLoop()
            } else {
                stage = Stage.NeedsPairing
            }
        }
    }

    fun sendOtp(phone: String) = launchAction("Couldn't send the code") {
        relay!!.sendOtp(phone.trim())
        stage = Stage.AwaitingOtp(phone.trim())
    }

    fun verifyOtp(code: String) {
        val awaiting = stage as? Stage.AwaitingOtp ?: return
        launchAction("Invalid code") {
            relay!!.verifyOtp(awaiting.phone, code.trim())
            if (relay.currentUserId() != null) {
                relay.exportSessionJson()?.let { MacKeychain.saveItem(ACCOUNT_SESSION, it) } // survive relaunch
                stage = Stage.NeedsPairing
            } else {
                stage = Stage.SignedOut
            }
        }
    }

    fun pair(phrase: String) = launchAction("Invalid recovery phrase") {
        val key = Pairing.relayKeyFromPhrase(phrase) // throws on bad words/checksum → surfaced
        relayKey = key
        MacKeychain.saveItem(ACCOUNT_RELAY_KEY, Base64.encode(key)) // survive relaunch (no re-pair)
        restoreEmail() // resume a previously-connected mailbox (password from Keychain)
        stage = Stage.Running
        startLoop()
    }

    /** Forget the saved session + pairing (e.g. to switch accounts); returns to the sign-in step. */
    fun signOut() {
        loopJob?.cancel(); loopJob = null
        relayKey = null
        imapConfig = null
        emailConnected = false
        error = null
        stage = Stage.SignedOut
        scope.launch(Dispatchers.IO) {
            MacKeychain.deleteItem(ACCOUNT_SESSION)
            MacKeychain.deleteItem(ACCOUNT_RELAY_KEY)
            relay?.signOut()
        }
    }

    /** Connect (or re-connect) an email mailbox for IMAP capture (Gmail/Outlook/Yahoo/iCloud). */
    fun configureEmail(email: String, appPassword: String) = launchAction("Couldn't connect email") {
        val trimmed = email.trim()
        val hostPort = ImapHosts.forEmail(trimmed)
            ?: error("Unsupported provider — use a personal Gmail, Outlook.com, Yahoo, or iCloud address.")
        // Providers show app passwords in space-separated groups of 4 (e.g. Gmail's 16-char code);
        // IMAP auth needs them with NO spaces, so strip whitespace before validating/storing.
        val password = appPassword.filterNot { it.isWhitespace() }
        val cfg = ImapConfig(trimmed, password, hostPort.first, hostPort.second)
        ImapReader(cfg).readSince(System.currentTimeMillis()) // validates connect/auth (returns nothing)
        MacKeychain.savePassword(trimmed, password)
        emailAccountFile.parentFile?.mkdirs()
        emailAccountFile.writeText(trimmed)
        // On first connect, seed the cursor 12 months back so historical bank emails are captured
        // (not only mail arriving after connect). Later runs advance the cursor past what's synced.
        if (emailCursorStore.load() == 0L) {
            emailCursorStore.save(System.currentTimeMillis() - EMAIL_BACKFILL_MS)
        }
        imapConfig = cfg
        connectedEmail = trimmed
        emailConnected = true
    }

    /** Rebuild a previously-connected mailbox from the saved address + Keychain password. */
    private fun restoreEmail() {
        if (imapConfig != null) return
        val email = runCatching { emailAccountFile.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val password = (MacKeychain.loadPassword(email) ?: return).filterNot { it.isWhitespace() }
        val hostPort = ImapHosts.forEmail(email) ?: return
        imapConfig = ImapConfig(email, password, hostPort.first, hostPort.second)
        connectedEmail = email
        emailConnected = true
    }

    private fun startLoop() {
        if (loopJob != null) return
        loopJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var pushed = 0
                var failed = false
                runCatching { pushed += pushSms() }.onFailure { failed = true; error = it.message ?: "SMS sync error" }
                imapConfig?.let { cfg ->
                    runCatching { pushed += pushEmail(cfg) }.onFailure { failed = true; error = it.message ?: "Email sync error" }
                }
                if (!failed) {
                    error = null
                    lastSyncNote = if (pushed > 0) "Synced $pushed new item(s)" else "Up to date"
                }
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun pushSms(): Int {
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

    private suspend fun pushEmail(cfg: ImapConfig): Int {
        val key = relayKey ?: return 0
        val imap = ImapReader(cfg)
        val pusher = RelayPusher(
            relayCrypto = crypto,
            relayKey = key,
            deviceId = deviceId,
            readSince = { cursor -> imap.readSince(cursor).let { it.captures to it.newCursor } },
            uploader = relay!!.uploader(),
            loadCursor = emailCursorStore::load,
            saveCursor = emailCursorStore::save,
        )
        return pusher.pushOnce().also { totalPushed += it }
    }

    private fun launchAction(errorPrefix: String, action: suspend () -> Unit) {
        if (relay == null) { stage = Stage.NeedsConfig; return }
        busy = true
        error = null
        scope.launch(Dispatchers.IO) {
            runCatching { action() }.onFailure { e -> error = "$errorPrefix: ${e.message ?: "unknown"}" }
            busy = false
        }
    }

    companion object {
        /** First-connect email backfill window: capture the last 12 months of bank emails. */
        private const val EMAIL_BACKFILL_MS: Long = 365L * 24 * 60 * 60 * 1000

        // Keychain item names for persisted companion state.
        private const val ACCOUNT_SESSION = "session"
        private const val ACCOUNT_RELAY_KEY = "relay_key"

        private fun defaultDeviceId(): String =
            runCatching { "mac-" + InetAddress.getLocalHost().hostName }
                .getOrElse { "mac-" + UUID.randomUUID().toString().take(8) }

        private fun appSupport(name: String): java.io.File =
            java.io.File(System.getProperty("user.home"), "Library/Application Support/HisaabCompanion/$name")
    }
}
