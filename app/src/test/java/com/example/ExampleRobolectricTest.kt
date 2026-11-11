package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CSCSecurityCam", appName)
  }

  @Test
  fun `launch MainActivity without crash`() {
    // The splash screen runs an infinite animation; with the system animation scale at zero the
    // frame clock stops rescheduling, so the main looper can reach idle as on a real device with
    // animations turned off.
    android.provider.Settings.Global.putFloat(
      ApplicationProvider.getApplicationContext<Context>().contentResolver,
      android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
      0f
    )
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    val activity = controller.create().start().resume().visible().get()
    org.junit.Assert.assertNotNull(activity)
  }

  @Test
  fun `guest mode sets isGuest true and navigates to home`() {
    val vm = com.example.viewmodel.MainViewModel()
    assertEquals(com.example.model.Screen.SPLASH, vm.uiState.value.currentScreen)
    
    vm.continueAsGuest()
    val state = vm.uiState.value
    assertEquals(com.example.model.Screen.HOME, state.currentScreen)
    org.junit.Assert.assertTrue(state.userProfile.isGuest)
  }

  @Test
  fun `login without an account store does not fake a signed in profile`() {
    val vm = com.example.viewmodel.MainViewModel()

    // Nessun archivio account (ViewModel senza Context): non si entra in HOME con un finto utente.
    vm.loginUser("mario.rossi@example.com", "Segreta123", rememberMe = true)
    assertEquals(com.example.model.Screen.SPLASH, vm.uiState.value.currentScreen)
    org.junit.Assert.assertFalse(vm.uiState.value.userProfile.isLoggedIn)
  }

  @Test
  fun `navigate back from login or register returns to splash`() {
    val vm = com.example.viewmodel.MainViewModel()
    vm.navigateTo(com.example.model.Screen.LOGIN)
    assertEquals(com.example.model.Screen.LOGIN, vm.uiState.value.currentScreen)
    vm.navigateBack()
    assertEquals(com.example.model.Screen.SPLASH, vm.uiState.value.currentScreen)

    vm.navigateTo(com.example.model.Screen.REGISTER)
    assertEquals(com.example.model.Screen.REGISTER, vm.uiState.value.currentScreen)
    vm.navigateBack()
    assertEquals(com.example.model.Screen.SPLASH, vm.uiState.value.currentScreen)
  }
}
