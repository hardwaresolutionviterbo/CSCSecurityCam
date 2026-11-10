package com.example

import android.content.Intent
import com.example.service.CameraStreamingService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Il servizio è quello che deve continuare a catturare con l'app in background, quindi il suo
 * ciclo start/stop e la notifica di foreground sono verificati qui (la sessione CameraX reale
 * richiede hardware e non è testabile in JVM).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraStreamingServiceTest {

    @After
    fun tearDown() {
        CameraStreamingService.frameSink = null
        CameraStreamingService.motionSink = null
        CameraStreamingService.stopSink = null
    }

    @Test
    fun `start and stop commands toggle the foreground streaming service`() {
        val controller = Robolectric.buildService(CameraStreamingService::class.java)
        val service = controller.create().get()
        try {
            service.onStartCommand(Intent(CameraStreamingService.ACTION_START), 0, 1)

            assertTrue("il servizio deve risultare attivo", CameraStreamingService.isRunning)
            assertNotNull(
                "manca la notifica di foreground",
                shadowOf(service).lastForegroundNotification
            )

            service.onStartCommand(Intent(CameraStreamingService.ACTION_STOP), 0, 1)
            assertFalse(
                "dopo lo stop il servizio non deve risultare attivo",
                CameraStreamingService.isRunning
            )
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `stop from the notification is propagated to the view model`() {
        var stopNotified = false
        CameraStreamingService.stopSink = { stopNotified = true }

        val controller = Robolectric.buildService(CameraStreamingService::class.java)
        val service = controller.create().get()
        try {
            service.onStartCommand(Intent(CameraStreamingService.ACTION_START), 0, 1)
            service.onStartCommand(Intent(CameraStreamingService.ACTION_STOP), 0, 1)
            assertTrue("lo stop da notifica deve avvisare il ViewModel", stopNotified)
            assertFalse(CameraStreamingService.isRunning)
        } finally {
            controller.destroy()
        }
    }
}
