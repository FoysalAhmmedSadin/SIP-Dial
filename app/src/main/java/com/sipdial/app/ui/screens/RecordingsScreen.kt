package com.sipdial.app.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.ui.theme.JetBrainsMono
import com.sipdial.app.util.CallerIdFormatter
import com.sipdial.app.util.ContactsHelper
import com.sipdial.app.util.DeviceContact
import com.sipdial.app.util.RecordingStorage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordedCalls: List<CallRecord>,
    contacts: List<DeviceContact>,
    onBack: () -> Unit,
    onDeleteRecording: (CallRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val existingRecords = remember(recordedCalls) {
        recordedCalls.filter { RecordingStorage.exists(context, it.recordingPath) }
    }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<CallRecord?>(null) }

    fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        playingId = null
        positionMs = 0
    }

    DisposableEffect(Unit) {
        onDispose { stopPlayback() }
    }

    LaunchedEffect(playingId) {
        while (playingId != null) {
            try { player?.let { positionMs = it.currentPosition } } catch (_: Exception) {}
            delay(200)
        }
    }

    fun togglePlay(rec: CallRecord) {
        val wasSame = playingId == rec.id
        stopPlayback()
        if (wasSame) return
        try {
            val mp = MediaPlayer().apply {
                RecordingStorage.setDataSource(this, context, rec.recordingPath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            player = mp
            durationMs = mp.duration
            playingId = rec.id
        } catch (_: Exception) {
            stopPlayback()
            Toast.makeText(context, "Unable to play recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareRecording(rec: CallRecord) {
        try {
            val uri: Uri = if (rec.recordingPath.startsWith("content://")) {
                Uri.parse(rec.recordingPath)
            } else {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(rec.recordingPath)
                )
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share call recording"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to share this recording", Toast.LENGTH_SHORT).show()
        }
    }

    deleteTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete recording?") },
            text = { Text("This deletes the audio file permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playingId == rec.id) stopPlayback()
                        deleteTarget = null
                        onDeleteRecording(rec)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.sipdial.app.ui.components.SipDialTopBar(
                title = "Call Recordings",
                onBack = onBack,
                backTestTag = "recordings_back"
            )
        }
    ) { paddingValues ->
        if (existingRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text("No Call Recordings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Enable Auto Call Recording in Calling Settings or tap Record during an active SIP call.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(existingRecords, key = { it.id }) { rec ->
                    val cleanNum = CallerIdFormatter.extractNumber(rec.remoteAddress)
                    val title = ContactsHelper.nameFor(cleanNum, contacts)
                        ?: rec.displayName.ifBlank { cleanNum }
                    val stamp = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.US).format(Date(rec.timestamp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val accountTag = if (rec.isSimCall()) "SIM" else "SIP • ${rec.resolvedAccountName()}"
                                    Text(
                                        text = "$accountTag • $stamp • ${rec.duration}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { togglePlay(rec) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (playingId == rec.id) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playingId == rec.id) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { shareRecording(rec) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share recording",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { deleteTarget = rec },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete recording",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (playingId == rec.id) {
                                AudioPlaybackBar(
                                    progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                                    onSeek = { f ->
                                        val p = (f * durationMs).toInt()
                                        positionMs = p
                                        try { player?.seekTo(p) } catch (_: Exception) {}
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Playing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "%02d:%02d / %02d:%02d".format(
                                            (positionMs / 1000) / 60,
                                            (positionMs / 1000) % 60,
                                            (durationMs / 1000) / 60,
                                            (durationMs / 1000) % 60
                                        ),
                                        fontFamily = JetBrainsMono,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
