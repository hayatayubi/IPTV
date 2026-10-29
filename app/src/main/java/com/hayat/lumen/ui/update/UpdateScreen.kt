package com.hayat.lumen.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.update.InstallState
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.ProgrammeProgress
import com.hayat.lumen.ui.components.ambient
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea

/**
 * "Update Lumen": what's new in the waiting release and one Install button. The first
 * time, Android needs Lumen to be allowed to install apps, so that step comes first.
 */
@Composable
fun UpdateScreen() {
    val updates = LocalAppContainer.current.updates
    val release by updates.available.collectAsStateWithLifecycle()
    val install by updates.install.collectAsStateWithLifecycle()

    // Re-read when coming back from Settings, where the user may just have allowed it.
    // (Like checking a permission again in SwiftUI's .onChange(of: scenePhase).)
    var allowed by remember { mutableStateOf(updates.canInstall()) }
    var noSettingsScreen by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = updates.canInstall()
        onPauseOrDispose { }
    }

    val buttonFocus = remember { FocusRequester() }

    Column(
        Modifier
            .fillMaxSize()
            .ambient()
            .padding(horizontal = SafeArea.Horizontal * 2, vertical = SafeArea.Vertical * 2)
            .widthIn(max = 900.dp),
    ) {
        val shown = release
        if (shown == null) {
            Text("Lumen is up to date", style = MaterialTheme.typography.displaySmall, color = LumenColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "You have version ${updates.currentVersion}.",
                style = MaterialTheme.typography.bodyLarge,
                color = LumenColors.TextSecondary,
            )
            return@Column
        }

        Text("Update Lumen", style = MaterialTheme.typography.displaySmall, color = LumenColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Version ${shown.version} is ready. You have ${updates.currentVersion}. " +
                "Your playlists, favourites and progress are kept.",
            style = MaterialTheme.typography.bodyLarge,
            color = LumenColors.TextSecondary,
        )
        if (shown.notes.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Text("What's new", style = MaterialTheme.typography.titleMedium, color = LumenColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                shown.notes.lines().joinToString("\n") { line ->
                    line.trim().removePrefix("- ").removePrefix("* ").let { if (it != line.trim()) "•  $it" else it }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = LumenColors.TextSecondary,
                // There's nothing to focus in the notes, so the D-pad couldn't scroll them: cap instead.
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(32.dp))

        when {
            !allowed -> {
                Text(
                    if (noSettingsScreen) {
                        "Open the TV's Settings → Apps → Security & restrictions → Unknown sources, and turn on Lumen."
                    } else {
                        "First, Android needs your OK for Lumen to install updates. " +
                            "On the next screen, turn on Lumen, then press Back."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextPrimary,
                )
                Spacer(Modifier.height(16.dp))
                LumenButton(
                    text = "Allow updates",
                    onClick = { noSettingsScreen = !updates.openInstallPermission() },
                    modifier = Modifier.focusRequester(buttonFocus),
                )
            }
            install is InstallState.Downloading -> {
                val progress = (install as InstallState.Downloading).progress
                Text(
                    if (progress == null) "Downloading…" else "Downloading… ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                ProgrammeProgress(progress ?: 0f, color = LumenColors.Accent, modifier = Modifier.width(480.dp), height = 6.dp)
            }
            install == InstallState.Installing -> {
                Text(
                    "Installing. If Android asks, choose Update. Lumen closes when it's done: open it again from the Home screen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextPrimary,
                )
            }
            else -> {
                val failed = install as? InstallState.Failed
                if (failed != null) {
                    Text(failed.message, style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextPrimary)
                    Spacer(Modifier.height(16.dp))
                }
                LumenButton(
                    text = if (failed != null) "Try again" else "Install update",
                    onClick = { updates.install(shown) },
                    modifier = Modifier.focusRequester(buttonFocus),
                )
            }
        }
    }

    // Focus the one button whenever it (re)appears: on open, after allowing, after a failure.
    LaunchedEffect(allowed, install is InstallState.Downloading || install == InstallState.Installing, release) {
        runCatching { buttonFocus.requestFocus() }
    }
}
