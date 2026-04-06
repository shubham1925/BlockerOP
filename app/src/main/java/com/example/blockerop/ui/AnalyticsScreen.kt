package com.example.blockerop.ui

import android.content.Context
import androidx.compose.foundation.Canvas
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
import com.example.blockerop.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val COLOR_BLOCKED = Emerald
private val COLOR_ALLOWED = Amber
private val COLOR_GRID    = Color(0x12FFFFFF)

private val APP_DISPLAY_NAMES = mapOf(
    "com.instagram.android" to "Instagram",
    "com.facebook.katana"   to "Facebook"
)

@Composable
fun AnalyticsScreen(context: Context, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val events = remember { EventLogger.readAll(context) }
    val prefs  = remember { BlockerPreferences(context) }

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
                    "Analytics",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextHigh,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // ── Tab selector ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Row {
                    listOf("Daily", "Weekly").forEachIndexed { idx, label ->
                        val selected = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) BgElevated else Color.Transparent)
                                .then(
                                    if (selected) Modifier.border(1.dp, BorderMid, RoundedCornerShape(9.dp))
                                    else Modifier
                                )
                                .clickable { selectedTab = idx }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) TextHigh else TextMid
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightsCard(events)
                if (selectedTab == 0) DailyContent(events, prefs)
                else WeeklyContent(events, prefs)
            }
        }
    }
}

// ── Insights card ─────────────────────────────────────────────────────────────

@Composable
private fun InsightsCard(events: List<AppOpenEvent>) {
    val allBlocked = remember(events) { events.filter { it.wasBlocked } }

    val peakHour: Int? = remember(allBlocked) {
        if (allBlocked.isEmpty()) null
        else {
            val byHour = IntArray(24)
            allBlocked.forEach { e ->
                val h = Calendar.getInstance().apply { timeInMillis = e.timestampMs }
                    .get(Calendar.HOUR_OF_DAY)
                byHour[h]++
            }
            byHour.indices.maxByOrNull { byHour[it] }
        }
    }

    val bestDay: String? = remember(allBlocked) {
        if (allBlocked.isEmpty()) null
        else {
            val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val byDay = IntArray(7)
            allBlocked.forEach { e ->
                val d = Calendar.getInstance().apply { timeInMillis = e.timestampMs }
                    .get(Calendar.DAY_OF_WEEK) - 1
                byDay[d]++
            }
            dayNames[byDay.indices.maxByOrNull { byDay[it] }!!]
        }
    }

    if (peakHour == null && bestDay == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Insights", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMid)
            if (peakHour != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️", fontSize = 14.sp)
                    Text("You resist most at ${formatHour(peakHour)}", fontSize = 14.sp, color = TextHigh)
                }
            }
            if (bestDay != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 14.sp)
                    Text("Strongest day: $bestDay", fontSize = 14.sp, color = TextHigh)
                }
            }
        }
    }
}

// ── Daily tab ─────────────────────────────────────────────────────────────────

@Composable
private fun DailyContent(events: List<AppOpenEvent>, prefs: BlockerPreferences) {
    val todayStart = startOfDay(daysAgo = 0)
    val todayEvents = events.filter { it.timestampMs >= todayStart }

    val hourLabels = (0 until 24).map { h ->
        when (h) { 0 -> "12a"; 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; 23 -> "11p"; else -> "" }
    }

    Text("Today", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextLow,
        modifier = Modifier.padding(top = 4.dp))

    for (pkg in prefs.blockedPackages.sorted()) {
        val pkgEvents = todayEvents.filter { it.packageName == pkg }
        val blocked = IntArray(24); val allowed = IntArray(24)
        for (e in pkgEvents) {
            val hour = Calendar.getInstance().apply { timeInMillis = e.timestampMs }.get(Calendar.HOUR_OF_DAY)
            if (e.wasBlocked) blocked[hour]++ else allowed[hour]++
        }
        AppStatCard(
            appName      = APP_DISPLAY_NAMES[pkg] ?: appShortName(pkg),
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
private fun WeeklyContent(events: List<AppOpenEvent>, prefs: BlockerPreferences) {
    val weekStart = startOfDay(daysAgo = 6)
    val weekEvents = events.filter { it.timestampMs >= weekStart }

    val dayLabels = (6 downTo 0).map { daysAgo ->
        if (daysAgo == 0) "Today"
        else SimpleDateFormat("EEE", Locale.getDefault()).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time
        )
    }

    Text("Last 7 days", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextLow,
        modifier = Modifier.padding(top = 4.dp))

    for (pkg in prefs.blockedPackages.sorted()) {
        val pkgEvents = weekEvents.filter { it.packageName == pkg }
        val blocked = IntArray(7); val allowed = IntArray(7)
        for (e in pkgEvents) {
            val dayIdx = ((e.timestampMs - weekStart) / 86_400_000L).toInt().coerceIn(0, 6)
            if (e.wasBlocked) blocked[dayIdx]++ else allowed[dayIdx]++
        }
        AppStatCard(
            appName      = APP_DISPLAY_NAMES[pkg] ?: appShortName(pkg),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextHigh)
                Text(
                    "${totalBlocked + totalAllowed} opens",
                    fontSize = 12.sp, color = TextLow
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatPill(COLOR_BLOCKED, "$totalBlocked resisted", EmeraldDim)
                StatPill(COLOR_ALLOWED, "$totalAllowed opened", AmberDim)
            }
            Spacer(Modifier.height(14.dp))
            if (totalBlocked == 0 && totalAllowed == 0) {
                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    Text("No activity today", color = TextLow, fontSize = 13.sp)
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
private fun StatPill(color: Color, label: String, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(6.dp)) { drawCircle(color) }
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
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
    val labelStyle = TextStyle(fontSize = 9.sp, color = TextLow)

    Canvas(modifier = modifier) {
        val padLeft   = 8.dp.toPx()
        val padRight  = 8.dp.toPx()
        val padTop    = 8.dp.toPx()
        val padBottom = 24.dp.toPx()
        val drawW = size.width  - padLeft - padRight
        val drawH = size.height - padTop  - padBottom
        val n     = xLabels.size
        val step  = drawW / (n - 1).coerceAtLeast(1).toFloat()

        fun xAt(i: Int) = padLeft + i * step
        fun yAt(v: Int) = padTop + drawH - (v / maxVal) * drawH

        repeat(4) { row ->
            val y = padTop + drawH * row / 3f
            drawLine(COLOR_GRID, Offset(padLeft, y), Offset(padLeft + drawW, y), strokeWidth = 1.dp.toPx())
        }

        fun drawSeries(data: List<Int>, color: Color) {
            if (data.isEmpty()) return
            val path = Path()
            data.forEachIndexed { i, v ->
                val x = xAt(i); val y = yAt(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            data.forEachIndexed { i, v ->
                if (v > 0) {
                    val cx = xAt(i); val cy = yAt(v)
                    drawCircle(color,        radius = 4.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color(0xFF0F1728), radius = 2.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }

        drawSeries(blockedData, COLOR_BLOCKED)
        drawSeries(allowedData, COLOR_ALLOWED)

        xLabels.forEachIndexed { i, label ->
            if (label.isNotEmpty()) {
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(measured, topLeft = Offset(xAt(i) - measured.size.width / 2f, padTop + drawH + 4.dp.toPx()))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun startOfDay(daysAgo: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun formatHour(hour: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h ${if (hour < 12) "AM" else "PM"}"
}

private fun appShortName(pkg: String) = pkg.substringAfterLast('.')
