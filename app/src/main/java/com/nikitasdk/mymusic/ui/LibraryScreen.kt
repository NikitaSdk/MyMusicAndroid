package com.nikitasdk.mymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nikitasdk.mymusic.data.Downloads
import com.nikitasdk.mymusic.data.Library

@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    val songsMap by Library.songs.collectAsStateWithLifecycle()
    val saved by Library.saved.collectAsStateWithLifecycle()
    val downloaded by Downloads.downloaded.collectAsStateWithLifecycle()
    var onlyDownloaded by rememberSaveable { mutableStateOf(false) }

    val songs = saved.mapNotNull { songsMap[it] }.let { list ->
        if (onlyDownloaded) list.filter { it.id in downloaded } else list
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScreenTitle("Медиатека")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Сохранённое", !onlyDownloaded) { onlyDownloaded = false }
            Chip("Скачанное", onlyDownloaded) { onlyDownloaded = true }
        }
        if (songs.isEmpty()) {
            if (onlyDownloaded) {
                EmptyState(Icons.Rounded.DownloadForOffline, "Нет скачанных треков", "Нажмите «⋮» у трека → «Скачать», чтобы слушать без интернета")
            } else {
                EmptyState(Icons.Rounded.Favorite, "Пока пусто", "Нажмите «⋮» у трека → «В медиатеку»")
            }
        } else {
            SongList(
                songs = songs,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 8.dp),
                header = { PlayShuffleRow(songs) },
                onRemove = { Library.setSaved(it, false) },
                removeLabel = "Убрать из медиатеки"
            )
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.Surface,
            labelColor = Palette.TextDim,
            selectedContainerColor = Palette.Accent,
            selectedLabelColor = Color.White
        ),
        border = null
    )
}
