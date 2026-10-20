package app.hisaab.capture

import app.hisaab.domain.CaptureConfig

/** Background capture runs once onboarding is done (a master secret exists) and any capture channel is on. */
fun backgroundCaptureWanted(cfg: CaptureConfig?, hasMasterSecret: Boolean): Boolean =
    hasMasterSecret && cfg != null &&
        (cfg.captureEnabled || cfg.emailCaptureEnabled || cfg.relayCaptureEnabled || cfg.notificationCaptureEnabled)
