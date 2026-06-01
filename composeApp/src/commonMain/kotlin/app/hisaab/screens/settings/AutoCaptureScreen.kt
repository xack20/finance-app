package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.HRadio
import app.hisaab.design.components.HToggle
import app.hisaab.design.components.MidnightSlider
import app.hisaab.design.components.SurfaceCard
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.CloudProvider
import app.hisaab.domain.EngineMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCaptureScreen(
    onBack: () -> Unit,
    onConsent: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val smsSupported = remember { container.captureService.capabilities().contains(CaptureChannel.SMS) }
    val viewModel = remember {
        val service = container.captureService
        AutoCaptureViewModel(
            configRepo = container.captureConfigRepository,
            senderRepo = container.senderRepository,
            accountRepo = container.accountRepository,
            hasSmsPermission = { service.hasSmsPermission() },
            requestSmsPermission = { service.requestSmsPermission() },
            // M3-int Fix 4: run backfill through coordinator so each RawCapture is processed.
            runBackfill = { nowMs -> container.captureCoordinator.runInitialBackfill(nowMs) },
            loadApiKey = { key -> container.secureStorage.loadString(key) },
            storeApiKey = { key, value -> container.secureStorage.storeString(key, value) },
            clearApiKey = { key -> container.secureStorage.storeString(key, "") },
            router = container.llmRouter,
            // M3-int Fix 3: master toggle gates the coordinator.
            onStartCapture = { container.startCapture() },
            onStopCapture = { container.stopCapture() },
        )
    }
    val cfg by viewModel.config.collectAsState()
    val senders by viewModel.senders.collectAsState()
    val unmapped by viewModel.unmappedSenders.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val keyValidation by viewModel.keyValidation.collectAsState()
    val permissionDenied by viewModel.permissionDenied.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-capture", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        val c = cfg ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = HisaabSpacing.gutter),
        ) {
            Spacer(Modifier.height(8.dp))

            if (!smsSupported) {
                IosUnavailableExplainer(palette)
                Spacer(Modifier.height(40.dp))
                return@Column
            }

            // ── Master toggle ────────────────────────────────────────────────
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow("Capture transactions from SMS", c.captureEnabled, palette) {
                    viewModel.setCaptureEnabled(it)
                }
            }

            // ── Permission-denied warning ────────────────────────────────────
            if (permissionDenied) {
                Spacer(Modifier.height(8.dp))
                SurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.negative, HisaabShapes.card),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
                    ) {
                        Text("⚠", color = palette.negative, fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "SMS permission was denied. Grant notification access instead, or enable SMS in system settings.",
                                color = palette.negative,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            // Re-toggle affordance styled as a lime pill
                            Row(
                                modifier = Modifier
                                    .clip(HisaabShapes.pill)
                                    .background(palette.accent)
                                    .clickable { viewModel.setCaptureEnabled(true) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text("Grant", color = palette.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // ── Engine ───────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("Engine", palette)
            EnginePicker(mode = c.engineMode, palette = palette, onSelect = viewModel::setEngineMode)

            if (c.engineMode == EngineMode.CLOUD) {
                // ── Cloud provider ───────────────────────────────────────────
                Spacer(Modifier.height(16.dp))
                Label("Provider", palette)
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    CloudProvider.entries.forEachIndexed { index, provider ->
                        RadioRow(
                            provider.name.lowercase().replaceFirstChar { it.uppercase() },
                            c.cloudProvider == provider, palette,
                        ) {
                            viewModel.setCloudProvider(provider, c.cloudModel)
                        }
                        if (index < CloudProvider.entries.size - 1) {
                            HorizontalDivider(color = palette.hair)
                        }
                    }
                }

                val selected = c.cloudProvider
                if (selected != null) {
                    // ── API key field ────────────────────────────────────────
                    Spacer(Modifier.height(12.dp))
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        ApiKeyField(
                            provider = selected,
                            currentMasked = viewModel.apiKeyFor(selected)?.let { if (it.isBlank()) null else "••••••••" },
                            onSet = { viewModel.setApiKey(selected, it) },
                            palette = palette,
                        )
                        Spacer(Modifier.height(8.dp))
                        val hasKey = (viewModel.apiKeyFor(selected)?.isNotBlank() == true)
                        GlassButton(
                            text = "Validate",
                            onClick = { viewModel.validateApiKey() },
                            enabled = hasKey,
                            modifier = Modifier,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (keyValidation != KeyValidation.IDLE) {
                            Text(
                                when (keyValidation) {
                                    KeyValidation.IDLE -> ""
                                    KeyValidation.CHECKING -> "Checking…"
                                    KeyValidation.VALID -> "Key valid ✓"
                                    KeyValidation.INVALID -> "Key invalid"
                                },
                                color = when (keyValidation) {
                                    KeyValidation.VALID -> palette.positive
                                    KeyValidation.INVALID -> palette.negative
                                    else -> palette.muted
                                },
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingRow("Model", c.cloudModel ?: "default", palette) {
                        // Model text entry handled inline; left as a tap target for a future picker.
                    }
                    SettingRow(
                        "Cloud consent",
                        if (c.cloudConsentAt != null) "Granted" else "Required",
                        palette,
                        valueColor = palette.accent,
                        chevron = true,
                        onClick = onConsent,
                    )
                }
            }

            // ── Privacy ──────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("Privacy", palette)
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow("Redact PII before cloud calls", c.redactionEnabled, palette) { viewModel.setRedaction(it) }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Redaction masks account and phone numbers. Amount and merchant still leave the device when using cloud.",
                    color = palette.muted,
                    fontSize = 11.sp,
                )
            }

            // ── Trust ────────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("Trust", palette)
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow("Always review before posting", c.alwaysReview, palette) { viewModel.setAlwaysReview(it) }
                if (!c.alwaysReview) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Auto-post confidence",
                            color = palette.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(c.autoPostThreshold * 100).toInt()}%",
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    MidnightSlider(
                        value = c.autoPostThreshold.toFloat(),
                        onValueChange = { viewModel.setAutoPostThreshold(it.toDouble()) },
                        valueRange = 0.5f..0.99f,
                    )
                    if (c.autoPostThreshold != DEFAULT_AUTO_POST_THRESHOLD) {
                        Text(
                            "Reset to default",
                            color = palette.accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { viewModel.resetThresholdToDefault() }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }

            // ── Senders ──────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("Senders", palette)
            if (unmapped.isNotEmpty()) {
                NewSenderPrompt(
                    count = unmapped.size,
                    firstName = unmapped.first().displayName,
                    palette = palette,
                )
            }
            if (senders.isNotEmpty()) {
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    senders.forEachIndexed { index, sender ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sender.displayName, color = palette.onBackground)
                                val mappedName = sender.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name }
                                if (mappedName != null) {
                                    Text(mappedName, color = palette.muted, fontSize = 11.sp)
                                } else {
                                    // "new sender detected" → inline "map this sender" affordance.
                                    Row {
                                        accounts.take(3).forEach { acct ->
                                            Text(
                                                "Map → ${acct.name}",
                                                color = palette.accent, fontSize = 11.sp,
                                                modifier = Modifier
                                                    .padding(end = 10.dp)
                                                    .clickable { viewModel.setSenderAccount(sender.senderId, acct.id) },
                                            )
                                        }
                                        if (accounts.isEmpty()) {
                                            Text("No account", color = palette.muted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            HToggle(
                                checked = sender.isFinancial,
                                onCheckedChange = { viewModel.setSenderEnabled(sender.senderId, it) },
                            )
                        }
                        if (index < senders.size - 1) {
                            HorizontalDivider(color = palette.hair)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            var showAddSender by remember { mutableStateOf(false) }
            TextButton(onClick = { showAddSender = true }) { Text("Add a sender", color = palette.accent) }
            if (showAddSender) {
                AddSenderInline(
                    palette = palette,
                    onAdd = { id, name -> viewModel.addSender(id, name, BankType.BANK); showAddSender = false },
                    onCancel = { showAddSender = false },
                )
            }

            // ── History ──────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("History", palette)
            GlassButton(
                text = "Import last 90 days",
                onClick = { viewModel.backfillLast90Days() },
                enabled = c.captureEnabled,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Public for Task 13 Compose UI tests — renders the real engine-picker rows. */
@Composable
fun EnginePicker(
    mode: EngineMode,
    palette: HisaabColors.Palette,
    onSelect: (EngineMode) -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(EngineMode.ON_DEVICE) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
        ) {
            HRadio(selected = mode == EngineMode.ON_DEVICE, onClick = { onSelect(EngineMode.ON_DEVICE) })
            Text("On-device (private, offline)", color = palette.onBackground, modifier = Modifier.weight(1f))
            if (mode == EngineMode.ON_DEVICE) Text("✓", color = palette.accent)
        }
        HorizontalDivider(color = palette.hair)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(EngineMode.CLOUD) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
        ) {
            HRadio(selected = mode == EngineMode.CLOUD, onClick = { onSelect(EngineMode.CLOUD) })
            Text("Cloud (your own API key)", color = palette.onBackground, modifier = Modifier.weight(1f))
            if (mode == EngineMode.CLOUD) Text("✓", color = palette.accent)
        }
    }
}

@Composable
private fun IosUnavailableExplainer(palette: HisaabColors.Palette) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "SMS capture is unavailable on iOS",
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "iOS doesn't let apps read your SMS. You'll be able to paste a bank SMS here to log it — coming in a later update.",
            color = palette.muted,
        )
        Spacer(Modifier.height(16.dp))
        // Disabled "coming soon" Paste affordance (paste intake is a later slice).
        Row(
            modifier = Modifier
                .clip(HisaabShapes.pill)
                .background(palette.rule)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Paste a bank SMS (coming soon)", color = palette.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NewSenderPrompt(count: Int, firstName: String, palette: HisaabColors.Palette) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (count == 1) "New sender detected: $firstName" else "$count new senders detected",
            color = palette.onBackground,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Map them to an account below so Hisaab can auto-log them.",
            color = palette.muted,
            fontSize = 11.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(text: String, palette: HisaabColors.Palette) {
    Text(text.uppercase(), color = palette.accent, letterSpacing = 2.sp, fontSize = 11.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, palette: HisaabColors.Palette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        HToggle(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, palette: HisaabColors.Palette, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
    ) {
        HRadio(selected = selected, onClick = onSelect)
        Text(label, color = palette.onBackground, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = palette.accent)
    }
}

@Composable
private fun ApiKeyField(
    provider: CloudProvider,
    currentMasked: String?,
    onSet: (String) -> Unit,
    palette: HisaabColors.Palette,
) {
    var text by remember { mutableStateOf("") }
    Text(
        "${provider.name.lowercase().replaceFirstChar { it.uppercase() }} API key",
        color = palette.muted,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = TextStyle(color = palette.onBackground, fontSize = 15.sp),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
            .clip(HisaabShapes.field)
            .background(palette.surfaceRaised)
            .border(1.dp, palette.hair, HisaabShapes.field)
            .padding(12.dp),
    )
    if (currentMasked != null && text.isBlank()) {
        Spacer(Modifier.height(4.dp))
        Text("Saved: $currentMasked", color = palette.muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { if (text.isNotBlank()) { onSet(text); text = "" } }) {
        Text("Save key", color = palette.accent)
    }
}

@Composable
private fun AddSenderInline(
    palette: HisaabColors.Palette,
    onAdd: (senderId: String, displayName: String) -> Unit,
    onCancel: () -> Unit,
) {
    var senderId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = senderId, onValueChange = { senderId = it },
            textStyle = TextStyle(color = palette.onBackground, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
                .clip(HisaabShapes.field)
                .background(palette.surfaceRaised)
                .border(1.dp, palette.hair, HisaabShapes.field)
                .padding(12.dp),
        )
        Text("Sender ID (e.g. BRAC BANK)", color = palette.muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = name, onValueChange = { name = it },
            textStyle = TextStyle(color = palette.onBackground, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
                .clip(HisaabShapes.field)
                .background(palette.surfaceRaised)
                .border(1.dp, palette.hair, HisaabShapes.field)
                .padding(12.dp),
        )
        Text("Display name", color = palette.muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { if (senderId.isNotBlank()) onAdd(senderId.trim(), name.ifBlank { senderId }.trim()) }) {
                Text("Add", color = palette.accent)
            }
            TextButton(onClick = onCancel) { Text("Cancel", color = palette.muted) }
        }
    }
}
