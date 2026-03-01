package ch.onepass.onepass.model.user

import android.util.Log
import ch.onepass.onepass.graphql.AddFavoriteEventMutation
import ch.onepass.onepass.graphql.GetMeQuery
import ch.onepass.onepass.graphql.GetMyFavoriteEventIdsQuery
import ch.onepass.onepass.graphql.GetUserQuery
import ch.onepass.onepass.graphql.RemoveFavoriteEventMutation
import ch.onepass.onepass.graphql.SearchUsersQuery
import ch.onepass.onepass.graphql.UpdateUserMutation
import ch.onepass.onepass.graphql.type.UpdateUserInput
import ch.onepass.onepass.model.staff.StaffSearchResult
import ch.onepass.onepass.model.user.UserMapper.toStaffSearchResult
import ch.onepass.onepass.model.user.UserMapper.toUser
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GraphQL-backed implementation of [UserRepository].
 *
 * This repository uses Apollo GraphQL client to fetch and mutate user data from the backend API,
 * replacing Firebase Firestore calls with type-safe GraphQL queries and mutations.
 *
 * Key features:
 * - Type-safe GraphQL queries and mutations with compile-time validation
 * - Automatic authentication via Firebase Auth tokens
 * - Conversion from GraphQL types to domain models
 * - Flow-based reactive data streams for favorites
 */
class UserRepositoryGraphQL : UserRepository {
    private val apolloClient = ApolloClientProvider.getClient()

    companion object {
        private const val TAG = "UserRepositoryGraphQL"
    }

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Retrieves the currently authenticated user via the `me` query.
     */
    override suspend fun getCurrentUser(): User? {
        return try {
            val response = apolloClient.query(GetMeQuery()).execute()

            if (response.hasErrors()) {
                Log.e(TAG, "GraphQL errors: ${response.errors}")
                throw Exception("Failed to fetch current user: ${response.errors?.firstOrNull()?.message}")
            }

            response.data?.me?.toUser()
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching current user", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves the current user or creates a new one if they do not exist.
     *
     * In the GraphQL implementation, the backend handles user creation automatically
     * on first authentication. This method simply calls `me` query.
     */
    override suspend fun getOrCreateUser(): User? {
        return getCurrentUser()
    }

    /**
     * Updates the last login timestamp for the specified user.
     *
     * Note: There is no explicit `updateLastLogin` mutation in the GraphQL schema.
     * The backend handles last-login tracking automatically upon authentication.
     */
    override suspend fun updateLastLogin(uid: String) {
        Log.w(TAG, "updateLastLogin via GraphQL not yet implemented — backend handles this automatically")
    }

    /**
     * Retrieves a user by their unique ID.
     */
    override suspend fun getUserById(uid: String): Result<StaffSearchResult?> = try {
        val response = apolloClient.query(GetUserQuery(uid)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to fetch user: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(response.data?.user?.toStaffSearchResult())
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error fetching user by ID: $uid", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Searches for users by the specified search type.
     *
     * Note: The GraphQL API does not differentiate between search types (name vs email).
     * The backend handles search across all relevant fields. The [organizationId] filter
     * is also not supported by the current GraphQL schema.
     */
    override suspend fun searchUsers(
        query: String,
        searchType: UserSearchType,
        organizationId: String?
    ): Result<List<StaffSearchResult>> = try {
        require(query.isNotBlank()) { "Query cannot be blank" }

        val response = apolloClient.query(
            SearchUsersQuery(
                query = query.trim(),
                limit = 20
            )
        ).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to search users: ${response.errors?.firstOrNull()?.message}"))
        } else {
            val results = response.data?.searchUsers?.map { it.toStaffSearchResult() } ?: emptyList()
            Result.success(results)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error searching users", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Retrieves the set of IDs for events marked as favorite by the current user.
     *
     * Note: This emits once (not real-time like Firebase snapshot listeners).
     * The [uid] parameter is not used — auth token determines the user.
     */
    override fun getFavoriteEvents(uid: String): Flow<Set<String>> = flow {
        try {
            val response = apolloClient.query(GetMyFavoriteEventIdsQuery()).execute()

            if (response.hasErrors()) {
                Log.e(TAG, "GraphQL errors: ${response.errors}")
                emit(emptySet())
                return@flow
            }

            val eventIds = response.data?.me?.favoriteEvents?.map { it.id }?.toSet() ?: emptySet()
            emit(eventIds)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching favorite events", e)
            emit(emptySet())
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching favorite events", e)
            emit(emptySet())
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    /**
     * Adds an event to the current user's list of favorite events.
     *
     * Note: The [uid] parameter is not used — auth token determines the user.
     */
    override suspend fun addFavoriteEvent(uid: String, eventId: String): Result<Unit> = try {
        val response = apolloClient.mutation(AddFavoriteEventMutation(eventId)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to add favorite event: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error adding favorite event: $eventId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Removes an event from the current user's list of favorite events.
     *
     * Note: The [uid] parameter is not used — auth token determines the user.
     */
    override suspend fun removeFavoriteEvent(uid: String, eventId: String): Result<Unit> = try {
        val response = apolloClient.mutation(RemoveFavoriteEventMutation(eventId)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to remove favorite event: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error removing favorite event: $eventId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Updates a specific field on the current user's profile.
     *
     * Maps known domain field names to the [UpdateUserInput] fields.
     * Supported fields: displayName, bio, avatarUrl, coverUrl, phoneE164, country,
     * showEmail, analyticsEnabled.
     *
     * Note: The [uid] parameter is not used — auth token determines the user.
     */
    override suspend fun updateUserField(uid: String, field: String, value: Any): Result<Unit> {
        return try {
            val input: UpdateUserInput = when (field) {
                "displayName" -> UpdateUserInput(
                    displayName = Optional.present(value as? String)
                )
                "bio" -> UpdateUserInput(
                    bio = Optional.present(value as? String)
                )
                "avatarUrl" -> UpdateUserInput(
                    avatarUrl = Optional.present(value as? String)
                )
                "coverUrl" -> UpdateUserInput(
                    coverUrl = Optional.present(value as? String)
                )
                "phoneE164" -> UpdateUserInput(
                    phoneE164 = Optional.present(value as? String)
                )
                "country" -> UpdateUserInput(
                    country = Optional.present(value as? String)
                )
                "showEmail" -> UpdateUserInput(
                    showEmail = Optional.present(value as? Boolean)
                )
                "analyticsEnabled" -> UpdateUserInput(
                    analyticsEnabled = Optional.present(value as? Boolean)
                )
                else -> return Result.failure(
                    IllegalArgumentException(
                        "Unknown user field: '$field'. Supported fields: displayName, bio, avatarUrl, " +
                            "coverUrl, phoneE164, country, showEmail, analyticsEnabled"
                    )
                )
            }

            val response = apolloClient.mutation(UpdateUserMutation(input)).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to update user field '$field': ${response.errors?.firstOrNull()?.message}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error updating user field: $field", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
