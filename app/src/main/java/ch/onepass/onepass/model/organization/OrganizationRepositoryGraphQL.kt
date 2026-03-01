package ch.onepass.onepass.model.organization

import android.util.Log
import ch.onepass.onepass.graphql.AcceptInvitationMutation
import ch.onepass.onepass.graphql.CreateOrganizationMutation
import ch.onepass.onepass.graphql.DeleteOrganizationMutation
import ch.onepass.onepass.graphql.GetMyInvitationsQuery
import ch.onepass.onepass.graphql.GetMyOrganizationsQuery
import ch.onepass.onepass.graphql.GetOrganizationInvitationsQuery
import ch.onepass.onepass.graphql.GetOrganizationQuery
import ch.onepass.onepass.graphql.InviteMemberMutation
import ch.onepass.onepass.graphql.RejectInvitationMutation
import ch.onepass.onepass.graphql.SearchOrganizationsQuery
import ch.onepass.onepass.graphql.UpdateOrganizationMutation
import ch.onepass.onepass.graphql.type.CreateOrganizationInput
import ch.onepass.onepass.graphql.type.InviteMemberInput
import ch.onepass.onepass.graphql.type.OrganizationRole as GraphQLOrganizationRole
import ch.onepass.onepass.graphql.type.UpdateOrganizationInput
import ch.onepass.onepass.model.organization.OrganizationMapper.toInvitation
import ch.onepass.onepass.model.organization.OrganizationMapper.toOrganization
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.exception.ApolloException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GraphQL-backed implementation of [OrganizationRepository].
 *
 * This repository uses Apollo GraphQL client to fetch and mutate organization data from the
 * backend API, replacing Firebase Firestore calls with type-safe GraphQL queries and mutations.
 *
 * Key features:
 * - Type-safe GraphQL queries and mutations with compile-time validation
 * - Automatic authentication via Firebase Auth tokens
 * - Conversion from GraphQL types to domain models
 * - Flow-based reactive data streams
 *
 * Note: Some queries (like getOrganizationsByStatus and getVerifiedOrganizations) are not
 * directly supported by the current GraphQL schema. deleteInvitation is also not in the schema.
 */
class OrganizationRepositoryGraphQL : OrganizationRepository {
    private val apolloClient = ApolloClientProvider.getClient()

