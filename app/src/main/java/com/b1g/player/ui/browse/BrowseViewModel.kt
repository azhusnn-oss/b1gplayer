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
    val error: String? = null,
) {
    /**
     * Filtering happens in memory: a playlist source has everything loaded already,
     * and Xtream has no search endpoint, so either way the list is local.
     */
    val visibleItems: List<BrowseItem>
        get() = if (query.isBlank()) items
        else items.filter { it.title.contains(query.trim(), ignoreCase = true) }
}

class BrowseViewModel(private val source: ContentSource) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        select(ContentKind.LIVE)
    }

    fun select(kind: ContentKind) {
        _state.update { it.copy(kind = kind, selectedCategoryId = null, query = "") }
        reload()
    }

    fun selectCategory(categoryId: String?) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
        reload()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun reload() {
        // Switching tabs quickly must not leave a stale response to win the race.
        loadJob?.cancel()
        val current = _state.value
        _state.update { it.copy(isLoading = true, error = null) }

        loadJob = viewModelScope.launch {
            runCatching {
                val categories = source.categories(current.kind)
                val items = when (current.kind) {
                    ContentKind.LIVE -> source.liveChannels(current.selectedCategoryId).map {
                        BrowseItem(it.id, it.name, it.categoryName, it.logoUrl, it.stream)
                    }
                    ContentKind.VOD -> source.vod(current.selectedCategoryId).map {
                        BrowseItem(it.id, it.name, it.rating?.let { r -> "★ $r" }, it.logoUrl, it.stream)
                    }
                    ContentKind.SERIES -> source.series(current.selectedCategoryId).map {
                        BrowseItem(it.id, it.name, it.plot?.take(80), it.coverUrl, null)
                    }
                }
                categories to items
            }.onSuccess { (categories, items) ->
                _state.update { it.copy(isLoading = false, categories = categories, items = items) }
            }.onFailure { error ->
                _state.update {
                    it.copy(isLoading = false, error = error.message ?: "Could not load content")
                }
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
}
