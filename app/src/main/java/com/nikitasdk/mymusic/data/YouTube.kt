package com.nikitasdk.mymusic.data

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Поиск музыки и получение ссылки на аудиопоток. Все функции блокирующие — вызывать не в UI-потоке. */
object YouTube {

    data class AudioSource(val url: String, val extension: String)

    private val idRegex = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")
    private val sizeRegex = Regex("=w\\d+-h\\d+")
    private val cache = ConcurrentHashMap<String, Pair<Long, AudioSource>>()
    private const val CACHE_TTL = 60 * 60 * 1000L   // ссылки YouTube живут несколько часов

    fun search(query: String): List<Song> {
        val music = runCatching { runSearch(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
            .getOrDefault(emptyList())
        if (music.isNotEmpty()) return music
        return runSearch(query, YoutubeSearchQueryHandlerFactory.VIDEOS)
    }

    private fun runSearch(query: String, filter: String): List<Song> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSong() }
            .distinctBy { it.id }
    }

    private fun StreamInfoItem.toSong(): Song? {
        val id = idRegex.find(url)?.groupValues?.get(1) ?: return null
        val thumb = thumbnails.maxByOrNull { it.height }?.url
            ?.replace(sizeRegex, "=w544-h544")   // обложки YouTube Music — в высоком качестве
        return Song(
            id = id,
            title = name,
            artist = uploaderName ?: "",
            duration = if (duration > 0) duration else 0,
            thumbnail = thumb
        )
    }

    fun audio(id: String): AudioSource {
        cache[id]?.let { (time, source) ->
            if (System.currentTimeMillis() - time < CACHE_TTL) return source
        }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val stream = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("Нет аудиопотока")
        val ext = when (stream.format) {
            MediaFormat.M4A -> "m4a"
            else -> stream.format?.suffix ?: "webm"
        }
        val source = AudioSource(stream.content, ext)
        cache[id] = System.currentTimeMillis() to source
        return source
    }

    fun invalidate(id: String) {
        cache.remove(id)
    }
}

/** Заголовки и параметры, с которыми YouTube отдаёт аудиопоток без ограничений скорости. */
object YtHttp {
    val POST_BODY = byteArrayOf(0x78, 0)
    private val requestNumber = AtomicLong(0)

    fun isYouTubeStream(url: String) = url.contains("googlevideo.com") && url.contains("/videoplayback")

    fun withRn(url: String): String =
        if (isYouTubeStream(url) && !url.contains("&rn=")) url + "&rn=" + requestNumber.incrementAndGet()
        else url

    fun headers(url: String): Map<String, String> {
        val h = HashMap<String, String>()
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            h["Origin"] = "https://www.youtube.com"
            h["Referer"] = "https://www.youtube.com"
            h["Sec-Fetch-Dest"] = "empty"
            h["Sec-Fetch-Mode"] = "cors"
            h["Sec-Fetch-Site"] = "cross-site"
        }
        h["User-Agent"] = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            NetDownloader.USER_AGENT
        }
        return h
    }
}
