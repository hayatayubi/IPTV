package com.hayat.lumen.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.ui.addsource.AddSourceScreen
import com.hayat.lumen.ui.guide.GuideSettingsScreen
import com.hayat.lumen.ui.home.HomeScreen
import com.hayat.lumen.ui.live.LiveScreen
import com.hayat.lumen.ui.player.PlayerScreen
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.LumenMotion

/** Navigation destinations. The back stack is just a list of these. */
sealed interface Route
data object AddSourceRoute : Route
data object HomeRoute : Route
data object LiveRoute : Route
/** [inFavourites]: Up/Down zaps through favourites instead of the channel's group. */
data class PlayerRoute(val channelId: Long, val inFavourites: Boolean = false) : Route
data object GuideSettingsRoute : Route

/**
 * App navigation. Like SwiftUI's NavigationStack(path:), the back stack is a list we
 * own: push = add, pop = remove last. Back pops; Back on the last screen exits.
 */
@Composable
fun LumenRoot(onExit: () -> Unit) {
    val container = LocalAppContainer.current

    // First launch (no playlists yet) opens on "Add a playlist", otherwise on Home (favourites).
    val start by produceState<Route?>(initialValue = null) {
        value = if (container.live.hasSources()) HomeRoute else AddSourceRoute
    }
    val first = start
    if (first == null) {
        Box(Modifier.fillMaxSize().background(LumenColors.Background))
        return
    }

    val backStack = remember { mutableStateListOf(first) }
    val fade: () -> ContentTransform = { fadeIn(LumenMotion.focus()) togetherWith fadeOut(LumenMotion.focus()) }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.background(LumenColors.Background),
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onExit() },
        entryDecorators = listOf(
            // Keeps each screen's remembered UI state (scroll positions) while it's in the stack…
            rememberSaveableStateHolderNavEntryDecorator(),
            // …and gives each screen its own ViewModels, cleared when it's popped.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { fade() },
        popTransitionSpec = { fade() },
        predictivePopTransitionSpec = { fade() },
        entryProvider = entryProvider {
            entry<AddSourceRoute> {
                AddSourceScreen(onAdded = {
                    container.epg.updateStaleInBackground() // load the new playlist's guide
                    // Replace "Add" with Home, then open Live on top: there are no favourites yet.
                    // Back from Live goes to Home; Back from Home exits.
                    backStack.add(HomeRoute)
                    backStack.removeAll { it != HomeRoute }
                    backStack.add(LiveRoute)
                })
            }
            entry<HomeRoute> {
                HomeScreen(
                    onPlay = { backStack.add(PlayerRoute(it, inFavourites = true)) },
                    onBrowse = { backStack.add(LiveRoute) },
                )
            }
            entry<LiveRoute> {
                LiveScreen(
                    onPlay = { backStack.add(PlayerRoute(it)) },
                    onOpenGuide = { backStack.add(GuideSettingsRoute) },
                )
            }
            entry<PlayerRoute> { route ->
                PlayerScreen(channelId = route.channelId, inFavourites = route.inFavourites)
            }
            entry<GuideSettingsRoute> {
                GuideSettingsScreen()
            }
        },
    )
}
