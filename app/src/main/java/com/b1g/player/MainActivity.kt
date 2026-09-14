package com.b1g.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.b1g.player.core.source.ContentSource
import com.b1g.player.ui.browse.BrowseScreen
import com.b1g.player.ui.browse.BrowseViewModel
import com.b1g.player.ui.login.LoginScreen
import com.b1g.player.ui.login.LoginViewModel
import com.b1g.player.ui.player.PlayerActivity
import com.b1g.player.ui.theme.B1gPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            B1gPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

/**
 * Two states: no connected source (login) or one connected source (browse).
 * Deliberately not a navigation graph yet — there is one transition to model, and a
 * graph would only hide it.
 */
@Composable
private fun AppRoot() {
    val container = LocalContext.current.appContainer
    var source by remember { mutableStateOf<ContentSource?>(container.activeSource) }
    val context = LocalContext.current

    val current = source
    if (current == null) {
        val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory(container))
        LoginScreen(viewModel = viewModel, onConnected = { source = it })
    } else {
        val viewModel: BrowseViewModel = viewModel(
            key = current.config.id,
            factory = BrowseViewModel.Factory(current),
        )
        BrowseScreen(
            viewModel = viewModel,
            onPlay = { title, stream ->
                context.startActivity(PlayerActivity.intent(context, title, stream))
            },
        )
    }
}
