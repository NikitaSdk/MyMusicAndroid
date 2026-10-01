package com.nikitasdk.mymusic.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network as AndroidNetwork
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Следит за интернетом: без сети доступны только скачанные треки. */
object Network {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    fun init(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        _online.value = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: AndroidNetwork) {
                _online.value = true
            }

            override fun onLost(network: AndroidNetwork) {
                _online.value = false
            }
        })
    }
}
