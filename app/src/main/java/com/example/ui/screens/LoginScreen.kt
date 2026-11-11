package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CscDeepBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscNeonGlow
import com.example.ui.theme.CscNightBlue
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscOrangeGlow
import com.example.ui.theme.CscPanel
import com.example.ui.theme.CscSecondaryText
import com.example.ui.theme.CscWhite
import com.example.ui.theme.MontserratFontFamily

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: (email: String, password: String, rememberMe: Boolean) -> Unit,
    onGoogleSignIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    fun validateAndSubmit() {
        focusManager.clearFocus()
        var valid = true

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            emailError = "Inserisci l'indirizzo email"
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            emailError = "Formato email non valido"
            valid = false
        } else {
            emailError = null
        }

        if (password.isEmpty()) {
            passwordError = "Inserisci la password"
            valid = false
        } else if (password.length < 6) {
            passwordError = "La password deve contenere almeno 6 caratteri"
            valid = false
        } else {
            passwordError = null
        }

        if (valid) {
            onLoginSuccess(trimmedEmail, password, rememberMe)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscDeepBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CscNightBlue)
                        .border(1.dp, CscGlassBorder, CircleShape)
                        .testTag("login_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Torna indietro",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Accedi al tuo account",
                    fontSize = 18.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Brand Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF131D31),
                                CscNightBlue
                            )
                        )
                    )
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = CscOrange)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF162035))
                            .border(1.5.dp, CscOrange, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_csc_logo),
                            contentDescription = "Logo CSC",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CSC ",
                            fontSize = 20.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "SecurityCam",
                            fontSize = 20.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = CscOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Accedi per sincronizzare videocamere, notifiche ed eventi",
                        fontSize = 12.sp,
                        fontFamily = MontserratFontFamily,
                        color = CscSecondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Email Field
                Column {
                    Text(
                        text = "EMAIL",
                        fontSize = 11.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = CscWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            if (emailError != null) emailError = null
                        },
                        placeholder = {
                            Text("es. operatore@csc.com", color = CscSecondaryText.copy(alpha = 0.6f), fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = CscOrange, modifier = Modifier.size(20.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CscOrange,
                            unfocusedBorderColor = CscGlassBorder,
                            focusedContainerColor = CscNightBlue,
                            unfocusedContainerColor = Color(0xFF0C1322),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
                        isError = emailError != null
                    )
                    if (emailError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = emailError ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontFamily = MontserratFontFamily
                        )
                    }
                }

                // Password Field
                Column {
                    Text(
                        text = "PASSWORD",
                        fontSize = 11.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = CscWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (passwordError != null) passwordError = null
                        },
                        placeholder = {
                            Text("Inserisci la tua password", color = CscSecondaryText.copy(alpha = 0.6f), fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CscOrange, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "Nascondi password" else "Mostra password",
                                    tint = CscSecondaryText
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { validateAndSubmit() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CscOrange,
                            unfocusedBorderColor = CscGlassBorder,
                            focusedContainerColor = CscNightBlue,
                            unfocusedContainerColor = Color(0xFF0C1322),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
                        isError = passwordError != null
                    )
                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passwordError ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontFamily = MontserratFontFamily
                        )
                    }
                }

                // Row: Ricordami & Password dimenticata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CscOrange,
                                uncheckedColor = CscSecondaryText,
                                checkmarkColor = Color.White
                            )
                        )
                        Text(
                            text = "Ricordami",
                            fontSize = 13.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscWhite
                        )
                    }

                    Text(
                        text = "Password dimenticata?",
                        fontSize = 13.sp,
                        fontFamily = MontserratFontFamily,
                        color = CscOrange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { showForgotPasswordDialog = true }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Submit Button - Accedi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(50.dp),
                        spotColor = CscOrange,
                        ambientColor = CscNeonGlow
                    )
            ) {
                Button(
                    onClick = { validateAndSubmit() },
                    modifier = Modifier.fillMaxSize().testTag("login_submit_btn"),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CscOrange,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "Accedi",
                        fontSize = 16.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onGoogleSignIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_google_btn"),
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, CscGlassBorder)
            ) {
                Text(
                    text = "Continua con Google",
                    fontSize = 15.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Switch to Register or Guest
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Non hai ancora un account? ",
                    fontSize = 13.sp,
                    fontFamily = MontserratFontFamily,
                    color = CscSecondaryText
                )
                Text(
                    text = "Registrati",
                    fontSize = 13.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CscOrange,
                    modifier = Modifier
                        .clickable { onNavigateToRegister() }
                        .padding(4.dp)
                        .testTag("login_go_to_register_btn")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Oppure ",
                    fontSize = 12.sp,
                    fontFamily = MontserratFontFamily,
                    color = CscSecondaryText
                )
                Text(
                    text = "Continua come ospite",
                    fontSize = 12.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .clickable { onContinueAsGuest() }
                        .padding(4.dp)
                        .testTag("login_guest_shortcut_btn")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security note info card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CscOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Modalità Autenticazione: Le credenziali sono salvate localmente in sicurezza. Per il cloud-sync remoto è supportato l'accesso WebRTC.",
                    fontSize = 11.sp,
                    fontFamily = MontserratFontFamily,
                    color = CscSecondaryText,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = CscPanel,
            title = {
                Text(
                    text = "Recupero Password",
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Inserisci l'indirizzo email associato al tuo profilo CSC SecurityCam. Riceverai un link sicuro per reimpostare la password.",
                    fontFamily = MontserratFontFamily,
                    fontSize = 13.sp,
                    color = CscWhite
                )
            },
            confirmButton = {
                Button(
                    onClick = { showForgotPasswordDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CscOrange)
                ) {
                    Text("Invia Link", color = Color.White, fontFamily = MontserratFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Annulla", color = CscSecondaryText, fontFamily = MontserratFontFamily)
                }
            }
        )
    }
}
