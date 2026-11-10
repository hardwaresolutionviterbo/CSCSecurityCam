package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.RecordingItem

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: String,
    val durationSeconds: Int,
    val isMotionEvent: Boolean,
    val isSnapshot: Boolean,
    val cameraName: String,
    /** Uri MediaStore del file: è il riferimento che permette di riaprirlo dopo il riavvio. */
    val uri: String,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val mimeType: String
)

fun RecordingEntity.toRecordingItem(): RecordingItem = RecordingItem(
    id = id,
    title = title,
    timestamp = timestamp,
    durationSeconds = durationSeconds,
    isMotionEvent = isMotionEvent,
    isSnapshot = isSnapshot,
    filePath = uri,
    cameraName = cameraName,
    uri = uri,
    fileSizeBytes = fileSizeBytes,
    createdAtMillis = createdAtMillis,
    mimeType = mimeType
)

fun RecordingItem.toEntity(): RecordingEntity = RecordingEntity(
    id = id,
    title = title,
    timestamp = timestamp,
    durationSeconds = durationSeconds,
    isMotionEvent = isMotionEvent,
    isSnapshot = isSnapshot,
    cameraName = cameraName,
    uri = uri.ifEmpty { filePath },
    fileSizeBytes = fileSizeBytes,
    createdAtMillis = if (createdAtMillis > 0L) createdAtMillis else System.currentTimeMillis(),
    mimeType = mimeType.ifEmpty { if (isSnapshot) "image/jpeg" else "video/mp4" }
)
