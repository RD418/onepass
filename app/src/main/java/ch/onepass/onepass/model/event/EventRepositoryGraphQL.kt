package ch.onepass.onepass.model.event

import android.util.Log
import ch.onepass.onepass.graphql.AddEventImageMutation
import ch.onepass.onepass.graphql.CreateEventMutation
import ch.onepass.onepass.graphql.DeleteEventMutation
import ch.onepass.onepass.graphql.GetEventQuery
import ch.onepass.onepass.graphql.GetEventsQuery
import ch.onepass.onepass.graphql.GetFeaturedEventsQuery
import ch.onepass.onepass.graphql.RemoveEventImageMutation
import ch.onepass.onepass.graphql.UpdateEventMutation
import ch.onepass.onepass.graphql.type.CreateEventInput
import ch.onepass.onepass.graphql.type.EventFilters as GraphQLEventFilters
import ch.onepass.onepass.graphql.type.EventStatus as GraphQLEventStatus
import ch.onepass.onepass.graphql.type.LocationInput as GraphQLLocationInput
import ch.onepass.onepass.graphql.type.PricingTierInput
import ch.onepass.onepass.graphql.type.UpdateEventInput
import ch.onepass.onepass.model.event.EventMapper.toEvent
import ch.onepass.onepass.model.map.Location
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * GraphQL-backed implementation of [EventRepository].
 * 
 * This repository uses Apollo GraphQL client to fetch and mutate event data from the backend API,
 * replacing Firebase Firestore calls with type-safe GraphQL queries and mutations.
 * 
 * Key features:
 * - Type-safe GraphQL queries and mutations with compile-time validation
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

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Converts a Firebase [Timestamp] to a kotlinx.datetime [Instant] for GraphQL mutations. */
    private fun Timestamp.toKotlinxInstant(): Instant =
        Instant.fromEpochMilliseconds(this.toDate().time)

    /** Converts a domain [EventStatus] to the GraphQL [GraphQLEventStatus] enum. */
    private fun EventStatus.toGraphQL(): GraphQLEventStatus = when (this) {
        EventStatus.DRAFT -> GraphQLEventStatus.DRAFT
        EventStatus.PUBLISHED -> GraphQLEventStatus.PUBLISHED
        EventStatus.CLOSED -> GraphQLEventStatus.CLOSED
        EventStatus.CANCELLED -> GraphQLEventStatus.CANCELLED
    }

    /** Converts a domain [Location] to a [GraphQLLocationInput]. */
    private fun Location.toGraphQLInput(): GraphQLLocationInput =
        GraphQLLocationInput(
            name = this.name,
            latitude = this.coordinates?.latitude ?: 0.0,
            longitude = this.coordinates?.longitude ?: 0.0
        )

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

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
                        first = DEFAULT_PAGE_SIZE,
                        after = cursor,
                        filters = null
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
                        first = DEFAULT_PAGE_SIZE,
                        after = cursor,
                        filters = GraphQLEventFilters(
                                searchQuery = Optional.present(query),
                                status = Optional.present(GraphQLEventStatus.PUBLISHED)
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
                        first = DEFAULT_PAGE_SIZE,
                        after = cursor,
                        filters = GraphQLEventFilters(
                                organizerId = Optional.present(orgId)
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
     */
    override fun getEventsByLocation(center: Location, radiusKm: Double): Flow<List<Event>> = flow {
        try {
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = DEFAULT_PAGE_SIZE,
                        after = cursor,
                        filters = GraphQLEventFilters(
                                location = Optional.present(
                                    GraphQLLocationInput(
                                        name = center.name,
                                        latitude = center.coordinates?.latitude ?: 0.0,
                                        longitude = center.coordinates?.longitude ?: 0.0
                                    )
                                ),
                                radiusKm = Optional.present(radiusKm)
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
                        first = DEFAULT_PAGE_SIZE,
                        after = cursor,
                        filters = GraphQLEventFilters(
                                tags = Optional.present(listOf(tag))
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
            val graphqlStatus = status.toGraphQL()
            val events = mutableListOf<Event>()
            var cursor: String? = null
            var hasNextPage = true

            while (hasNextPage) {
                val response = apolloClient.query(
                    GetEventsQuery(
                        first = DEFAULT_PAGE_SIZE,
                        after = cursor,
                        filters = GraphQLEventFilters(
                                status = Optional.present(graphqlStatus)
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

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    /**
     * Creates a new event via GraphQL mutation.
     *
     * @return [Result] containing the new event's ID on success.
     */
    override suspend fun createEvent(event: Event): Result<String> {
        return try {
            requireNotNull(event.startTime) { "Event startTime is required" }
            requireNotNull(event.endTime) { "Event endTime is required" }

            val input = CreateEventInput(
                title = event.title,
                description = event.description,
                organizerId = event.organizerId,
                startTime = event.startTime.toKotlinxInstant(),
                endTime = event.endTime.toKotlinxInstant(),
                capacity = event.capacity,
                location = Optional.presentIfNotNull(event.location?.toGraphQLInput()),
                currency = Optional.present(event.currency),
                pricingTiers = event.pricingTiers.map { tier ->
                    PricingTierInput(
                        name = tier.name,
                        price = tier.price,
                        quantity = tier.quantity
                    )
                },
                images = Optional.present(event.images),
                tags = Optional.present(event.tags)
            )

            val response = apolloClient.mutation(CreateEventMutation(input)).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to create event: ${response.errors?.firstOrNull()?.message}"))
            } else {
                val id = response.data?.createEvent?.id
                    ?: return Result.failure(Exception("Create event returned no ID"))
                Result.success(id)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error creating event", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing event via GraphQL mutation.
     * All provided fields will be updated on the backend.
     */
    override suspend fun updateEvent(event: Event): Result<Unit> = try {
        val input = UpdateEventInput(
            title = Optional.present(event.title),
            description = Optional.present(event.description),
            status = Optional.present(event.status.toGraphQL()),
            location = Optional.presentIfNotNull(event.location?.toGraphQLInput()),
            startTime = Optional.presentIfNotNull(event.startTime?.toKotlinxInstant()),
            endTime = Optional.presentIfNotNull(event.endTime?.toKotlinxInstant()),
            capacity = Optional.present(event.capacity),
            pricingTiers = Optional.present(
                event.pricingTiers.map { tier ->
                    PricingTierInput(
                        name = tier.name,
                        price = tier.price,
                        quantity = tier.quantity
                    )
                }
            ),
            images = Optional.present(event.images),
            tags = Optional.present(event.tags)
        )

        val response = apolloClient.mutation(UpdateEventMutation(event.eventId, input)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to update event: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error updating event: ${event.eventId}", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Deletes an event via GraphQL mutation.
     */
    override suspend fun deleteEvent(eventId: String): Result<Unit> = try {
        val response = apolloClient.mutation(DeleteEventMutation(eventId)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to delete event: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error deleting event: $eventId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Adds a single image URL to an event's image list.
     */
    override suspend fun addEventImage(eventId: String, imageUrl: String): Result<Unit> = try {
        val response = apolloClient.mutation(AddEventImageMutation(eventId, imageUrl)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to add event image: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error adding event image: $eventId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Removes a single image URL from an event's image list.
     */
    override suspend fun removeEventImage(eventId: String, imageUrl: String): Result<Unit> = try {
        val response = apolloClient.mutation(RemoveEventImageMutation(eventId, imageUrl)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to remove event image: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error removing event image: $eventId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Replaces the full image list for an event by computing the diff between
     * the current images and the new image list, then calling add/remove mutations.
     */
    override suspend fun updateEventImages(eventId: String, imageUrls: List<String>): Result<Unit> {
        return try {
            // Fetch current event to compute diff
            val currentResponse = apolloClient.query(GetEventQuery(eventId)).execute()
            if (currentResponse.hasErrors()) {
                return Result.failure(
                    Exception("Failed to fetch event for image update: ${currentResponse.errors?.firstOrNull()?.message}")
                )
            }
            val currentImages = currentResponse.data?.event?.images ?: emptyList()

            val toAdd = imageUrls.filter { it !in currentImages }
            val toRemove = currentImages.filter { it !in imageUrls }

            for (url in toAdd) {
                val result = addEventImage(eventId, url)
                if (result.isFailure) return result
            }
            for (url in toRemove) {
                val result = removeEventImage(eventId, url)
                if (result.isFailure) return result
            }

            Result.success(Unit)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error updating event images: $eventId", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
