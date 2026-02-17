package com.warrantykeeper.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warrantykeeper.data.repository.DocumentRepository
import com.warrantykeeper.domain.model.Document
import com.warrantykeeper.domain.model.DocumentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<FilterType>(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType.asStateFlow()

    private val _sortType = MutableStateFlow<SortType>(SortType.DATE_ADDED)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    val documents: StateFlow<List<Document>> = combine(
        repository.getAllDocuments(),
        _searchQuery,
        _filterType,
        _sortType
    ) { docs, query, filter, sort ->
        var filtered = docs

        // Apply search filter
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                        (it.storeName?.contains(query, ignoreCase = true) == true)
            }
        }

        // Apply type filter
        filtered = when (filter) {
            FilterType.ALL -> filtered
            FilterType.RECEIPTS -> filtered.filter { it.type == DocumentType.RECEIPT }
            FilterType.WARRANTIES -> filtered.filter { it.type == DocumentType.WARRANTY }
            FilterType.ACTIVE_WARRANTIES -> filtered.filter {
                it.type == DocumentType.WARRANTY &&
                        (it.getDaysUntilExpiry() ?: -1) >= 0
            }
            FilterType.EXPIRED_WARRANTIES -> filtered.filter {
                it.type == DocumentType.WARRANTY &&
                        (it.getDaysUntilExpiry() ?: -1) < 0
            }
        }

        // Apply sorting
        when (sort) {
            SortType.DATE_ADDED -> filtered.sortedByDescending { it.createdAt }
            SortType.EXPIRY_DATE -> filtered.sortedBy { it.warrantyEndDate }
            SortType.NAME -> filtered.sortedBy { it.title }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterTypeChange(type: FilterType) {
        _filterType.value = type
    }

    fun onSortTypeChange(type: SortType) {
        _sortType.value = type
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }
}

enum class FilterType {
    ALL,
    RECEIPTS,
    WARRANTIES,
    ACTIVE_WARRANTIES,
    EXPIRED_WARRANTIES
}

enum class SortType {
    DATE_ADDED,
    EXPIRY_DATE,
    NAME
}
