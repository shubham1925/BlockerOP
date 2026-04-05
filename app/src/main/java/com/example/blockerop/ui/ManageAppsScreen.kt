package com.example.blockerop.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { BlockerPreferences(context) }
    var blockedPkgs by remember { mutableStateOf(prefs.blockedPackages) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Apps") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add app")
            }
        }
    ) { padding ->
        if (blockedPkgs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No apps blocked. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

    if (showAddDialog) {
        AddAppDialog(
            context = context,
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

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = packageName, size = 40)
            Spacer(Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove",
                    tint = Color(0xFFB71C1C))
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
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Add App", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(packageName = app.packageName, size = 36)
                                Spacer(Modifier.width(12.dp))
                                TextButton(
                                    onClick = { onAdd(app.packageName); onDismiss() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        app.label,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ── App icon composable ───────────────────────────────────────────────────────

@Composable
fun AppIcon(packageName: String, size: Int = 40) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            val pm = context.packageManager
            // Pass ApplicationInfo directly — more reliable than passing the string
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
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
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
