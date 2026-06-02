package app.hisaab.domain

import kotlinx.serialization.Serializable

/**
 * A raw captured message before any parsing. Produced by CaptureService (M3-2).
 * @Serializable so it can be the E2EE payload of the Mac→phone capture relay.
 *
 * Lives in :sharedCore so the macOS helper reuses the EXACT type (identical JSON shape) when it
 * seals captures for the relay.
 */
@Serializable
data class RawCapture(
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val channel: CaptureChannel,
)

/** How a capture entered the app. */
@Serializable
enum class CaptureChannel { SMS, NOTIFICATION, PASTE }
