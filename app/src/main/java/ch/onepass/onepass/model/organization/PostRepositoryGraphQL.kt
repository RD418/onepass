package ch.onepass.onepass.model.organization

import android.util.Log
import ch.onepass.onepass.graphql.CreatePostMutation
import ch.onepass.onepass.graphql.GetPostsByOrganizationQuery
import ch.onepass.onepass.model.organization.PostMapper.toPost
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.exception.ApolloException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GraphQL-backed implementation of [PostRepository].
 *
 * This repository uses Apollo GraphQL client to fetch and mutate post data from the backend API,
 * replacing Firebase Firestore calls with type-safe GraphQL queries and mutations.
 *
 * Key features:
 * - Type-safe GraphQL queries and mutations with compile-time validation
 * - Automatic authentication via Firebase Auth tokens
 * - Conversion from GraphQL types to domain models
 * - Flow-based reactive data streams
 *
 * Schema notes:
 * - Posts are fetched through the Organization type's `posts` relation.
 * - `deletePost`, `likePost`, and `unlikePost` have no matching GraphQL mutations in the current
 *   schema and remain unsupported in this implementation.
 * - The GraphQL `createPost` mutation requires a `title` field which is not present in the
 *   domain [Post] model; the first 50 characters of content are used as the title.
 */
class PostRepositoryGraphQL : PostRepository {
    private val apolloClient = ApolloClientProvider.getClient()

    companion object {
        private const val TAG = "PostRepositoryGraphQL"
        /** Max characters for the synthetic title derived from post content. */
        private const val TITLE_PREVIEW_LENGTH = 50
    }

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Retrieves all posts for a specific organization, ordered by creation date (newest first).
     */
    override fun getPostsByOrganization(organizationId: String): Flow<List<Post>> = flow {
        try {
            val response = apolloClient.query(
                GetPostsByOrganizationQuery(organizationId)
            ).execute()

            if (response.hasErrors()) {
                Log.e(TAG, "GraphQL errors: ${response.errors}")
                throw Exception("Failed to fetch posts: ${response.errors?.firstOrNull()?.message}")
            }

            val posts = response.data?.organization?.posts?.map { it.toPost() }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            emit(posts)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching posts for organization: $organizationId", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    /**
     * Creates a new post for an organization via GraphQL mutation.
     *
     * Because the GraphQL `createPost` mutation requires a `title` field that is absent in the
     * domain [Post] model, the first [TITLE_PREVIEW_LENGTH] characters of the sanitized content
     * are used as the title automatically.
     *
     * @return [Result] containing the new post's ID on success.
     */
    override suspend fun createPost(post: Post): Result<String> {
        return try {
            val sanitizedContent = Post.sanitizeContent(post.content)
                ?: return Result.failure(IllegalArgumentException("Post content cannot be empty or blank"))

            // The GraphQL schema requires a title; use content preview as a synthetic title
            val syntheticTitle = sanitizedContent.take(TITLE_PREVIEW_LENGTH)

            val response = apolloClient.mutation(
                CreatePostMutation(
                    organizationId = post.organizationId,
                    title = syntheticTitle,
                    content = sanitizedContent,
                    imageUrl = null // domain Post has no imageUrl field
                )
            ).execute()

            if (response.hasErrors()) {
                Result.failure(Exception("Failed to create post: ${response.errors?.firstOrNull()?.message}"))
            } else {
                val id = response.data?.createPost?.id
                    ?: return Result.failure(Exception("Create post returned no ID"))
                Result.success(id)
            }
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error creating post for org: ${post.organizationId}", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Deletes a post.
     *
     * Note: There is no `deletePost` mutation in the current GraphQL schema. This operation
     * is not supported by this implementation.
     */
    override suspend fun deletePost(postId: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "deletePost via GraphQL is not yet supported — no matching mutation in schema"
            )
        )
    }

    /**
     * Adds a like to a post.
     *
     * Note: There is no `likePost` mutation in the current GraphQL schema. This operation
     * is not supported by this implementation.
     */
    override suspend fun likePost(postId: String, userId: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "likePost via GraphQL is not yet supported — no matching mutation in schema"
            )
        )
    }

    /**
     * Removes a like from a post.
     *
     * Note: There is no `unlikePost` mutation in the current GraphQL schema. This operation
     * is not supported by this implementation.
     */
    override suspend fun unlikePost(postId: String, userId: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "unlikePost via GraphQL is not yet supported — no matching mutation in schema"
            )
        )
    }
}
