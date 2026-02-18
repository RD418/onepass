package ch.onepass.onepass.repository

import ch.onepass.onepass.model.event.EventRepository
import ch.onepass.onepass.model.event.EventRepositoryFirebase
import ch.onepass.onepass.model.event.EventRepositoryGraphQL
import ch.onepass.onepass.model.map.LocationRepository
import ch.onepass.onepass.model.map.NominatimLocationRepository
import ch.onepass.onepass.model.storage.StorageRepository
import ch.onepass.onepass.model.storage.StorageRepositoryFirebase

/**
 * Central provider for repository instances across the application.
 * 
 * This object manages repository lifecycle and allows switching between
 * different implementations (e.g., Firebase vs GraphQL).
 * 
 * Usage:
 * ```kotlin
 * // Use GraphQL for events
 * RepositoryProvider.useGraphQLForEvents = true
 * val eventRepo = RepositoryProvider.eventRepository
 * ```
 */
object RepositoryProvider {
  /**
   * Flag to determine which EventRepository implementation to use.
   * - true: Use GraphQL-based repository (Apollo Client)
   * - false: Use Firebase Firestore repository (default)
   * 
   * Change this flag to switch between implementations for testing or gradual migration.
   */
  var useGraphQLForEvents: Boolean = false

  private val _eventRepositoryFirebase: EventRepository by lazy { EventRepositoryFirebase() }
  private val _eventRepositoryGraphQL: EventRepository by lazy { EventRepositoryGraphQL() }
  
  /**
   * Gets the active EventRepository implementation based on [useGraphQLForEvents] flag.
   */
  val eventRepository: EventRepository
    get() = if (useGraphQLForEvents) _eventRepositoryGraphQL else _eventRepositoryFirebase

  private val _storageRepository: StorageRepository by lazy { StorageRepositoryFirebase() }
  var storageRepository: StorageRepository = _storageRepository
  
  private val _locationRepository: LocationRepository by lazy { NominatimLocationRepository() }
  var locationRepository: LocationRepository = _locationRepository
}
