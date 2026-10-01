package com.nikitasdk.mymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.nikitasdk.mymusic.data.Downloads
import com.nikitasdk.mymusic.data.Library
import com.nikitasdk.mymusic.playback.PlayerConnection

/** Мини-плеер над нижней панелью. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val state by PlayerConnection.state.collectAsStateWithLifecycle()
    val song = state.song ?: return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, 46.dp, corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextDim, fontSize = 13.sp)
            }
            PlayPauseButton(state.isPlaying, state.isBuffering, size = 44, iconSize = 26)
            IconButton(onClick = { PlayerConnection.next() }, enabled = state.hasNext) {
                Icon(Icons.Rounded.SkipNext, "Следующий", tint = if (state.hasNext) Palette.Text else Palette.TextDim)
            }
        }
        val fraction = if (state.duration > 0) state.position.toFloat() / state.duration else 0f
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Palette.Accent,
            trackColor = Color.Transparent
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, isBuffering: Boolean, size: Int, iconSize: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Palette.AccentGradient)
            .clickable { PlayerConnection.togglePlay() },
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            CircularProgressIndicator(Modifier.size((iconSize - 2).dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "Пауза" else "Играть",
                tint = Color.White,
                modifier = Modifier.size(iconSize.dp)
            )
        }
    }
}

/** Полноэкранный плеер с размытой обложкой на фоне. */
@Composable
fun FullPlayer(onClose: () -> Unit) {
    val state by PlayerConnection.state.collectAsStateWithLifecycle()
    val saved by Library.saved.collectAsStateWithLifecycle()
    val downloaded by Downloads.downloaded.collectAsStateWithLifecycle()
    val progress by Downloads.progress.collectAsStateWithLifecycle()
    val song = state.song
    var dragging by remember { mutableStateOf<Float?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Palette.Background).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        val art: Any? = song?.let { Downloads.thumbFile(it.id) ?: it.thumbnail }
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.35f), Palette.Background.copy(alpha = 0.85f), Palette.Background)
                )
            )
        )

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", modifier = Modifier.size(32.dp))
                }
                Text(
                    "СЕЙЧАС ИГРАЕТ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextDim,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(1f))

            ArtworkBox(
                song,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Palette.Accent, spotColor = Palette.Accent),
                24.dp
            )

            Spacer(Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(song?.artist ?: "", color = Palette.TextDim, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (song != null) {
                    val isSaved = song.id in saved
                    IconButton(onClick = { Library.setSaved(song, !isSaved) }) {
                        Icon(
                            if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "В медиатеку",
                            tint = if (isSaved) Palette.Accent else Palette.Text,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val duration = state.duration.coerceAtLeast(1)
            val fraction = dragging ?: (state.position.toFloat() / duration).coerceIn(0f, 1f)
            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { PlayerConnection.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Palette.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatMs((fraction * state.duration).toLong()), color = Palette.TextDim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatMs(state.duration), color = Palette.TextDim, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerConnection.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Перемешать", tint = if (state.shuffle) Palette.Accent else Palette.TextDim)
                }
                IconButton(onClick = { PlayerConnection.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Предыдущий", modifier = Modifier.size(40.dp))
                }
                PlayPauseButton(state.isPlaying, state.isBuffering, size = 76, iconSize = 40)
                IconButton(onClick = { PlayerConnection.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Следующий", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { PlayerConnection.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Повтор",
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Palette.TextDim else Palette.Accent
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (song != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val p = progress[song.id]
                    val isDownloaded = song.id in downloaded
                    when {
                        p != null -> PillButton("${(p * 100).toInt()}%", Icons.Rounded.Download, false, {})
                        isDownloaded -> PillButton("Скачано", Icons.Rounded.DownloadDone, false, { Downloads.delete(song.id) })
                        else -> PillButton("Скачать", Icons.Rounded.Download, false, { Downloads.start(song) })
                    }
                    PillButton("В плейлист", Icons.Rounded.PlaylistAdd, false, { addDialog = true })
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    if (addDialog && song != null) {
        AddToPlaylistDialog(song) { addDialog = false }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
