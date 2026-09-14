package com.b1g.player.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.b1g.player.AppContainer
import com.b1g.player.core.login.FieldError
import com.b1g.player.core.login.M3uInput
import com.b1g.player.core.login.SourceBuildResult
import com.b1g.player.core.login.XtreamInput
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.source.ConnectResult
import com.b1g.player.core.source.ContentSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class LoginMode { XTREAM, M3U }

data class LoginUiState(
    val mode: LoginMode = LoginMode.XTREAM,
    val xtream: XtreamInput = XtreamInput(),
    val m3u: M3uInput = M3uInput(),
    val fieldErrors: List<FieldError> = emptyList(),
    val isConnecting: Boolean = false,
    /** A message from the server or the network layer, not a form error. */
    val connectionError: String? = null,
    val savedSources: List<SourceConfig> = emptyList(),
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(savedSources = container.sourceStore.sources.value))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.sourceStore.sources.collect { sources ->
                _state.update { it.copy(savedSources = sources) }
            }
        }
    }

    fun setMode(mode: LoginMode) = _state.update { it.copy(mode = mode, fieldErrors = emptyList(), connectionError = null) }

    fun updateXtream(transform: (XtreamInput) -> XtreamInput) =
        _state.update { it.copy(xtream = transform(it.xtream), fieldErrors = emptyList(), connectionError = null) }

    fun updateM3u(transform: (M3uInput) -> M3uInput) =
        _state.update { it.copy(m3u = transform(it.m3u), fieldErrors = emptyList(), connectionError = null) }

    fun removeSource(id: String) = container.sourceStore.remove(id)

    /** Validates the form, connects, and only saves the account once it works. */
    fun connect(onConnected: (ContentSource) -> Unit) {
        val current = _state.value
        val id = UUID.randomUUID().toString()
        val built = when (current.mode) {
            LoginMode.XTREAM -> current.xtream.build(id)
            LoginMode.M3U -> current.m3u.build(id)
        }

        when (built) {
            is SourceBuildResult.Invalid -> _state.update { it.copy(fieldErrors = built.errors) }
            is SourceBuildResult.Valid -> connect(built.config, save = true, onConnected = onConnected)
        }
    }

    /** Reopens a source the user already saved. */
    fun connectSaved(config: SourceConfig, onConnected: (ContentSource) -> Unit) =
        connect(config, save = false, onConnected = onConnected)

    private fun connect(config: SourceConfig, save: Boolean, onConnected: (ContentSource) -> Unit) {
        _state.update { it.copy(isConnecting = true, connectionError = null, fieldErrors = emptyList()) }

        viewModelScope.launch {
            val source = container.sourceFactory.create(config)
            when (val result = source.connect()) {
                is ConnectResult.Success -> {
                    if (save) container.sourceStore.save(config)
                    container.sourceStore.lastUsedId = config.id
                    container.activeSource = source
                    _state.update { it.copy(isConnecting = false) }
                    onConnected(source)
                }
                is ConnectResult.Failure ->
                    _state.update { it.copy(isConnecting = false, connectionError = result.message) }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(container) as T
    }
}
