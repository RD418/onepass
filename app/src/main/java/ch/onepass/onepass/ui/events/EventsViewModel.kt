package ch.onepass.onepass.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.onepass.onepass.model.event.Event
import ch.onepass.onepass.repository.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for managing event listing and search functionality.
 * 
 * This ViewModel demonstrates how to use the GraphQL-powered EventRepository
 * to fetch and display events in the UI.
 * 
 * Example usage in a Composable:
 * ```kotlin
 * @Composable
 * fun EventsScreen(viewModel: EventsViewModel = viewModel()) {
 *     val uiState by viewModel.uiState.collectAsState()
 *     
 *     LaunchedEffect(Unit) {
 *         viewModel.loadFeaturedEvents()
 *     }
 *     
 *     when (uiState) {
 *         is EventsUiState.Loading -> LoadingIndicator()
 *         is EventsUiState.Success -> EventsList(events = uiState.events)
 *         is EventsUiState.Error -> ErrorMessage(message = uiState.message)
 *     }
 * }
 * ```
 */
class EventsViewModel : ViewModel() {
    // Use the repository from RepositoryProvider (respects GraphQL flag)
    private val eventRepository = RepositoryProvider.eventRepository

    private val _uiState = MutableStateFlow<EventsUiState>(EventsUiState.Loading)
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Optionally load featured events on init
        // loadFeaturedEvents()
    }

    /**
     * Loads featured events from the repository.
     * Updates UI state based on success or failure.
     */
    fun loadFeaturedEvents() {
        viewModelScope.launch {
            _uiState.value = EventsUiState.Loading
            
            eventRepository.getFeaturedEvents()
                .catch { error ->
                    _uiState.value = EventsUiState.Error(
                        error.message ?: "Failed to load featured events"
                    )
                }
                .collect { events ->
                    _uiState.value = EventsUiState.Success(events)
                }
        }
    }

    /**
     * Loads all published events.
     */
    fun loadAllEvents() {
        viewModelScope.launch {
            _uiState.value = EventsUiState.Loading
            
            eventRepository.getAllEvents()
                .catch { error ->
                    _uiState.value = EventsUiState.Error(
                        error.message ?: "Failed to load events"
                    )
                }
                .collect { events ->
                    // Filter for published events only
                    val publishedEvents = events.filter { it.isPublished }
                    _uiState.value = EventsUiState.Success(publishedEvents)
                }
        }
    }

    /**
     * Searches for events matching the query string.
     */
    fun searchEvents(query: String) {
        _searchQuery.value = query
        
        if (query.isBlank()) {
            loadFeaturedEvents()
            return
        }

        viewModelScope.launch {
            _uiState.value = EventsUiState.Loading
            
            eventRepository.searchEvents(query)
                .catch { error ->
                    _uiState.value = EventsUiState.Error(
                        error.message ?: "Failed to search events"
                    )
                }
                .collect { events ->
                    _uiState.value = EventsUiState.Success(events)
                }
        }
    }

    /**
     * Loads events for a specific organization.
     */
    fun loadEventsByOrganization(organizationId: String) {
        viewModelScope.launch {
            _uiState.value = EventsUiState.Loading
            
            eventRepository.getEventsByOrganization(organizationId)
                .catch { error ->
                    _uiState.value = EventsUiState.Error(
                        error.message ?: "Failed to load organization events"
                    )
                }
                .collect { events ->
                    _uiState.value = EventsUiState.Success(events)
                }
        }
    }

    /**
     * Clears any error state and resets to initial state.
     */
    fun clearError() {
        _uiState.value = EventsUiState.Loading
    }
}

/**
 * UI state for the events screen.
 */
sealed class EventsUiState {
    object Loading : EventsUiState()
    data class Success(val events: List<Event>) : EventsUiState()
    data class Error(val message: String) : EventsUiState()
}
