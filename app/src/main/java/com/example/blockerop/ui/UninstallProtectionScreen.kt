package com.example.blockerop.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.blockerop.ui.theme.*
import kotlinx.coroutines.delay

private const val COOLDOWN_MS    = 24 * 60 * 60 * 1_000L
private const val CONFIRM_PHRASE = "let me go"

@Composable
fun UninstallProtectionScreen(resumeKey: Int = 0, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { BlockerPreferences(context) }

    var tick by remember { mutableIntStateOf(0) }

    val isAdminActive  = remember(tick, resumeKey) { isAdminActive(context) }
    val requestedAt    = remember(tick, resumeKey) { prefs.uninstallRequestedAt }
    val cooldownDoneAt = requestedAt + COOLDOWN_MS

    var remainingMs by remember { mutableLongStateOf(cooldownDoneAt - System.currentTimeMillis()) }
    LaunchedEffect(requestedAt) {
        while (requestedAt > 0 && remainingMs > 0) {
            delay(1_000)
            remainingMs = cooldownDoneAt - System.currentTimeMillis()
        }
    }

    val cooldownExpired = requestedAt > 0 && remainingMs <= 0

    var showPhraseDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = BgDeep) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .padding(top = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextMid)
                }
                Text(
                    "Uninstall Protection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextHigh,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        onUninstall = { deactivateAdminAndUninstall(context, prefs); tick++ },
                        onCancel    = { showCancelDialog = true }
                    )
                    requestedAt > 0 -> CooldownActiveCard(
                        remainingMs = remainingMs,
                        onCancel    = { showCancelDialog = true }
                    )
                    else -> ProtectedCard(onRequestUninstall = { showPhraseDialog = true })
                }

                HowItWorksCard()
            }
        }
    }

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

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor   = BgElevated,
            title  = { Text("Cancel uninstall request?", color = TextHigh, fontWeight = FontWeight.Bold) },
            text   = { Text("The 24-hour countdown will reset.", color = TextMid, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    cancelUninstallRequest(context, prefs)
                    showCancelDialog = false
                    tick++
                }) { Text("Yes, cancel", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep waiting", color = TextMid)
                }
            }
        )
    }
}

// ── State cards ───────────────────────────────────────────────────────────────

@Composable
private fun DisabledCard(onEnable: () -> Unit) {
    StatusCard(
        emoji = "🔓",
        title = "Protection Off",
        titleColor = TextMid,
        body = "Enable to prevent impulsive uninstalls. You'll still be able to remove the app after typing a confirmation phrase and waiting 24 hours."
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Indigo, Color(0xFF8B5CF6))))
                .clickable(onClick = onEnable),
            contentAlignment = Alignment.Center
        ) {
            Text("Enable Protection", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProtectedCard(onRequestUninstall: () -> Unit) {
    StatusCard(
        emoji = "🔒",
        title = "Protection Active",
        titleColor = Emerald,
        body = "BlockerOP cannot be uninstalled directly from Settings. To remove it, use the button below to start the 24-hour cooldown."
    ) {
        OutlinedButton(
            onClick = onRequestUninstall,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Rose.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose)
        ) {
            Text("Request Uninstall →", fontWeight = FontWeight.Medium)
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
        emoji = "⏳",
        title = "Cooldown in Progress",
        titleColor = Amber,
        body = "Your uninstall request is pending. Come back when the timer reaches zero."
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AmberDim)
                .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                countdown,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Amber,
                letterSpacing = 2.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel Request", color = TextMid, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CooldownExpiredCard(onUninstall: () -> Unit, onCancel: () -> Unit) {
    StatusCard(
        emoji = "✅",
        title = "Ready to Uninstall",
        titleColor = Indigo,
        body = "Your 24-hour waiting period is over. Tap below to remove BlockerOP."
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RoseDim)
                .border(1.dp, Rose.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable(onClick = onUninstall),
            contentAlignment = Alignment.Center
        ) {
            Text("Uninstall Now", color = Rose, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Changed my mind — keep the app",
                color = Emerald,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StatusCard(
    emoji: String,
    title: String,
    titleColor: Color,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(emoji, fontSize = 28.sp)
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = titleColor)
            }
            Text(body, fontSize = 14.sp, color = TextMid, lineHeight = 22.sp)
            content()
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How it works", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMid)
            listOf(
                "1  Enable protection — grants Device Admin so Android blocks direct uninstall",
                "2  Request uninstall — type the exact confirmation phrase",
                "3  Wait 24 hours — a notification will remind you when time is up",
                "4  Uninstall Now — protection is lifted and the app is removed"
            ).forEach {
                Text(it, fontSize = 13.sp, color = TextLow, lineHeight = 20.sp)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Confirm Uninstall Request", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextHigh)
                Text("Type the phrase below exactly to start your 24-hour countdown:", fontSize = 14.sp, color = TextMid, lineHeight = 22.sp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgInput)
                        .border(1.dp, BorderMid, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\"$CONFIRM_PHRASE\"", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextHigh)
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Type here…", color = TextLow) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = input.isNotEmpty() && !matches,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = if (matches) Emerald else Indigo,
                        unfocusedBorderColor = BorderMid,
                        errorBorderColor     = Rose,
                        focusedTextColor     = TextHigh,
                        unfocusedTextColor   = TextHigh,
                        cursorColor          = Indigo,
                        unfocusedContainerColor = BgInput,
                        focusedContainerColor   = BgInput,
                        errorContainerColor     = RoseDim
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderMid),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMid)
                    ) { Text("Cancel") }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (matches) RoseDim else BgInput)
                            .border(1.dp, if (matches) Rose.copy(alpha = 0.5f) else BorderMid, RoundedCornerShape(12.dp))
                            .clickable(enabled = matches, onClick = onConfirmed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Start Countdown",
                            color = if (matches) Rose else TextLow,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
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
    if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
    else am.set(AlarmManager.RTC_WAKEUP, fireAt, pi)
}

private fun cancelUninstallRequest(context: Context, prefs: BlockerPreferences) {
    prefs.uninstallRequestedAt = 0L
    val pi = PendingIntent.getBroadcast(
        context, UninstallReadyReceiver.REQUEST_CODE,
        Intent(context, UninstallReadyReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    pi?.let { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it) }
}

private fun deactivateAdminAndUninstall(context: Context, prefs: BlockerPreferences) {
    prefs.uninstallRequestedAt = 0L
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    dpm.removeActiveAdmin(adminComponent(context))
    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${context.packageName}")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
