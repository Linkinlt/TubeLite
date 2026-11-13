package com.linkinlt.tubelite

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        NewPipe.init(DownloaderImpl, Localization("vi", "VN"), ContentCountry("VN"))
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) ImageLoader.clear()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ImageLoader.clear()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
