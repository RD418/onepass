package ch.onepass.onepass.repository

import ch.onepass.onepass.model.event.EventRepository
import ch.onepass.onepass.model.event.EventRepositoryFirebase
import ch.onepass.onepass.model.event.EventRepositoryGraphQL
import ch.onepass.onepass.model.map.LocationRepository
import ch.onepass.onepass.model.map.NominatimLocationRepository
import ch.onepass.onepass.model.membership.MembershipRepository
import ch.onepass.onepass.model.membership.MembershipRepositoryFirebase
import ch.onepass.onepass.model.membership.MembershipRepositoryGraphQL
import ch.onepass.onepass.model.organization.OrganizationRepository
import ch.onepass.onepass.model.organization.OrganizationRepositoryFirebase
import ch.onepass.onepass.model.organization.OrganizationRepositoryGraphQL
import ch.onepass.onepass.model.organization.PostRepository
import ch.onepass.onepass.model.organization.PostRepositoryFirebase
import ch.onepass.onepass.model.organization.PostRepositoryGraphQL
import ch.onepass.onepass.model.storage.StorageRepository
import ch.onepass.onepass.model.storage.StorageRepositoryFirebase
import ch.onepass.onepass.model.ticket.TicketRepository
import ch.onepass.onepass.model.ticket.TicketRepositoryFirebase
import ch.onepass.onepass.model.ticket.TicketRepositoryGraphQL
import ch.onepass.onepass.model.user.UserRepository
import ch.onepass.onepass.model.user.UserRepositoryFirebase
import ch.onepass.onepass.model.user.UserRepositoryGraphQL

/**
 * Central provider for repository instances across the application.
 * 
 * This object manages repository lifecycle and allows switching between
 * different implementations (e.g., Firebase vs GraphQL).
 * 
 * Usage:
 * ```kotlin
 * // Use GraphQL for all repositories
 * RepositoryProvider.useGraphQL = true
 * val eventRepo = RepositoryProvider.eventRepository
 * val userRepo = RepositoryProvider.userRepository
 * ```
 */
object RepositoryProvider {
  /**
   * Flag to determine which repository implementations to use.
   * - true: Use GraphQL-based repositories (Apollo Client)
   * - false: Use Firebase Firestore repositories (default)
   * 
   * Change this flag to switch between implementations for testing or gradual migration.
   */
  var useGraphQL: Boolean = false

  /**
   * Per-repository override flags. These take precedence over the global [useGraphQL] flag
   * when set to a non-null value. Use these for gradual migration of individual repositories.
   */
  var useGraphQLForEvents: Boolean? = null
  var useGraphQLForUsers: Boolean? = null
  var useGraphQLForTickets: Boolean? = null
  var useGraphQLForOrganizations: Boolean? = null
  var useGraphQLForPosts: Boolean? = null
  var useGraphQLForMemberships: Boolean? = null

  // ---- Event Repository ----
  private val _eventRepositoryFirebase: EventRepository by lazy { EventRepositoryFirebase() }
  private val _eventRepositoryGraphQL: EventRepository by lazy { EventRepositoryGraphQL() }

  val eventRepository: EventRepository
    get() = if (useGraphQLForEvents ?: useGraphQL) _eventRepositoryGraphQL else _eventRepositoryFirebase

  // ---- User Repository ----
  private val _userRepositoryFirebase: UserRepository by lazy { UserRepositoryFirebase() }
  private val _userRepositoryGraphQL: UserRepository by lazy { UserRepositoryGraphQL() }

  val userRepository: UserRepository
    get() = if (useGraphQLForUsers ?: useGraphQL) _userRepositoryGraphQL else _userRepositoryFirebase

  // ---- Ticket Repository ----
  private val _ticketRepositoryFirebase: TicketRepository by lazy { TicketRepositoryFirebase() }
  private val _ticketRepositoryGraphQL: TicketRepository by lazy { TicketRepositoryGraphQL() }

  val ticketRepository: TicketRepository
    get() = if (useGraphQLForTickets ?: useGraphQL) _ticketRepositoryGraphQL else _ticketRepositoryFirebase

  // ---- Organization Repository ----
  private val _organizationRepositoryFirebase: OrganizationRepository by lazy { OrganizationRepositoryFirebase() }
  private val _organizationRepositoryGraphQL: OrganizationRepository by lazy { OrganizationRepositoryGraphQL() }

  val organizationRepository: OrganizationRepository
    get() = if (useGraphQLForOrganizations ?: useGraphQL) _organizationRepositoryGraphQL else _organizationRepositoryFirebase

  // ---- Post Repository ----
  private val _postRepositoryFirebase: PostRepository by lazy { PostRepositoryFirebase() }
  private val _postRepositoryGraphQL: PostRepository by lazy { PostRepositoryGraphQL() }

  val postRepository: PostRepository
    get() = if (useGraphQLForPosts ?: useGraphQL) _postRepositoryGraphQL else _postRepositoryFirebase

  // ---- Membership Repository ----
  private val _membershipRepositoryFirebase: MembershipRepository by lazy { MembershipRepositoryFirebase() }
  private val _membershipRepositoryGraphQL: MembershipRepository by lazy { MembershipRepositoryGraphQL() }

  val membershipRepository: MembershipRepository
    get() = if (useGraphQLForMemberships ?: useGraphQL) _membershipRepositoryGraphQL else _membershipRepositoryFirebase

  // ---- Storage Repository (Firebase only) ----
  private val _storageRepository: StorageRepository by lazy { StorageRepositoryFirebase() }
  var storageRepository: StorageRepository = _storageRepository
  
  // ---- Location Repository ----
  private val _locationRepository: LocationRepository by lazy { NominatimLocationRepository() }
  var locationRepository: LocationRepository = _locationRepository
}
