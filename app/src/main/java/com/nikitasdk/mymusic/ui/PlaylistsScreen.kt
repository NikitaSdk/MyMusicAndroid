package com.nikitasdk.mymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nikitasdk.mymusic.data.Library
import com.nikitasdk.mymusic.data.Playlist
import com.nikitasdk.mymusic.data.Song
import com.nikitasdk.mymusic.data.formatDuration

@Composable
fun PlaylistsScreen(contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val songsMap by Library.songs.collectAsStateWithLifecycle()
    var createDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScreenTitle("Плейлисты")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(Modifier.clickable { createDialog = true }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(Palette.AccentGradient),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Add, null, tint = Color.White) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Новый плейлист", style = MaterialTheme.typography.titleMedium)
                    Text(" ", color = Palette.TextDim)
                }
            }
            items(playlists, key = { it.id }) { pl ->
                Column(Modifier.clickable { onOpen(pl.id) }) {
                    Collage(pl.songIds.mapNotNull { songsMap[it] }, Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(8.dp))
                    Text(pl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tracksLabel(pl.songIds.size), color = Palette.TextDim)
                }
            }
        }
    }

    if (createDialog) {
        NameDialog(
            title = "Новый плейлист",
            initial = "",
            confirm = "Создать",
            onDismiss = { createDialog = false },
            onConfirm = { Library.createPlaylist(it); createDialog = false }
        )
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: String, contentPadding: PaddingValues, onBack: () -> Unit) {
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val songsMap by Library.songs.collectAsStateWithLifecycle()
    val playlist: Playlist? = playlists.firstOrNull { it.id == playlistId }
    if (playlist == null) {
        LaunchedEffect(playlistId) { onBack() }
        return
    }
    val songs = playlist.songIds.mapNotNull { songsMap[it] }
    var menu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Назад") }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; renameDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить плейлист") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; deleteDialog = true }
                    )
                }
            }
        }

        SongList(
            songs = songs,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 8.dp),
            header = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Collage(songs, Modifier.size(200.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                    val total = songs.sumOf { it.duration }
                    Text(
                        listOf(tracksLabel(songs.size), formatDuration(total)).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Palette.TextDim
                    )
                    Spacer(Modifier.height(8.dp))
                    if (songs.isNotEmpty()) {
                        PlayShuffleRow(songs)
                    } else {
                        EmptyState(Icons.Rounded.QueueMusic, "Плейлист пуст", "Найдите трек и нажмите «⋮» → «Добавить в плейлист»")
                    }
                }
            },
            onRemove = { Library.removeFromPlaylist(playlist.id, it.id) },
            removeLabel = "Убрать из плейлиста"
        )
    }

    if (renameDialog) {
        NameDialog("Переименовать", playlist.name, "Сохранить", { renameDialog = false }) {
            Library.renamePlaylist(playlist.id, it); renameDialog = false
        }
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            containerColor = Palette.Surface,
            title = { Text("Удалить «${playlist.name}»?") },
            text = { Text("Треки останутся в медиатеке и загрузках.") },
            confirmButton = {
                TextButton(onClick = { deleteDialog = false; Library.deletePlaylist(playlist.id); onBack() }) {
                    Text("Удалить", color = Palette.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Отмена") } }
        )
    }
}

/** Обложка плейлиста: коллаж 2×2 из обложек треков. */
@Composable
fun Collage(songs: List<Song>, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    when {
        songs.isEmpty() -> Box(
            modifier.clip(shape).background(Palette.AccentGradient),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(48.dp)) }

        songs.size < 4 -> ArtworkBox(songs.first(), modifier, 18.dp)

        else -> Column(modifier.clip(shape)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..1) {
                        ArtworkBox(songs[row * 2 + col], Modifier.weight(1f).fillMaxSize(), 0.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = { NameField(name, { name = it }, "Название") },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

fun tracksLabel(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "треков"
        n % 10 == 1 -> "трек"
        n % 10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$n $word"
}
