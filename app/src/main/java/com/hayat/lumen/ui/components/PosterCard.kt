package com.hayat.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.Poster
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.util.VodNames
import java.text.NumberFormat

/** Poster width on the Movies and Series rows. Posters are 2:3, like the App Store / TV app. */
val PosterWidth = 130.dp

/**
 * A film or series poster. The artwork fills the card; if there is none (or it fails to load)
 * the title is written on the tile instead. Continue watching adds a progress line and,
 * for a series, which episode you're on.
 */
@Composable
fun PosterCard(
    poster: Poster,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = PosterWidth,
) {
    val container = LocalAppContainer.current
    var failed by remember(poster.posterUrl) { mutableStateOf(poster.posterUrl == null) }
    LumenCard(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(width).aspectRatio(2f / 3f)) {
        Box(Modifier.fillMaxSize().background(LumenColors.GlassStrong)) {
            if (failed) {
                val name = remember(poster.name) { VodNames.display(poster.name) }
                Text(
                    name.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = LumenColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            } else {
                AsyncImage(
                    model = poster.posterUrl,
                    contentDescription = poster.name,
                    imageLoader = container.images,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val progress = poster.progress
            if (progress != null || poster.season != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (poster.season != null && poster.episode != null) {
                        Text(
                            episodeLabel(poster.season, poster.episode),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (progress != null) ProgrammeProgress(progress, color = Color.White, height = 3.dp)
                }
            }
        }
    }
}

/** The tile at the end of a row that opens the whole category. */
@Composable
fun SeeAllCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = PosterWidth) {
    LumenCard(onClick = onClick, modifier = modifier.width(width).aspectRatio(2f / 3f)) {
        Column(Modifier.fillMaxSize().background(LumenColors.GlassStrong), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("See all", style = MaterialTheme.typography.titleMedium, color = LumenColors.TextPrimary)
            Text(formatCount(count), style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextSecondary)
        }
    }
}

/** "S1 · E3". */
fun episodeLabel(season: Int, episode: Int) = "S$season · E$episode"

fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)
