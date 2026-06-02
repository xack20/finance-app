package app.hisaab.mac

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Root UI: renders the current [Stage] of the [CompanionController] plus the setup checklist. */
@Composable
fun CompanionApp(controller: CompanionController) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Hisaab Companion", style = MaterialTheme.typography.headlineSmall)
        when (val stage = controller.stage) {
            Stage.NeedsConfig -> NeedsConfigStep()
            Stage.SignedOut -> SignInStep(controller)
            is Stage.AwaitingOtp -> OtpStep(controller, stage.phone)
            Stage.NeedsPairing -> PairStep(controller)
            Stage.Running -> RunningStep(controller)
        }
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Divider()
        SetupChecklist()
    }
}

@Composable
private fun NeedsConfigStep() {
    Text("Set your Supabase project before signing in:")
    Text(
        "Set HISAAB_SUPABASE_URL and HISAAB_SUPABASE_ANON_KEY, or create " +
            "~/.hisaab/companion.properties with supabase.url and supabase.anon_key. Then relaunch.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SignInStep(controller: CompanionController) {
    var phone by remember { mutableStateOf("") }
    Text("Sign in with the same phone number as your Hisaab app.")
    OutlinedTextField(phone, { phone = it }, label = { Text("Phone (e.g. +8801…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(onClick = { controller.sendOtp(phone) }, enabled = !controller.busy && phone.isNotBlank()) { Text("Send code") }
}

@Composable
private fun OtpStep(controller: CompanionController, phone: String) {
    var code by remember { mutableStateOf("") }
    Text("Enter the code sent to $phone.")
    OutlinedTextField(code, { code = it }, label = { Text("6-digit code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(onClick = { controller.verifyOtp(code) }, enabled = !controller.busy && code.isNotBlank()) { Text("Verify") }
}

@Composable
private fun PairStep(controller: CompanionController) {
    var phrase by remember { mutableStateOf("") }
    Text("Enter your 24-word recovery phrase to pair encryption (it is never stored).")
    OutlinedTextField(phrase, { phrase = it }, label = { Text("Recovery phrase") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { controller.pair(phrase) }, enabled = !controller.busy && phrase.isNotBlank()) { Text("Pair & start") }
}

@Composable
private fun RunningStep(controller: CompanionController) {
    Text("✓ Running — forwarding bank SMS to your phone.", style = MaterialTheme.typography.titleMedium)
    controller.lastSyncNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text("Total items synced this session: ${controller.totalPushed}", style = MaterialTheme.typography.bodySmall)
    EmailSection(controller)
    Divider()
    OutlinedButton(onClick = { controller.signOut() }, enabled = !controller.busy) {
        Text("Sign out / switch account")
    }
}

/**
 * Optional email (IMAP) capture — connect a consumer mailbox; bank emails then sync alongside SMS.
 * The form stays visible even when connected so a rejected app-password can be re-entered.
 */
@Composable
private fun EmailSection(controller: CompanionController) {
    Divider()
    if (controller.emailConnected) {
        Text("✓ Email connected — bank emails are syncing too.", style = MaterialTheme.typography.bodyMedium)
        Text("Wrong password or changing mailbox? Re-enter below.", style = MaterialTheme.typography.bodySmall)
    } else {
        Text("Also capture bank emails (optional — personal Gmail / Outlook.com / Yahoo / iCloud).", style = MaterialTheme.typography.bodySmall)
    }
    var email by remember { mutableStateOf(controller.connectedEmail.orEmpty()) }
    var appPassword by remember { mutableStateOf("") }
    OutlinedTextField(email, { email = it }, label = { Text("Email address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(appPassword, { appPassword = it }, label = { Text("App-specific password (spaces are fine)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(
        onClick = { controller.configureEmail(email, appPassword) },
        enabled = !controller.busy && email.isNotBlank() && appPassword.isNotBlank(),
    ) { Text(if (controller.emailConnected) "Update email login" else "Connect email") }
}

@Composable
private fun SetupChecklist() {
    Text("One-time setup", style = MaterialTheme.typography.titleSmall)
    Text("1. Grant Full Disk Access to this app, then relaunch.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { SystemSettings.openFullDiskAccess() }) { Text("Open Full Disk Access settings") }
    Text(
        "2. On your iPhone: Settings → Messages → Text Message Forwarding → enable this Mac.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = { SystemSettings.openMessages() }) { Text("Open Messages") }
}
