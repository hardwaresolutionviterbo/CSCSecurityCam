package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.RecordingRepository
import com.example.data.SettingsRepository
import com.example.media.MediaFormat
import com.example.media.MediaStoreStorage
import com.example.model.AppSettings
import com.example.model.RecordingItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordingPersistenceTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun sampleRecording(id: String = "rec-1") = RecordingItem(
        id = id,
        title = "Clip 00:07",
        timestamp = "10/11 22:30",
        durationSeconds = 7,
        isSnapshot = false,
        cameraName = "Videocamera",
        uri = "content://media/external/video/media/1",
        fileSizeBytes = 1_234L,
        createdAtMillis = 1_700_000_000_000L,
        mimeType = "video/mp4"
    )

    @Test
    fun `recording rows survive a database reopen`() = runBlocking {
        val dbName = "recordings-persistence-test.db"
        context().deleteDatabase(dbName)

        val first = Room.databaseBuilder(context(), AppDatabase::class.java, dbName).build()
        RecordingRepository(first.recordingDao()).save(sampleRecording())
        first.close()

        val second = Room.databaseBuilder(context(), AppDatabase::class.java, dbName).build()
        try {
            val reopened = RecordingRepository(second.recordingDao()).all()
            assertEquals(1, reopened.size)
            assertEquals("content://media/external/video/media/1", reopened.first().uri)
            assertEquals(7, reopened.first().durationSeconds)
            assertEquals(1_234L, reopened.first().fileSizeBytes)
            assertTrue(reopened.first().hasFile)
        } finally {
            second.close()
            context().deleteDatabase(dbName)
        }
    }

    @Test
    fun `recordings flow exposes saved rows newest first`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
        try {
            val repository = RecordingRepository(db.recordingDao())
            repository.save(sampleRecording("old").copy(createdAtMillis = 1_000L, title = "Vecchia"))
            repository.save(sampleRecording("new").copy(createdAtMillis = 2_000L, title = "Nuova"))

            val rows = repository.recordings.first()
            assertEquals(2, rows.size)
            assertEquals("Nuova", rows.first().title)
        } finally {
            db.close()
        }
    }

    @Test
    fun `delete removes the row and the file from disk`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
        try {
            val file = File(context().cacheDir, "clip-test.mp4").apply { writeBytes(ByteArray(64)) }
            val uri = Uri.fromFile(file).toString()
            val repository = RecordingRepository(db.recordingDao())
            repository.save(sampleRecording().copy(uri = uri, fileSizeBytes = file.length()))

            val removed = MediaStoreStorage.delete(context(), uri)
            repository.delete("rec-1")

            assertTrue("il file deve sparire dal disco", removed)
            assertFalse(file.exists())
            assertTrue(repository.all().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `settings survive a database reopen`() = runBlocking {
        val dbName = "settings-persistence-test.db"
        context().deleteDatabase(dbName)

        val first = Room.databaseBuilder(context(), AppDatabase::class.java, dbName).build()
        val custom = AppSettings(
            videoQuality = "4K (Ultra HD)",
            audioMode = "Solo ascolto ambientale",
            motionDetectionEnabled = false,
            motionSensitivity = 0.35f,
            autoRecordingOnlyMotion = false,
            notificationsEnabled = false,
            remoteAccessWebRtc = true,
            nightVisionHardware = true,
            port = 9100
        )
        SettingsRepository(first.settingsDao()).save(custom)
        first.close()

        val second = Room.databaseBuilder(context(), AppDatabase::class.java, dbName).build()
        try {
            val reopened = SettingsRepository(second.settingsDao()).get()
            assertEquals("4K (Ultra HD)", reopened.videoQuality)
            assertEquals(9100, reopened.port)
            assertEquals(0.35f, reopened.motionSensitivity, 0.001f)
            assertTrue(reopened.remoteAccessWebRtc)
            assertFalse(reopened.motionDetectionEnabled)
        } finally {
            second.close()
            context().deleteDatabase(dbName)
        }
    }

    @Test
    fun `media names and formats are stable`() {
        assertEquals("00:07", MediaFormat.formatDuration(7))
        assertEquals("01:05", MediaFormat.formatDuration(65))
        assertEquals("—", MediaFormat.formatSize(0))
        assertTrue(MediaFormat.formatSize(1_048_576).contains("MB"))
        assertTrue(MediaFormat.snapshotFileName(42L).endsWith("42"))
        assertTrue(MediaFormat.clipFileName(42L).endsWith("42"))
    }
}
