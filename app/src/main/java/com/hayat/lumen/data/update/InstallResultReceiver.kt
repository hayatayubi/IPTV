package com.hayat.lumen.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hayat.lumen.LumenApp

/**
 * Android reports how an update install went by sending this broadcast (the callback
 * given to `PackageInstaller.Session.commit`). Not exported: only Android's installer,
 * holding our PendingIntent, can send it.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as LumenApp).container.updates.onInstallResult(intent)
    }
}
