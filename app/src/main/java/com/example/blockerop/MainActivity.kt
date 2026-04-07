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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.blockerop.ui.theme.*

class MainActivity : ComponentActivity() {

    private var resumeKey by mutableStateOf(0)
    private var openUninstall by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUninstall = intent.getBooleanExtra(EXTRA_SHOW_UNINSTALL, false)
        EventLogger.restoreFromBackupIfNeeded(this)
        enableEdgeToEdge()
        setContent {
            BlockerOPTheme {
                BlockerApp(resumeKey, openUninstall)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_UNINSTALL, false)) {
            openUninstall = true
        }
    }

    override fun onResume() {
        super.onResume()
        resumeKey++
        val prefs = BlockerPreferences(this)
        if (prefs.isSetupComplete) {
            StreakManager.refresh(prefs, EventLogger.readAll(this))
        }
    }

    companion object {
        const val EXTRA_SHOW_UNINSTALL = "show_uninstall"
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
fun BlockerApp(resumeKey: Int = 0, openUninstall: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { BlockerPreferences(context) }
    var showAnalytics  by remember { mutableStateOf(false) }
    var showManageApps by remember { mutableStateOf(false) }
    var showUninstall  by remember { mutableStateOf(openUninstall) }

    val allGranted = remember(resumeKey) { allPermissionsGranted(context) }

    when {
        showAnalytics -> AnalyticsScreen(context, onBack = { showAnalytics = false })
        showManageApps -> ManageAppsScreen(onBack = { showManageApps = false })
        showUninstall -> UninstallProtectionScreen(resumeKey = resumeKey, onBack = { showUninstall = false })
        prefs.isSetupComplete && allGranted -> {
            StatusScreen(
                context         = context,
                resumeKey       = resumeKey,
                onShowAnalytics = { showAnalytics  = true },
                onManageApps    = { showManageApps = true },
                onUninstall     = { showUninstall  = true }
            )
        }
        else -> {
            SetupWizard(
                context  = context,
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

private data class SetupStep(
    val emoji: String,
    val title: String,
    val description: String,
    val buttonLabel: String,
    val isGranted: () -> Boolean,
    val grant: () -> Unit
)

@Composable
fun SetupWizard(context: Context, resumeKey: Int = 0, onSetupComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    val steps = listOf(
        SetupStep(
            emoji = "🛡️",
            title = "Welcome to BlockerOP",
            description = "This app blocks Instagram and Facebook outside your chosen access window. You decide the hours — once set, it enforces them automatically.\n\nThree permissions are needed. The next screens walk you through each one.",
            buttonLabel = "Get Started",
            isGranted = { true },
            grant = {}
        ),
        SetupStep(
            emoji = "📱",
            title = "Draw Over Other Apps",
            description = "BlockerOP needs to show a full-screen overlay on top of blocked apps. Tap the button below, find BlockerOP in the list, and turn the toggle on.",
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
            emoji = "👁️",
            title = "Accessibility Service",
            description = "BlockerOP uses Android's Accessibility API to detect when a blocked app comes to the foreground. Find BlockerOP under Installed Apps and enable it.",
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
            emoji = "📊",
            title = "Usage Access",
            description = "As a backup, BlockerOP polls the foreground app using Usage Stats. Find BlockerOP in the list and enable Permit Usage Access.",
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
    val granted = remember(step, resumeKey) { currentStep.isGranted() }
    val isLastStep = step == steps.lastIndex
    val canProceed = step == 0 || granted

    Surface(modifier = Modifier.fillMaxSize(), color = BgDeep) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == step) Indigo else BorderMid)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Step icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(IndigoDim)
                    .border(1.dp, BorderMid, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(currentStep.emoji, fontSize = 40.sp)
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = currentStep.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextHigh,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = currentStep.description,
                fontSize = 15.sp,
                color = TextMid,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(Modifier.weight(1f))

            // Status chip (permission steps only)
            if (step > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (granted) EmeraldDim else RoseDim)
                        .border(1.dp, if (granted) Emerald.copy(alpha = 0.4f) else Rose.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (granted) Emerald else Rose, CircleShape)
                        )
                        Text(
                            if (granted) "Permission granted" else "Not granted yet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (granted) Emerald else Rose
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Grant button
            if (step > 0 && !granted) {
                OutlinedButton(
                    onClick = currentStep.grant,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderMid),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHigh)
                ) {
                    Text(currentStep.buttonLabel, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Primary CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canProceed)
                            Brush.linearGradient(listOf(Indigo, Color(0xFF8B5CF6)))
                        else
                            Brush.linearGradient(listOf(BorderMid, BorderMid))
                    )
                    .clickable(enabled = canProceed) {
                        if (isLastStep) onSetupComplete() else step++
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLastStep) "Start Blocking" else "Continue",
                    color = if (canProceed) Color.White else TextLow,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
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
    var showDialog   by remember { mutableStateOf(false) }
    var windowLabel  by remember(resumeKey) { mutableStateOf(buildWindowLabel(prefs)) }
    var streak       by remember(resumeKey) { mutableIntStateOf(prefs.streakDays) }
    val allOk        = remember(resumeKey) { allPermissionsGranted(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = BgDeep) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── App header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BlockerOP", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextHigh)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (allOk) EmeraldDim else RoseDim)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (allOk) Emerald else Rose, CircleShape)
                        )
                        Text(
                            if (allOk) "Active" else "Issues",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allOk) Emerald else Rose
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Hero card ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (allOk)
                                listOf(Color(0xFF1B1F4F), Color(0xFF0F1728))
                            else
                                listOf(Color(0xFF3A1A1A), Color(0xFF0F1728))
                        )
                    )
                    .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (streak > 0) "🔥 $streak day${if (streak == 1) "" else "s"} clean" else "Start your streak",
                        fontSize = if (streak > 0) 34.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHigh
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TextLow,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Access window  $windowLabel",
                            fontSize = 13.sp,
                            color = TextMid
                        )
                    }
                    if (!allOk) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "One or more permissions are missing.",
                            fontSize = 13.sp,
                            color = Rose
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Action cards ──────────────────────────────────────────────────
            ActionCard(
                icon       = Icons.Default.Schedule,
                iconTint   = Indigo,
                iconBg     = IndigoDim,
                title      = "Configure Schedule",
                subtitle   = "Adjust your daily access window",
                onClick    = { showDialog = true }
            )
            ActionCard(
                icon       = Icons.Default.Apps,
                iconTint   = Emerald,
                iconBg     = EmeraldDim,
                title      = "Manage Blocked Apps",
                subtitle   = "Add or remove apps from the block list",
                onClick    = onManageApps
            )
            ActionCard(
                icon       = Icons.Default.BarChart,
                iconTint   = Amber,
                iconBg     = AmberDim,
                title      = "Analytics",
                subtitle   = "View your resistance stats",
                onClick    = onShowAnalytics
            )
            ActionCard(
                icon       = Icons.Default.Security,
                iconTint   = Rose,
                iconBg     = RoseDim,
                title      = "Uninstall Protection",
                subtitle   = "Prevent impulsive app removal",
                onClick    = onUninstall
            )

            // Re-run setup if something broke
            if (!allOk) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        prefs.isSetupComplete = false
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Rose.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose)
                ) {
                    Text("Re-run Setup", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showDialog) {
        ConfigureSlotsDialog(
            prefs    = prefs,
            onDismiss = { showDialog = false },
            onSaved  = { windowLabel = buildWindowLabel(prefs) }
        )
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
                Text(subtitle, fontSize = 12.sp, color = TextMid)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextLow,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun buildWindowLabel(prefs: BlockerPreferences) =
    "${BlockSchedule.formatMinutes(prefs.allowStartMinutes)} – ${BlockSchedule.formatMinutes(prefs.allowEndMinutes)}"

// ── Configure slots dialog ────────────────────────────────────────────────────

private fun buildTimeSlots(): Array<String> = Array(48) { i -> BlockSchedule.formatMinutes(i * 30) }

@Composable
fun ConfigureSlotsDialog(prefs: BlockerPreferences, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val timeSlots = remember { buildTimeSlots() }
    var startIdx by remember { mutableIntStateOf(prefs.allowStartMinutes / 30) }
    var endIdx   by remember { mutableIntStateOf(prefs.allowEndMinutes   / 30) }

    val durationMinutes = (endIdx - startIdx) * 30
    val isValid = endIdx > startIdx && durationMinutes <= 120

    val cooldownMs = 48L * 60 * 60 * 1000
    val msSinceChange = System.currentTimeMillis() - prefs.lastScheduleChangedAt
    val onCooldown = prefs.lastScheduleChangedAt > 0L && msSinceChange < cooldownMs
    val msRemaining = if (onCooldown) cooldownMs - msSinceChange else 0L
    val hoursLeft = (msRemaining / (1000 * 60 * 60)).toInt()
    val minutesLeft = ((msRemaining % (1000 * 60 * 60)) / (1000 * 60)).toInt()
    val canSave = isValid && !onCooldown

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Configure Schedule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextHigh,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Max window: 2 hours",
                    fontSize = 12.sp,
                    color = TextLow,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("From", fontSize = 11.sp, color = TextMid,
                            modifier = Modifier.padding(bottom = 8.dp))
                        AndroidView(
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 0
                                    maxValue = 46
                                    displayedValues = timeSlots.copyOfRange(0, 47)
                                    wrapSelectorWheel = false
                                    value = startIdx
                                    setOnValueChangedListener { _, _, new -> startIdx = new }
                                }
                            }
                        )
                    }
                    Text("to", fontSize = 15.sp, color = TextMid)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Until", fontSize = 11.sp, color = TextMid,
                            modifier = Modifier.padding(bottom = 8.dp))
                        AndroidView(
                            factory = { ctx ->
                                NumberPicker(ctx).apply {
                                    minValue = 1
                                    maxValue = 47
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

                // Validation feedback
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                onCooldown -> AmberDim
                                isValid    -> EmeraldDim
                                else       -> RoseDim
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        onCooldown -> Text(
                            "Locked for ${hoursLeft}h ${minutesLeft}m — change limit: once per 48h",
                            fontSize = 13.sp,
                            color = Amber,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        isValid -> {
                            val h = durationMinutes / 60
                            val m = durationMinutes % 60
                            val label = when {
                                h > 0 && m > 0 -> "${h}h ${m}m window"
                                h > 0 -> "${h}h window"
                                else  -> "${m}m window"
                            }
                            Text(label, fontSize = 13.sp, color = Emerald, fontWeight = FontWeight.Medium)
                        }
                        else -> Text(
                            if (endIdx <= startIdx) "End time must be after start" else "Maximum window is 2 hours",
                            fontSize = 13.sp,
                            color = Rose,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .background(
                                if (canSave) Brush.linearGradient(listOf(Indigo, Color(0xFF8B5CF6)))
                                else Brush.linearGradient(listOf(BorderMid, BorderMid))
                            )
                            .clickable(enabled = canSave) {
                                prefs.allowStartMinutes = startIdx * 30
                                prefs.allowEndMinutes   = endIdx   * 30
                                prefs.lastScheduleChangedAt = System.currentTimeMillis()
                                onSaved()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Save",
                            color = if (canSave) Color.White else TextLow,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
