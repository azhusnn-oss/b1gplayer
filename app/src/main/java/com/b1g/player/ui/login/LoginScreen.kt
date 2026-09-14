package com.b1g.player.ui.login

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.b1g.player.R
import com.b1g.player.core.login.SourceError
import com.b1g.player.core.login.SourceField
import com.b1g.player.core.model.SourceConfig
import com.b1g.player.core.source.ContentSource

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onConnected: (ContentSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        TabRow(selectedTabIndex = state.mode.ordinal, modifier = Modifier.widthIn(max = 560.dp)) {
            Tab(
                selected = state.mode == LoginMode.XTREAM,
                onClick = { viewModel.setMode(LoginMode.XTREAM) },
                text = { Text(stringResource(R.string.login_tab_xtream)) },
            )
            Tab(
                selected = state.mode == LoginMode.M3U,
                onClick = { viewModel.setMode(LoginMode.M3U) },
                text = { Text(stringResource(R.string.login_tab_m3u)) },
            )
        }

        when (state.mode) {
            LoginMode.XTREAM -> XtreamForm(state, viewModel)
            LoginMode.M3U -> M3uForm(state, viewModel)
        }

        state.connectionError?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { viewModel.connect(onConnected) },
                enabled = !state.isConnecting,
            ) {
                Text(
                    stringResource(
                        if (state.isConnecting) R.string.login_connecting else R.string.login_connect
                    )
                )
            }
            if (state.isConnecting) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        if (state.savedSources.isNotEmpty()) {
            Text(
                text = "Saved sources",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            state.savedSources.forEach { config ->
                SavedSourceRow(
                    config = config,
                    enabled = !state.isConnecting,
                    onOpen = { viewModel.connectSaved(config, onConnected) },
                    onRemove = { viewModel.removeSource(config.id) },
                )
            }
        }
    }
}

@Composable
private fun XtreamForm(state: LoginUiState, viewModel: LoginViewModel) {
    val input = state.xtream
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(
            value = input.displayName,
            onValueChange = { new -> viewModel.updateXtream { it.copy(displayName = new) } },
            label = R.string.login_display_name,
        )
        Field(
            value = input.server,
            onValueChange = { new -> viewModel.updateXtream { it.copy(server = new) } },
            label = R.string.login_server,
            keyboardType = KeyboardType.Uri,
            error = state.errorFor(SourceField.SERVER),
        )
        Field(
            value = input.username,
            onValueChange = { new -> viewModel.updateXtream { it.copy(username = new) } },
            label = R.string.login_username,
            error = state.errorFor(SourceField.USERNAME),
        )
        Field(
            value = input.password,
            onValueChange = { new -> viewModel.updateXtream { it.copy(password = new) } },
            label = R.string.login_password,
            isPassword = true,
            error = state.errorFor(SourceField.PASSWORD),
        )
        Field(
            value = input.userAgent,
            onValueChange = { new -> viewModel.updateXtream { it.copy(userAgent = new) } },
            label = R.string.login_user_agent,
        )
    }
}

@Composable
private fun M3uForm(state: LoginUiState, viewModel: LoginViewModel) {
    val input = state.m3u
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(
            value = input.displayName,
            onValueChange = { new -> viewModel.updateM3u { it.copy(displayName = new) } },
            label = R.string.login_display_name,
        )
        Field(
            value = input.playlistUrl,
            onValueChange = { new -> viewModel.updateM3u { it.copy(playlistUrl = new) } },
            label = R.string.login_playlist_url,
            keyboardType = KeyboardType.Uri,
            error = state.errorFor(SourceField.PLAYLIST_URL),
        )
        Field(
            value = input.epgUrl,
            onValueChange = { new -> viewModel.updateM3u { it.copy(epgUrl = new) } },
            label = R.string.login_epg_url,
            keyboardType = KeyboardType.Uri,
        )
        Field(
            value = input.userAgent,
            onValueChange = { new -> viewModel.updateM3u { it.copy(userAgent = new) } },
            label = R.string.login_user_agent,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    error: SourceError? = null,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(label)) },
            singleLine = true,
            isError = error != null,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        )
        error?.let {
            Text(
                text = stringResource(it.messageFor(label)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SavedSourceRow(
    config: SourceConfig,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(config.displayName) },
        supportingContent = {
            Text(
                when (config) {
                    is SourceConfig.Xtream -> "Xtream · ${config.server.baseUrl}"
                    is SourceConfig.M3u -> "Playlist · ${config.playlistUrl}"
                }
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.browse_sign_out))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clickableWhen(enabled, onOpen),
    )
}

private fun LoginUiState.errorFor(field: SourceField): SourceError? =
    fieldErrors.firstOrNull { it.field == field }?.error

/** Maps a validation code to a message that names the field it belongs to. */
@StringRes
private fun SourceError.messageFor(@StringRes label: Int): Int = when (this) {
    SourceError.REQUIRED -> when (label) {
        R.string.login_server -> R.string.error_server_required
        R.string.login_username -> R.string.error_username_required
        R.string.login_password -> R.string.error_password_required
        else -> R.string.error_playlist_required
    }
    SourceError.INVALID_SERVER -> R.string.error_server_invalid
    SourceError.INVALID_URL -> R.string.error_playlist_scheme
}

private fun Modifier.clickableWhen(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier.clickable(onClick = onClick)) else this
