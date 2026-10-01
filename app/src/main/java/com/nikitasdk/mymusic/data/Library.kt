package com.nikitasdk.mymusic.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Медиатека: сохранённые треки, плейлисты и история поиска. Хранится в JSON-файле. */
object Library {
    private lateinit var file: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _songs = MutableStateFlow<Map<String, Song>>(emptyMap())
    private val _saved = MutableStateFlow<List<String>>(emptyList())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _history = MutableStateFlow<List<String>>(emptyList())

    val songs: StateFlow<Map<String, Song>> = _songs
    val saved: StateFlow<List<String>> = _saved
    val playlists: StateFlow<List<Playlist>> = _playlists
    val history: StateFlow<List<String>> = _history

    fun init(context: Context) {
        file = File(context.filesDir, "library.json")
        load()
    }

    fun song(id: String): Song? = _songs.value[id]

    fun remember(song: Song) {
        val existing = _songs.value[song.id]
        if (existing == song) return
        // не затираем известную длительность пустой
        val merged = if (existing != null && song.duration == 0L) song.copy(duration = existing.duration) else song
        _songs.update { it + (song.id to merged) }
        persist()
    }

    fun rememberAll(list: List<Song>) {
        _songs.update { map -> map + list.filter { it.id !in map }.associateBy { it.id } }
        persist()
    }

    // --- Сохранённое ---

    fun isSaved(id: String) = id in _saved.value

    fun setSaved(song: Song, saved: Boolean) {
        remember(song)
        _saved.update { list ->
            if (saved) listOf(song.id) + (list - song.id) else list - song.id
        }
        persist()
    }

    // --- Плейлисты ---

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim(), emptyList())
        _playlists.update { it + playlist }
        persist()
        return playlist
    }

    fun renamePlaylist(id: String, name: String) {
        _playlists.update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }
        persist()
    }

    fun deletePlaylist(id: String) {
        _playlists.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        remember(song)
        _playlists.update { list ->
            list.map {
                if (it.id == playlistId && song.id !in it.songIds) it.copy(songIds = it.songIds + song.id) else it
            }
        }
        persist()
    }

    fun removeFromPlaylist(playlistId: String, songId: String) {
        _playlists.update { list ->
            list.map { if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it }
        }
        persist()
    }

    // --- История поиска ---

    fun addHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _history.update { (listOf(q) + it.filterNot { h -> h.equals(q, ignoreCase = true) }).take(10) }
        persist()
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist()
    }

    // --- Хранение ---

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val songsJson = root.optJSONArray("songs") ?: JSONArray()
            val songs = HashMap<String, Song>()
            for (i in 0 until songsJson.length()) {
                val o = songsJson.getJSONObject(i)
                val s = Song(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    duration = o.optLong("duration"),
                    thumbnail = o.optString("thumbnail").ifEmpty { null }
                )
                songs[s.id] = s
            }
            _songs.value = songs
            _saved.value = root.optJSONArray("saved").toStringList()
            _history.value = root.optJSONArray("history").toStringList()
            val pls = root.optJSONArray("playlists") ?: JSONArray()
            _playlists.value = (0 until pls.length()).map { i ->
                val o = pls.getJSONObject(i)
                Playlist(o.getString("id"), o.optString("name"), o.optJSONArray("songs").toStringList())
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun persist() {
        val songs = _songs.value
        val saved = _saved.value
        val playlists = _playlists.value
        val history = _history.value
        scope.launch {
            writeLock.withLock {
                val root = JSONObject()
                root.put("songs", JSONArray().apply {
                    songs.values.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id)
                            put("title", s.title)
                            put("artist", s.artist)
                            put("duration", s.duration)
                            put("thumbnail", s.thumbnail ?: "")
                        })
                    }
                })
                root.put("saved", JSONArray(saved))
                root.put("history", JSONArray(history))
                root.put("playlists", JSONArray().apply {
                    playlists.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("songs", JSONArray(p.songIds))
                        })
                    }
                })
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            }
        }
    }
}
