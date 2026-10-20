package app.hisaab.capture.agent

import app.hisaab.agent.AgentLoop
import app.hisaab.agent.AgentProvider
import app.hisaab.agent.AgentTurnResult
import app.hisaab.agent.ReadTool
import app.hisaab.agent.ToolRegistry
import app.hisaab.capture.CaptureEvent
import app.hisaab.data.AgentQuestionRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.domain.CandidateTransaction
import app.hisaab.llm.LlmError
import app.hisaab.llm.LlmException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What one batch run did. [undecided] messages stay queued for another try. */
data class BatchOutcome(val events: List<CaptureEvent.AutoPosted>, val decided: Int, val undecided: Int)

/**
 * Reads a batch of captured messages and settles them: the model decides (with read tools), code validates the
 * decisions ([parseDecisions]) and writes them ([CaptureCommitter]). Validation errors get one repair round;
 * messages still undecided are retried in a later batch and become a question after [MAX_ATTEMPTS] runs.
 * Provider failures (bad key, network) propagate to the worker and leave the batch untouched.
 */
class CaptureAgent(
    private val provider: AgentProvider,
    private val readTools: List<ReadTool>,
    private val context: suspend () -> AgentContext,
    private val committer: CaptureCommitter,
    private val inbox: CaptureInboxRepository,
    private val questions: AgentQuestionRepository,
    /** The message text as it may leave the phone (contacts added, codes masked, redacted when enabled). */
    private val prepare: suspend (CandidateTransaction) -> String = { it.rawBody },
    /** The sender as it may leave the phone (a P2P sender is a phone number). */
    private val prepareSender: suspend (String) -> String = { it },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val today: () -> String = { Clock.System.now().toLocalDateTime(timeZone).date.toString() },
    private val maxIterations: Int = 6,
) : BatchProcessor {
    private val registry = ToolRegistry(readTools, captureDecisionDescriptors())

    override suspend fun process(batch: List<CandidateTransaction>): BatchOutcome {
        if (batch.isEmpty()) return BatchOutcome(emptyList(), 0, 0)
        // The model sees short numbers, never database ids.
        val byNumber = batch.mapIndexed { i, m -> (i + 1).toString() to m }.toMap()
        val ctx = context()
        val loop = AgentLoop(
            provider, registry, CaptureAgentPrompt.system(registry, ctx, today()), maxIterations, CAPTURE_MAX_TOKENS,
        )
        val accountNames = ctx.accounts.map { it.name }.toSet()

        val first = batchCall { loop.run(emptyList(), renderBatch(byNumber)) } ?: return giveUpOnce(batch)
        var result = parse(first, byNumber.keys, accountNames)
        if (result.errors.isNotEmpty()) {
            val repair = "Some decisions were rejected:\n" + result.errors.joinToString("\n") { "- $it" } +
                "\nReply with a corrected final that contains ALL decisions for this batch."
            val second = batchCall { loop.run(first.updatedHistory.drop(1), repair) } ?: return giveUpOnce(batch)
            result = parse(second, byNumber.keys, accountNames)
        }

        val decisions = result.decisions.map { d -> d.withMessageIds(d.messageIds.mapNotNull { byNumber[it]?.id }) }
        // A batch that can't be written (an account vanished since the prompt) is retried whole, not half-written.
        val events = runCatching { committer.commit(batch, decisions) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        val undecided = if (events == null) batch else result.undecided.mapNotNull { byNumber[it] }
        settleUndecided(undecided)
        return BatchOutcome(events.orEmpty(), decided = batch.size - undecided.size, undecided = undecided.size)
    }

    /**
     * Runs a model call; null when the failure is this batch's own (a reply cut off at max_tokens, a refusal, a
     * rejected request, a timeout) — retrying the same batch unchanged would fail and pay again. Connection,
     * rate-limit, outage and key errors are rethrown for the worker to retry later without blaming the batch.
     */
    private suspend fun <T> batchCall(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: LlmException) {
        val error = e.error
        val batchFault = error is LlmError.Decode || error is LlmError.Refused ||
            (error is LlmError.ProviderError && !error.retryable)
        if (batchFault) null else throw e
    } catch (e: HttpRequestTimeoutException) {
        null
    }

    /** The batch counts one attempt; after [MAX_ATTEMPTS] its messages become questions instead of being retried. */
    private suspend fun giveUpOnce(batch: List<CandidateTransaction>): BatchOutcome {
        settleUndecided(batch)
        return BatchOutcome(emptyList(), decided = 0, undecided = batch.size)
    }

    private fun parse(turn: AgentTurnResult, numbers: Set<String>, accounts: Set<String>): DecisionResult =
        if (turn.cappedOut) DecisionResult(emptyList(), emptyList(), numbers)
        else parseDecisions(turn.proposedWrites, numbers, accounts)

    private suspend fun settleUndecided(messages: List<CandidateTransaction>) {
        val givingUp = messages.filter { it.attempts + 1 >= MAX_ATTEMPTS }
        messages.forEach { inbox.bumpAttempts(it.id) }
        if (givingUp.isNotEmpty()) {
            committer.commit(
                givingUp,
                givingUp.map {
                    CaptureDecision.Ask(listOf(it.id), "I couldn't work out this message — what is it?", emptyList())
                },
            )
        }
    }

    private suspend fun renderBatch(byNumber: Map<String, CandidateTransaction>): String = buildString {
        appendLine("Messages (oldest first):")
        byNumber.forEach { (number, m) ->
            appendLine()
            appendLine("[$number] ${m.channel.name} · from ${prepareSender(m.sender)} · ${formatTime(m.receivedAt)}")
            appendLine(prepare(m).take(MAX_BODY_CHARS))
            m.questionId?.let { questions.getById(it) }?.takeIf { it.answer != null }?.let { q ->
                appendLine("(You asked the user: \"${q.question}\" — they answered: \"${q.answer}\")")
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone)
        return "${t.date} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        /** Room for ~25 decisions plus the model's thinking (thinking counts toward max_tokens). */
        private const val CAPTURE_MAX_TOKENS = 16_000
        /** Long emails are cut; the money lines are near the top. */
        private const val MAX_BODY_CHARS = 2_000
    }
}

private fun CaptureDecision.withMessageIds(ids: List<String>): CaptureDecision = when (this) {
    is CaptureDecision.PostTransaction -> copy(messageIds = ids)
    is CaptureDecision.PostTransfer -> copy(messageIds = ids)
    is CaptureDecision.PostLendBorrow -> copy(messageIds = ids)
    is CaptureDecision.Skip -> copy(messageIds = ids)
    is CaptureDecision.Ask -> copy(messageIds = ids)
    is CaptureDecision.CreateAccount, is CaptureDecision.Remember -> this
}
