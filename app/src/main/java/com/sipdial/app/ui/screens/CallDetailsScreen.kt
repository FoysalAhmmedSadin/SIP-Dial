package com.sipdial.app.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.ui.components.ContactAvatar
import com.sipdial.app.ui.theme.JetBrainsMono
import com.sipdial.app.ui.theme.LocalSemanticColors
import com.sipdial.app.util.RecordingStorage
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WhatsAppColor = Color(0xFF25D366)
private val TelegramColor = Color(0xFF229ED9)

private fun detailDate(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(ts))
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today $time"
        sameYear && dayDiff == 1 -> "Yesterday $time"
        else -> SimpleDateFormat("MMM dd, h:mm a", Locale.US).format(Date(ts))
    }
}

private fun durationText(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return when {
        m > 0 && s > 0 -> "$m min $s sec"
        m > 0 -> "$m min"
        else -> "$s sec"
    }
}

private fun recordStatusText(r: CallRecord): String = when {
    r.isIncoming && !r.isAnswered -> "Missed"
    !r.isIncoming && r.duration <= 0 -> "Not connected"
    r.isIncoming -> "Received • ${durationText(r.duration)}"
    else -> "Outgoing • ${durationText(r.duration)}"
}

private fun formatClockMs(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailsScreen(
    number: String,
    name: String?,
    photoUri: String?,
    records: List<CallRecord>,
    isFavorite: Boolean,
    isBlocked: Boolean,
    detectMessengers: suspend (String) -> Pair<Boolean, Boolean>,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onOpenChat: (app: String, number: String) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToCategory: (category: String) -> Unit,
    onToggleBlock: () -> Unit,
    onDeleteRecording: (CallRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current

    var menuOpen by remember { mutableStateOf(false) }
    var messengers by remember(number) { mutableStateOf(false to false) }
    var deleteRecTarget by remember { mutableStateOf<CallRecord?>(null) }

    LaunchedEffect(number) {
        messengers = try { detectMessengers(number) } catch (_: Exception) { false to false }
    }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }

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

    fun togglePlay(r: CallRecord) {
        val wasThis = playingId == r.id
        stopPlayback()
        if (wasThis) return
        try {
            val mp = MediaPlayer().apply {
                RecordingStorage.setDataSource(this, ctx, r.recordingPath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            player = mp
            durationMs = mp.duration
            playingId = r.id
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    fun shareRecording(r: CallRecord) {
        try {
            val uri: Uri = if (r.recordingPath.startsWith("content://")) {
                Uri.parse(r.recordingPath)
            } else {
                FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    File(r.recordingPath)
                )
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share call recording"))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Unable to share this recording", Toast.LENGTH_SHORT).show()
        }
    }

    deleteRecTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteRecTarget = null },
            title = { Text("Delete call recording?") },
            text = { Text("Removes the audio recording file from storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playingId == rec.id) stopPlayback()
                        deleteRecTarget = null
                        onDeleteRecording(rec)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRecTarget = null }) { Text("Cancel") }
            }
        )
    }

    val shownName = name?.takeIf { it.isNotBlank() } ?: number

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.sipdial.app.ui.components.SipDialTopBar(
                title = "Call Details",
                onBack = onBack,
                backTestTag = "call_details_back",
                actions = {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("call_details_favorite")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                            tint = if (isFavorite) semantic.warning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.testTag("call_details_menu")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text("Add to Client") },
                                onClick = { menuOpen = false; onAddToCategory("client") }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text("Add to Office") },
                                onClick = { menuOpen = false; onAddToCategory("office") }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isBlocked) "Unblock number" else "Block number") },
                                onClick = { menuOpen = false; onToggleBlock() }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactAvatar(
                        name = shownName,
                        photoUri = photoUri,
                        size = 86.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = shownName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isBlocked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Blocked Number",
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.danger
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = number,
                                fontFamily = JetBrainsMono,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            DetailCircleAction(
                                icon = Icons.Filled.Call,
                                desc = "Call",
                                tint = MaterialTheme.colorScheme.primary,
                                bg = MaterialTheme.colorScheme.primaryContainer,
                                onClick = onCall
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            DetailCircleAction(
                                icon = Icons.Filled.Sms,
                                desc = "Message",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                bg = MaterialTheme.colorScheme.secondaryContainer,
                                onClick = onMessage
                            )
                        }
                        if (messengers.first) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            MessengerActionRow("Message on WhatsApp", WhatsAppColor) {
                                onOpenChat("whatsapp", number)
                            }
                        }
                        if (messengers.second) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            MessengerActionRow("Message on Telegram", TelegramColor) {
                                onOpenChat("telegram", number)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Call History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (records.isEmpty()) {
                            Text(
                                text = "No calls recorded for this number.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        records.forEachIndexed { idx, r ->
                            val missed = r.isIncoming && !r.isAnswered
                            val hasRec = remember(r.recordingPath) {
                                RecordingStorage.exists(ctx, r.recordingPath)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = detailDate(r.timestamp),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (missed) semantic.danger else MaterialTheme.colorScheme.onSurface
                                        )
                                        val accountLineText = if (r.isSimCall()) {
                                            "SIM Line"
                                        } else {
                                            "SIP • ${r.resolvedAccountName()}"
                                        }
                                        Text(
                                            text = accountLineText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = recordStatusText(r),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (missed) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (hasRec) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { togglePlay(r) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (playingId == r.id) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                    contentDescription = "Play recording",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { shareRecording(r) },
                                            modifier = Modifier.size(48.dp).testTag("call_details_share_rec_${r.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "Share recording",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { deleteRecTarget = r },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete recording",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                if (hasRec && playingId == r.id) {
                                    Spacer(modifier = Modifier.height(6.dp))
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
                                            text = "Recorded ${durationText(r.duration)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatClockMs(positionMs),
                                            fontFamily = JetBrainsMono,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (idx < records.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioPlaybackBar(
    progress: Float,
    onSeek: (Float) -> Unit
) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    var widthPx by remember { mutableIntStateOf(1) }
    val p = progress.coerceIn(0f, 1f)
    val thumbPx = with(density) { 20.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            Modifier
                .offset { IntOffset((p * (widthPx - thumbPx)).toInt(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, primary, CircleShape)
        )
    }
}

@Composable
private fun DetailCircleAction(
    icon: ImageVector,
    desc: String,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MessengerActionRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
