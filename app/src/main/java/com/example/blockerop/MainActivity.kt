package com.example.blockerop

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.data.EventLogger
import com.example.blockerop.data.StreakManager
import com.example.blockerop.scheduler.BlockSchedule
import com.example.blockerop.scheduler.WeeklyReportScheduler
import com.example.blockerop.service.BlockerForegroundService
import com.example.blockerop.service.GuardJobService
import com.example.blockerop.ui.AnalyticsScreen
import com.example.blockerop.ui.ManageAppsScreen
import com.example.blockerop.ui.UninstallProtectionScreen
import com.example.blockerop.ui.theme.BlockerOPTheme

class MainActivity : ComponentActivity() {

    // Incremented on every onResume so composables that use this as a key
    // re-evaluate permission checks when the user returns from system settings.
    private var resumeKey by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore analytics from external backup if internal data was wiped
        // (e.g. fresh install over a previous version, or after a reinstall).
        EventLogger.restoreFromBackupIfNeeded(this)
        enableEdgeToEdge()
        setContent {
            BlockerOPTheme {
                BlockerApp(resumeKey)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeKey++
        // Update streak once per day
        val prefs = BlockerPreferences(this)
        if (prefs.isSetupComplete) {
            StreakManager.refresh(prefs, EventLogger.readAll(this))
        }
    }
}

// ── Permission checks ─────────────────────────────────────────────────────────

fun hasOverlayPermission(context: Context) = Settings.canDrawOverlays(context)

fun hasAccessibilityPermission(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val id = "${context.packageName}/.service.BlockerAccessibilityService"
    return enabled.any { it.id == id }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = ops.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun allPermissionsGranted(context: Context) =
    hasOverlayPermission(context) &&
            hasAccessibilityPermission(context) &&
            hasUsageStatsPermission(context)

// ── Top-level composable ──────────────────────────────────────────────────────

@Composable
fun BlockerApp(resumeKey: Int = 0) {
    val context = LocalContext.current
    val prefs = remember { BlockerPreferences(context) }
    var showAnalytics   by remember { mutableStateOf(false) }
    var showManageApps  by remember { mutableStateOf(false) }
    var showUninstall   by remember { mutableStateOf(false) }

    val allGranted = remember(resumeKey) { allPermissionsGranted(context) }

    when {
        showAnalytics -> {
            AnalyticsScreen(context, onBack = { showAnalytics = false })
        }
        showManageApps -> {
            ManageAppsScreen(onBack = { showManageApps = false })
        }
        showUninstall -> {
            UninstallProtectionScreen(resumeKey = resumeKey, onBack = { showUninstall = false })
        }
        prefs.isSetupComplete && allGranted -> {
            StatusScreen(
                context          = context,
                resumeKey        = resumeKey,
                onShowAnalytics  = { showAnalytics  = true },
                onManageApps     = { showManageApps = true },
                onUninstall      = { showUninstall  = true }
            )
        }
        else -> {
            SetupWizard(
                context = context,
                resumeKey = resumeKey,
                onSetupComplete = {
                    prefs.isSetupComplete = true
                    BlockerForegroundService.start(context)
                    GuardJobService.schedule(context)
                    WeeklyReportScheduler.schedule(context)
                }
            )
        }
    }
}

// ── Setup wizard ──────────────────────────────────────────────────────────────

@Composable
fun SetupWizard(context: Context, resumeKey: Int = 0, onSetupComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    val steps = listOf(
        SetupStep(
            title = "Welcome to BlockerOP",
            description = "This app blocks Instagram and Facebook outside your chosen access window. You decide the hours — once set up, it enforces them automatically.\n\nTo do this, it needs three special permissions. The next screens will guide you through each one.",
            buttonLabel = "Get Started",
            isGranted = { true },
            grant = {}
        ),
        SetupStep(
            title = "Draw Over Other Apps",
            description = "BlockerOP needs to draw a full-screen overlay on top of blocked apps. Tap the button below, find BlockerOP in the list, and turn the toggle on.",
            buttonLabel = "Open Settings",
            isGranted = { hasOverlayPermission(context) },
            grant = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        ),
        SetupStep(
            title = "Accessibility Service",
            description = "BlockerOP uses Android's Accessibility API to detect when a blocked app comes to the foreground. Tap the button, find BlockerOP under Installed Apps, and enable it.",
            buttonLabel = "Open Accessibility Settings",
            isGranted = { hasAccessibilityPermission(context) },
            grant = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        ),
        SetupStep(
            title = "Usage Access",
            description = "As a backup, BlockerOP polls which app is in the foreground using Usage Stats. Tap the button, find BlockerOP, and enable Permit Usage Access.",
            buttonLabel = "Open Usage Access Settings",
            isGranted = { hasUsageStatsPermission(context) },
            grant = {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        )
    )

    val currentStep = steps[step]
    // Re-evaluated whenever the user returns from system settings (resumeKey changes)
    val granted = remember(step, resumeKey) { currentStep.isGranted() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            Text(
                text = "Step ${step + 1} of ${steps.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = currentStep.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Text(
                text = currentStep.description,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // Status badge
            if (step > 0) {
                StatusBadge(granted = granted)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Grant button (only for permission steps, and only when not granted)
            if (step > 0 && !granted) {
                Button(
                    onClick = currentStep.grant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(currentStep.buttonLabel)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Next / Finish button
            val isLastStep = step == steps.lastIndex
            val canProceed = step == 0 || granted

            Button(
                onClick = {
                    if (isLastStep) {
                        onSetupComplete()
                    } else {
                        step++
                    }
                },
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastStep) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isLastStep) "Start Blocking" else "Next")
            }
        }
    }
}

@Composable
private fun StatusBadge(granted: Boolean) {
    val (bg, text) = if (granted)
        Color(0xFF2E7D32) to "Granted"
    else
        Color(0xFFB71C1C) to "Not Granted"

    Box(
        modifier = Modifier
            .background(bg, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ── Status screen ─────────────────────────────────────────────────────────────

@Composable
fun StatusScreen(
    context: Context,
    resumeKey: Int = 0,
    onShowAnalytics: () -> Unit = {},
    onManageApps: () -> Unit = {},
    onUninstall: () -> Unit = {}
) {
    val prefs = remember { BlockerPreferences(context) }
    var showDialog by remember { mutableStateOf(false) }

    var windowLabel by remember(resumeKey) { mutableStateOf(buildWindowLabel(prefs)) }
    var streak      by remember(resumeKey) { mutableIntStateOf(prefs.streakDays) }
    val allOk = remember(resumeKey) { allPermissionsGranted(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (allOk) "Blocking Active" else "Action Required",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (allOk) Color(0xFF2E7D32) else Color(0xFFB71C1C)
            )

            Spacer(Modifier.height(8.dp))

            // Streak badge
            if (streak > 0) {
                Text(
                    text = "🔥 $streak day${if (streak == 1) "" else "s"} clean",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE65100)
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = "Access window: $windowLabel",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Configure Slots")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onManageApps,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Manage Blocked Apps")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShowAnalytics,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Analytics")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onUninstall,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Uninstall Protection")
            }

            if (!allOk) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        prefs.isSetupComplete = false
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Re-run Setup", color = Color(0xFFB71C1C))
                }
            }
        }
    }

    if (showDialog) {
        ConfigureSlotsDialog(
            prefs = prefs,
            onDismiss = { showDialog = false },
            onSaved = { windowLabel = buildWindowLabel(prefs) }
        )
    }
}

private fun buildWindowLabel(prefs: BlockerPreferences) =
    "${BlockSchedule.formatMinutes(prefs.allowStartMinutes)} – ${BlockSchedule.formatMinutes(prefs.allowEndMinutes)}"

// ── Configure slots dialog ────────────────────────────────────────────────────

/** 48 slots of 30 min each, from 12:00 AM (index 0) to 11:30 PM (index 47). */
private fun buildTimeSlots(): Array<String> = Array(48) { i ->
    BlockSchedule.formatMinutes(i * 30)
}

@Composable
fun ConfigureSlotsDialog(
    prefs: BlockerPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val timeSlots = remember { buildTimeSlots() }

    // Indices into timeSlots (each index = 30 min)
    var startIdx by remember { mutableIntStateOf(prefs.allowStartMinutes / 30) }
    var endIdx   by remember { mutableIntStateOf(prefs.allowEndMinutes   / 30) }

    val durationMinutes = (endIdx - startIdx) * 30
    val isValid = endIdx > startIdx && durationMinutes <= 120

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Configure Slots",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Max window: 2 hours",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Start", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp))
                        AndroidView(
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 0
                                    maxValue = 46   // max start = 11:00 PM (leaves room for ≥30 min)
                                    displayedValues = timeSlots.copyOfRange(0, 47)
                                    wrapSelectorWheel = false
                                    value = startIdx
                                    setOnValueChangedListener { _, _, new -> startIdx = new }
                                }
                            }
                        )
                    }

                    Text("to", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // End picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("End", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp))
                        AndroidView(
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 1    // earliest end = 12:30 AM
                                    maxValue = 47   // latest end = 11:30 PM
                                    displayedValues = timeSlots.copyOfRange(1, 48)
                                    wrapSelectorWheel = false
                                    value = endIdx
                                    setOnValueChangedListener { _, _, new -> endIdx = new }
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Validation / duration feedback
                if (isValid) {
                    val h = durationMinutes / 60
                    val m = durationMinutes % 60
                    val label = when {
                        h > 0 && m > 0 -> "${h}h ${m}m"
                        h > 0          -> "${h}h"
                        else           -> "${m}m"
                    }
                    Text("Duration: $label", fontSize = 13.sp, color = Color(0xFF2E7D32))
                } else {
                    val errorMsg = when {
                        endIdx <= startIdx -> "End time must be after start time"
                        else               -> "Maximum window is 2 hours"
                    }
                    Text(errorMsg, fontSize = 13.sp, color = Color(0xFFB71C1C))
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Cancel") }

                    Button(
                        onClick = {
                            prefs.allowStartMinutes = startIdx * 30
                            prefs.allowEndMinutes   = endIdx   * 30
                            onSaved()
                            onDismiss()
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Save") }
                }
            }
        }
    }
}

// ── Data ──────────────────────────────────────────────────────────────────────

private data class SetupStep(
    val title: String,
    val description: String,
    val buttonLabel: String,
    val isGranted: () -> Boolean,
    val grant: () -> Unit
)
