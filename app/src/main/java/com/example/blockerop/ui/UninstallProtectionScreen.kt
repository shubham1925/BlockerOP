package com.example.blockerop.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.receiver.BlockerDeviceAdminReceiver
import com.example.blockerop.receiver.UninstallReadyReceiver
import kotlinx.coroutines.delay

private const val COOLDOWN_MS       = 24 * 60 * 60 * 1_000L   // 24 hours
private const val CONFIRM_PHRASE    = "let me go"              // must type this exactly

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallProtectionScreen(resumeKey: Int = 0, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { BlockerPreferences(context) }

    // Recompose trigger — incremented by in-screen actions; resumeKey covers return-from-settings
    var tick by remember { mutableIntStateOf(0) }

    val isAdminActive  = remember(tick, resumeKey) { isAdminActive(context) }
    val requestedAt    = remember(tick, resumeKey) { prefs.uninstallRequestedAt }
    val cooldownDoneAt = requestedAt + COOLDOWN_MS

    // Live countdown (updates every second while a request is pending)
    var remainingMs by remember { mutableLongStateOf(cooldownDoneAt - System.currentTimeMillis()) }
    LaunchedEffect(requestedAt) {
        while (requestedAt > 0 && remainingMs > 0) {
            delay(1_000)
            remainingMs = cooldownDoneAt - System.currentTimeMillis()
        }
    }

    val cooldownExpired = requestedAt > 0 && remainingMs <= 0

    var showPhraseDialog    by remember { mutableStateOf(false) }
    var showCancelDialog    by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uninstall Protection") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── State card ────────────────────────────────────────────────────
            when {
                !isAdminActive -> DisabledCard(
                    onEnable = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Prevents impulsive uninstall. You can still remove the app after a 24-hour cooldown.")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) { }
                    }
                )

                cooldownExpired -> CooldownExpiredCard(
                    onUninstall = {
                        deactivateAdminAndUninstall(context, prefs)
                        tick++
                    },
                    onCancel = { showCancelDialog = true }
                )

                requestedAt > 0 -> CooldownActiveCard(
                    remainingMs = remainingMs,
                    onCancel = { showCancelDialog = true }
                )

                else -> ProtectedCard(
                    onRequestUninstall = { showPhraseDialog = true }
                )
            }

            // ── How it works ──────────────────────────────────────────────────
            HowItWorksCard()
        }
    }

    // ── Phrase confirmation dialog ────────────────────────────────────────────
    if (showPhraseDialog) {
        PhraseDialog(
            onConfirmed = {
                prefs.uninstallRequestedAt = System.currentTimeMillis()
                remainingMs = COOLDOWN_MS
                scheduleUninstallReadyAlarm(context)
                showPhraseDialog = false
                tick++
            },
            onDismiss = { showPhraseDialog = false }
        )
    }

    // ── Cancel confirmation dialog ────────────────────────────────────────────
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel uninstall request?") },
            text  = { Text("The 24-hour countdown will be reset and you'll need to start over.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelUninstallRequest(context, prefs)
                    showCancelDialog = false
                    tick++
                }) { Text("Yes, cancel", color = Color(0xFFB71C1C)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep waiting") }
            }
        )
    }
}

// ── State cards ───────────────────────────────────────────────────────────────

@Composable
private fun DisabledCard(onEnable: () -> Unit) {
    StatusCard(
        icon = "🔓",
        title = "Protection Off",
        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        body = "Enable to prevent impulsive uninstalls. You'll still be able to remove the app after typing a confirmation phrase and waiting 24 hours."
    ) {
        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Enable Uninstall Protection")
        }
    }
}

@Composable
private fun ProtectedCard(onRequestUninstall: () -> Unit) {
    StatusCard(
        icon = "🔒",
        title = "Protection Active",
        titleColor = Color(0xFF2E7D32),
        body = "BlockerOP cannot be uninstalled directly from Settings. To remove it, use the button below."
    ) {
        OutlinedButton(
            onClick = onRequestUninstall,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
        ) {
            Text("Request Uninstall →")
        }
    }
}

@Composable
private fun CooldownActiveCard(remainingMs: Long, onCancel: () -> Unit) {
    val h = (remainingMs / 3_600_000).toInt()
    val m = ((remainingMs % 3_600_000) / 60_000).toInt()
    val s = ((remainingMs % 60_000) / 1_000).toInt()
    val countdown = "%02d:%02d:%02d".format(h, m, s)

    StatusCard(
        icon = "⏳",
        title = "Cooldown in Progress",
        titleColor = Color(0xFFE65100),
        body = "Your uninstall request was received. Come back when the timer reaches zero."
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x1AE65100), RoundedCornerShape(12.dp))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(countdown, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100), letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel Request", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CooldownExpiredCard(onUninstall: () -> Unit, onCancel: () -> Unit) {
    StatusCard(
        icon = "✅",
        title = "Ready to Uninstall",
        titleColor = Color(0xFF1565C0),
        body = "Your 24-hour waiting period is over. Tap the button below to remove BlockerOP."
    ) {
        Button(
            onClick = onUninstall,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
        ) {
            Text("Uninstall Now")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Changed my mind — keep the app",
                color = Color(0xFF2E7D32), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatusCard(
    icon: String,
    title: String,
    titleColor: Color,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(icon, fontSize = 24.sp)
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = titleColor)
            }
            Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp)
            content()
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How it works", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            listOf(
                "1  Enable protection — grants Device Admin so Android blocks direct uninstall",
                "2  Request uninstall — type the exact confirmation phrase",
                "3  Wait 24 hours — a notification will remind you when time is up",
                "4  Uninstall Now — protection is lifted and the app is removed"
            ).forEach {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp)
            }
        }
    }
}

// ── Phrase dialog ─────────────────────────────────────────────────────────────

@Composable
private fun PhraseDialog(onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val matches = input.trim().equals(CONFIRM_PHRASE, ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("Confirm Uninstall Request",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Text("Type the phrase below exactly to start your 24-hour countdown:",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Phrase to type — shown prominently
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\"$CONFIRM_PHRASE\"",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface)
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Type here…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = input.isNotEmpty() && !matches
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirmed,
                        enabled = matches,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Text("Start Countdown")
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun adminComponent(context: Context) =
    ComponentName(context, BlockerDeviceAdminReceiver::class.java)

fun isAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(adminComponent(context))
}

private fun scheduleUninstallReadyAlarm(context: Context) {
    val pi = PendingIntent.getBroadcast(
        context, UninstallReadyReceiver.REQUEST_CODE,
        Intent(context, UninstallReadyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val fireAt = System.currentTimeMillis() + COOLDOWN_MS
    if (am.canScheduleExactAlarms()) {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
    } else {
        am.set(AlarmManager.RTC_WAKEUP, fireAt, pi)
    }
}

private fun cancelUninstallRequest(context: Context, prefs: BlockerPreferences) {
    prefs.uninstallRequestedAt = 0L
    // Cancel the pending alarm
    val pi = PendingIntent.getBroadcast(
        context, UninstallReadyReceiver.REQUEST_CODE,
        Intent(context, UninstallReadyReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    pi?.let {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
    }
}

private fun deactivateAdminAndUninstall(context: Context, prefs: BlockerPreferences) {
    prefs.uninstallRequestedAt = 0L
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    dpm.removeActiveAdmin(adminComponent(context))
    // Launch system uninstall screen
    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE,
        Uri.parse("package:${context.packageName}")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
