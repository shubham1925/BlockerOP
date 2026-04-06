package com.example.blockerop.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.blockerop.data.BlockerPreferences
import com.example.blockerop.ui.theme.*

@Composable
fun ManageAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { BlockerPreferences(context) }
    var blockedPkgs by remember { mutableStateOf(prefs.blockedPackages) }
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = BgDeep) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .padding(top = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextMid)
                    }
                    Text(
                        "Blocked Apps",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHigh,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(44.dp),
                    containerColor = Indigo,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add app", modifier = Modifier.size(20.dp))
                }
            }

            if (blockedPkgs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📱", fontSize = 48.sp)
                        Text("No apps blocked yet", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextHigh)
                        Text("Tap + to add an app", fontSize = 14.sp, color = TextMid)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(blockedPkgs.sorted(), key = { it }) { pkg ->
                        BlockedAppRow(
                            packageName = pkg,
                            onRemove = {
                                val updated = blockedPkgs - pkg
                                blockedPkgs = updated
                                prefs.blockedPackages = updated
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAppDialog(
            context        = context,
            alreadyBlocked = blockedPkgs,
            onAdd = { pkg ->
                val updated = blockedPkgs + pkg
                blockedPkgs = updated
                prefs.blockedPackages = updated
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

// ── Blocked app row ───────────────────────────────────────────────────────────

@Composable
private fun BlockedAppRow(packageName: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val label = remember(packageName) { appLabel(context, packageName) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = packageName, size = 42)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextHigh)
                Text(packageName, fontSize = 11.sp, color = TextLow, maxLines = 1)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove",
                    tint = Rose, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Add app dialog ────────────────────────────────────────────────────────────

@Composable
private fun AddAppDialog(
    context: Context,
    alreadyBlocked: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val allApps = remember(alreadyBlocked) { loadInstallableApps(context, alreadyBlocked) }
    val filtered = remember(query, allApps) {
        if (query.isBlank()) allApps
        else allApps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BgElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add App", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextHigh,
                    modifier = Modifier.padding(bottom = 14.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps…", color = TextLow) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Indigo,
                        unfocusedBorderColor = BorderMid,
                        focusedTextColor     = TextHigh,
                        unfocusedTextColor   = TextHigh,
                        cursorColor          = Indigo,
                        unfocusedContainerColor = BgInput,
                        focusedContainerColor   = BgInput
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = TextLow, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(packageName = app.packageName, size = 38)
                                Spacer(Modifier.width(12.dp))
                                TextButton(
                                    onClick = { onAdd(app.packageName); onDismiss() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        app.label,
                                        fontWeight = FontWeight.Medium,
                                        color = TextHigh,
                                        fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = TextMid)
                }
            }
        }
    }
}

// ── App icon ──────────────────────────────────────────────────────────────────

@Composable
fun AppIcon(packageName: String, size: Int = 40) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val d = pm.getApplicationIcon(appInfo)
            val w = d.intrinsicWidth.coerceIn(1, 512)
            val h = d.intrinsicHeight.coerceIn(1, 512)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(BgElevated, RoundedCornerShape(10.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class AppInfo(val packageName: String, val label: String)

private fun appLabel(context: Context, packageName: String): String =
    runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

private fun loadInstallableApps(context: Context, exclude: Set<String>): List<AppInfo> {
    val pm = context.packageManager
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { info ->
            pm.getLaunchIntentForPackage(info.packageName) != null &&
                info.packageName != context.packageName &&
                info.packageName !in exclude
        }
        .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}
