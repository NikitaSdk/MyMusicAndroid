package com.nikitasdk.mymusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nikitasdk.mymusic.MainActivity
import com.nikitasdk.mymusic.data.Downloads
import com.nikitasdk.mymusic.data.YouTube
import com.nikitasdk.mymusic.data.YtHttp
import java.io.IOException

/**
 * Фоновый сервис плеера. Работает при выключенном экране,
 * показывает уведомление с управлением и обложку на экране блокировки.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val upstream = DefaultDataSource.Factory(this, http)
        val dataSource = ResolvingDataSource.Factory(upstream) { spec -> resolve(spec) }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)   // пауза при отключении наушников
            .setWakeMode(C.WAKE_MODE_NETWORK)     // не засыпать при выключенном экране
            .build()

        // Если трек не загрузился (нет сети, удалён) — переходим к следующему
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                player.currentMediaItem?.mediaId?.let { YouTube.invalidate(it) }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Ссылки на треки передаются через requestMetadata — восстанавливаем их здесь
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val items = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: Uri.parse("yt://${item.mediaId}")
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(items)
                }
            })
            .build()
    }

    /** yt://ID → локальный файл (если скачан) или прямая ссылка на аудио YouTube. */
    private fun resolve(spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme != "yt") return spec
        val id = uri.toString().removePrefix("yt://")

        Downloads.file(id)?.let { return spec.withUri(Uri.fromFile(it)) }

        val source = try {
            YouTube.audio(id)
        } catch (e: Exception) {
            throw IOException("Не удалось получить аудио", e)
        }
        val url = YtHttp.withRn(source.url)
        return spec.buildUpon()
            .setUri(Uri.parse(url))
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(YtHttp.POST_BODY)
            .setHttpRequestHeaders(spec.httpRequestHeaders + YtHttp.headers(url))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
