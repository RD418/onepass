package ch.onepass.onepass.model.event

import android.util.Log
import ch.onepass.onepass.graphql.GetEventQuery
import ch.onepass.onepass.graphql.GetEventsQuery
import ch.onepass.onepass.graphql.GetFeaturedEventsQuery
import ch.onepass.onepass.graphql.type.EventStatus as GraphQLEventStatus
import ch.onepass.onepass.model.event.EventMapper.toEvent
import ch.onepass.onepass.model.map.Location
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GraphQL-backed implementation of [EventRepository].
 * 
 * This repository uses Apollo GraphQL client to fetch event data from the backend API,
 * replacing Firebase Firestore calls with type-safe GraphQL queries.
 * 
 * Key features:
 * - Type-safe GraphQL queries with compile-time validation
 * - Automatic authentication via Firebase Auth tokens
 * - Conversion from GraphQL types to domain models
 * - Flow-based reactive data streams
 */
class EventRepositoryGraphQL : EventRepository {
    private val apolloClient = ApolloClientProvider.getClient()

    companion object {
        private const val TAG = "EventRepositoryGraphQL"
        private const val DEFAULT_PAGE_SIZE = 20
    }

    /**
     * Retrieves all events, sorted by start time in ascending order.
     * Uses cursor-based pagination to fetch all events.
     */
    override fun getAllEvents(): Flow<List<Event>> = flow {
        try {
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            // Fetch all pages
            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = Optional.present(DEFAULT_PAGE_SIZE),
                        after = Optional.presentIfNotNull(cursor),
                        filters = Optional.absent()
                    )
                ).execute()

                if (response.hasErrors()) {
                    Log.e(TAG, "GraphQL errors: ${response.errors}")
                    throw Exception("Failed to fetch events: ${response.errors?.firstOrNull()?.message}")
                }

