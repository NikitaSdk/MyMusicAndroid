package com.nikitasdk.mymusic

import android.app.Application
import com.nikitasdk.mymusic.data.Downloads
import com.nikitasdk.mymusic.data.Library
import com.nikitasdk.mymusic.data.NetDownloader
import com.nikitasdk.mymusic.data.Network
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        NewPipe.init(NetDownloader(http), Localization("ru", "UA"), ContentCountry("UA"))
        Library.init(this)
        Downloads.init(this)
        Network.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
        lateinit var http: OkHttpClient
            private set
    }
}
