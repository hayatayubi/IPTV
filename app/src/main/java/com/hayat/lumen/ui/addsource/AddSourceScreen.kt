package com.hayat.lumen.ui.addsource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.hayat.lumen.data.xtream.XtreamLogin
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
        if (url.isBlank()) {
            _state.value = AddState.Failed("Paste a playlist URL first.")
            return
        }
        import(onAdded) { progress -> repo.addM3u(name.ifBlank { DEFAULT_NAME }, url, progress) }
    }

    fun addXtream(name: String, server: String, username: String, password: String, onAdded: () -> Unit) {
        val login = XtreamLogin.of(server, username, password)
        if (login == null) {
            _state.value = AddState.Failed("Enter the server address, username and password.")
            return
        }
        import(onAdded) { progress -> repo.addXtream(name.ifBlank { DEFAULT_NAME }, login, progress) }
    }

    private fun import(onAdded: () -> Unit, work: suspend (onProgress: (Int) -> Unit) -> Unit) {
        if (_state.value is AddState.Importing) return
        viewModelScope.launch {
            _state.value = AddState.Importing(0)
            try {
                work { _state.value = AddState.Importing(it) }
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
    var xtreamMode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val urlFocus = remember { FocusRequester() }
    val importing = state is AddState.Importing

    Box(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
        contentAlignment = Alignment.Center,
    ) {
        // Unlike a SwiftUI ScrollView, a Compose Column doesn't scroll unless asked. With
        // verticalScroll, moving focus to something off-screen scrolls it into view.
        Column(
            Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                Text("Add a playlist", style = MaterialTheme.typography.displaySmall, color = LumenColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (xtreamMode) "Enter the server, username and password from your provider."
                    else "Paste your M3U or Xtream (get.php) link. The Google TV app on your phone has a keyboard that makes this easier.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextSecondary,
                )
            }
            if (xtreamMode) {
                // Four fields don't fit one per row above the buttons on a 540 dp tall TV screen,
                // so they go two per row. `weight` splits a Row's width like flexible frames in SwiftUI.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LumenTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = "Server",
                        placeholder = "http://example.com:8080",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.weight(1.6f).focusRequester(urlFocus),
                    )
                    LumenTextField(value = name, onValueChange = { name = it }, label = "Name", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LumenTextField(value = username, onValueChange = { username = it }, label = "Username", modifier = Modifier.weight(1f))
                    LumenTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                LumenTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Playlist URL",
                    placeholder = "http://…",
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier.focusRequester(urlFocus),
                )
                LumenTextField(value = name, onValueChange = { name = it }, label = "Name")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LumenButton(
                    text = if (importing) "Importing…" else "Add playlist",
                    onClick = {
                        if (xtreamMode) vm.addXtream(name, server, username, password, onAdded)
                        else vm.add(name, url, onAdded)
                    },
                    enabled = !importing,
                )
                LumenButton(
                    text = if (xtreamMode) "Use a playlist link" else "Use Xtream login",
                    onClick = { xtreamMode = !xtreamMode },
                    enabled = !importing,
                )
            }
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
