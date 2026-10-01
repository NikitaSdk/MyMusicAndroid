package com.nikitasdk.mymusic.data

data class Song(
    val id: String,          // videoId на YouTube
    val title: String,
    val artist: String,
    val duration: Long,      // секунды, 0 если неизвестно
    val thumbnail: String?
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String>
)

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
