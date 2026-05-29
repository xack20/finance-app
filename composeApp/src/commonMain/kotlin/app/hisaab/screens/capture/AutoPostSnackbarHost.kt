package app.hisaab.screens.capture

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.hisaab.LocalAppContainer
import app.hisaab.capture.CaptureEvent
import app.hisaab.domain.Direction
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Collects [AppContainer.captureEvents] and, for each [CaptureEvent.AutoPosted], shows a transient
 * "+amount · sender · auto-added · Undo" snackbar. Undo deletes the posted txn and marks the
 * candidate DISMISSED.
 *
 * Mount this once, alongside the main NavHost (see MainGraph).
 */
@Composable
fun AutoPostSnackbarHost(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        container.captureEvents.filterIsInstance<CaptureEvent.AutoPosted>().collect { event ->
            val result = hostState.showSnackbar(
                message = labelFor(event),
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                scope.launch { undo(container, event) }
            }
        }
    }

    SnackbarHost(hostState = hostState, modifier = modifier)
}

private fun labelFor(event: CaptureEvent.AutoPosted): String {
    val sign = if (event.direction == Direction.CREDIT) "+" else "−"
    return "$sign৳${event.amount.toInt()} · ${event.sender} · auto-added"
}

private suspend fun undo(container: app.hisaab.AppContainer, event: CaptureEvent.AutoPosted) {
    container.transactionRepository.delete(event.txnId)
    container.captureInboxRepository.markDismissed(event.candidateId)
}
