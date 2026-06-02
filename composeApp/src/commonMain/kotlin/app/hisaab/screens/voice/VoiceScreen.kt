package app.hisaab.screens.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.agent.AgentAvailability
import app.hisaab.agent.ProposedWrite
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.LocalReduceMotion
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SurfaceCard
import app.hisaab.screens.agent.SparkleOrb
import app.hisaab.screens.agent.summarize
import kotlinx.coroutines.delay
import kotlin.random.Random

private val VOICE_EXAMPLES = listOf(
    "Lunch e 500 taka", "Rickshaw 60", "Salary pelam 65000",
    "Karim ke 2000 dhar dilam", "Food e koto khorcho holo?",
)

/**
 * Voice-first capture screen (Neo `VoiceFlow`, neo-voice.jsx). Drives [VoiceViewModel], which reuses
 * the Assistant's STT + parse/write engine. [onTypeInstead] switches to the manual entry screen;
 * [onChat] opens the full chat Assistant.
 */
@Composable
fun VoiceScreen(onClose: () -> Unit, onTypeInstead: () -> Unit, onChat: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = remember(container) {
        VoiceViewModel(runtime = container.agentRuntime(), speechToText = container.speechToText)
    }
    val state by vm.state.collectAsState()
    val p = LocalHisaabPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val gate = state.gate
        if (gate != null) {
            GateStep(gate, onClose)
        } else {
            when (val step = state.step) {
                VoiceStep.Listen -> ListenStep(state, vm, onClose, onTypeInstead, onChat)
                VoiceStep.Parsing -> ParsingStep(state.transcript, onClose)
                is VoiceStep.Answer -> AnswerStep(step.message, state.transcript, vm, onClose)
                is VoiceStep.Draft -> DraftStep(step.writes, state.error, vm, onClose)
                is VoiceStep.Done -> DoneStep(step.writes, vm, onClose)
            }
        }
    }
}

/* ── shared bits ─────────────────────────────────────────────────────────── */

@Composable
private fun CloseBar(onClose: () -> Unit, right: @Composable (() -> Unit)? = null) {
    val p = LocalHisaabPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp).clip(CircleShape).background(p.glass)
                .border(1.dp, p.hair, CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { HisaabIcon("close", tint = p.onBackground, size = 19.dp) }
        right?.invoke()
    }
}

/** Push-to-talk mic orb (116dp): lime + pulse while held, else a lime glyph. Hold to speak, release to parse. */
@Composable
private fun MicOrb(listening: Boolean, onHoldStart: () -> Unit, onHoldEnd: () -> Unit) {
    val p = LocalHisaabPalette.current
    val reduce = LocalReduceMotion.current
    val scale = if (listening && !reduce) {
        val t = rememberInfiniteTransition(label = "orb")
        t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "orbScale").value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(116.dp).scale(scale).clip(CircleShape)
            .background(if (listening) p.accent else p.surface)
            .border(if (listening) 0.dp else 1.dp, if (listening) p.accent else p.hair, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onHoldStart()
                    tryAwaitRelease() // suspends until the finger lifts (or the gesture is cancelled)
                    onHoldEnd()
                })
            },
        contentAlignment = Alignment.Center,
    ) { HisaabIcon("mic", tint = if (listening) p.onAccent else p.accent, size = 48.dp) }
}

