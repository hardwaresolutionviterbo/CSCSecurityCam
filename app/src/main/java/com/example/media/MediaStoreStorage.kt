package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SavedMedia(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
)

/**
 * Salvataggio dei media con riferimenti stabili:
 * - istantanee in MediaStore Pictures/CSCSecurityCam (su Android 10+), altrimenti nella cartella
 *   esterna dell'app;
 * - video scritti direttamente da CameraX con [videoContentValues] in Movies/CSCSecurityCam.
 */
object MediaStoreStorage {

    private const val TAG = "MediaStoreStorage"

    const val ALBUM = "CSCSecurityCam"
    const val JPEG_MIME = "image/jpeg"
    const val VIDEO_MIME = "video/mp4"

    suspend fun saveSnapshot(
        context: Context,
        jpeg: ByteArray,
        displayName: String
    ): SavedMedia? = withContext(Dispatchers.IO) {
        if (jpeg.isEmpty()) return@withContext null
        val name = if (displayName.endsWith(".jpg", ignoreCase = true)) displayName else "$displayName.jpg"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, jpeg, name)
            } else {
                saveToAppStorage(context, jpeg, name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Salvataggio istantanea non riuscito", e)
            null
        }
    }

    private fun saveToMediaStore(context: Context, jpeg: ByteArray, name: String): SavedMedia? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = resolver.openOutputStream(uri)?.use { stream ->
            stream.write(jpeg)
            true
        } ?: false

        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }

        val publish = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, publish, null, null)
        return SavedMedia(uri, name, jpeg.size.toLong(), JPEG_MIME)
    }

    private fun saveToAppStorage(context: Context, jpeg: ByteArray, name: String): SavedMedia? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val album = File(base, ALBUM).apply { mkdirs() }
        val file = File(album, name)
        file.writeBytes(jpeg)
        return SavedMedia(Uri.fromFile(file), name, file.length(), JPEG_MIME)
    }

    /** ContentValues usati da CameraX per creare il video direttamente in MediaStore. */
    fun videoContentValues(displayName: String): ContentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$ALBUM"
            )
        }
    }

    fun querySizeBytes(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() } ?: 0L
        }
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Dimensione non leggibile per $uri", e)
            0L
        }
    }

    /** Elimina davvero il file: MediaStore per i content uri, altrimenti dal disco. */
    fun delete(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                "file" -> uri.path?.let { File(it).delete() } ?: false
                else -> File(uriString).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Eliminazione non riuscita per $uriString", e)
            false
        }
    }
}