                val data = response.data?.events
                if (data != null) {
                    events.addAll(data.edges.map { it.node.toEvent() })
                    hasNextPage = data.pageInfo.hasNextPage
                    cursor = data.pageInfo.endCursor
                } else {
                    hasNextPage = false
                }
            }

            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching all events", e)
            throw Exception("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all events", e)
            throw e
        }
    }

    /**
     * Searches for events whose titles match the given query.
     */
    override fun searchEvents(query: String): Flow<List<Event>> = flow {
        try {
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = Optional.present(DEFAULT_PAGE_SIZE),
                        after = Optional.presentIfNotNull(cursor),
                        filters = Optional.present(
                            GetEventsQuery.EventFilters(
                                searchQuery = Optional.present(query),
                                status = Optional.present(GraphQLEventStatus.PUBLISHED)
                            )
                        )
                    )
                ).execute()

                if (response.hasErrors()) {
                    throw Exception("Failed to search events: ${response.errors?.firstOrNull()?.message}")
                }

                val data = response.data?.events
                if (data != null) {
                    events.addAll(data.edges.map { it.node.toEvent() })
                    hasNextPage = data.pageInfo.hasNextPage
                    cursor = data.pageInfo.endCursor
                } else {
                    hasNextPage = false
                }
            }

            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error searching events", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves a specific event by its unique ID.
     */
    override fun getEventById(eventId: String): Flow<Event?> = flow {
        try {
            val response = apolloClient.query(GetEventQuery(eventId)).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch event: ${response.errors?.firstOrNull()?.message}")
            }

            val event = response.data?.event?.toEvent()
            emit(event)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching event by ID: $eventId", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves all events organized by a specific organization/user.
     */
    override fun getEventsByOrganization(orgId: String): Flow<List<Event>> = flow {
        try {
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = Optional.present(DEFAULT_PAGE_SIZE),
                        after = Optional.presentIfNotNull(cursor),
                        filters = Optional.present(
                            GetEventsQuery.EventFilters(
                                organizerId = Optional.present(orgId)
                            )
                        )
                    )
                ).execute()

                if (response.hasErrors()) {
                    throw Exception("Failed to fetch organization events: ${response.errors?.firstOrNull()?.message}")
                }

                val data = response.data?.events
                if (data != null) {
                    events.addAll(data.edges.map { it.node.toEvent() })
                    hasNextPage = data.pageInfo.hasNextPage
                    cursor = data.pageInfo.endCursor
                } else {
                    hasNextPage = false
                }
            }

            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching events by organization", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves events within a geographic radius of a given location.
     * Note: This uses GraphQL location filtering.
     */
    override fun getEventsByLocation(center: Location, radiusKm: Double): Flow<List<Event>> = flow {
        try {
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = Optional.present(DEFAULT_PAGE_SIZE),
                        after = Optional.presentIfNotNull(cursor),
                        filters = Optional.present(
                            GetEventsQuery.EventFilters(
                                location = Optional.present(
                                    GetEventsQuery.LocationInput(
                                        name = center.name,
                                        latitude = center.geopoint.latitude,
                                        longitude = center.geopoint.longitude
                                    )
                                ),
                                radiusKm = Optional.present(radiusKm)
                            )
                        )
                    )
                ).execute()

                if (response.hasErrors()) {
                    throw Exception("Failed to fetch events by location: ${response.errors?.firstOrNull()?.message}")
                }

                val data = response.data?.events
                if (data != null) {
                    events.addAll(data.edges.map { it.node.toEvent() })
                    hasNextPage = data.pageInfo.hasNextPage
                    cursor = data.pageInfo.endCursor
                } else {
                    hasNextPage = false
                }
            }

            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching events by location", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves all events associated with a specific tag.
     */
    override fun getEventsByTag(tag: String): Flow<List<Event>> = flow {
        try {
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = Optional.present(DEFAULT_PAGE_SIZE),
                        after = Optional.presentIfNotNull(cursor),
                        filters = Optional.present(
                            GetEventsQuery.EventFilters(
                                tags = Optional.present(listOf(tag))
                            )
                        )
                    )
                ).execute()

                if (response.hasErrors()) {
                    throw Exception("Failed to fetch events by tag: ${response.errors?.firstOrNull()?.message}")
                }

                val data = response.data?.events
                if (data != null) {
                    events.addAll(data.edges.map { it.node.toEvent() })
                    hasNextPage = data.pageInfo.hasNextPage
                    cursor = data.pageInfo.endCursor
                } else {
                    hasNextPage = false
                }
            }

            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching events by tag", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves a curated list of featured events.
     */
    override fun getFeaturedEvents(): Flow<List<Event>> = flow {
        try {
            val response = apolloClient.query(GetFeaturedEventsQuery()).execute()

            if (response.hasErrors()) {
                Log.e(TAG, "GraphQL errors: ${response.errors}")
                throw Exception("Failed to fetch featured events: ${response.errors?.firstOrNull()?.message}")
            }

            val events = response.data?.featuredEvents?.map { it.toEvent() } ?: emptyList()
            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching featured events", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves all events with a specific status.
     */
    override fun getEventsByStatus(status: EventStatus): Flow<List<Event>> = flow {
        try {
            val graphqlStatus = when (status) {
                EventStatus.DRAFT -> GraphQLEventStatus.DRAFT
                EventStatus.PUBLISHED -> GraphQLEventStatus.PUBLISHED
                EventStatus.CLOSED -> GraphQLEventStatus.CLOSED
                EventStatus.CANCELLED -> GraphQLEventStatus.CANCELLED
            }

            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = Optional.present(DEFAULT_PAGE_SIZE),
                        after = Optional.presentIfNotNull(cursor),
                        filters = Optional.present(
                            GetEventsQuery.EventFilters(
                                status = Optional.present(graphqlStatus)
                            )
                        )
                    )
                ).execute()

                if (response.hasErrors()) {
                    throw Exception("Failed to fetch events by status: ${response.errors?.firstOrNull()?.message}")
                }

                val data = response.data?.events
                if (data != null) {
                    events.addAll(data.edges.map { it.node.toEvent() })
                    hasNextPage = data.pageInfo.hasNextPage
                    cursor = data.pageInfo.endCursor
                } else {
                    hasNextPage = false
                }
            }

            emit(events)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching events by status", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Create, update, and delete operations are not yet implemented for GraphQL.
     * These require mutations which will be added in future iterations.
     */
    override suspend fun createEvent(event: Event): Result<String> {
        return Result.failure(NotImplementedError("Create event via GraphQL not yet implemented"))
    }

    override suspend fun updateEvent(event: Event): Result<Unit> {
        return Result.failure(NotImplementedError("Update event via GraphQL not yet implemented"))
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> {
        return Result.failure(NotImplementedError("Delete event via GraphQL not yet implemented"))
    }

    override suspend fun addEventImage(eventId: String, imageUrl: String): Result<Unit> {
        return Result.failure(NotImplementedError("Add event image via GraphQL not yet implemented"))
    }

    override suspend fun removeEventImage(eventId: String, imageUrl: String): Result<Unit> {
        return Result.failure(NotImplementedError("Remove event image via GraphQL not yet implemented"))
    }

    override suspend fun updateEventImages(eventId: String, imageUrls: List<String>): Result<Unit> {
        return Result.failure(NotImplementedError("Update event images via GraphQL not yet implemented"))
    }
}
