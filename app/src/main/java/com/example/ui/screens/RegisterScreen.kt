package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CscDeepBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscNeonGlow
import com.example.ui.theme.CscNightBlue
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanel
import com.example.ui.theme.CscSecondaryText
import com.example.ui.theme.CscWhite
import com.example.ui.theme.MontserratFontFamily

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegisterSuccess: (firstName: String, lastName: String, email: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val focusManager = LocalFocusManager.current

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }

    var firstNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var termsError by remember { mutableStateOf<String?>(null) }
    var showTermsDialog by remember { mutableStateOf(false) }

    fun validateAndSubmit() {
        focusManager.clearFocus()
        var valid = true

        if (firstName.trim().isEmpty()) {
            firstNameError = "Inserisci il tuo nome"
            valid = false
        } else {
            firstNameError = null
        }

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
            passwordError = "Inserisci una password"
            valid = false
        } else if (password.length < 6) {
            passwordError = "Almeno 6 caratteri"
            valid = false
        } else {
            passwordError = null
        }

        if (confirmPassword != password) {
            confirmPasswordError = "Le password non coincidono"
            valid = false
        } else {
            confirmPasswordError = null
        }

        if (!acceptedTerms) {
            termsError = "Devi accettare i Termini e la Privacy Policy per continuare"
            valid = false
        } else {
            termsError = null
        }

        if (valid) {
            onRegisterSuccess(firstName.trim(), lastName.trim(), trimmedEmail)
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
                        .testTag("register_back_btn")
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
                    text = "Crea nuovo account",
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
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = CscOrange)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF162035))
                            .border(1.dp, CscOrange, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_csc_logo),
                            contentDescription = "Logo CSC",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CSC ",
                                fontSize = 18.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "SecurityCam",
                                fontSize = 18.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CscOrange
                            )
                        }
                        Text(
                            text = "Protezione intelligente e videosorveglianza",
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscSecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Registration Form
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Nome e Cognome Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Nome
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NOME *",
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = CscWhite,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = {
                                firstName = it
                                if (firstNameError != null) firstNameError = null
                            },
                            placeholder = { Text("Mario", color = CscSecondaryText.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CscOrange,
                                unfocusedBorderColor = CscGlassBorder,
                                focusedContainerColor = CscNightBlue,
                                unfocusedContainerColor = Color(0xFF0C1322),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("register_name_input"),
                            isError = firstNameError != null
                        )
                        if (firstNameError != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(firstNameError ?: "", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }
                    }

                    // Cognome
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COGNOME",
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = CscWhite,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = { Text("Rossi", color = CscSecondaryText.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CscOrange,
                                unfocusedBorderColor = CscGlassBorder,
                                focusedContainerColor = CscNightBlue,
                                unfocusedContainerColor = Color(0xFF0C1322),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("register_surname_input")
                        )
                    }
                }

                // Email
                Column {
                    Text(
                        text = "EMAIL *",
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
                        placeholder = { Text("es. mario.rossi@email.com", color = CscSecondaryText.copy(alpha = 0.5f), fontSize = 13.sp) },
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
                        modifier = Modifier.fillMaxWidth().testTag("register_email_input"),
                        isError = emailError != null
                    )
                    if (emailError != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(emailError ?: "", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }

                // Password
                Column {
                    Text(
                        text = "PASSWORD *",
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
                        placeholder = { Text("Minimo 6 caratteri", color = CscSecondaryText.copy(alpha = 0.5f), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CscOrange, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = CscSecondaryText
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CscOrange,
                            unfocusedBorderColor = CscGlassBorder,
                            focusedContainerColor = CscNightBlue,
                            unfocusedContainerColor = Color(0xFF0C1322),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("register_password_input"),
                        isError = passwordError != null
                    )
                    if (passwordError != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(passwordError ?: "", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }

                // Conferma Password
                Column {
                    Text(
                        text = "CONFERMA PASSWORD *",
                        fontSize = 11.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = CscWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            if (confirmPasswordError != null) confirmPasswordError = null
                        },
                        placeholder = { Text("Ripeti la password", color = CscSecondaryText.copy(alpha = 0.5f), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CscOrange, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = CscSecondaryText
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                        modifier = Modifier.fillMaxWidth().testTag("register_confirm_password_input"),
                        isError = confirmPasswordError != null
                    )
                    if (confirmPasswordError != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(confirmPasswordError ?: "", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }

                // Accettazione Termini e Privacy
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptedTerms,
                            onCheckedChange = {
                                acceptedTerms = it
                                if (termsError != null) termsError = null
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CscOrange,
                                uncheckedColor = CscSecondaryText,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.testTag("register_terms_checkbox")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Accetto i Termini e Condizioni e la Privacy Policy di CSC SecurityCam",
                            fontSize = 12.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscWhite,
                            modifier = Modifier.clickable { showTermsDialog = true }
                        )
                    }
                    if (termsError != null) {
                        Text(
                            text = termsError ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Submit Button - Crea Account
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
                    modifier = Modifier.fillMaxSize().testTag("register_submit_btn"),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CscOrange,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = "Crea account",
                        fontSize = 16.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Switch to Login
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hai già un account? ",
                    fontSize = 13.sp,
                    fontFamily = MontserratFontFamily,
                    color = CscSecondaryText
                )
                Text(
                    text = "Accedi",
                    fontSize = 13.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CscOrange,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .padding(4.dp)
                        .testTag("register_go_to_login_btn")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Terms & Privacy Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = CscPanel,
            title = {
                Text(
                    text = "Termini & Privacy Policy",
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. Trattamento Dati: Il flusso video generato dalla videocamera rimane prioritariamente all'interno della rete locale (LAN).",
                        fontFamily = MontserratFontFamily,
                        fontSize = 12.sp,
                        color = CscWhite
                    )
                    Text(
                        text = "2. Connessione Remota P2P: Se abilitata la modalità WebRTC, la crittografia DTLS/SRTP protegge i frame scambiati tra i due dispositivi.",
                        fontFamily = MontserratFontFamily,
                        fontSize = 12.sp,
                        color = CscWhite
                    )
                    Text(
                        text = "3. Registrazioni e Privacy: I file video e le istantanee risiedono sulla memoria fisica del dispositivo e non vengono divulgati a terzi.",
                        fontFamily = MontserratFontFamily,
                        fontSize = 12.sp,
                        color = CscWhite
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        acceptedTerms = true
                        showTermsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CscOrange)
                ) {
                    Text("Accetta", color = Color.White, fontFamily = MontserratFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Chiudi", color = CscSecondaryText, fontFamily = MontserratFontFamily)
                }
            }
        )
    }
}
