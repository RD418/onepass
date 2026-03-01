package ch.onepass.onepass.model.organization

import ch.onepass.onepass.graphql.GetPostsByOrganizationQuery
import com.google.firebase.Timestamp
import kotlinx.datetime.Instant
import java.util.Date

/**
 * Mapper utility to convert GraphQL-generated post types to domain models.
 *
 * This mapper handles the conversion between:
 * - GraphQL Post types -> Domain Post model
 * - GraphQL DateTime (kotlinx.datetime.Instant) -> Firebase Timestamp
 *
 * Note: The GraphQL Post type does not include `authorId`, `authorName`, or `likedBy` fields.
 * These are set to default values in the mapping.
 */
object PostMapper {
    /**
     * Converts a kotlinx.datetime.Instant to a Firebase Timestamp.
     */
    private fun Instant.toFirebaseTimestamp(): Timestamp {
        return Timestamp(Date(this.toEpochMilliseconds()))
    }

    /**
     * Converts a Post from GetPostsByOrganizationQuery to domain Post model.
     */
    fun GetPostsByOrganizationQuery.Post.toPost(): Post {
        return Post(
            id = this.id,
            organizationId = this.organizationId,
            authorId = "", // Not available in GraphQL response
            authorName = "", // Not available in GraphQL response
            content = this.content,
            likedBy = emptyList(), // Not available in GraphQL response
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp()
        )
    }
}
