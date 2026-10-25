package com.hayat.lumen.ui.hello

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.ui.components.LumenCard
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea

/**
 * Phase 0 smoke test: two rows of focusable cards.
 * Proves install, D-pad focus movement, OK / long-press OK, and that focus
 * comes back to the same card when you leave a row and return.
 */
@Composable
fun HelloScreen() {
    // `remember { mutableStateOf(...) }` is Compose's @State.
    var status by remember { mutableStateOf("Press OK on a card. Hold OK to long-press.") }
    val firstCard = remember { FocusRequester() }

    Column(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(vertical = SafeArea.Vertical),
    ) {
        Column(Modifier.padding(horizontal = SafeArea.Horizontal)) {
            Text("Lumen", style = MaterialTheme.typography.displaySmall, color = LumenColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextSecondary)
        }
        Spacer(Modifier.height(32.dp))
        CardRow(
            title = "Row one",
            labels = (1..12).map { "Card $it" },
            onAction = { status = it },
            firstItemFocus = firstCard,
        )
        Spacer(Modifier.height(16.dp))
        CardRow(
            title = "Row two",
            labels = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel"),
            onAction = { status = it },
        )
    }

    // Like SwiftUI's .task / .onAppear: runs once when this enters composition.
    // Gives the first card focus so the remote works immediately.
    LaunchedEffect(Unit) { firstCard.requestFocus() }
}

@Composable
private fun CardRow(
    title: String,
    labels: List<String>,
    onAction: (String) -> Unit,
    firstItemFocus: FocusRequester? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = LumenColors.TextSecondary,
            modifier = Modifier.padding(horizontal = SafeArea.Horizontal),
        )
        LazyRow(
            // focusRestorer: when focus re-enters this row (e.g. pressing Up from
            // the row below), go back to the card that was last focused here.
            modifier = Modifier.focusRestorer(),
            // Content padding instead of outer padding, so cards scroll edge to edge
            // but rest inside the safe area, with room for the focus scale + glow.
            contentPadding = PaddingValues(horizontal = SafeArea.Horizontal, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(labels) { index, label ->
                LumenCard(
                    onClick = { onAction("OK on $label") },
                    onLongClick = { onAction("Long-press on $label") },
                    modifier = Modifier
                        .width(240.dp)
                        .aspectRatio(16f / 9f)
                        .then(if (index == 0 && firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.titleLarge, color = LumenColors.TextPrimary)
                    }
                }
            }
        }
    }
}
