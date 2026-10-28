package com.hayat.lumen.data.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A newer Lumen on the GitHub Releases page. */
data class Release(
    val version: String,
    /** The release description: what changed, one line per change. */
    val notes: String,
    val apkUrl: String,
    /** Bytes, or -1 if GitHub didn't say. */
    val size: Long,
)

sealed interface InstallState {
    data object Idle : InstallState
    /** [progress] 0..1, or null while the size is unknown. */
    data class Downloading(val progress: Float?) : InstallState
    /** Handed to Android: its install screen is showing, or it's installing. */
    data object Installing : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * Over-the-air updates from the repo's GitHub Releases. Each release has the signed APK
 * attached and is tagged with its version ("v0.2.0"). Lumen asks GitHub for the latest
 * release, and if it's newer, offers it on Home.
 *
 * Installing streams the APK straight into an Android [PackageInstaller] session (no file
 * on disk, no FileProvider). Android checks it's signed with the same key as the installed
 * app, then shows its own "Do you want to update this app?" screen. The app's data is kept.
 */
class UpdateRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    val currentVersion: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    private val _available = MutableStateFlow<Release?>(null)
    /** A newer release than this app, once a check has found one. */
    val available: StateFlow<Release?> = _available.asStateFlow()

    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    val install: StateFlow<InstallState> = _install.asStateFlow()

    private var lastCheck = 0L
    private var checking: Job? = null
    private var installing: Job? = null

    /**
     * Asks GitHub for the latest release, at most every [CHECK_EVERY_MS] unless [force]d.
     * Failures are silent: no network just means no update banner.
     */
    fun checkInBackground(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (checking?.isActive == true || (!force && now - lastCheck < CHECK_EVERY_MS)) return
        lastCheck = now
        checking = scope.launch(Dispatchers.IO) {
            try {
                val latest = fetchLatest() ?: return@launch
                _available.value = latest.takeIf { Versions.isNewer(it.version, currentVersion) }
            } catch (e: IOException) {
                Log.w(TAG, "Update check failed: ${e.javaClass.simpleName}")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Update check failed: unexpected reply")
            }
        }
    }

    /**
     * True once the user has let Lumen install apps (Settings → Apps → Lumen →
     * "Install unknown apps"). Android asks for this once per app that installs APKs.
     */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for Lumen. False if this TV has no such screen. */
    fun openInstallPermission(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    /** Downloads [release] into an install session and hands it to Android. */
    fun install(release: Release) {
        if (installing?.isActive == true) return
        installing = scope.launch(Dispatchers.IO) {
            _install.value = InstallState.Downloading(null)
            val installer = context.packageManager.packageInstaller
            var sessionId = -1
            try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (release.size > 0) setSize(release.size)
                    // Once Lumen has installed itself, Android 12+ may let later updates
                    // through without the confirm screen. If not, it simply asks as usual.
                    if (Build.VERSION.SDK_INT >= 31) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    download(release, session)
                    val callback = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                        // Mutable: Android adds the result (and its confirm screen) as extras.
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    _install.value = InstallState.Installing
                    session.commit(callback.intentSender)
                }
            } catch (e: IOException) {
                if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
                Log.w(TAG, "Update download failed: ${e.javaClass.simpleName}")
                _install.value = InstallState.Failed("The download didn't finish. Check the connection and try again.")
            } catch (e: SecurityException) {
                if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
                _install.value = InstallState.Failed("Android didn't let Lumen install the update.")
            }
        }
    }

    /** Called by [InstallResultReceiver] with Android's answer to the commit. */
    internal fun onInstallResult(intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's "update this app?" screen. Lumen is on screen, so it may open it.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    _install.value = InstallState.Failed("Android didn't show its install screen.")
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    _install.value = InstallState.Failed("Android didn't show its install screen.")
                }
            }
            // Normally never seen: Android stops Lumen to replace it.
            PackageInstaller.STATUS_SUCCESS -> _install.value = InstallState.Idle
            // Back on the confirm screen.
            PackageInstaller.STATUS_FAILURE_ABORTED -> _install.value = InstallState.Idle
            else -> {
                Log.w(TAG, "Install failed: status $status")
                _install.value = InstallState.Failed(
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_CONFLICT,
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                            "This update is signed differently from the installed Lumen, so Android refused it."
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough space on the TV for the update."
                        PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded file isn't a valid app."
                        else -> "Android couldn't install the update."
                    },
                )
            }
        }
    }

    private suspend fun download(release: Release, session: PackageInstaller.Session) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            session.openWrite("lumen.apk", 0, total.coerceAtLeast(-1)).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastShown = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        if (total > 0) {
                            // Only publish whole percents, so the screen isn't redrawn 1,000 times.
                            val percent = (written * 100 / total).toInt()
                            if (percent != lastShown) {
                                lastShown = percent
                                _install.value = InstallState.Downloading(percent / 100f)
                            }
                        }
                    }
                    session.fsync(out)
                }
            }
        }
    }

    /** The latest published release with an APK attached, or null if there's none. */
    private fun fetchLatest(): Release? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            // 404 = no releases yet.
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return JsonReader(response.body.charStream()).use(::readRelease)
        }
    }

    private fun readRelease(reader: JsonReader): Release? {
        var tag: String? = null
        var notes = ""
        var apk: Pair<String, Long>? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tag_name" -> tag = reader.stringOrNull()
                "body" -> notes = reader.stringOrNull().orEmpty()
                "assets" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val asset = readAsset(reader)
                        if (apk == null && asset != null) apk = asset
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val version = tag ?: return null
        val (url, size) = apk ?: return null
        return Release(version.removePrefix("v"), notes.trim(), url, size)
    }

    /** An asset's download link and size, if it's an APK. */
    private fun readAsset(reader: JsonReader): Pair<String, Long>? {
        var name: String? = null
        var url: String? = null
        var size = -1L
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.stringOrNull()
                "browser_download_url" -> url = reader.stringOrNull()
                "size" -> size = if (reader.peek() == JsonToken.NUMBER) reader.nextLong() else { reader.skipValue(); -1 }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return if (name?.endsWith(".apk", ignoreCase = true) == true && url != null) url to size else null
    }

    private fun JsonReader.stringOrNull(): String? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

    private companion object {
        const val TAG = "Updates"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/lumen-tv/Lumen-TV/releases/latest"
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    }
}
