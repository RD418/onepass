package ch.onepass.onepass.model.user

import ch.onepass.onepass.graphql.GetMeQuery
import ch.onepass.onepass.graphql.GetUserQuery
import ch.onepass.onepass.graphql.SearchUsersQuery
import ch.onepass.onepass.model.staff.StaffSearchResult
import kotlinx.datetime.Instant
import java.util.Date

/**
 * Mapper utility to convert GraphQL-generated types to domain models.
 *
 * This mapper handles the conversion between:
 * - GraphQL User types -> Domain User / StaffSearchResult models
 * - GraphQL DateTime (kotlinx.datetime.Instant) -> java.util.Date
 */
object UserMapper {
    /**
     * Converts a kotlinx.datetime.Instant to a java.util.Date.
     */
    private fun Instant.toDate(): Date {
        return Date(this.toEpochMilliseconds())
    }

    /**
     * Converts the GraphQL Me type from GetMeQuery to domain User model.
     */
    fun GetMeQuery.Me.toUser(): User {
        return User(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName ?: "",
            bio = this.bio,
            avatarUrl = this.avatarUrl,
            coverUrl = this.coverUrl,
            phoneE164 = this.phoneE164,
            country = this.country,
            status = when (this.status.name) {
                "ACTIVE" -> Status.ACTIVE
                "BANNED" -> Status.BANNED
                "DELETED" -> Status.DELETED
                else -> Status.ACTIVE
            },
            showEmail = this.showEmail,
            analyticsEnabled = this.analyticsEnabled,
            createdAt = this.createdAt.toDate(),
            lastLoginAt = this.lastLoginAt?.toDate()
        )
    }

    /**
     * Converts a GraphQL User type from GetUserQuery to StaffSearchResult model.
     */
    fun GetUserQuery.User.toStaffSearchResult(): StaffSearchResult {
        return StaffSearchResult(
            id = this.uid,
            email = this.email,
            displayName = this.displayName ?: "",
            avatarUrl = this.avatarUrl
        )
    }

    /**
     * Converts a GraphQL User type from SearchUsersQuery to StaffSearchResult model.
     */
    fun SearchUsersQuery.SearchUser.toStaffSearchResult(): StaffSearchResult {
        return StaffSearchResult(
            id = this.uid,
            email = this.email,
            displayName = this.displayName ?: "",
            avatarUrl = this.avatarUrl
        )
    }
}
