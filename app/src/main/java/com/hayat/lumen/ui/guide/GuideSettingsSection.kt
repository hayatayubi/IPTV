package com.hayat.lumen.ui.guide

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.EpgStateEntity
import com.hayat.lumen.data.db.SourceEntity
import com.hayat.lumen.data.db.SourceType
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.theme.LumenColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat

data class GuideSourceUi(
    val source: SourceEntity,
    /** False when there's no guide link at all (an M3U without url-tvg and nothing entered). */
    val hasGuide: Boolean,
    val state: EpgStateEntity?,
    /** Programmes stored so far while updating, else null. */
    val updating: Int?,
)

class GuideSettingsViewModel(live: LiveRepository, private val epg: EpgRepository) : ViewModel() {
    val sources: StateFlow<List<GuideSourceUi>> =
        combine(live.sources, epg.states, epg.updating) { sources, states, updating ->
            val bySource = states.associateBy { it.sourceId }
            sources.map { GuideSourceUi(it, epg.guideUrl(it) != null, bySource[it.id], updating[it.id]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(sourceId: Long, url: String) {
        viewModelScope.launch { epg.setGuideUrl(sourceId, url) }
    }

    fun update(sourceId: Long) = epg.updateInBackground(sourceId)
}

/** Settings → TV guide: one guide link per playlist, its status, and Update now. */
@Composable
fun GuideSettingsSection(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { GuideSettingsViewModel(container.live, container.epg) }
    val sources by vm.sources.collectAsStateWithLifecycle()

    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text(
            "Lumen shows what's on now and next from an XMLTV guide. Xtream logins use your provider's guide " +
                "automatically; paste a link to use a different one.",
            style = MaterialTheme.typography.bodyLarge,
            color = LumenColors.TextSecondary,
        )
        sources.forEach { item ->
            SourceGuide(
                item = item,
                onSave = { vm.save(item.source.id, it) },
                onUpdate = { vm.update(item.source.id) },
            )
        }
    }
}

@Composable
private fun SourceGuide(item: GuideSourceUi, onSave: (String) -> Unit, onUpdate: () -> Unit) {
    val source = item.source
    // Only a link the user (or the playlist's url-tvg) set is shown. The automatic Xtream
    // link has the password in it, so it never appears on screen.
    var url by remember(source.id, source.epgUrl) { mutableStateOf(source.epgUrl.orEmpty()) }
    val busy = item.updating != null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(source.name, style = MaterialTheme.typography.headlineSmall, color = LumenColors.TextPrimary)
        Text(status(item), style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextSecondary)
        LumenTextField(
            value = url,
            onValueChange = { url = it },
            label = "Guide link (XMLTV)",
            placeholder = if (source.type == SourceType.XTREAM) "Automatic: your provider's guide" else "http://…/guide.xml.gz",
            keyboardType = KeyboardType.Uri,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LumenButton(text = "Save", onClick = { onSave(url) }, enabled = !busy && url.trim() != source.epgUrl.orEmpty())
            LumenButton(text = if (busy) "Updating…" else "Update now", onClick = onUpdate, enabled = !busy && item.hasGuide)
        }
    }
}

private fun status(item: GuideSourceUi): String {
    val count = NumberFormat.getIntegerInstance()
    val state = item.state
    val updatedAt = state?.updatedAt
    val updated = updatedAt?.let {
        "Updated ${DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)} · " +
            "${count.format(state.programmeCount)} programmes for ${count.format(state.channelCount)} channels."
    }
    return when {
        item.updating != null ->
            if (item.updating == 0) "Downloading the guide…" else "Updating… ${count.format(item.updating)} programmes"
        state?.error != null -> listOfNotNull(state.error, updated?.let { "Still showing the last guide. $it" }).joinToString(" ")
        updated != null -> updated
        !item.hasGuide -> "No guide yet. Paste an XMLTV link from your provider."
        else -> "Not loaded yet."
    }
}
