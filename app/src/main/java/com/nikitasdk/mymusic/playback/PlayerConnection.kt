package com.nikitasdk.mymusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nikitasdk.mymusic.data.Downloads
import com.nikitasdk.mymusic.data.Library
import com.nikitasdk.mymusic.data.Network
import com.nikitasdk.mymusic.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false
)

/** Связь интерфейса с фоновым сервисом плеера. */
object PlayerConnection {
    private var controller: MediaController? = null
    private var appContext: Context? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onPlayerError(error: PlaybackException) {
            appContext?.let {
                Toast.makeText(it, "Не удалось воспроизвести трек", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun connect(context: Context) {
        if (controller != null) return
        appContext = context.applicationContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let {
                controller = it
                it.addListener(listener)
                refresh()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.value = PlayerState(
            song = item?.toSong(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            position = c.currentPosition.coerceAtLeast(0),
            duration = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem()
        )
    }

    // --- Команды ---

    fun play(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val online = Network.online.value
        // Без интернета играем только скачанное
        val playable = if (online) songs else songs.filter { Downloads.isDownloaded(it.id) }
        val start = playable.indexOfFirst { it.id == songs.getOrNull(startIndex)?.id }
        if (start < 0) {
            appContext?.let { Toast.makeText(it, "Нет интернета: доступны только скачанные треки", Toast.LENGTH_SHORT).show() }
            return
        }
        Library.rememberAll(playable)
        c.shuffleModeEnabled = false
        c.setMediaItems(playable.map { it.toMediaItem() }, start, 0)
        c.prepare()
        c.play()
    }

    fun shuffle(songs: List<Song>) {
        if (songs.isEmpty()) return
        play(songs.shuffled(), 0)
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        Library.remember(song)
        if (c.mediaItemCount == 0) {
            play(listOf(song))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // --- Преобразования ---

    private fun Song.toMediaItem(): MediaItem {
        val uri = Downloads.file(id)?.let { Uri.fromFile(it) } ?: Uri.parse("yt://$id")
        val art = Downloads.thumbFile(id)?.let { Uri.fromFile(it) } ?: thumbnail?.let { Uri.parse(it) }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(art)
                    .build()
            )
            .build()
    }

    private fun MediaItem.toSong(): Song =
        Library.song(mediaId) ?: Song(
            id = mediaId,
            title = mediaMetadata.title?.toString() ?: "",
            artist = mediaMetadata.artist?.toString() ?: "",
            duration = 0,
            thumbnail = mediaMetadata.artworkUri?.toString()
        )
}
