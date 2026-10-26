package com.hayat.lumen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

/** Created once per process, before any Activity (like an iOS AppDelegate). */
class LumenApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Coil asks for its app-wide image loader here the first time an image loads.
    override fun newImageLoader(context: PlatformContext): ImageLoader = container.images
}
