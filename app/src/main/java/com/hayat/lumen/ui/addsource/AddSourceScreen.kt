package com.hayat.lumen.ui.addsource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.ImportException
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat

sealed interface AddState {
    data object Idle : AddState
    data class Importing(val channels: Int) : AddState
    data class Failed(val message: String) : AddState
}

// A ViewModel outlives recompositions and configuration changes, a bit like an
// @Observable model held by a view's @State. Compose reads its StateFlows.
class AddSourceViewModel(private val repo: LiveRepository) : ViewModel() {
    private val _state = MutableStateFlow<AddState>(AddState.Idle)
    val state: StateFlow<AddState> = _state

    fun add(name: String, url: String, onAdded: () -> Unit) {
        if (_state.value is AddState.Importing) return
        if (url.isBlank()) {
            _state.value = AddState.Failed("Paste a playlist URL first.")
            return
        }
        viewModelScope.launch {
            _state.value = AddState.Importing(0)
            try {
                repo.addM3u(name.ifBlank { DEFAULT_NAME }, url) { _state.value = AddState.Importing(it) }
                _state.value = AddState.Idle
                onAdded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _state.value = AddState.Failed(e.message ?: GENERIC_ERROR)
            } catch (e: Exception) {
                _state.value = AddState.Failed(GENERIC_ERROR)
            }
        }
    }

    companion object {
        const val DEFAULT_NAME = "My playlist"
        private const val GENERIC_ERROR = "Something went wrong importing this playlist."
    }
}

@Composable
fun AddSourceScreen(onAdded: () -> Unit) {
    val container = LocalAppContainer.current
    // Manual DI: the ViewModel gets its dependencies straight from the container.
    val vm = viewModel { AddSourceViewModel(container.live) }
    val state by vm.state.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(AddSourceViewModel.DEFAULT_NAME) }
    var url by remember { mutableStateOf("") }
    val urlFocus = remember { FocusRequester() }
    val importing = state is AddState.Importing

    Box(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("Add a playlist", style = MaterialTheme.typography.displaySmall, color = LumenColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste your M3U playlist URL. The Google TV app on your phone has a keyboard that makes this easier.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextSecondary,
                )
            }
            LumenTextField(
                value = url,
                onValueChange = { url = it },
                label = "Playlist URL",
                placeholder = "http://…",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.focusRequester(urlFocus),
            )
            LumenTextField(value = name, onValueChange = { name = it }, label = "Name")
            LumenButton(
                text = if (importing) "Importing…" else "Add playlist",
                onClick = { vm.add(name, url, onAdded) },
                enabled = !importing,
            )
            Text(
                text = when (val s = state) {
                    AddState.Idle -> ""
                    is AddState.Importing ->
                        if (s.channels == 0) "Downloading playlist…"
                        else "Importing… ${NumberFormat.getIntegerInstance().format(s.channels)} channels"
                    is AddState.Failed -> s.message
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state is AddState.Failed) LumenColors.TextPrimary else LumenColors.TextSecondary,
            )
        }
    }

    LaunchedEffect(Unit) { urlFocus.requestFocus() }
}
