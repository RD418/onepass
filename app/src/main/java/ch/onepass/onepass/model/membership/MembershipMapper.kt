package ch.onepass.onepass.model.membership

import ch.onepass.onepass.graphql.GetMembershipsByOrganizationQuery
import ch.onepass.onepass.graphql.GetMyMembershipsQuery
import ch.onepass.onepass.model.organization.OrganizationRole
import com.google.firebase.Timestamp
import kotlinx.datetime.Instant
import java.util.Date

/**
 * Mapper utility to convert GraphQL-generated membership types to domain models.
 *
 * This mapper handles the conversion between:
 * - GraphQL Membership types -> Domain Membership model
 * - GraphQL DateTime (kotlinx.datetime.Instant) -> Firebase Timestamp
 * - GraphQL OrganizationRole enum -> Domain OrganizationRole enum
 *
 * Note: The GraphQL Membership type has `joinedAt` which maps to the domain `createdAt` field,
 * and uses `organizationId` which maps to the domain `orgId` field.
 */
object MembershipMapper {
    /**
     * Converts a kotlinx.datetime.Instant to a Firebase Timestamp.
     */
    private fun Instant.toFirebaseTimestamp(): Timestamp {
        return Timestamp(Date(this.toEpochMilliseconds()))
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
     * Converts a Membership from GetMembershipsByOrganizationQuery to domain Membership model.
     */
    fun GetMembershipsByOrganizationQuery.Membership.toMembership(): ch.onepass.onepass.model.membership.Membership {
        return ch.onepass.onepass.model.membership.Membership(
            membershipId = this.id,
            userId = this.userId,
            orgId = this.organizationId,
            role = mapOrganizationRole(this.role.name),
            createdAt = this.joinedAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp()
        )
    }

    /**
     * Converts a Membership from GetMyMembershipsQuery to domain Membership model.
     */
    fun GetMyMembershipsQuery.MyMembership.toMembership(): ch.onepass.onepass.model.membership.Membership {
        return ch.onepass.onepass.model.membership.Membership(
            membershipId = this.id,
            userId = this.userId,
            orgId = this.organizationId,
            role = mapOrganizationRole(this.role.name),
            createdAt = this.joinedAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp()
        )
    }
}
