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
  fun `login and register update user profile and navigate to home`() {
    val vm = com.example.viewmodel.MainViewModel()
    
    vm.loginUser("mario.rossi@example.com", rememberMe = true)
    assertEquals(com.example.model.Screen.HOME, vm.uiState.value.currentScreen)
    org.junit.Assert.assertFalse(vm.uiState.value.userProfile.isGuest)
    assertEquals("mario.rossi@example.com", vm.uiState.value.userProfile.email)

    vm.navigateTo(com.example.model.Screen.REGISTER)
    assertEquals(com.example.model.Screen.REGISTER, vm.uiState.value.currentScreen)
    vm.registerUser("Luigi", "Verdi", "luigi.verdi@example.com")
    assertEquals(com.example.model.Screen.HOME, vm.uiState.value.currentScreen)
    assertEquals("Luigi Verdi", vm.uiState.value.userProfile.fullName)
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
