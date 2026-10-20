package app.hisaab.capture.agent

import app.hisaab.agent.AgentProvider
import app.hisaab.agent.ChatMessage
import app.hisaab.capture.CaptureEvent
import app.hisaab.capture.ChannelStatusTracker
import app.hisaab.data.CaptureConfigRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.domain.CandidateTransaction
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Settles one batch of queued messages ([CaptureAgent] in production). */
fun interface BatchProcessor {
    suspend fun process(batch: List<CandidateTransaction>): BatchOutcome
}

/** How many AI calls the capture agent made today — the daily spending cap. */
interface AgentCallBudget {
    suspend fun callsToday(): Int
    suspend fun recordCall()
}

/** [AgentCallBudget] kept in capture_config, keyed by the local date from [today]. */
class ConfigCallBudget(private val config: CaptureConfigRepository, private val today: () -> String) : AgentCallBudget {
    override suspend fun callsToday() = config.agentCallsToday(today())
    override suspend fun recordCall() = config.recordAgentCall(today())
}

/** Counts every model call (repair rounds and read-tool turns included) against [budget]. */
class CountingAgentProvider(private val delegate: AgentProvider, private val budget: AgentCallBudget) : AgentProvider {
    override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int): String {
        budget.recordCall()
        return delegate.complete(messages, maxTokens)
    }
}

/**
 * Drains the capture queue through the agent: waits [debounceMs] after a kick (an SMS, its app notification and
 * its email arrive together and belong in one batch), then processes batches of [batchSize] until the queue is
 * empty, the daily cap is hit, or the AI is unavailable. Status goes to [status] (the "Cloud AI" sync line).
 */
class CaptureAgentWorker(
    private val inbox: CaptureInboxRepository,
    /** Null when no cloud AI can be used (no key, no consent, on-device mode). */
    private val processor: suspend () -> BatchProcessor?,
    private val budget: AgentCallBudget,
    private val status: ChannelStatusTracker,
    private val onPosted: suspend (List<CaptureEvent.AutoPosted>) -> Unit = {},
    private val debounceMs: Long = 5_000,
    /** First wait after a failed drain; doubles per failure in a row up to [maxRetryMs]. */
    private val retryMs: Long = 120_000,
    private val maxRetryMs: Long = 30 * 60_000,
    private val batchSize: Int = 25,
    private val dailyCap: Int = DAILY_CALL_CAP,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** How long until the local day changes and the daily cap resets. */
    private val millisUntilMidnight: () -> Long = {
        val zone = TimeZone.currentSystemDefault()
        val tomorrow = Clock.System.now().toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
        tomorrow.atStartOfDayIn(zone).toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()
    },
) {
    private val kicks = Channel<Unit>(Channel.CONFLATED)

    /** Something was queued (or answered): drain soon. Safe to call from anywhere, any number of times. */
    fun kick() {
        kicks.trySend(Unit)
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        kick() // whatever was already waiting
        var failuresInARow = 0
        for (signal in kicks) {
            delay(debounceMs)
            when (runUntilIdle()) {
                Drain.RETRY -> {
                    failuresInARow++
                    val wait = minOf(retryMs * (1L shl minOf(failuresInARow - 1, MAX_BACKOFF_DOUBLINGS)), maxRetryMs)
                    launch { delay(wait); kick() }
                }
                Drain.CAPPED -> {
                    failuresInARow = 0
                    launch { delay(millisUntilMidnight()); kick() }
                }
                Drain.IDLE, Drain.BLOCKED -> failuresInARow = 0
            }
        }
    }

    /** IDLE = queue empty; BLOCKED = needs the user (key, consent); CAPPED = daily cap; RETRY = try again later. */
    enum class Drain { IDLE, BLOCKED, CAPPED, RETRY }

    /** Never throws (except cancellation): a crash here would take down the sticky background service with it. */
    suspend fun runUntilIdle(): Drain = try {
        drain()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        status.failed(e.message ?: "AI reading failed", persistent = false)
        Drain.RETRY
    }

    private suspend fun drain(): Drain {
        var done = 0
        while (true) {
            val remaining = inbox.queuedCount()
            if (remaining == 0) {
                status.succeeded()
                return Drain.IDLE
            }
            if (budget.callsToday() >= dailyCap) {
                status.failed("Daily AI limit reached — reading resumes tomorrow", persistent = true)
                return Drain.CAPPED
            }
            val agent = processor()
            if (agent == null) {
                status.failed("Add a cloud AI key and agree to cloud processing to read messages", persistent = true)
                return Drain.BLOCKED
            }
            status.importing(done, done + remaining)
            val outcome = try {
                agent.process(inbox.nextQueued(batchSize, recentSinceMs = now() - RECENT_FIRST_MS))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                if (e.error == LlmError.InvalidKey) {
                    status.failed("key rejected — update it in Auto-capture", persistent = true)
                    return Drain.BLOCKED
                }
                status.failed(e.error.message, persistent = false)
                return Drain.RETRY
            } catch (e: Exception) {
                status.failed(e.message ?: "couldn't reach the AI", persistent = false) // offline, timeout…
                return Drain.RETRY
            }
            if (outcome.events.isNotEmpty()) onPosted(outcome.events)
            done += outcome.decided
            if (outcome.decided == 0) return Drain.RETRY // nothing settled: don't spin on the same batch
        }
    }

    companion object {
        /** About 600 batches of 25 — the whole history of a heavy phone in a day or two. */
        const val DAILY_CALL_CAP = 600
        /** Messages from the last day jump the queue, so today's payment isn't stuck behind an import. */
        private const val RECENT_FIRST_MS = 24 * 60 * 60_000L
        private const val MAX_BACKOFF_DOUBLINGS = 6
    }
}
