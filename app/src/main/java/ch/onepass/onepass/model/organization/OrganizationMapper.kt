package ch.onepass.onepass.model.organization

import ch.onepass.onepass.graphql.GetMyInvitationsQuery
import ch.onepass.onepass.graphql.GetMyOrganizationsQuery
import ch.onepass.onepass.graphql.GetOrganizationInvitationsQuery
import ch.onepass.onepass.graphql.GetOrganizationQuery
import ch.onepass.onepass.graphql.SearchOrganizationsQuery
import com.google.firebase.Timestamp
import kotlinx.datetime.Instant
import java.util.Date

/**
 * Mapper utility to convert GraphQL-generated organization types to domain models.
 *
 * This mapper handles the conversion between:
 * - GraphQL Organization types -> Domain Organization model
 * - GraphQL OrganizationInvitation types -> Domain OrganizationInvitation model
 * - GraphQL DateTime (kotlinx.datetime.Instant) -> Firebase Timestamp
 * - GraphQL enum types -> Domain enum types
 */
object OrganizationMapper {
    /**
     * Converts a kotlinx.datetime.Instant to a Firebase Timestamp.
     */
    private fun Instant.toFirebaseTimestamp(): Timestamp {
        return Timestamp(Date(this.toEpochMilliseconds()))
    }

    /**
     * Maps a GraphQL OrganizationStatus name to the domain OrganizationStatus enum.
     */
    private fun mapOrganizationStatus(statusName: String): OrganizationStatus {
        return when (statusName) {
            "PENDING" -> OrganizationStatus.PENDING
            "ACTIVE" -> OrganizationStatus.ACTIVE
            "SUSPENDED" -> OrganizationStatus.SUSPENDED
            "ARCHIVED" -> OrganizationStatus.ARCHIVED
            else -> OrganizationStatus.PENDING
        }
    }

    /**
     * Maps a GraphQL OrganizationRole name to the domain OrganizationRole enum.
     */
    private fun mapOrganizationRole(roleName: String): OrganizationRole {
        return when (roleName) {
            "OWNER" -> OrganizationRole.OWNER
            "ADMIN" -> OrganizationRole.ADMIN
            "MEMBER" -> OrganizationRole.MEMBER
            "STAFF" -> OrganizationRole.STAFF
            else -> OrganizationRole.MEMBER
        }
    }

    /**
     * Maps a GraphQL InvitationStatus name to the domain InvitationStatus enum.
     */
    private fun mapInvitationStatus(statusName: String): InvitationStatus {
        return when (statusName) {
            "PENDING" -> InvitationStatus.PENDING
            "ACCEPTED" -> InvitationStatus.ACCEPTED
            "REJECTED" -> InvitationStatus.REJECTED
            "EXPIRED" -> InvitationStatus.EXPIRED
            "REVOKED" -> InvitationStatus.REVOKED
            else -> InvitationStatus.PENDING
        }
    }

    /**
     * Converts a full Organization from GetOrganizationQuery to domain Organization model.
     */
    fun GetOrganizationQuery.Organization.toOrganization(): Organization {
        return Organization(
            id = this.id,
            name = this.name,
            description = this.description ?: "",
            ownerId = this.ownerId,
            status = mapOrganizationStatus(this.status.name),
            verified = this.verified,
            profileImageUrl = this.profileImageUrl,
            coverImageUrl = this.coverImageUrl,
            website = this.website,
            instagram = this.instagram,
            tiktok = this.tiktok,
            facebook = this.facebook,
            contactEmail = this.contactEmail,
            contactPhone = this.contactPhone,
            phonePrefix = this.phonePrefix,
            address = this.address,
            followerCount = this.followerCount,
            averageRating = this.averageRating.toFloat(),
            stripeConnectedAccountId = this.stripeConnectedAccountId,
            stripeAccountStatus = this.stripeAccountStatus,
            stripeChargesEnabled = this.stripeChargesEnabled,
            stripePayoutsEnabled = this.stripePayoutsEnabled,
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp()
        )
    }

    /**
     * Converts a MyOrganization from GetMyOrganizationsQuery to domain Organization model.
     */
    fun GetMyOrganizationsQuery.MyOrganization.toOrganization(): Organization {
        return Organization(
            id = this.id,
            name = this.name,
            description = this.description ?: "",
            ownerId = this.ownerId,
            status = mapOrganizationStatus(this.status.name),
            verified = this.verified,
            profileImageUrl = this.profileImageUrl,
            coverImageUrl = this.coverImageUrl,
            followerCount = this.followerCount,
            averageRating = this.averageRating.toFloat(),
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp()
        )
    }

    /**
     * Converts a SearchOrganization from SearchOrganizationsQuery to domain Organization model.
     */
    fun SearchOrganizationsQuery.SearchOrganization.toOrganization(): Organization {
        return Organization(
            id = this.id,
            name = this.name,
            description = this.description ?: "",
            ownerId = this.ownerId,
            status = mapOrganizationStatus(this.status.name),
            verified = this.verified,
            profileImageUrl = this.profileImageUrl,
            coverImageUrl = this.coverImageUrl,
            followerCount = this.followerCount,
            averageRating = this.averageRating.toFloat(),
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp()
        )
    }

    /**
     * Converts an OrganizationInvitation from GetOrganizationInvitationsQuery to domain model.
     */
    fun GetOrganizationInvitationsQuery.OrganizationInvitation.toInvitation(): ch.onepass.onepass.model.organization.OrganizationInvitation {
        return ch.onepass.onepass.model.organization.OrganizationInvitation(
            id = this.id,
            orgId = this.orgId,
            inviteeEmail = this.inviteeEmail,
            role = mapOrganizationRole(this.role.name),
            invitedBy = this.invitedBy,
            status = mapInvitationStatus(this.status.name),
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp(),
            expiresAt = this.expiresAt?.toFirebaseTimestamp()
        )
    }

    /**
     * Converts a MyInvitation from GetMyInvitationsQuery to domain OrganizationInvitation model.
     */
    fun GetMyInvitationsQuery.MyInvitation.toInvitation(): ch.onepass.onepass.model.organization.OrganizationInvitation {
        return ch.onepass.onepass.model.organization.OrganizationInvitation(
            id = this.id,
            orgId = this.orgId,
            inviteeEmail = this.inviteeEmail,
            role = mapOrganizationRole(this.role.name),
            invitedBy = this.invitedBy,
            status = mapInvitationStatus(this.status.name),
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp(),
            expiresAt = this.expiresAt?.toFirebaseTimestamp()
        )
    }
}
