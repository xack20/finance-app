package app.hisaab.screens.capture

import app.hisaab.domain.AgentQuestion
import app.hisaab.domain.CandidateTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One question from the capture agent, with the messages it's about. */
data class QuestionCard(val question: AgentQuestion, val messages: List<CandidateTransaction>)

/** Drives the Questions screen: what the capture agent couldn't work out, answered in the user's own words. */
class QuestionsViewModel(
    openQuestions: Flow<List<AgentQuestion>>,
    private val messagesFor: suspend (questionId: String) -> List<CandidateTransaction>,
    private val answerQuestion: suspend (questionId: String, answer: String) -> Unit,
    private val dismissQuestion: suspend (questionId: String) -> Unit,
    /** Wakes the agent so it re-reads the answered messages now, not at the next SMS. */
    private val onAnswered: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val cards: StateFlow<List<QuestionCard>> = openQuestions
        .map { questions -> questions.map { QuestionCard(it, messagesFor(it.id)) } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun answer(questionId: String, answer: String) {
        val text = answer.trim()
        if (text.isEmpty()) return
        scope.launch {
            answerQuestion(questionId, text)
            onAnswered()
        }
    }

    fun notAPayment(questionId: String) {
        scope.launch { dismissQuestion(questionId) }
    }

    fun dispose() {
        scope.cancel()
    }
}
