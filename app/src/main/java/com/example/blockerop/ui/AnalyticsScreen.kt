package com.example.blockerop.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blockerop.data.AppOpenEvent
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.data.EventLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ── Constants ─────────────────────────────────────────────────────────────────

private val COLOR_BLOCKED = Color(0xFFE53935)
private val COLOR_ALLOWED = Color(0xFF43A047)
private val COLOR_GRID    = Color(0x1A000000)

private val APP_DISPLAY_NAMES = mapOf(
    "com.instagram.android" to "Instagram",
    "com.facebook.katana"   to "Facebook"
)

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(context: Context, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val events = remember { EventLogger.readAll(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Daily") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Weekly") })
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) DailyContent(events)
                else WeeklyContent(events)
            }
        }
    }
}

// ── Daily tab ─────────────────────────────────────────────────────────────────

@Composable
private fun DailyContent(events: List<AppOpenEvent>) {
    val todayStart = startOfDay(daysAgo = 0)
    val todayEvents = events.filter { it.timestampMs >= todayStart }

    // x-axis: 24 hours, label only the readable ones
    val hourLabels = (0 until 24).map { h ->
        when (h) { 0 -> "12a"; 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; 23 -> "11p"; else -> "" }
    }

    Text(
        "Today",
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    for (pkg in BlockerPreferences.BLOCKED_PACKAGES) {
        val pkgEvents = todayEvents.filter { it.packageName == pkg }
        val blocked = IntArray(24)
        val allowed = IntArray(24)
        for (e in pkgEvents) {
            val hour = Calendar.getInstance()
                .apply { timeInMillis = e.timestampMs }
                .get(Calendar.HOUR_OF_DAY)
            if (e.wasBlocked) blocked[hour]++ else allowed[hour]++
        }
        AppStatCard(
            appName      = APP_DISPLAY_NAMES[pkg] ?: pkg,
            totalBlocked = pkgEvents.count { it.wasBlocked },
            totalAllowed = pkgEvents.count { !it.wasBlocked },
            blockedData  = blocked.toList(),
            allowedData  = allowed.toList(),
            xLabels      = hourLabels
        )
    }
}

// ── Weekly tab ────────────────────────────────────────────────────────────────

@Composable
private fun WeeklyContent(events: List<AppOpenEvent>) {
    val weekStart = startOfDay(daysAgo = 6)
    val weekEvents = events.filter { it.timestampMs >= weekStart }

    // Labels: [6-days-ago … today], index 0 = oldest
    val dayLabels = (6 downTo 0).map { daysAgo ->
        if (daysAgo == 0) "Today"
        else SimpleDateFormat("EEE", Locale.getDefault()).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time
        )
    }

    Text(
        "Last 7 days",
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    for (pkg in BlockerPreferences.BLOCKED_PACKAGES) {
        val pkgEvents = weekEvents.filter { it.packageName == pkg }
        val blocked = IntArray(7)
        val allowed = IntArray(7)
        for (e in pkgEvents) {
            // day 0 = 6 days ago, day 6 = today
            val dayIdx = ((e.timestampMs - weekStart) / 86_400_000L)
                .toInt().coerceIn(0, 6)
            if (e.wasBlocked) blocked[dayIdx]++ else allowed[dayIdx]++
        }
        AppStatCard(
            appName      = APP_DISPLAY_NAMES[pkg] ?: pkg,
            totalBlocked = pkgEvents.count { it.wasBlocked },
            totalAllowed = pkgEvents.count { !it.wasBlocked },
            blockedData  = blocked.toList(),
            allowedData  = allowed.toList(),
            xLabels      = dayLabels
        )
    }
}

// ── App stat card ─────────────────────────────────────────────────────────────

@Composable
private fun AppStatCard(
    appName: String,
    totalBlocked: Int,
    totalAllowed: Int,
    blockedData: List<Int>,
    allowedData: List<Int>,
    xLabels: List<String>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // Header row: app name + totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${totalBlocked + totalAllowed} total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Legend
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendDot(COLOR_BLOCKED, "$totalBlocked blocked")
                LegendDot(COLOR_ALLOWED, "$totalAllowed allowed")
            }

            Spacer(Modifier.height(12.dp))

            // Chart or empty state
            if (totalBlocked == 0 && totalAllowed == 0) {
                Box(
                    Modifier.fillMaxWidth().height(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No data yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp)
                }
            } else {
                TimeChart(
                    blockedData = blockedData,
                    allowedData = allowedData,
                    xLabels     = xLabels,
                    modifier    = Modifier.fillMaxWidth().height(150.dp)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Line chart ────────────────────────────────────────────────────────────────

@Composable
private fun TimeChart(
    blockedData: List<Int>,
    allowedData: List<Int>,
    xLabels: List<String>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val maxVal = maxOf(blockedData.maxOrNull() ?: 0, allowedData.maxOrNull() ?: 0, 1).toFloat()
    val labelStyle = TextStyle(fontSize = 9.sp, color = Color(0xFF888888))

    Canvas(modifier = modifier) {
        val padLeft   = 8.dp.toPx()
        val padRight  = 8.dp.toPx()
        val padTop    = 8.dp.toPx()
        val padBottom = 24.dp.toPx()

        val drawW = size.width  - padLeft - padRight
        val drawH = size.height - padTop  - padBottom
        val n     = xLabels.size
        val step  = drawW / (n - 1).coerceAtLeast(1).toFloat()

        fun xAt(i: Int)  = padLeft + i * step
        fun yAt(v: Int)  = padTop + drawH - (v / maxVal) * drawH

        // ── Horizontal grid lines (4 evenly spaced) ───────────────────────────
        repeat(4) { row ->
            val y = padTop + drawH * row / 3f
            drawLine(
                COLOR_GRID,
                Offset(padLeft, y),
                Offset(padLeft + drawW, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // ── Draw one data series ───────────────────────────────────────────────
        fun drawSeries(data: List<Int>, color: Color) {
            if (data.isEmpty()) return

            // Polyline
            val path = Path()
            data.forEachIndexed { i, v ->
                val x = xAt(i); val y = yAt(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(
                width = 2.dp.toPx(),
                cap   = StrokeCap.Round,
                join  = StrokeJoin.Round
            ))

            // Dots at non-zero data points
            data.forEachIndexed { i, v ->
                if (v > 0) {
                    val cx = xAt(i); val cy = yAt(v)
                    drawCircle(color,       radius = 4.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }

        // Draw blocked behind allowed so green dots are on top where they overlap
        drawSeries(blockedData, COLOR_BLOCKED)
        drawSeries(allowedData, COLOR_ALLOWED)

        // ── X-axis labels ─────────────────────────────────────────────────────
        xLabels.forEachIndexed { i, label ->
            if (label.isNotEmpty()) {
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    measured,
                    topLeft = Offset(
                        xAt(i) - measured.size.width / 2f,
                        padTop + drawH + 4.dp.toPx()
                    )
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun startOfDay(daysAgo: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
