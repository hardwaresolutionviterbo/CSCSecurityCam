package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.media.MediaFormat
import com.example.model.RecordingItem
import com.example.model.Screen
import com.example.ui.components.CscBottomNav
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscMidnightBlue
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscPanelBlueVariant
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTextMuted
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily
import com.example.viewmodel.MainUiState

@Composable
fun PlaybackScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onDeleteRecording: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    var selectedFilter by remember { mutableStateOf("Tutti") }
    var viewingRecording by remember { mutableStateOf<RecordingItem?>(null) }

    // Spazio reale del volume del telefono, non un 64 GB fisso.
    val usedStorage = (state.storageTotalBytes - state.storageFreeBytes).coerceAtLeast(0L)
    val storageProgress = if (state.storageTotalBytes > 0L) {
        (usedStorage.toDouble() / state.storageTotalBytes).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    val filteredList = remember(selectedFilter, state.recordings) {
        when (selectedFilter) {
            "Movimento" -> state.recordings.filter { it.isMotionEvent }
            "Istantanee" -> state.recordings.filter { it.isSnapshot }
            "Manuali" -> state.recordings.filter { !it.isMotionEvent && !it.isSnapshot }
            else -> state.recordings
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground,
        bottomBar = {
            CscBottomNav(
                currentScreen = Screen.PLAYBACK,
                onNavigate = onNavigate
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Torna indietro",
                        tint = CscTitleWhite
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Registrazioni",
                    fontSize = 20.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CscTitleWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            com.example.ui.components.SecurityHeroCard(
                title = "I tuoi momenti, sempre disponibili",
                subtitle = "Eventi, istantanee e registrazioni organizzati",
                modifier = Modifier.fillMaxWidth(),
                compact = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Storage Overview Card - Glassmorphism
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CscGlassCard)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Memoria Registrazioni",
                            fontSize = 13.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = CscTitleWhite
                        )
                        Text(
                            text = "${MediaFormat.formatSize(usedStorage)} / " +
                                MediaFormat.formatSize(state.storageTotalBytes),
                            fontSize = 12.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { storageProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CscOrange,
                        trackColor = Color(0xFF1E2838)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Registrazioni: ${MediaFormat.formatSize(state.recordingsBytes)}",
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscTextMuted
                        )
                        Text(
                            text = "Liberi: ${MediaFormat.formatSize(state.storageFreeBytes)}",
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscSubtitleGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips Row
            val filters = listOf("Tutti", "Movimento", "Istantanee", "Manuali")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CscOrange else CscGlassCard)
                            .border(
                                1.dp,
                                if (isSelected) CscOrange else CscGlassBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else CscTitleWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna registrazione in questa categoria",
                        color = CscTextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        RecordingCard(
                            item = item,
                            onPlay = { viewingRecording = item },
                            onDelete = { onDeleteRecording(item.id) }
                        )
                    }
                }
            }
        }
    }

    // Playback Preview Dialog
    val playerContext = androidx.compose.ui.platform.LocalContext.current

    viewingRecording?.let { item ->
        AlertDialog(
            onDismissRequest = { viewingRecording = null },
            containerColor = CscPanelBlue,
            title = {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isSnapshot && item.uri.isNotEmpty()) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_csc_logo),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(CscOrange.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${item.cameraName} • ${item.timestamp} • " +
                            if (item.isSnapshot) {
                                MediaFormat.formatSize(item.fileSizeBytes)
                            } else {
                                "${MediaFormat.formatDuration(item.durationSeconds)} · ${MediaFormat.formatSize(item.fileSizeBytes)}"
                            },
                        color = CscTextMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { openMedia(playerContext, item) },
                        colors = ButtonDefaults.buttonColors(containerColor = CscOrange)
                    ) {
                        Text("Apri", color = Color.White)
                    }
                    TextButton(onClick = { viewingRecording = null }) {
                        Text("Chiudi", color = CscTextMuted)
                    }
                }
            }
        )
    }
}

@Composable
fun RecordingCard(
    item: RecordingItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CscGlassCard)
            .border(1.dp, CscGlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with play badge
        Box(
            modifier = Modifier
                .size(76.dp, 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (item.isSnapshot && item.uri.isNotEmpty()) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_csc_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(CscOrange.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isSnapshot) Icons.Default.PhotoCamera else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Category badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                item.isMotionEvent -> CscOrange.copy(alpha = 0.25f)
                                item.isSnapshot -> Color(0x333B82F6)
                                else -> Color(0x33FFFFFF)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when {
                            item.isMotionEvent -> "MOVIMENTO"
                            item.isSnapshot -> "ISTANTANEA"
                            else -> "MANUALE"
                        },
                        fontSize = 9.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isMotionEvent -> CscOrange
                            item.isSnapshot -> Color(0xFF60A5FA)
                            else -> CscTitleWhite
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                fontSize = 13.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold,
                color = CscTitleWhite,
                maxLines = 1
            )

            Text(
                text = "${item.timestamp} • ${item.cameraName} • " +
                    if (item.isSnapshot) {
                        MediaFormat.formatSize(item.fileSizeBytes)
                    } else {
                        "${MediaFormat.formatDuration(item.durationSeconds)} · ${MediaFormat.formatSize(item.fileSizeBytes)}"
                    },
                fontSize = 11.sp,
                fontFamily = MontserratFontFamily,
                color = CscSubtitleGray,
                maxLines = 1
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Elimina registrazione",
                tint = CscSubtitleGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Apre il media salvato con l'app di sistema (galleria o lettore video). */
private fun openMedia(context: Context, item: RecordingItem) {
    val uri = item.uri.ifEmpty { item.filePath }
    if (uri.isEmpty()) {
        Toast.makeText(context, "File non disponibile", Toast.LENGTH_SHORT).show()
        return
    }
    val mime = item.mimeType.ifEmpty { if (item.isSnapshot) "image/jpeg" else "video/mp4" }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uri), mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Nessuna app per aprire il file", Toast.LENGTH_SHORT).show()
    }
}
