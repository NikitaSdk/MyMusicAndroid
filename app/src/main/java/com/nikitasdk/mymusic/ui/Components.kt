package com.nikitasdk.mymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nikitasdk.mymusic.data.Downloads
import com.nikitasdk.mymusic.data.Library
import com.nikitasdk.mymusic.data.Network
import com.nikitasdk.mymusic.data.Song
import com.nikitasdk.mymusic.data.formatDuration
import com.nikitasdk.mymusic.playback.PlayerConnection

/** Обложка: из локального файла (работает офлайн) или из сети. */
@Composable
fun Artwork(song: Song?, size: Dp, corner: Dp = 10.dp, modifier: Modifier = Modifier) {
    ArtworkBox(song, modifier.size(size), corner)
}

@Composable
fun ArtworkBox(song: Song?, modifier: Modifier, corner: Dp) {
    val downloaded by Downloads.downloaded.collectAsStateWithLifecycle()
    val model: Any? = song?.let { s ->
        if (s.id in downloaded) Downloads.thumbFile(s.id) ?: s.thumbnail else s.thumbnail
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Palette.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Palette.TextDim.copy(alpha = 0.5f))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
    )
}

/** Кнопка-«таблетка» с градиентом или нейтральная. */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(CircleShape)
            .then(
                if (primary) Modifier.background(Palette.AccentGradient)
                else Modifier.background(Palette.SurfaceHigh)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (primary) Color.White else Palette.Text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (primary) Color.White else Palette.Text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PlayShuffleRow(songs: List<Song>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PillButton("Слушать", Icons.Rounded.PlayArrow, true, { PlayerConnection.play(songs) }, Modifier.weight(1f))
        PillButton("Перемешать", Icons.Rounded.Shuffle, false, { PlayerConnection.shuffle(songs) }, Modifier.weight(1f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(Palette.AccentGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Список треков с общей логикой нажатий. */
@Composable
fun SongList(
    songs: List<Song>,
    contentPadding: PaddingValues,
    header: (@Composable () -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
    removeLabel: String = "Удалить"
) {
    val current by PlayerConnection.state.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = contentPadding) {
        if (header != null) item { header() }
        items(songs.size, key = { songs[it].id }) { index ->
            SongRow(
                song = songs[index],
                isCurrent = current.song?.id == songs[index].id,
                onClick = { PlayerConnection.play(songs, index) },
                onRemove = onRemove?.let { remove -> { remove(songs[index]) } },
                removeLabel = removeLabel
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Удалить"
) {
    val online by Network.online.collectAsStateWithLifecycle()
    val downloaded by Downloads.downloaded.collectAsStateWithLifecycle()
    val progress by Downloads.progress.collectAsStateWithLifecycle()
    val saved by Library.saved.collectAsStateWithLifecycle()
    val isDownloaded = song.id in downloaded
    val available = online || isDownloaded
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Palette.Surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            .alpha(if (available) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Palette.Accent else Palette.Text
            )
            val sub = listOf(song.artist, formatDuration(song.duration)).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        val p = progress[song.id]
        when {
            p != null -> CircularProgressIndicator(
                progress = { p },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = Palette.Accent,
                trackColor = Palette.SurfaceHigh
            )
            isDownloaded -> Icon(Icons.Rounded.DownloadDone, "Скачано", tint = Palette.Accent, modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Ещё", tint = Palette.TextDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Играть следующим") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                    onClick = { menu = false; PlayerConnection.playNext(song) }
                )
                val isSaved = song.id in saved
                DropdownMenuItem(
                    text = { Text(if (isSaved) "Убрать из медиатеки" else "В медиатеку") },
                    leadingIcon = { Icon(if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                    onClick = { menu = false; Library.setSaved(song, !isSaved) }
                )
                when {
                    isDownloaded -> DropdownMenuItem(
                        text = { Text("Удалить загрузку") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; Downloads.delete(song.id) }
                    )
                    p == null -> DropdownMenuItem(
                        text = { Text("Скачать") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { menu = false; Downloads.start(song) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Добавить в плейлист") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; addDialog = true }
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text(removeLabel) },
                        leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                        onClick = { menu = false; onRemove() }
                    )
                }
            }
        }
    }

    if (addDialog) {
        AddToPlaylistDialog(song) { addDialog = false }
    }
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Добавить в плейлист") },
        text = {
            Column {
                playlists.forEach { pl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { Library.addToPlaylist(pl.id, song); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = Palette.Accent)
                        Spacer(Modifier.width(12.dp))
                        Text(pl.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pl.songIds.size}", color = Palette.TextDim)
                    }
                }
                Spacer(Modifier.height(8.dp))
                NameField(newName, { newName = it }, "Новый плейлист")
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    val pl = Library.createPlaylist(newName)
                    Library.addToPlaylist(pl.id, song)
                    onDismiss()
                }
            ) { Text("Создать и добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun NameField(value: String, onChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Add, null) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Palette.SurfaceHigh,
            unfocusedContainerColor = Palette.SurfaceHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
