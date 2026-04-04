package com.example.blockerop.data

import android.content.Context
import java.io.File

data class AppOpenEvent(
    val timestampMs: Long,
    val packageName: String,
    val wasBlocked: Boolean
)

/**
 * Persists app-open events in two places:
 *
 *  1. Internal storage  (context.filesDir/app_events.csv)
 *     → fast, private, cleared on uninstall.
 *
 *  2. External app-specific dir  (getExternalFilesDir/app_events_backup.csv)
 *     → survives APK upgrades / adb re-installs; cleared only on uninstall.
 *
 * On first launch after a fresh install [restoreFromBackupIfNeeded] copies
 * the external mirror back to internal storage automatically.
 *
 * Android Auto Backup (configured in data_extraction_rules.xml) additionally
 * syncs both files to the user's Google account, covering full reinstalls.
 */
object EventLogger {

    private const val INTERNAL_FILE = "app_events.csv"
    private const val BACKUP_FILE   = "app_events_backup.csv"

    // ── Write ─────────────────────────────────────────────────────────────────

    fun log(context: Context, packageName: String, wasBlocked: Boolean) {
        val line = "${System.currentTimeMillis()},$packageName,$wasBlocked\n"
        writeToInternal(context, line)
        writeToBackup(context, line)
    }

    private fun writeToInternal(context: Context, line: String) {
        try { File(context.filesDir, INTERNAL_FILE).appendText(line) }
        catch (_: Exception) { }
    }

    private fun writeToBackup(context: Context, line: String) {
        try { backupFile(context)?.appendText(line) }
        catch (_: Exception) { }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun readAll(context: Context): List<AppOpenEvent> {
        return try {
            val file = File(context.filesDir, INTERNAL_FILE)
            if (!file.exists()) return emptyList()
            file.readLines().mapNotNull(::parseLine)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Call once on app startup (e.g. MainActivity.onCreate).
     * If the internal file is absent or empty but the external backup has data,
     * the backup is copied to internal storage so analytics are not lost.
     */
    fun restoreFromBackupIfNeeded(context: Context) {
        val internal = File(context.filesDir, INTERNAL_FILE)
        if (internal.exists() && internal.length() > 0) return   // nothing to restore

        val backup = backupFile(context) ?: return
        if (!backup.exists() || backup.length() == 0L) return

        try {
            backup.copyTo(internal, overwrite = true)
        } catch (_: Exception) { }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun backupFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return File(dir, BACKUP_FILE)
    }

    private fun parseLine(line: String): AppOpenEvent? {
        val parts = line.split(",")
        if (parts.size != 3) return null
        return try {
            AppOpenEvent(parts[0].toLong(), parts[1], parts[2].toBoolean())
        } catch (_: Exception) { null }
    }
}
