package ch.onepass.onepass.model.membership

import android.util.Log
import ch.onepass.onepass.graphql.GetMembershipsByOrganizationQuery
import ch.onepass.onepass.graphql.GetMyMembershipsQuery
import ch.onepass.onepass.graphql.RemoveMemberMutation
import ch.onepass.onepass.graphql.UpdateMemberRoleMutation
import ch.onepass.onepass.graphql.type.OrganizationRole as GraphQLOrganizationRole
import ch.onepass.onepass.graphql.type.UpdateMemberRoleInput
import ch.onepass.onepass.model.membership.MembershipMapper.toMembership
import ch.onepass.onepass.model.organization.OrganizationRole
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.exception.ApolloException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GraphQL-backed implementation of [MembershipRepository].
 *
 * This repository uses Apollo GraphQL client to fetch and mutate membership data from the
 * backend API, replacing Firebase Firestore calls with type-safe GraphQL queries and mutations.
 *
 * Key features:
 * - Type-safe GraphQL queries and mutations with compile-time validation
 * - Automatic authentication via Firebase Auth tokens
 * - Conversion from GraphQL types to domain models
 * - Flow-based reactive data streams
 *
 * Schema notes:
 * - Memberships are fetched through the Organization type's `memberships` relation.
 * - `removeMembership` and `updateMembership` require the membership ID, which is resolved
 *   by first querying the org's member list and finding the matching user.
 * - `addMembership` is not supported — direct member addition requires a schema extension.
 *   Use `OrganizationRepository.createInvitation` + `updateInvitationStatus(ACCEPTED)` instead.
 * - `getOrganizationsByUser` is resolved via `myMemberships` query for the authenticated user.
 */
class MembershipRepositoryGraphQL : MembershipRepository {
    private val apolloClient = ApolloClientProvider.getClient()

