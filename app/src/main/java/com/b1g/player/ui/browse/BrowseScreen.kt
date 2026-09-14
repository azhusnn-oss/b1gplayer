package com.b1g.player.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.b1g.player.R
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.StreamRequest

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onPlay: (title: String, stream: StreamRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Drilling into a series replaces the list without leaving the screen.
    var episodes by remember { mutableStateOf<List<BrowseItem>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.kind.ordinal) {
            ContentKind.entries.forEach { kind ->
                Tab(
                    selected = state.kind == kind,
                    onClick = {
                        episodes = null
                        viewModel.select(kind)
                    },
                    text = { Text(stringResource(kind.labelRes())) },
                )
            }
        }

        if (episodes == null) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.browse_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text(stringResource(R.string.browse_all_categories)) },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { message ->
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = message, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::reload, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.browse_retry))
                }
            }
        }

        val rows = episodes ?: state.visibleItems
        if (rows.isEmpty() && !state.isLoading && state.error == null) {
            Text(
                text = stringResource(R.string.browse_empty),
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { item ->
                ContentRow(
                    item = item,
                    onClick = {
                        val stream = item.stream
                        if (stream != null) {
                            onPlay(item.title, stream)
                        } else {
                            // A series row has no stream: open its episodes instead.
                            viewModel.episodes(item.id) { loaded -> episodes = loaded }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ContentRow(item: BrowseItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title, maxLines = 1) },
        supportingContent = item.subtitle?.let { { Text(it, maxLines = 2) } },
        leadingContent = {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun ContentKind.labelRes(): Int = when (this) {
    ContentKind.LIVE -> R.string.browse_tab_live
    ContentKind.VOD -> R.string.browse_tab_movies
    ContentKind.SERIES -> R.string.browse_tab_series
}
