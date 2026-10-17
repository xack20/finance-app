package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.DisposableEffect
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
import app.hisaab.capture.ChannelStatus
import app.hisaab.capture.label
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.HRadio
import app.hisaab.design.components.HToggle
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightSlider
import app.hisaab.design.components.NeoTopBar
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
            startHistoryImport = { container.startHistoryImport() },
            loadApiKey = { key -> container.secureStorage.loadString(key) },
            storeApiKey = { key, value -> container.secureStorage.storeString(key, value) },
            clearApiKey = { key -> container.secureStorage.storeString(key, "") },
            router = container.llmRouter,
            // M3-int Fix 3: master toggle gates the coordinator.
            onStartCapture = { container.startCapture() },
            onStopCapture = { container.stopCapture() },
            // Notification fallback: mirror the flag to the OS-bound listener's gate (Android only).
            onSetNotificationGate = { service.setNotificationListenerEnabled(it) },
            syncStatuses = container.syncStatuses,
            hasNotificationPermission = { service.hasNotificationPermission() },
            requestNotificationPermission = { service.requestNotificationPermission() },
            isBackgroundRunningAllowed = { service.isBackgroundRunningAllowed() },
            openBackgroundRunningSettings = { service.openBackgroundRunningSettings() },
        )
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }
    val cfg by viewModel.config.collectAsState()
    val senders by viewModel.senders.collectAsState()
    val unmapped by viewModel.unmappedSenders.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val keyValidation by viewModel.keyValidation.collectAsState()
    val savedKeys by viewModel.savedKeys.collectAsState()
    val sync by viewModel.syncStatuses.collectAsState()
    val notificationsDenied by viewModel.notificationsDenied.collectAsState()
    val backgroundAllowed by viewModel.backgroundAllowed.collectAsState()
    // The exemption is granted in a system dialog that doesn't recompose us; re-check while the screen is shown.
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.refreshBackgroundAllowed()
            delay(BACKGROUND_RECHECK_MS)
        }
    }
    val permissionDenied by viewModel.permissionDenied.collectAsState()
    // P2 Mac companion: "ready to pair" once signed in + a master secret exists on this device.
    val relayReady = remember { container.secureStorage.loadMasterSecret() != null && container.authRepository.isSignedIn() }
    // Notification fallback: snapshot whether the OS notification-access grant is in place.
    val notifAccess = remember { container.captureService.hasNotificationAccess() }

    Scaffold(
        topBar = { NeoTopBar(title = "Auto-capture", onBack = onBack) },
        containerColor = palette.background,
    ) { padding ->
        val c = cfg ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = HisaabSpacing.gutter),
        ) {
            Spacer(Modifier.height(8.dp))

            // Mac companion — only where the phone can't read SMS itself (iOS); there it's the only automatic
            // SMS path. An Android phone reads its own SMS, so the relay would just duplicate it.
            if (!smsSupported) {
                MacCompanionSection(
                    enabled = c.relayCaptureEnabled,
                    ready = relayReady,
                    palette = palette,
                    onToggle = viewModel::setRelayCaptureEnabled,
                )
            }

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

            // ── In-app email (IMAP) capture ──────────────────────────────────
            Spacer(Modifier.height(24.dp))
            EmailCaptureSection(c.emailCaptureEnabled, palette, viewModel)

            // ── Notification fallback ────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            NotificationSection(
                enabled = c.notificationCaptureEnabled,
                hasAccess = notifAccess,
                palette = palette,
                onToggle = viewModel::setNotificationCaptureEnabled,
                onGrantAccess = { container.captureService.openNotificationAccessSettings() },
            )

            // ── Permission-denied warning ────────────────────────────────────
            if (permissionDenied) {
                Spacer(Modifier.height(8.dp))
                SurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.negative, HisaabShapes.card),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
                    ) {
                        HisaabIcon("warn", tint = palette.negative, size = 18.dp)
                        Text(
                            "SMS permission denied",
                            color = palette.negative,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            modifier = Modifier
                                .clip(HisaabShapes.pill)
                                .background(palette.accent)
                                .clickable { viewModel.setCaptureEnabled(true) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("Grant", color = palette.onAccent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
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
                    // Design order: Claude, OpenAI, Gemini (neo-settings.jsx:151).
                    val providerOrder = listOf(CloudProvider.CLAUDE, CloudProvider.OPENAI, CloudProvider.GEMINI)
                    providerOrder.forEachIndexed { index, provider ->
                        val label = when (provider) {
                            CloudProvider.CLAUDE -> "Claude"
                            CloudProvider.OPENAI -> "OpenAI"
                            CloudProvider.GEMINI -> "Gemini"
                        }
                        RadioRow(label, c.cloudProvider == provider, palette) {
                            viewModel.setCloudProvider(provider, c.cloudModel)
                        }
                        if (index < providerOrder.size - 1) {
                            HorizontalDivider(color = palette.hair2)
                        }
                    }
                }

                val selected = c.cloudProvider
                if (selected != null) {
                    val hasKey = selected in savedKeys
                    // ── API key field ────────────────────────────────────────
                    Spacer(Modifier.height(12.dp))
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        ApiKeyField(
                            provider = selected,
                            currentMasked = if (hasKey) "••••••••" else null,
                            onSet = { viewModel.setApiKey(selected, it) },
                            palette = palette,
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassButton(
                            text = "Validate",
                            onClick = { viewModel.validateApiKey() },
                            enabled = hasKey,
                            modifier = Modifier,
                        )
                        Spacer(Modifier.height(4.dp))
                        val validation = keyValidation
                        if (validation != KeyValidation.Idle) {
                            Text(
                                when (validation) {
                                    KeyValidation.Idle -> ""
                                    KeyValidation.Checking -> "Checking with the provider…"
                                    KeyValidation.Valid -> "Valid key"
                                    is KeyValidation.Invalid -> validation.reason
                                },
                                color = when (validation) {
                                    KeyValidation.Valid -> palette.positive
                                    is KeyValidation.Invalid -> palette.negative
                                    KeyValidation.Idle, KeyValidation.Checking -> palette.muted
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
                        "Review",
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
                    "Masks account and phone numbers. Amount and merchant still leave the device when using cloud.",
                    color = palette.muted,
                    fontSize = 13.sp,
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
                            "${thresholdPercent(c.autoPostThreshold)}%",
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    MidnightSlider(
                        value = c.autoPostThreshold.toFloat(),
                        onValueChange = { viewModel.setAutoPostThreshold(it.toDouble()) },
                        valueRange = AUTO_POST_THRESHOLD_RANGE,
                    )
                    if (c.autoPostThreshold < 0.5) {
                        Text(
                            "Below 50%, more wrong entries get posted without review — check Review and history often.",
                            color = palette.accent,
                            fontSize = 12.sp,
                        )
                    }
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
                                    Text(mappedName, color = palette.muted, fontSize = 12.5.sp)
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
                                            Text("No account", color = palette.muted, fontSize = 12.5.sp)
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

            // ── Sync status ──────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("Sync", palette)
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                SyncStatusLine("SMS", sync.sms, palette)
                SyncStatusLine("Email", sync.email, palette)
                if (!smsSupported) SyncStatusLine("Mac relay", sync.relay, palette)
                if (c.engineMode == EngineMode.CLOUD) SyncStatusLine("Cloud AI", sync.ai, palette)
            }
            if (!backgroundAllowed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Background running isn't allowed — your phone may stop sync when Hisaab is closed. Tap to allow.",
                    color = palette.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { viewModel.allowBackgroundRunning() },
                )
            }
            if (notificationsDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Notifications are off — sync runs but you can't see its status. Tap to allow.",
                    color = palette.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { viewModel.requestNotifications() },
                )
            }

            // ── History ──────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Label("History", palette)
            val smsImporting = sync.sms is ChannelStatus.Importing
            GlassButton(
                text = if (smsImporting) "Importing SMS…" else "Import all SMS",
                onClick = { viewModel.importAllHistory() },
                enabled = c.captureEnabled && !smsImporting,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Email history imports by itself once a mailbox is connected — follow it on the Email line above.",
                color = palette.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

private const val BACKGROUND_RECHECK_MS = 2_000L

@Composable
private fun SyncStatusLine(channel: String, status: ChannelStatus, palette: HisaabColors.Palette) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(channel, color = palette.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            status.label(),
            color = if (status is ChannelStatus.Error) palette.negative else palette.muted,
            fontSize = 13.sp,
        )
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
        EngineOption(
            icon = "shield",
            title = "On-device",
            sub = "Private, offline",
            active = mode == EngineMode.ON_DEVICE,
            palette = palette,
            onClick = { onSelect(EngineMode.ON_DEVICE) },
        )
        HorizontalDivider(color = palette.hair2)
        EngineOption(
            icon = "cloud",
            title = "Cloud",
            sub = "Your own API key",
            active = mode == EngineMode.CLOUD,
            palette = palette,
            onClick = { onSelect(EngineMode.CLOUD) },
        )
    }
}

@Composable
private fun EngineOption(
    icon: String,
    title: String,
    sub: String,
    active: Boolean,
    palette: HisaabColors.Palette,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.lg),
    ) {
        HisaabIcon(name = icon, tint = if (active) palette.accent else palette.faint, size = 22.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = palette.muted, fontSize = 13.sp)
        }
        if (active) HisaabIcon("check", tint = palette.accent, size = 20.dp, strokeWidth = 2.4f)
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
    // Faint eyebrow per .eyebrow token (neo-theme.css:57 → var(--faint)), not lime.
    Text(
        text.uppercase(),
        color = palette.faint,
        letterSpacing = 1.54.sp,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(HisaabSpacing.md))
}

/** EP3: in-app IMAP email capture (Android). Connect a consumer mailbox or toggle off when on. */
@Composable
private fun EmailCaptureSection(enabled: Boolean, palette: HisaabColors.Palette, viewModel: AutoCaptureViewModel) {
    Label("Email", palette)
    if (enabled) {
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            ToggleRow("Capture transactions from email", true, palette) { viewModel.setEmailCaptureEnabled(false) }
            Text("Connected — bank emails sync alongside SMS.", color = palette.muted, fontSize = 12.sp)
        }
        return
    }
    var email by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Capture bank emails — personal Gmail / Outlook.com / Yahoo / iCloud, using an app-specific password.",
            color = palette.muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("Email address", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = email,
            onValueChange = { email = it },
            textStyle = TextStyle(color = palette.onBackground, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth()
                .clip(HisaabShapes.field).background(palette.surfaceRaised).border(1.dp, palette.hair, HisaabShapes.field).padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("App-specific password", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = appPassword,
            onValueChange = { appPassword = it },
            textStyle = TextStyle(color = palette.onBackground, fontSize = 15.sp),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
                .clip(HisaabShapes.field).background(palette.surfaceRaised).border(1.dp, palette.hair, HisaabShapes.field).padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            if (email.isNotBlank() && appPassword.isNotBlank()) {
                viewModel.configureEmail(email, appPassword)
                email = ""
                appPassword = ""
            }
        }) {
            Text("Connect email", color = palette.accent)
        }
    }
}

/**
 * Notification fallback (Android): toggle for the NotificationListener path plus the one-time
 * "grant notification access" flow. The OS won't deliver notifications to our listener until the
 * user grants access in system settings, so we surface that gap and deep-link them there.
 */
@Composable
private fun NotificationSection(
    enabled: Boolean,
    hasAccess: Boolean,
    palette: HisaabColors.Palette,
    onToggle: (Boolean) -> Unit,
    onGrantAccess: () -> Unit,
) {
    Label("Notifications", palette)
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        ToggleRow("Capture transactions from notifications", enabled, palette, onToggle)
        Spacer(Modifier.height(6.dp))
        Text(
            "A fallback for banks that push app notifications instead of SMS. Needs one-time " +
                "notification access.",
            color = palette.muted,
            fontSize = 12.sp,
        )
        if (enabled && !hasAccess) {
            Spacer(Modifier.height(8.dp))
            Text("Notification access not granted yet.", color = palette.accent, fontSize = 12.sp)
            TextButton(onClick = onGrantAccess) {
                Text("Grant notification access", color = palette.accent)
            }
        } else if (enabled) {
            Spacer(Modifier.height(8.dp))
            Text("Access granted — notifications sync alongside SMS.", color = palette.muted, fontSize = 12.sp)
        }
    }
}

/** P2: toggle + status for the Mac→phone SMS relay. Shown only where the phone can't read SMS (iOS). */
@Composable
private fun MacCompanionSection(
    enabled: Boolean,
    ready: Boolean,
    palette: HisaabColors.Palette,
    onToggle: (Boolean) -> Unit,
) {
    Label("Mac companion", palette)
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        ToggleRow("Capture SMS forwarded to your Mac", enabled, palette, onToggle)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (ready) {
            "Ready to pair. Install the Hisaab Mac app, enter your recovery phrase, and turn on " +
                "Text Message Forwarding to this Mac — bank SMS will then sync here automatically."
        } else {
            "Sign in and finish onboarding first. Then the Hisaab Mac app can forward your bank " +
                "SMS here securely (end-to-end encrypted)."
        },
        color = palette.muted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, palette: HisaabColors.Palette, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Design: toggle-row labels are text/16/500 (neo-settings.jsx:166,172), not muted.
        Text(label, color = palette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        HToggle(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, palette: HisaabColors.Palette, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
    ) {
        // No trailing check — the radio fill conveys selection (neo-settings.jsx:151).
        HRadio(selected = selected, onClick = onSelect)
        Text(label, color = palette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
