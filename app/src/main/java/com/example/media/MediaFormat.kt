package com.example.media

import java.util.Locale

/** Formattazioni usate dall'elenco registrazioni, testabili senza Android. */
object MediaFormat {

    fun formatDuration(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val remaining = safe % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remaining)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.getDefault(), "%.0f KB", kb)
        }
        val mb = kb / 1024.0
        if (mb < 1024.0) {
            return String.format(Locale.getDefault(), "%.1f MB", mb)
        }
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    fun snapshotFileName(timestampMillis: Long): String = "CSC_snapshot_$timestampMillis"

    fun clipFileName(timestampMillis: Long): String = "CSC_clip_$timestampMillis"
}
