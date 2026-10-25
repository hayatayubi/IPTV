package com.hayat.lumen

import android.app.Application

/** Created once per process, before any Activity (like an iOS AppDelegate). */
class LumenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
