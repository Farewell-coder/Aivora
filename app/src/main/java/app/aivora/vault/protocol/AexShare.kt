package app.aivora.vault.protocol

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Writes .akcp files into the app-private cache and hands them to the Android Sharesheet
 * through a FileProvider content:// URI. Real filesystem paths are never exposed.
 */
object AexShare {
    private const val CACHE_DIR = "aex-share"
    private const val STALE_AFTER_MS = 10 * 60 * 1000L
    private const val MAX_CACHED_FILES = 20

    /** Removes share files older than [STALE_AFTER_MS] and caps the number kept on disk. */
    fun cleanup(context: Context) {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.isDirectory) return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > STALE_AFTER_MS) file.delete()
        }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_CACHED_FILES)?.forEach { it.delete() }
    }

    /**
     * Writes the export to the cache and builds a chooser intent.
     * Returns the intent plus the content:// URI that was granted read permission.
     */
    suspend fun prepare(
        context: Context,
        export: Aex.Export,
        fileName: String,
        subject: String
    ): Result<Pair<Intent, Uri>> = withContext(Dispatchers.IO) {
        runCatching {
            cleanup(context)
            val dir = File(context.cacheDir, CACHE_DIR)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("无法创建临时目录")
            val target = File(dir, sanitize(fileName))
            target.writeBytes(export.bytes)

            val authority = "${context.packageName}.aexfile"
            val uri = FileProvider.getUriForFile(context, authority, target)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = Aex.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "分享 AEX 配置").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            chooser to uri
        }
    }

    /** Returns whether any installed activity can receive the AEX chooser. */
    fun canShare(context: Context): Boolean {
        val probe = Intent(Intent.ACTION_SEND).apply {
            type = Aex.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://${context.packageName}.aexfile/probe"))
        }
        return context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    }

    fun sanitize(name: String): String = Aex.sanitizeFileName(name)
}
