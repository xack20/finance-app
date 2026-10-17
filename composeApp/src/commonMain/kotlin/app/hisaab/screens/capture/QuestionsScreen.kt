package app.hisaab.screens.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.NeoTopBar
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SurfaceCard
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun QuestionsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = remember {
        val questions = container.agentQuestionRepository
        QuestionsViewModel(
            openQuestions = questions.observeOpen(),
            messagesFor = container.captureInboxRepository::messagesFor,
            answerQuestion = questions::answer,
            dismissQuestion = questions::dismiss,
            onAnswered = { container.kickCaptureAgent() },
        )
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }
    val cards by viewModel.cards.collectAsState()
    QuestionsContent(cards, onBack, viewModel::answer, viewModel::notAPayment)
}

/** The testable body: plain data + callbacks, no container. */
@Composable
fun QuestionsContent(
    cards: List<QuestionCard>,
    onBack: () -> Unit,
    onAnswer: (questionId: String, answer: String) -> Unit,
    onNotAPayment: (questionId: String) -> Unit,
) {
    val palette = LocalHisaabPalette.current
    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(title = "Questions", onBack = onBack)
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(palette.positiveSoft),
                        contentAlignment = Alignment.Center,
                    ) { HisaabIcon("check", tint = palette.positive, size = 30.dp, strokeWidth = 2.2f) }
                    Text("No questions", color = palette.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(
                        "Hisaab reads your messages and adds payments by itself. It asks here only when it can't tell.",
                        color = palette.muted,
                        fontSize = 13.sp,
                    )
                }
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(cards, key = { it.question.id }) { card -> QuestionCardView(card, onAnswer, onNotAPayment) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QuestionCardView(
    card: QuestionCard,
    onAnswer: (questionId: String, answer: String) -> Unit,
    onNotAPayment: (questionId: String) -> Unit,
) {
    val palette = LocalHisaabPalette.current
    var answer by remember(card.question.id) { mutableStateOf("") }
    val id = card.question.id
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(card.question.question, color = palette.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp)
        Spacer(Modifier.height(10.dp))
        card.messages.take(MAX_MESSAGES_SHOWN).forEach { m ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(HisaabShapes.field).background(palette.surfaceRaised).padding(10.dp),
            ) {
                Text("${m.sender} · ${shortTime(m.receivedAt)}", color = palette.muted, fontSize = 11.5.sp)
                Text(m.rawBody, color = palette.onBackground, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        if (card.messages.size > MAX_MESSAGES_SHOWN) {
            Text("…and ${card.messages.size - MAX_MESSAGES_SHOWN} more like these", color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (card.question.options.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                card.question.options.forEach { option ->
                    Text(
                        option,
                        color = palette.onAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(HisaabShapes.pill).background(palette.accent)
                            .clickable { onAnswer(id, option) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        MidnightTextField(value = answer, onValueChange = { answer = it }, placeholder = "Or type your answer")
        Spacer(Modifier.height(8.dp))
        PrimaryButton(text = "Send answer", onClick = { onAnswer(id, answer) }, enabled = answer.isNotBlank())
        Text(
            "Not a payment",
            color = palette.negative,
            fontSize = 13.sp,
            modifier = Modifier.clickable { onNotAPayment(id) }.padding(top = 10.dp),
        )
    }
}

private const val MAX_MESSAGES_SHOWN = 3

private fun shortTime(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.date} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
