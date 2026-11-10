package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Screen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PlaybackScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.CscDeepBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                CscSecurityCamApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CscSecurityCamApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val liveBitmap by viewModel.clientLiveBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initContinuousOperationManagers(context)
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CscDeepBackground)
    ) {
        AnimatedContent(
            targetState = state.currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { targetScreen ->
            when (targetScreen) {
                Screen.SPLASH -> {
                    SplashScreen(
                        onLoginClick = { viewModel.navigateTo(Screen.LOGIN) },
                        onRegisterClick = { viewModel.navigateTo(Screen.REGISTER) },
                        onGuestClick = { viewModel.continueAsGuest() }
                    )
                }

                Screen.LOGIN -> {
                    LoginScreen(
                        onBack = { viewModel.navigateBack() },
                        onLoginSuccess = { email, rememberMe -> viewModel.loginUser(email, rememberMe) },
                        onNavigateToRegister = { viewModel.navigateTo(Screen.REGISTER) },
                        onContinueAsGuest = { viewModel.continueAsGuest() }
                    )
                }

                Screen.REGISTER -> {
                    RegisterScreen(
                        onBack = { viewModel.navigateBack() },
                        onRegisterSuccess = { first, last, email -> viewModel.registerUser(first, last, email) },
                        onNavigateToLogin = { viewModel.navigateTo(Screen.LOGIN) }
                    )
                }

                Screen.HOME -> {
                    HomeScreen(
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }

                Screen.CAMERA -> {
                    CameraScreen(
                        state = state,
                        onBack = { viewModel.navigateBack() },
                        onToggleStreaming = { viewModel.toggleCameraStreaming(context) },
                        onToggleLens = { viewModel.toggleLens() },
                        onToggleTorch = { viewModel.toggleTorch() },
                        onFrameEncoded = { viewModel.broadcastEncodedFrame(it) },
                        motionDetector = viewModel.motionDetector,
                        onMotionResult = { viewModel.handleMotionResult(context, it) },
                        onToggleStealth = { viewModel.toggleStealthPowerSaving() }
                    )
                }

                Screen.MONITOR -> {
                    MonitorScreen(
                        state = state,
                        liveBitmap = liveBitmap,
                        onBack = { viewModel.navigateBack() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onReconnect = { viewModel.selectDevice(state.selectedDeviceId) },
                        onToggleAudio = { viewModel.toggleAudioListening() },
                        onTakeSnapshot = { viewModel.takeSnapshot() },
                        onToggleRecord = { viewModel.toggleRecording() },
                        onPanTilt = { dx, dy -> viewModel.panTilt(dx, dy) },
                        onResetPtz = { viewModel.resetPtz() },
                        onZoomIn = { viewModel.zoomIn() },
                        onZoomOut = { viewModel.zoomOut() },
                        onToggleNightVision = { viewModel.toggleNightVision() },
                        onToggleMotionDetection = { viewModel.toggleMotionDetection() },
                        onRemoteTorch = { viewModel.sendRemoteTorch() },
                        onRemoteFlipLens = { viewModel.sendRemoteFlipLens() },
                        onTwoWayTalk = { active -> viewModel.setTwoWayTalk(active) }
                    )
                }

                Screen.DEVICES -> {
                    DevicesScreen(
                        state = state,
                        onBack = { viewModel.navigateBack() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onSelectDevice = { viewModel.selectDevice(it) },
                        onAddDevice = { name, ip, loc, type -> viewModel.addDevice(name, ip, loc, type) }
                    )
                }

                Screen.PLAYBACK -> {
                    PlaybackScreen(
                        state = state,
                        onBack = { viewModel.navigateBack() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onDeleteRecording = { viewModel.deleteRecording(it) }
                    )
                }

                Screen.SETTINGS -> {
                    SettingsScreen(
                        state = state,
                        onBack = { viewModel.navigateBack() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onUpdateVideoQuality = { q -> viewModel.updateSettings { it.copy(videoQuality = q) } },
                        onUpdateAudioMode = { a -> viewModel.updateSettings { it.copy(audioMode = a) } },
                        onToggleMotionDetection = { m -> viewModel.updateSettings { it.copy(motionDetectionEnabled = m) } },
                        onToggleAutoRecording = { r -> viewModel.updateSettings { it.copy(autoRecordingOnlyMotion = r) } },
                        onToggleNotifications = { n -> viewModel.updateSettings { it.copy(notificationsEnabled = n) } },
                        onToggleRemoteAccess = { rem -> viewModel.updateSettings { it.copy(remoteAccessWebRtc = rem) } },
                        onUpdateMotionSensitivity = { s -> viewModel.updateSettings { it.copy(motionSensitivity = s) } }
                    )
                }
            }
        }
    }
}
