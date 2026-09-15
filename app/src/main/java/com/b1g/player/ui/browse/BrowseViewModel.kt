package com.b1g.player.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.b1g.player.core.model.Category
import com.b1g.player.core.model.ContentKind
import com.b1g.player.core.model.StreamRequest
import com.b1g.player.core.source.ContentSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row in the browse list, flattened so live, movies and series render alike. */
data class BrowseItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    /** Null for a series, which opens its episode list instead of playing. */
    val stream: StreamRequest?,
)

data class BrowseUiState(
    val kind: ContentKind = ContentKind.LIVE,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    val items: List<BrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** True once a short page comes back, so scrolling stops asking for more. */
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Pages the catalogue instead of loading it.
 *
 * Category and search filtering are passed down to the source, which answers them
 * from the database — a provider's full line-up is far too large to hold as a list
 * and filter here.
 */
class BrowseViewModel(private val source: ContentSource) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        select(ContentKind.LIVE)
    }

    fun select(kind: ContentKind) {
        _state.update { it.copy(kind = kind, selectedCategoryId = null, query = "") }
        restart()
    }

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
        restart()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        restart()
    }

    fun reload() = restart()

    /** Discards what is stored and fetches the catalogue again. */
    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, items = emptyList(), endReached = false, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { source.refresh() }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Could not refresh")
                    }
                    return@launch
                }
            load(offset = 0)
        }
    }

    /** Called as the list nears its end. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return

        _state.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch { load(offset = current.items.size) }
    }

    private fun restart() {
        // Switching tabs or typing must not let a stale page win the race.
        loadJob?.cancel()
        _state.update {
            it.copy(items = emptyList(), endReached = false, isLoading = true, error = null)
        }
        loadJob = viewModelScope.launch { load(offset = 0) }
    }

    private suspend fun load(offset: Int) {
        val current = _state.value
        val query = current.query.trim().takeIf { it.isNotEmpty() }

        runCatching {
            val categories =
                if (offset == 0) source.categories(current.kind) else current.categories

            val page = when (current.kind) {
                ContentKind.LIVE ->
                    source.liveChannels(current.selectedCategoryId, query, PAGE, offset).map {
                        BrowseItem(it.id, it.name, it.categoryName, it.logoUrl, it.stream)
                    }

                ContentKind.VOD ->
                    source.vod(current.selectedCategoryId, query, PAGE, offset).map {
                        BrowseItem(it.id, it.name, it.rating?.let { r -> "★ $r" }, it.logoUrl, it.stream)
                    }

                ContentKind.SERIES ->
                    source.series(current.selectedCategoryId, query, PAGE, offset).map {
                        val episodes = it.episodeCount?.let { count ->
                            if (count == 1) "1 episode" else "$count episodes"
                        }
                        BrowseItem(it.id, it.name, episodes ?: it.plot?.take(80), it.coverUrl, null)
                    }
            }
            categories to page
        }.onSuccess { (categories, page) ->
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    categories = categories,
                    items = if (offset == 0) page else it.items + page,
                    endReached = page.size < PAGE,
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = error.message ?: "Could not load content",
                )
            }
        }
    }

    /** Loads a series' episodes on demand, since the list view does not need them. */
    fun episodes(seriesId: String, onLoaded: (List<BrowseItem>) -> Unit) {
        viewModelScope.launch {
            runCatching { source.episodes(seriesId) }
                .onSuccess { episodes ->
                    onLoaded(
                        episodes.map {
                            BrowseItem(
                                id = it.id,
                                title = "S${it.seasonNumber}E${it.episodeNumber} · ${it.title}",
                                subtitle = it.plot?.take(80),
                                imageUrl = it.stillUrl,
                                stream = it.stream,
                            )
                        }
                    )
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not load episodes") }
                }
        }
    }

    class Factory(private val source: ContentSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BrowseViewModel(source) as T
    }

    private companion object {
        /** Big enough to fill a screen and then some, small enough to stay instant. */
        const val PAGE = 100
    }
}
