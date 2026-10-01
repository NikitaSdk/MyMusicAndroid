package com.nikitasdk.mymusic.data

import android.content.Context
import android.widget.Toast
import com.nikitasdk.mymusic.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/** Скачивание треков в память приложения для прослушивания без интернета. */
object Downloads {
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloaded: StateFlow<Set<String>> = _downloaded
    val progress: StateFlow<Map<String, Float>> = _progress

    private const val CHUNK = 4L * 1024 * 1024

    fun init(context: Context) {
        dir = File(context.filesDir, "music").apply { mkdirs() }
        dir.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
        _downloaded.value = dir.listFiles()
            ?.filter { it.extension != "jpg" }
            ?.map { it.nameWithoutExtension }
            ?.toSet() ?: emptySet()
    }

    fun isDownloaded(id: String) = id in _downloaded.value

    fun file(id: String): File? =
        if (!isDownloaded(id)) null
        else dir.listFiles()?.firstOrNull { it.nameWithoutExtension == id && it.extension != "jpg" && it.extension != "part" }

    fun thumbFile(id: String): File? = File(dir, "$id.jpg").takeIf { it.exists() }

    fun start(song: Song) {
        if (isDownloaded(song.id) || song.id in _progress.value) return
        Library.setSaved(song, true)   // скачанное всегда попадает в медиатеку
        _progress.update { it + (song.id to 0f) }
        scope.launch {
            try {
                download(song)
                _downloaded.update { it + song.id }
            } catch (e: Exception) {
                YouTube.invalidate(song.id)
                File(dir, "${song.id}.part").delete()
                toast("Не удалось скачать «${song.title}»")
            } finally {
                _progress.update { it - song.id }
            }
        }
    }

    fun delete(id: String) {
        dir.listFiles()?.filter { it.nameWithoutExtension == id }?.forEach { it.delete() }
        _downloaded.update { it - id }
    }

    private fun download(song: Song) {
        val source = YouTube.audio(song.id)
        val total = Regex("[?&]clen=(\\d+)").find(source.url)?.groupValues?.get(1)?.toLongOrNull()
        val part = File(dir, "${song.id}.part")
        var position = 0L

        part.outputStream().use { out ->
            while (true) {
                val end = position + CHUNK - 1
                val url = YtHttp.withRn(source.url)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(YtHttp.POST_BODY.toRequestBody())
                    .header("Range", "bytes=$position-$end")
                    .apply { YtHttp.headers(url).forEach { (k, v) -> header(k, v) } }
                    .build()

                val read = App.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Пустой ответ")
                    val count = body.byteStream().copyTo(out)
                    if (response.code == 200) -1L else count   // 200 = сервер отдал файл целиком
                }

                if (read == -1L) break
                position += read
                if (total != null && total > 0) {
                    _progress.update { it + (song.id to (position.toFloat() / total).coerceIn(0f, 1f)) }
                }
                if (read <= 0L) break
                if (total != null && position >= total) break
                if (total == null && read < CHUNK) break
            }
        }
        part.renameTo(File(dir, "${song.id}.${source.extension}"))

        // обложка — чтобы показывалась и без интернета
        song.thumbnail?.let { thumb ->
            runCatching {
                val req = okhttp3.Request.Builder().url(thumb).build()
                App.http.newCall(req).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes()?.let { File(dir, "${song.id}.jpg").writeBytes(it) }
                }
            }
        }
    }

    private suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(App.instance, text, Toast.LENGTH_SHORT).show()
    }
}
