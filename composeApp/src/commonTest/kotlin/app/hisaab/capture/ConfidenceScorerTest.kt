package app.hisaab.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfidenceScorerTest {

    @Test
    fun `complete template with resolved fields and known sender scores high`() {
        val score = ConfidenceScorer.score(
            templateComplete = true,
            fieldsResolved = true,
            llmConfidence = null,
            senderKnown = true,
        )
        assertTrue(score >= 0.9, "expected >= 0.9 but was $score")
    }

    @Test
    fun `llm-only unknown is capped at 0_7`() {
        val score = ConfidenceScorer.score(
            templateComplete = false,
            fieldsResolved = true,
            llmConfidence = 0.95,
            senderKnown = false,
        )
        assertTrue(score <= 0.7, "expected <= 0.7 but was $score")
    }

    @Test
    fun `unresolved fields drag score below auto-post threshold`() {
        val score = ConfidenceScorer.score(
            templateComplete = true,
            fieldsResolved = false,
            llmConfidence = null,
            senderKnown = true,
        )
        assertTrue(score < 0.85, "expected < 0.85 but was $score")
    }

    @Test
    fun `nothing resolved scores zero`() {
        assertEquals(
            0.0,
            ConfidenceScorer.score(
                templateComplete = false,
                fieldsResolved = false,
                llmConfidence = null,
                senderKnown = false,
            ),
        )
    }

    @Test
    fun `output is always clamped to 0_1`() {
        val high = ConfidenceScorer.score(true, true, 1.0, true)
        val low = ConfidenceScorer.score(false, false, 0.0, false)
        assertTrue(high in 0.0..1.0)
        assertTrue(low in 0.0..1.0)
    }
}