    companion object {
        private const val TAG = "MembershipRepoGraphQL"
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Maps a domain [OrganizationRole] to the GraphQL [GraphQLOrganizationRole] enum. */
    private fun OrganizationRole.toGraphQL(): GraphQLOrganizationRole = when (this) {
        OrganizationRole.OWNER -> GraphQLOrganizationRole.OWNER
        OrganizationRole.ADMIN -> GraphQLOrganizationRole.ADMIN
        OrganizationRole.MEMBER -> GraphQLOrganizationRole.MEMBER
        OrganizationRole.STAFF -> GraphQLOrganizationRole.STAFF
    }

    /**
     * Resolves the membership ID for a given (userId, orgId) pair by querying the org's members.
     *
     * @return [Result] containing the membership ID, or failure if not found.
     */
    private suspend fun resolveMembershipId(userId: String, orgId: String): Result<String> = try {
        val response = apolloClient.query(GetMembershipsByOrganizationQuery(orgId)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to fetch memberships: ${response.errors?.firstOrNull()?.message}"))
        } else {
            val membership = response.data?.organization?.memberships?.find { it.userId == userId }
            if (membership != null) {
                Result.success(membership.id)
            } else {
                Result.failure(
                    NoSuchElementException("Membership not found for user '$userId' in organization '$orgId'")
                )
            }
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error resolving membership ID for user $userId in org $orgId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Retrieves all users who are members of a specific organization.
     */
    override suspend fun getUsersByOrganization(orgId: String): Result<List<Membership>> = try {
        val response = apolloClient.query(
            GetMembershipsByOrganizationQuery(orgId)
        ).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to fetch memberships: ${response.errors?.firstOrNull()?.message}"))
        } else {
            val memberships = response.data?.organization?.memberships
                ?.map { it.toMembership() }
                ?: emptyList()
            Result.success(memberships)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error fetching memberships for org: $orgId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Retrieves all users who are members of a specific organization as a Flow.
     */
    override fun getUsersByOrganizationFlow(orgId: String): Flow<List<Membership>> = flow {
        try {
            val response = apolloClient.query(
                GetMembershipsByOrganizationQuery(orgId)
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch memberships: ${response.errors?.firstOrNull()?.message}")
            }

            val memberships = response.data?.organization?.memberships
                ?.map { it.toMembership() }
                ?: emptyList()
            emit(memberships)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching memberships for org: $orgId", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves all organizations that a specific user belongs to.
     *
     * Note: Uses `myMemberships` query — auth token determines the user.
     * The [userId] parameter is ignored in GraphQL mode.
     */
    override suspend fun getOrganizationsByUser(userId: String): Result<List<Membership>> {
        return try {
            val response = apolloClient.query(GetMyMembershipsQuery()).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to fetch my memberships: ${response.errors?.firstOrNull()?.message}"))
            } else {
                val memberships = response.data?.myMemberships?.map { it.toMembership() } ?: emptyList()
                Result.success(memberships)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching organizations by user", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Retrieves all organizations that a specific user belongs to as a Flow.
     *
     * Note: Uses `myMemberships` query — auth token determines the user.
     * The [userId] parameter is ignored in GraphQL mode.
     */
    override fun getOrganizationsByUserFlow(userId: String): Flow<List<Membership>> = flow {
        try {
            val response = apolloClient.query(GetMyMembershipsQuery()).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch my memberships: ${response.errors?.firstOrNull()?.message}")
            }

            val memberships = response.data?.myMemberships?.map { it.toMembership() } ?: emptyList()
            emit(memberships)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching organizations by user flow", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Checks if a membership with any of the given roles exists for the user in the organization.
     */
    override suspend fun hasMembership(
        userId: String,
        orgId: String,
        roles: List<OrganizationRole>
    ): Boolean = try {
        val response = apolloClient.query(
            GetMembershipsByOrganizationQuery(orgId)
        ).execute()

        if (response.hasErrors()) {
            false
        } else {
            response.data?.organization?.memberships?.any { membership ->
                membership.userId == userId &&
                    roles.map { it.name }.contains(membership.role.name)
            } ?: false
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error checking membership for user $userId in org $orgId", e)
        false
    }

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    /**
     * Directly adding a membership is not supported via GraphQL.
     *
     * The GraphQL schema only supports invitation-based member addition. To add a member:
     * 1. Call [OrganizationRepository.createInvitation] to send an invitation
     * 2. The invitee calls [OrganizationRepository.updateInvitationStatus] with ACCEPTED
     *
     * A schema extension with an `addMember(userId, orgId, role)` mutation would be needed
     * for direct admin-initiated additions.
     */
    override suspend fun addMembership(
        userId: String,
        orgId: String,
        role: OrganizationRole
    ): Result<String> {
        return Result.failure(
            UnsupportedOperationException(
                "addMembership via GraphQL is not supported. Use OrganizationRepository." +
                    "createInvitation() followed by updateInvitationStatus(ACCEPTED) instead"
            )
        )
    }

    /**
     * Removes a membership relationship between a user and an organization.
     *
     * Because the GraphQL `removeMember` mutation requires the membership ID (not userId+orgId),
     * this method first queries the organization's member list to resolve the membership ID,
     * then calls the mutation. This incurs one extra network round-trip.
     */
    override suspend fun removeMembership(userId: String, orgId: String): Result<Unit> {
        val membershipIdResult = resolveMembershipId(userId, orgId)
        if (membershipIdResult.isFailure) return membershipIdResult.map { }

        val membershipId = membershipIdResult.getOrThrow()

        return try {
            val response = apolloClient.mutation(RemoveMemberMutation(membershipId)).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to remove member: ${response.errors?.firstOrNull()?.message}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error removing membership $membershipId", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Updates the role of an existing membership.
     *
     * Because the GraphQL `updateMemberRole` mutation requires the membership ID (not userId+orgId),
     * this method first queries the organization's member list to resolve the membership ID,
     * then calls the mutation. This incurs one extra network round-trip.
     */
    override suspend fun updateMembership(
        userId: String,
        orgId: String,
        newRole: OrganizationRole
    ): Result<Unit> {
        val membershipIdResult = resolveMembershipId(userId, orgId)
        if (membershipIdResult.isFailure) return membershipIdResult.map { }

        val membershipId = membershipIdResult.getOrThrow()

        return try {
            val input = UpdateMemberRoleInput(
                membershipId = membershipId,
                role = newRole.toGraphQL()
            )

            val response = apolloClient.mutation(UpdateMemberRoleMutation(input)).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to update member role: ${response.errors?.firstOrNull()?.message}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error updating membership $membershipId to role $newRole", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
