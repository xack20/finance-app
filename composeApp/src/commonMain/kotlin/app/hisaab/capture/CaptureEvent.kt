package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * Events emitted by CapturePipeline for the UI layer to react to. The pipeline
 * pushes these into a MutableSharedFlow injected by AppContainer; M3-5's
 * snackbar host collects AppContainer.captureEvents and shows the quiet
 * "auto-added · Undo" snackbar. Decouples the pure pipeline from any UI/ctor
 * callback (R3): the pipeline never holds a lambda or a Composable.
 */
sealed interface CaptureEvent {

    /**
     * A high-confidence candidate was auto-posted to the ledger.
     * [txnId] is the posted ledger transaction (Undo deletes it); [candidateId]
     * is the capture_inbox row (Undo sets it DISMISSED); [amount]/[sender]/
     * [direction] drive the snackbar copy.
     */
    data class AutoPosted(
        val txnId: String,
        val candidateId: String,
        val amount: Double,
        val sender: String,
        val direction: Direction,
    ) : CaptureEvent
}