    companion object {
        private const val TAG = "OrgRepositoryGraphQL"
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

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Retrieves a specific organization by its unique ID.
     */
    override fun getOrganizationById(organizationId: String): Flow<Organization?> = flow {
        try {
            val response = apolloClient.query(GetOrganizationQuery(organizationId)).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch organization: ${response.errors?.firstOrNull()?.message}")
            }

            val organization = response.data?.organization?.toOrganization()
            emit(organization)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching organization by ID: $organizationId", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves all organizations owned by the current user.
     *
     * Note: Uses `myOrganizations` query which returns organizations for the authenticated user.
     * The [ownerId] parameter is not used — auth token determines the user.
     */
    override fun getOrganizationsByOwner(ownerId: String): Flow<List<Organization>> = flow {
        try {
            val response = apolloClient.query(GetMyOrganizationsQuery()).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch organizations: ${response.errors?.firstOrNull()?.message}")
            }

            val organizations = response.data?.myOrganizations?.map { it.toOrganization() }
                ?: emptyList()
            emit(organizations)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching organizations by owner", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves all organizations with a specific status.
     *
     * Note: Not directly supported by the current GraphQL schema. A schema extension
     * with an `organizations(status: OrganizationStatus)` query is needed.
     */
    override fun getOrganizationsByStatus(status: OrganizationStatus): Flow<List<Organization>> = flow {
        throw NotImplementedError("getOrganizationsByStatus via GraphQL not yet implemented — schema extension required")
    }

    /**
     * Searches for organizations whose names match the given query.
     */
    override fun searchOrganizations(query: String): Flow<List<Organization>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            val response = apolloClient.query(
                SearchOrganizationsQuery(
                    query = query.trim(),
                    limit = 20
                )
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to search organizations: ${response.errors?.firstOrNull()?.message}")
            }

            val organizations = response.data?.searchOrganizations?.map { it.toOrganization() }
                ?: emptyList()
            emit(organizations)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error searching organizations", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves verified organizations.
     *
     * Note: Not directly supported by the current GraphQL schema. A schema extension
     * with a `verifiedOrganizations` query is needed.
     */
    override fun getVerifiedOrganizations(): Flow<List<Organization>> = flow {
        throw NotImplementedError("getVerifiedOrganizations via GraphQL not yet implemented — schema extension required")
    }

    /**
     * Retrieves pending invitations for a specific organization.
     */
    override fun getPendingInvitations(organizationId: String): Flow<List<OrganizationInvitation>> = flow {
        try {
            val response = apolloClient.query(
                GetOrganizationInvitationsQuery(
                    organizationId = organizationId
                )
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch invitations: ${response.errors?.firstOrNull()?.message}")
            }

            val invitations = response.data?.organizationInvitations
                ?.map { it.toInvitation() }
                ?.filter { it.status == InvitationStatus.PENDING }
                ?: emptyList()
            emit(invitations)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching pending invitations", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Retrieves invitations for the current user.
     *
     * Note: Uses `myInvitations` query — auth token determines the user.
     * The [email] parameter is not used in the GraphQL implementation.
     */
    override fun getInvitationsByEmail(email: String): Flow<List<OrganizationInvitation>> = flow {
        try {
            val response = apolloClient.query(GetMyInvitationsQuery()).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch invitations: ${response.errors?.firstOrNull()?.message}")
            }

            val invitations = response.data?.myInvitations?.map { it.toInvitation() }
                ?: emptyList()
            emit(invitations)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching invitations by email", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    /**
     * Creates a new organization via GraphQL mutation.
     *
     * Note: Server-managed fields (ownerId, status, verified, stripe fields, etc.)
     * are set automatically by the backend from the auth context.
     *
     * @return [Result] containing the new organization's ID on success.
     */
    override suspend fun createOrganization(organization: Organization): Result<String> {
        return try {
            val input = CreateOrganizationInput(
                name = organization.name,
                description = Optional.presentIfNotNull(organization.description.takeIf { it.isNotEmpty() }),
                profileImageUrl = Optional.present(organization.profileImageUrl),
                coverImageUrl = Optional.present(organization.coverImageUrl),
                website = Optional.present(organization.website),
                instagram = Optional.present(organization.instagram),
                tiktok = Optional.present(organization.tiktok),
                facebook = Optional.present(organization.facebook),
                contactEmail = Optional.present(organization.contactEmail),
                contactPhone = Optional.present(organization.contactPhone),
                phonePrefix = Optional.present(organization.phonePrefix),
                address = Optional.present(organization.address)
            )

            val response = apolloClient.mutation(CreateOrganizationMutation(input)).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to create organization: ${response.errors?.firstOrNull()?.message}"))
            } else {
                val id = response.data?.createOrganization?.id
                    ?: return Result.failure(Exception("Create organization returned no ID"))
                Result.success(id)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error creating organization", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Updates an existing organization via GraphQL mutation.
     * All provided fields are sent; the backend only modifies non-absent values.
     */
    override suspend fun updateOrganization(organization: Organization): Result<Unit> = try {
        val input = UpdateOrganizationInput(
            name = Optional.present(organization.name),
            description = Optional.present(organization.description.takeIf { it.isNotEmpty() }),
            profileImageUrl = Optional.present(organization.profileImageUrl),
            coverImageUrl = Optional.present(organization.coverImageUrl),
            website = Optional.present(organization.website),
            instagram = Optional.present(organization.instagram),
            tiktok = Optional.present(organization.tiktok),
            facebook = Optional.present(organization.facebook),
            contactEmail = Optional.present(organization.contactEmail),
            contactPhone = Optional.present(organization.contactPhone),
            phonePrefix = Optional.present(organization.phonePrefix),
            address = Optional.present(organization.address)
        )

        val response = apolloClient.mutation(UpdateOrganizationMutation(organization.id, input)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to update organization: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error updating organization: ${organization.id}", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Deletes an organization via GraphQL mutation.
     */
    override suspend fun deleteOrganization(organizationId: String): Result<Unit> = try {
        val response = apolloClient.mutation(DeleteOrganizationMutation(organizationId)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to delete organization: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error deleting organization: $organizationId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Creates an invitation to join an organization via GraphQL mutation.
     *
     * Maps the domain [OrganizationInvitation] to the [InviteMemberInput] type.
     *
     * @return [Result] containing the new invitation's ID on success.
     */
    override suspend fun createInvitation(invitation: OrganizationInvitation): Result<String> {
        return try {
            val input = InviteMemberInput(
                organizationId = invitation.orgId,
                inviteeEmail = invitation.inviteeEmail,
                role = invitation.role.toGraphQL()
            )

            val response = apolloClient.mutation(InviteMemberMutation(input)).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to create invitation: ${response.errors?.firstOrNull()?.message}"))
            } else {
                val id = response.data?.inviteMember?.id
                    ?: return Result.failure(Exception("Create invitation returned no ID"))
                Result.success(id)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error creating invitation", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Updates an invitation's status via GraphQL mutation.
     *
     * - [InvitationStatus.ACCEPTED] → calls `acceptInvitation` mutation
     * - [InvitationStatus.REJECTED] → calls `rejectInvitation` mutation
     * - Other statuses (EXPIRED, REVOKED) are not supported by the current schema.
     */
    override suspend fun updateInvitationStatus(
        invitationId: String,
        newStatus: InvitationStatus
    ): Result<Unit> = try {
        when (newStatus) {
            InvitationStatus.ACCEPTED -> {
                val response = apolloClient.mutation(AcceptInvitationMutation(invitationId)).execute()
                if (response.hasErrors()) {
                    Result.failure(Exception("Failed to accept invitation: ${response.errors?.firstOrNull()?.message}"))
                } else {
                    Result.success(Unit)
                }
            }
            InvitationStatus.REJECTED -> {
                val response = apolloClient.mutation(RejectInvitationMutation(invitationId)).execute()
                if (response.hasErrors()) {
                    Result.failure(Exception("Failed to reject invitation: ${response.errors?.firstOrNull()?.message}"))
                } else {
                    Result.success(Unit)
                }
            }
            else -> Result.failure(
                UnsupportedOperationException(
                    "Setting invitation status to '$newStatus' via GraphQL is not supported. " +
                        "Only ACCEPTED and REJECTED transitions are available."
                )
            )
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error updating invitation status: $invitationId → $newStatus", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Deletes an invitation.
     *
     * Note: There is no `deleteInvitation` mutation in the current GraphQL schema.
     * Use [updateInvitationStatus] with [InvitationStatus.REVOKED] as an alternative
     * if the schema is extended to support it.
     */
    override suspend fun deleteInvitation(invitationId: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "deleteInvitation via GraphQL is not yet supported — no matching mutation in schema"
            )
        )
    }

    /**
     * Updates only the profile image URL for an organization.
     *
     * All other fields in [UpdateOrganizationInput] are left absent, so only
     * profileImageUrl will be modified.
     */
    override suspend fun updateProfileImage(organizationId: String, imageUrl: String?): Result<Unit> = try {
        val input = UpdateOrganizationInput(
            profileImageUrl = Optional.present(imageUrl)
        )

        val response = apolloClient.mutation(UpdateOrganizationMutation(organizationId, input)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to update profile image: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error updating profile image for org: $organizationId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Updates only the cover image URL for an organization.
     *
     * All other fields in [UpdateOrganizationInput] are left absent, so only
     * coverImageUrl will be modified.
     */
    override suspend fun updateCoverImage(organizationId: String, imageUrl: String?): Result<Unit> = try {
        val input = UpdateOrganizationInput(
            coverImageUrl = Optional.present(imageUrl)
        )

        val response = apolloClient.mutation(UpdateOrganizationMutation(organizationId, input)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to update cover image: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error updating cover image for org: $organizationId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }
}
