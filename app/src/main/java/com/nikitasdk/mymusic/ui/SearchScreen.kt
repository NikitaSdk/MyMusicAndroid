package com.nikitasdk.mymusic.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nikitasdk.mymusic.data.Library
import com.nikitasdk.mymusic.data.Network
import com.nikitasdk.mymusic.data.Song
import com.nikitasdk.mymusic.data.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Состояние поиска живёт вне экрана — результаты не пропадают при переключении вкладок. */
object SearchModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Song>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun search(text: String = query.value) {
        val q = text.trim()
        if (q.isEmpty()) return
        query.value = q
        Library.addHistory(q)
        job?.cancel()
        job = scope.launch {
            loading.value = true
            error.value = null
            try {
                val found = withContext(Dispatchers.IO) { YouTube.search(q) }
                results.value = found
                if (found.isEmpty()) error.value = "Ничего не найдено"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error.value = "Ошибка поиска. Проверьте интернет."
            } finally {
                loading.value = false
            }
        }
    }

    fun clear() {
        job?.cancel()
        query.value = ""
        results.value = emptyList()
        error.value = null
        loading.value = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(contentPadding: PaddingValues) {
    val query by SearchModel.query.collectAsStateWithLifecycle()
    val results by SearchModel.results.collectAsStateWithLifecycle()
    val loading by SearchModel.loading.collectAsStateWithLifecycle()
    val error by SearchModel.error.collectAsStateWithLifecycle()
    val history by Library.history.collectAsStateWithLifecycle()
    val online by Network.online.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScreenTitle("Поиск")
        TextField(
            value = query,
            onValueChange = { SearchModel.query.value = it },
            singleLine = true,
            placeholder = { Text("Песни, исполнители, альбомы") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { SearchModel.clear() }) { Icon(Icons.Rounded.Close, "Очистить") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { SearchModel.search(); focus.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.Surface,
                unfocusedContainerColor = Palette.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Palette.Accent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (!online) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.WifiOff, null, tint = Palette.AccentWarm)
                Spacer(Modifier.width(12.dp))
                Text("Нет интернета. Скачанная музыка — в «Медиатеке».", color = Palette.TextDim)
            }
        }

        val bottom = PaddingValues(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
            results.isNotEmpty() -> SongList(results, bottom)
            error != null -> EmptyState(Icons.Rounded.Search, error ?: "", "Попробуйте другой запрос")
            history.isNotEmpty() -> Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Недавние запросы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Library.clearHistory() }) { Text("Очистить", color = Palette.TextDim) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { h ->
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Palette.Surface)
                                .clickable { SearchModel.search(h); focus.clearFocus() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(h)
                        }
                    }
                }
            }
            else -> EmptyState(Icons.Rounded.Search, "Найдите свою музыку", "Введите название песни или исполнителя")
        }
    }
}
