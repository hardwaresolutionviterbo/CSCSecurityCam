package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Premium
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanel
import com.example.ui.theme.CscSecondaryText
import com.example.ui.theme.MontserratFontFamily

/** Paywall del piano Free: compare solo quando si prova a superare il limite di telecamere. */
@Composable
fun PremiumUpsellDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("premium_upsell_dialog"),
        containerColor = CscPanel,
        title = {
            Text(
                text = "Passa a Premium",
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = "Con il piano Free puoi salvare ${Premium.FREE_CAMERA_LIMIT} telecamera. " +
                        "Con Premium ne aggiungi quante vuoi sullo stesso account.",
                    fontFamily = MontserratFontFamily,
                    fontSize = 13.sp,
                    color = CscSecondaryText
                )
                Spacer(modifier = Modifier.height(14.dp))
                listOf(
                    "Telecamere illimitate",
                    "Streaming e area personale su tutti i tuoi dispositivi",
                    "Disdetta quando vuoi"
                ).forEach { benefit ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CscOrange
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = benefit,
                            fontFamily = MontserratFontFamily,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CscOrange.copy(alpha = 0.12f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CscOrange
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Column {
                        Text(
                            text = Premium.PREMIUM_PRICE_LABEL,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = CscOrange
                        )
                        Text(
                            text = "Rinnovo automatico, disdici quando vuoi",
                            fontFamily = MontserratFontFamily,
                            fontSize = 11.sp,
                            color = CscSecondaryText
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(Premium.PLAY_STORE_URL))
                        )
                    }.isSuccess
                    if (opened) onDismiss()
                },
                modifier = Modifier.testTag("premium_upgrade_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CscOrange,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Abbonati • ${Premium.PREMIUM_PRICE_LABEL}",
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("premium_dismiss_btn")
            ) {
                Text(
                    text = "Non ora",
                    fontFamily = MontserratFontFamily,
                    color = CscSecondaryText
                )
            }
        }
    )
}
