package com.hayat.lumen.ui.vod

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.Poster
import com.hayat.lumen.data.db.VodKind
import com.hayat.lumen.data.vod.VodRepository
import com.hayat.lumen.ui.components.QualityTag
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.util.VodNames
import kotlinx.coroutines.CancellationException

/** What the big area above the rows (and the details page) shows about a film or series. */
data class HeroInfo(
    val key: String,
    val title: String,
    val year: Int?,
    val tags: List<String>,
    val rating: Double?,
    val genre: String? = null,
    val runtimeMin: Int? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
) {
    /** "2026 · Mystery, Thriller · 1 hr 54 min · ★ 6.4". */
    val meta: String
        get() = listOfNotNull(
            year?.toString(),
            genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(", "),
            runtimeMin?.let(::formatRuntime),
            rating?.let { "★ %.1f".format(it) },
        ).joinToString("  ·  ")
}

fun formatRuntime(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

/** Quick info from the stored row, for showing something at once. */
fun Poster.quickHero(): HeroInfo {
    val name = VodNames.display(name)
    return HeroInfo(key, name.title, name.year, name.tags, rating, posterUrl = posterUrl)
}

/**
 * Full info: series rows already hold plot and backdrop; films need a `get_vod_info` call
 * (cached). If that fails, the quick info is still shown.
 */
suspend fun VodRepository.hero(poster: Poster): HeroInfo {
    val quick = poster.quickHero()
    return when (poster.kind) {
        VodKind.SERIES -> {
            val s = series(poster.sourceId, poster.itemId) ?: return quick
            quick.copy(
                year = quick.year ?: s.releaseDate?.take(4)?.toIntOrNull(),
                genre = s.genre,
                plot = s.plot,
                backdropUrl = s.backdropUrl,
            )
        }
        VodKind.MOVIE -> {
            val info = try {
                movieInfo(poster.sourceId, poster.itemId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return quick
            quick.copy(
                year = quick.year ?: info.releaseDate?.take(4)?.toIntOrNull(),
                genre = info.genre,
                runtimeMin = info.durationSecs?.let { (it / 60).toInt() }?.takeIf { it > 0 },
                plot = info.plot,
                cast = info.cast,
                director = info.director,
                backdropUrl = info.backdropUrl,
            )
        }
    }
}

/**
 * The artwork behind the hero: the backdrop on the right two thirds, fading into the background
 * to the left and bottom so text stays readable. Without a backdrop, a blurred poster gives the colour.
 */
@Composable
fun BoxScope.Backdrop(hero: HeroInfo?, widthFraction: Float = 0.72f, heightFraction: Float = 0.75f) {
    val container = LocalAppContainer.current
    Crossfade(
        targetState = hero?.backdropUrl ?: hero?.posterUrl?.let { "poster:$it" },
        animationSpec = tween(500),
        label = "backdrop",
        modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(widthFraction).fillMaxHeight(heightFraction),
    ) { model ->
        if (model == null) return@Crossfade
        val isPoster = model.startsWith("poster:")
        AsyncImage(
            model = model.removePrefix("poster:"),
            contentDescription = null,
            imageLoader = container.images,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isPoster) Modifier.blur(48.dp).graphicsLayer { alpha = 0.45f } else Modifier)
                .drawWithContent {
                    drawContent()
                    // Fade to the background towards the left and the bottom.
                    drawRect(
                        Brush.horizontalGradient(
                            0f to LumenColors.Background,
                            0.45f to LumenColors.Background.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                    )
                    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to LumenColors.Background))
                },
        )
    }
}

/** Title, tags, the meta line and the plot. */
@Composable
fun HeroText(hero: HeroInfo?, modifier: Modifier = Modifier, plotLines: Int = 3) {
    Box(modifier, contentAlignment = Alignment.BottomStart) {
        Crossfade(hero, animationSpec = tween(250), label = "heroText") { shown ->
            if (shown == null) return@Crossfade
            Column(Modifier.widthIn(max = 640.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        shown.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = LumenColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    shown.tags.forEach { QualityTag(it) }
                }
                if (shown.meta.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(shown.meta, style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextSecondary, maxLines = 1)
                }
                shown.plot?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LumenColors.TextPrimary.copy(alpha = 0.85f),
                        maxLines = plotLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
