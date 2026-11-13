package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LiveEventsRepository
import com.example.model.LiveEvent
import com.example.model.LiveEventsUiState
import com.example.model.PerformanceCategory
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceStyle
import com.example.model.performanceStyle
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing real-time observation, fine-grained category filtering (Rap, Poetry,
 * Jazz, Spoken Word, etc.), and participant actions for live events backed by Cloud Firestore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveEventsViewModel(
    private val repository: LiveEventsRepository,
    private val currentUserId: String,
    private val includeLiveSessions: Boolean = true
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<PerformanceCategory?>(null)
    val selectedCategory: StateFlow<PerformanceCategory?> = _selectedCategory.asStateFlow()

    private val _selectedStyle = MutableStateFlow<PerformanceStyle?>(null)
    val selectedStyle: StateFlow<PerformanceStyle?> = _selectedStyle.asStateFlow()

    private val _joinedEventIds = MutableStateFlow<Set<String>>(emptySet())
    val joinedEventIds: StateFlow<Set<String>> = _joinedEventIds.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<LiveEventsUiState> = _retryTrigger
        .flatMapLatest {
            combine(
                repository.observeLiveEvents(
                    categoryFilter = null,
                    includeLiveSessions = includeLiveSessions
                ),
                _selectedCategory,
                _selectedStyle,
                _joinedEventIds
            ) { allEvents, categoryFilter, styleFilter, joinedIds ->
                val filtered = allEvents.filter { event ->
                    PerformanceCategoryCatalog.matchesFilter(
                        itemStyle = event.performanceStyle,
                        itemParentCategory = event.performanceCategory,
                        selectedStyle = styleFilter,
                        selectedCategory = categoryFilter
                    )
                }
                LiveEventsUiState.Success(
                    events = filtered,
                    joinedEventIds = joinedIds
                ) as LiveEventsUiState
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing live events for user $currentUserId", rootError)
                emit(
                    LiveEventsUiState.Error(
                        message = rootError.message ?: "Unable to load live events"
                    )
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LiveEventsUiState.Loading
        )

    fun setCategoryFilter(category: PerformanceCategory?) {
        _selectedCategory.value = category
        if (category == null) {
            _selectedStyle.value = null
        } else if (_selectedStyle.value != null && _selectedStyle.value?.parentCategory != category) {
            _selectedStyle.value = null
        }
    }

    fun setStyleFilter(style: PerformanceStyle?) {
        _selectedStyle.value = style
        _selectedCategory.value = null
    }

    fun retry() {
        _retryTrigger.update { it + 1 }
    }

    fun toggleJoinEvent(
        event: LiveEvent,
        onEventJoined: ((LiveEvent) -> Unit)? = null
    ) {
        val eventId = event.id
        if (eventId.isBlank()) return
        val alreadyJoined = eventId in _joinedEventIds.value

        if (alreadyJoined) {
            _joinedEventIds.update { it - eventId }
            viewModelScope.launch {
                val res = repository.leaveLiveEvent(eventId)
                if (res.isFailure) {
                    _joinedEventIds.update { it + eventId }
                }
            }
        } else {
            _joinedEventIds.update { it + eventId }
            viewModelScope.launch {
                val res = repository.joinLiveEvent(eventId)
                if (res.isSuccess) {
                    val updatedCount = res.getOrDefault(event.participantCount + 1L)
                    onEventJoined?.invoke(event.copy(participantCount = updatedCount))
                } else {
                    _joinedEventIds.update { it - eventId }
                }
            }
        }
    }

    fun hostLiveEvent(
        title: String,
        category: PerformanceCategory,
        genre: String,
        description: String = "",
        onCreated: ((LiveEvent) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.createLiveEvent(
                title = title,
                category = category,
                genre = genre,
                description = description,
                initialParticipants = 1
            )
            result.onSuccess { created ->
                _joinedEventIds.update { it + created.id }
                onCreated?.invoke(created)
            }
        }
    }

    private companion object {
        const val TAG = "LiveEventsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
