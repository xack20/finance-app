package app.hisaab.capture

/**
 * Blends parse signals into a 0..1 confidence used by the auto-post gate.
 *
 * - A fully-matched template from a known sender, with account+merchant+category
 *   resolved, reaches 0.9+ (auto-post territory).
 * - An LLM-only parse of an unknown message is hard-capped at 0.7 so it always
 *   routes to review, per design D5.
 * - Unresolved downstream fields (no account/merchant/category) pull the score
 *   below the default 0.85 threshold.
 */
object ConfidenceScorer {

    private const val TEMPLATE_BASE = 0.6
    private const val SENDER_KNOWN_BONUS = 0.2
    private const val FIELDS_RESOLVED_BONUS = 0.2
    private const val LLM_ONLY_CAP = 0.7

    fun score(
        templateComplete: Boolean,
        fieldsResolved: Boolean,
        llmConfidence: Double?,
        senderKnown: Boolean,
    ): Double {
        val raw = if (templateComplete) {
            TEMPLATE_BASE +
                (if (senderKnown) SENDER_KNOWN_BONUS else 0.0) +
                (if (fieldsResolved) FIELDS_RESOLVED_BONUS else 0.0)
        } else if (llmConfidence != null) {
            // LLM-only path: scale the model's self-report by field resolution, then cap.
            val base = llmConfidence * (if (fieldsResolved) 1.0 else 0.6)
            minOf(base, LLM_ONLY_CAP)
        } else {
            0.0
        }
        return raw.coerceIn(0.0, 1.0)
    }
}
