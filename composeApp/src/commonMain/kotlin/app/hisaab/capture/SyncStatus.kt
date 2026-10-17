package app.hisaab.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What one capture channel (SMS, email, Mac relay) is doing right now — shown in-app and in the notification. */
sealed interface ChannelStatus {
    data object Off : ChannelStatus
    data class Importing(val done: Int, val total: Int) : ChannelStatus
    data class UpToDate(val lastCheckedMs: Long) : ChannelStatus
    data class Error(val reason: String) : ChannelStatus
}

data class SyncStatuses(
    val sms: ChannelStatus = ChannelStatus.Off,
    val email: ChannelStatus = ChannelStatus.Off,
    val relay: ChannelStatus = ChannelStatus.Off,
    /** Cloud LLM used to parse unknown messages: Error when the provider rejects the key, cleared by a success. */
    val ai: ChannelStatus = ChannelStatus.Off,
)

/**
 * Turns a channel's pass results into a [ChannelStatus]. Errors the user must act on (rejected login, missing
 * permission) show at once; network blips show only after [networkFailureThreshold] failed passes in a row, so
 * the status doesn't flicker every 30 s poll.
 */
class ChannelStatusTracker(
    private val now: () -> Long,
    private val networkFailureThreshold: Int = 3,
) {
    private val _status = MutableStateFlow<ChannelStatus>(ChannelStatus.Off)
    val status: StateFlow<ChannelStatus> = _status.asStateFlow()

    private var consecutiveFailures = 0

    fun off() {
        consecutiveFailures = 0
        _status.value = ChannelStatus.Off
    }

    fun importing(done: Int, total: Int) {
        consecutiveFailures = 0
        _status.value = ChannelStatus.Importing(done, total)
    }

    fun succeeded() {
        consecutiveFailures = 0
        _status.value = ChannelStatus.UpToDate(now())
    }

    fun failed(reason: String, persistent: Boolean) {
        consecutiveFailures++
        if (persistent || consecutiveFailures >= networkFailureThreshold) _status.value = ChannelStatus.Error(reason)
    }
}

fun ChannelStatus.label(timeZone: TimeZone = TimeZone.currentSystemDefault()): String = when (this) {
    ChannelStatus.Off -> "Off"
    is ChannelStatus.Importing -> if (total == 0) "Importing…" else "Importing $done of $total"
    is ChannelStatus.UpToDate -> {
        val time = Instant.fromEpochMilliseconds(lastCheckedMs).toLocalDateTime(timeZone)
        "Up to date · last checked ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }
    is ChannelStatus.Error -> "Error: $reason"
}

/** One line for the ongoing notification: the first error, else the first import, else the idle line. */
fun notificationText(statuses: SyncStatuses): String {
    val channels = listOf(
        "SMS" to statuses.sms, "Email" to statuses.email, "Mac relay" to statuses.relay, "Cloud AI" to statuses.ai,
    )
    channels.firstOrNull { it.second is ChannelStatus.Error }?.let { (name, status) ->
        return "$name: ${(status as ChannelStatus.Error).reason}"
    }
    channels.firstOrNull { it.second is ChannelStatus.Importing }?.let { (name, status) ->
        val importing = status as ChannelStatus.Importing
        val lowered = if (name == "Email") "email" else name
        return if (importing.total == 0) "Importing $lowered…" else "Importing $lowered: ${importing.done} of ${importing.total}"
    }
    return "Watching SMS & email for transactions"
}
