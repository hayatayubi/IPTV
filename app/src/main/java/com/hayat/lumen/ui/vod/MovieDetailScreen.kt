package com.hayat.lumen.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.Poster
import com.hayat.lumen.data.db.VodKind
import com.hayat.lumen.data.db.WatchProgressEntity
import com.hayat.lumen.data.vod.VodRepository
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.Ambient
import com.hayat.lumen.data.ratings.OmdbClient
import com.hayat.lumen.R
import com.hayat.lumen.ui.components.ProgrammeProgress
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MovieDetailViewModel(
    private val vod: VodRepository,
    private val omdb: OmdbClient,
    private val omdbKey: () -> String?,
    private val sourceId: Long,
    private val streamId: String,
) : ViewModel() {
    private val _hero = MutableStateFlow<HeroInfo?>(null)
    val hero: StateFlow<HeroInfo?> = _hero

    val progress: StateFlow<WatchProgressEntity?> = vod.progress(sourceId, VodKind.MOVIE, streamId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val inMyList: StateFlow<Boolean> = vod.inMyList(sourceId, VodKind.MOVIE, streamId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            val movie = vod.movie(sourceId, streamId) ?: return@launch
            val poster = Poster(sourceId, VodKind.MOVIE, streamId, movie.name, movie.posterUrl, movie.rating)
            _hero.value = poster.quickHero()
            val full = vod.hero(poster)
            _hero.value = full
            _hero.value = omdb.addRatings(full, omdbKey(), series = false)
        }
    }

    fun toggleMyList() {
        viewModelScope.launch { vod.toggleMyList(sourceId, VodKind.MOVIE, streamId) }
    }
}

/**
 * A film's page: backdrop, title, details and plot, with Play (or Resume from where you
 * stopped, plus Start over) and My List.
 */
@Composable
fun MovieDetailScreen(sourceId: Long, streamId: String, onPlay: (startOver: Boolean) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { MovieDetailViewModel(container.vod, container.omdb, { container.prefs.omdbKey.value }, sourceId, streamId) }
    val hero by vm.hero.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val inMyList by vm.inMyList.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val resumeAt = VodRepository.resumePosition(progress)

    // Apple TV style: the backdrop fills the screen, darkening to the left and bottom for the text.
    Ambient {
        Backdrop(hero, widthFraction = 1f, heightFraction = 1f, leftFade = 0.6f)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical)
                .padding(top = 40.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            HeroText(hero, Modifier.fillMaxWidth(), plotLines = 3, titleStyle = MaterialTheme.typography.displayMedium)
            hero?.let { h ->
                val credits = listOfNotNull(h.cast?.let { "Starring  $it" }, h.director?.let { "Director  $it" })
                credits.forEach {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LumenColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LumenButton(
                    text = if (resumeAt != null) "Resume from ${formatPosition(resumeAt)}" else "Play",
                    onClick = { onPlay(false) },
                    primary = true,
                    icon = R.drawable.ic_play,
                    modifier = Modifier.focusRequester(playFocus),
                )
                if (resumeAt != null) LumenButton(text = "Start over", onClick = { onPlay(true) })
                // The heart is My List, as in Chillio. Filled when it's in.
                LumenButton(
                    text = "",
                    onClick = vm::toggleMyList,
                    icon = if (inMyList) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                )
            }
            val p = progress
            if (resumeAt != null && p != null && p.durationMs > 0) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgrammeProgress(p.positionMs.toFloat() / p.durationMs, LumenColors.TextPrimary, Modifier.width(220.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${formatRuntime(((p.durationMs - p.positionMs) / 60_000).toInt().coerceAtLeast(1))} left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LumenColors.TextSecondary,
                    )
                }
            }
            // As in Chillio: the scores get their own big row under the buttons. They arrive a moment
            // after the page (an OMDb lookup), so they slide in rather than make the page jump.
            val ratings = hero?.ratings
            AnimatedVisibility(ratings != null, enter = fadeIn() + expandVertically()) {
                if (ratings != null) {
                    Column {
                        Spacer(Modifier.height(28.dp))
                        Text("Ratings", style = MaterialTheme.typography.titleMedium, color = LumenColors.TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        RatingsPanel(ratings)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

/** "1:02:05" or "42:10". */
fun formatPosition(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