/** Recognizer-language toggle (বাংলা / EN) — sets the STT locale so Bangla isn't decoded as English. */
@Composable
private fun LangToggle(current: VoiceLang, onSelect: (VoiceLang) -> Unit) {
    val p = LocalHisaabPalette.current
    Row(
        modifier = Modifier
            .clip(HisaabShapes.pill).background(p.surface).border(1.dp, p.hair, HisaabShapes.pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        VoiceLang.entries.forEach { lang ->
            val on = lang == current
            Text(
                lang.label,
                color = if (on) p.onAccent else p.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(HisaabShapes.pill)
                    .background(if (on) p.accent else Color.Transparent)
                    .clickable { onSelect(lang) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Live waveform: 24 lime bars that jitter while listening (neo-voice.jsx Waveform). */
@Composable
private fun Waveform(active: Boolean) {
    val p = LocalHisaabPalette.current
    val reduce = LocalReduceMotion.current
    var bars by remember { mutableStateOf(List(24) { 0.25f }) }
    LaunchedEffect(active, reduce) {
        if (active && !reduce) {
            while (true) {
                bars = List(24) { 0.15f + Random.nextFloat() * 0.85f }
                delay(110)
            }
        } else {
            bars = List(24) { 0.22f }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).widthIn(max = 280.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEach { b ->
            Box(
                modifier = Modifier
                    .weight(1f).height((48 * b).dp).clip(HisaabShapes.pill)
                    .background(p.accent.copy(alpha = if (active) 0.45f + b * 0.5f else 0.3f)),
            )
        }
    }
}

/** Card listing each proposed write as a one-line summary (reuses the Assistant's [summarize]). */
@Composable
private fun DraftPreview(writes: List<ProposedWrite>, big: Boolean = false) {
    val p = LocalHisaabPalette.current
    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = if (big) 22.dp else 16.dp) {
        if (writes.isEmpty()) {
            Text("Nothing to save.", color = p.muted, fontSize = 14.sp)
        }
        writes.forEachIndexed { i, w ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Text(
                summarize(w),
                color = p.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (big) 18.sp else 15.sp,
            )
        }
    }
}

/* ── steps ───────────────────────────────────────────────────────────────── */

@Composable
private fun ColumnScope.ListenStep(state: VoiceUiState, vm: VoiceViewModel, onClose: () -> Unit, onTypeInstead: () -> Unit, onChat: () -> Unit) {
    val p = LocalHisaabPalette.current
    CloseBar(onClose, right = {
        Row(
            modifier = Modifier
                .clip(HisaabShapes.pill).background(p.glass).border(1.dp, p.hair, HisaabShapes.pill)
                .clickable(onClick = onTypeInstead).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            HisaabIcon("edit", tint = p.muted, size = 16.dp)
            Text("Type instead", color = p.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    })

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LangToggle(current = state.language, onSelect = vm::setLanguage)
        Spacer(Modifier.height(16.dp))
        Eyebrow(if (state.listening) "Listening… release to parse" else "Hold & speak", color = p.accent)
        Spacer(Modifier.height(10.dp))
        val shown = state.transcript
        Text(
            if (shown.isBlank()) "Hold the mic and\nsay a transaction" else shown,
            color = p.onBackground,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (shown.isBlank()) 24.sp else 22.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        MicOrb(listening = state.listening, onHoldStart = vm::onHoldStart, onHoldEnd = vm::onHoldEnd)
        Spacer(Modifier.height(22.dp))
        Waveform(active = state.listening)
        if (!state.sttAvailable) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Mic needs permission — type your command below instead.",
                color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        MidnightTextField(
            value = state.typed,
            onValueChange = vm::onTyped,
            placeholder = "…or type what you'd say",
            imeAction = ImeAction.Done,
            onImeAction = { vm.submit() },
        )
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = p.negative, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VOICE_EXAMPLES.forEach { ex ->
            Text(
                "“$ex”",
                color = p.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                modifier = Modifier
                    .clip(HisaabShapes.pill).background(p.glass).border(1.dp, p.hair, HisaabShapes.pill)
                    .clickable { vm.onTyped(ex) }.padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
        PrimaryButton(
            text = "Parse with AI",
            onClick = { vm.submit() },
            enabled = state.canSubmit,
            trailingIcon = "arrow-up",
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                "Ask in chat instead",
                color = p.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.clip(HisaabShapes.pill).clickable(onClick = onChat).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.ParsingStep(transcript: String, onClose: () -> Unit) {
    val p = LocalHisaabPalette.current
    CloseBar(onClose)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        SparkleOrb(size = 64, glow = true)
        Text("Understanding…", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (transcript.isNotBlank()) {
            Text(
                "“$transcript”",
                color = p.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.AnswerStep(message: String, transcript: String, vm: VoiceViewModel, onClose: () -> Unit) {
    val p = LocalHisaabPalette.current
    CloseBar(onClose)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (transcript.isNotBlank()) {
            Text("“$transcript”", color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
        }
        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 22.dp) {
            Text(message.ifBlank { "Here's what I found." }, color = p.onBackground, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(text = "Ask again", onClick = vm::redo, modifier = Modifier.weight(1f), fillMaxWidth = false)
            PrimaryButton(text = "Done", onClick = onClose, modifier = Modifier.weight(1f), fillMaxWidth = false)
        }
    }
}

@Composable
private fun ColumnScope.DraftStep(writes: List<ProposedWrite>, error: String?, vm: VoiceViewModel, onClose: () -> Unit) {
    val p = LocalHisaabPalette.current
    CloseBar(onClose)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow("Ready to save", color = p.accent)
        Spacer(Modifier.height(12.dp))
        DraftPreview(writes, big = true)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = p.negative, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(text = "Redo", onClick = vm::redo, modifier = Modifier.weight(1f), fillMaxWidth = false)
            PrimaryButton(text = "Save", onClick = vm::save, modifier = Modifier.weight(1f), fillMaxWidth = false, leadingIcon = "check")
        }
    }
}

@Composable
private fun ColumnScope.DoneStep(writes: List<ProposedWrite>, vm: VoiceViewModel, onClose: () -> Unit) {
    val p = LocalHisaabPalette.current
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(76.dp).clip(HisaabShapes.card).background(p.accent),
            contentAlignment = Alignment.Center,
        ) { HisaabIcon("check", tint = p.onAccent, size = 40.dp, strokeWidth = 2.4f) }
        Text("Saved", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        DraftPreview(writes)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(text = "Add another", onClick = vm::redo, modifier = Modifier.weight(1f), fillMaxWidth = false)
            PrimaryButton(text = "Done", onClick = onClose, modifier = Modifier.weight(1f), fillMaxWidth = false)
        }
    }
}

@Composable
private fun ColumnScope.GateStep(gate: AgentAvailability, onClose: () -> Unit) {
    val p = LocalHisaabPalette.current
    val reason = when (gate) {
        AgentAvailability.NeedsConsent -> "Turn on the cloud assistant in Settings to use voice capture."
        is AgentAvailability.Unavailable -> gate.reason
        AgentAvailability.Ready -> ""
    }
    CloseBar(onClose)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(HisaabShapes.card).background(p.accentSoft),
            contentAlignment = Alignment.Center,
        ) { HisaabIcon("mic", tint = p.accent, size = 30.dp) }
        Text(
            "Voice needs the assistant",
            color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, textAlign = TextAlign.Center,
        )
        Text(reason, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        Spacer(Modifier.height(4.dp))
        PrimaryButton(text = "Got it", onClick = onClose)
    }
}
