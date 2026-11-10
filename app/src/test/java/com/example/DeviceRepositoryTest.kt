package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DeviceRepository
import com.example.model.CameraDevice
import com.example.model.DeviceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceRepositoryTest {

    private fun sampleDevice(id: String = "dev-1") = CameraDevice(
        id = id,
        name = "Balcone",
        location = "Esterno",
        ipAddress = "192.168.1.20",
        port = 8765,
        isOnline = false,
        batteryPercent = 0,
        isLocalPhone = false,
        resolution = "1080p (Full HD)",
        type = DeviceType.SMARTPHONE
    )

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `saves and reads back a device`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
        try {
            val repository = DeviceRepository(db.deviceDao())
            repository.save(sampleDevice())

            val saved = repository.all()
            assertEquals(1, saved.size)
            assertEquals("Balcone", saved.first().name)
            assertEquals("192.168.1.20", saved.first().ipAddress)
            assertEquals(8765, saved.first().port)
            assertEquals(DeviceType.SMARTPHONE, saved.first().type)
        } finally {
            db.close()
        }
    }

    @Test
    fun `survives database reopen like an app restart`() = runBlocking {
        val dbName = "device-persistence-test.db"
        context().deleteDatabase(dbName)

        val first = Room.databaseBuilder(context(), AppDatabase::class.java, dbName).build()
        DeviceRepository(first.deviceDao()).save(sampleDevice())
        first.close()

        val second = Room.databaseBuilder(context(), AppDatabase::class.java, dbName).build()
        try {
            val reopened = DeviceRepository(second.deviceDao()).all()
            assertEquals(1, reopened.size)
            assertEquals("192.168.1.20", reopened.first().ipAddress)
        } finally {
            second.close()
            context().deleteDatabase(dbName)
        }
    }

    @Test
    fun `flow emits the saved devices`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
        try {
            val repository = DeviceRepository(db.deviceDao())
            repository.save(sampleDevice("a"))
            repository.save(sampleDevice("b").copy(name = "Ingresso", ipAddress = "192.168.1.21"))

            val devices = repository.devices.first()
            assertEquals(2, devices.size)
            assertTrue(devices.any { it.name == "Ingresso" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `delete removes the device`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
        try {
            val repository = DeviceRepository(db.deviceDao())
            repository.save(sampleDevice())
            repository.delete("dev-1")
            assertTrue(repository.all().isEmpty())
        } finally {
            db.close()
        }
    }
}
