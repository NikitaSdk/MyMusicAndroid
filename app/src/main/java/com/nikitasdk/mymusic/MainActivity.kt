package com.nikitasdk.mymusic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nikitasdk.mymusic.playback.PlayerConnection
import com.nikitasdk.mymusic.ui.FullPlayer
import com.nikitasdk.mymusic.ui.LibraryScreen
import com.nikitasdk.mymusic.ui.MiniPlayer
import com.nikitasdk.mymusic.ui.MyMusicTheme
import com.nikitasdk.mymusic.ui.Palette
import com.nikitasdk.mymusic.ui.PlaylistDetailScreen
import com.nikitasdk.mymusic.ui.PlaylistsScreen
import com.nikitasdk.mymusic.ui.SearchScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            MyMusicTheme { AppRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.connect(applicationContext)
    }

    override fun onStop() {
        PlayerConnection.release()
        super.onStop()
    }
}

private data class Tab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Поиск", Icons.Rounded.Search),
    Tab("Медиатека", Icons.Rounded.LibraryMusic),
    Tab("Плейлисты", Icons.Rounded.QueueMusic)
)

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    // Обновляем позицию трека для ползунка
    LaunchedEffect(Unit) {
        while (true) {
            PlayerConnection.refresh()
            delay(500)
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && openPlaylist != null) { openPlaylist = null }

    NotificationPermission()
    BackgroundHint()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Palette.Background,
            bottomBar = {
                Column {
                    MiniPlayer(onOpen = { playerOpen = true })
                    NavigationBar(containerColor = Palette.Background, tonalElevation = 0.dp) {
                        tabs.forEachIndexed { index, t ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = {
                                    if (tab == index) openPlaylist = null
                                    tab = index
                                },
                                icon = { Icon(t.icon, t.title) },
                                label = { Text(t.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Palette.Accent,
                                    indicatorColor = Palette.Accent,
                                    unselectedIconColor = Palette.TextDim,
                                    unselectedTextColor = Palette.TextDim
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> SearchScreen(padding)
                1 -> LibraryScreen(padding)
                else -> {
                    val id = openPlaylist
                    if (id == null) {
                        PlaylistsScreen(padding, onOpen = { openPlaylist = it })
                    } else {
                        PlaylistDetailScreen(id, padding, onBack = { openPlaylist = null })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            FullPlayer(onClose = { playerOpen = false })
        }
    }
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/** Один раз подсказываем снять ограничения батареи (важно для Xiaomi / HyperOS). */
@Composable
private fun BackgroundHint() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(!prefs.getBoolean("background_hint_shown", false)) }
    if (!show) return

    fun close() {
        prefs.edit().putBoolean("background_hint_shown", true).apply()
        show = false
    }

    AlertDialog(
        onDismissRequest = { close() },
        containerColor = Palette.Surface,
        title = { Text("Музыка в фоне") },
        text = {
            Text(
                "Чтобы музыка не останавливалась при выключенном экране, разрешите приложению " +
                    "работать без ограничений:\n\nНастройки приложения → Контроль активности " +
                    "(или «Батарея») → «Нет ограничений»."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                close()
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Открыть настройки", color = Palette.Accent) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Позже") } }
    )
}
