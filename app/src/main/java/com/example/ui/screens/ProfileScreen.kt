package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Premium
import com.example.model.UserProfile
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscSecondaryText
import com.example.ui.theme.CscTextMuted
import com.example.ui.theme.MontserratFontFamily
import com.example.viewmodel.MainUiState
import java.io.File

/** Area personale: dati account, foto profilo e stato dell'abbonamento. */
@Composable
fun ProfileScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onUpdateName: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val profile = state.userProfile

    var showNameDialog by remember { mutableStateOf(false) }
    var draftName by remember(profile.fullName) { mutableStateOf(profile.fullName) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickPhoto) }

    fun openPlayStore() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Premium.PLAY_STORE_URL)))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Torna indietro",
                        tint = CscIceWhite
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Area personale",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Intestazione account con foto scelta dall'utente.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CscGlassCard)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    profile = profile,
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CscIceWhite,
                        modifier = Modifier.testTag("profile_name")
                    )
                    Text(
                        text = profile.email,
                        fontSize = 12.sp,
                        color = CscSecondaryText
                    )
                    Text(
                        text = when {
                            profile.isGuest -> "Modalità ospite"
                            profile.provider == com.example.model.AuthProvider.GOOGLE ->
                                "Account Google collegato"
                            else -> "Account email verificato"
                        },
                        fontSize = 11.sp,
                        color = CscTextMuted
                    )
                }
                IconButton(
                    onClick = { draftName = profile.fullName; showNameDialog = true },
                    modifier = Modifier.testTag("profile_name_edit")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifica nome",
                        tint = CscOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stato dell'abbonamento: piano, giorni residui e prezzo.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CscGlassCard)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = CscOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Piano ${Premium.planLabel(profile)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CscIceWhite,
                            modifier = Modifier.testTag("profile_plan")
                        )
                        Text(
                            text = Premium.expiryLabel(profile),
                            fontSize = 13.sp,
                            color = if (profile.isPremium()) CscOrange else CscSecondaryText,
                            modifier = Modifier.testTag("profile_plan_expiry")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (profile.isPremium()) {
                        "Puoi salvare telecamere illimitate. L'abbonamento si rinnova a " +
                            "${Premium.PREMIUM_PRICE_LABEL} e puoi disdirlo quando vuoi dal Play Store."
                    } else {
                        "Il piano Free consente ${Premium.FREE_CAMERA_LIMIT} telecamera. " +
                            "Passa a Premium per aggiungerne altre e tenere tutto sincronizzato."
                    },
                    fontSize = 12.sp,
                    color = CscSecondaryText
                )

                if (!profile.isPremium()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { openPlayStore() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("profile_premium_btn"),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CscOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Passa a Premium • ${Premium.PREMIUM_PRICE_LABEL}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MontserratFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (profile.isLoggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CscGlassCard)
                        .border(1.dp, CscGlassBorder, RoundedCornerShape(18.dp))
                        .clickable { onLogout() }
                        .padding(16.dp)
                        .testTag("profile_logout_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        tint = CscOrange
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Esci dall'account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CscIceWhite
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = CscGlassCard,
            title = { Text("Nome visualizzato", color = CscIceWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CscOrange,
                        unfocusedBorderColor = CscGlassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.testTag("profile_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (draftName.trim().length >= 2) {
                            onUpdateName(draftName)
                            showNameDialog = false
                        }
                    },
                    modifier = Modifier.testTag("profile_name_save")
                ) {
                    Text("Salva", color = CscOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Annulla", color = CscSecondaryText)
                }
            }
        )
    }
}

@Composable
private fun Avatar(profile: UserProfile, onClick: () -> Unit) {
    val photo = remember(profile.photoPath) {
        profile.photoPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(CscOrange.copy(alpha = 0.18f))
            .border(1.5.dp, CscOrange, CircleShape)
            .clickable { onClick() }
            .testTag("profile_photo_btn"),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = "Foto profilo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = profile.fullName.trim().take(1).uppercase().ifBlank { "?" },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = CscOrange
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(CscOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Cambia foto",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
