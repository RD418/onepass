package ch.onepass.onepass

import android.app.Application
import ch.onepass.onepass.repository.RepositoryProvider

class OnePassApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ---------------------------------------------------------------
        // Backend switch: set to true to use GraphQL/Supabase backend,
        // false (default) to keep using Firebase Firestore.
        //
        // Flip this to true once your backend is running and the
        // GRAPHQL_URL is set correctly in local.properties.
        // ---------------------------------------------------------------
        // Set to true once backend is running and GRAPHQL_URL is configured
        // Set to false to fall back to Firebase Firestore
        RepositoryProvider.useGraphQL = true // ← change to true when ready

        // You can also enable GraphQL per-repository for gradual rollout:
        // RepositoryProvider.useGraphQLForEvents = true
        // RepositoryProvider.useGraphQLForUsers = true
        // RepositoryProvider.useGraphQLForOrganizations = true
        // RepositoryProvider.useGraphQLForTickets = true
        // RepositoryProvider.useGraphQLForPosts = true
        // RepositoryProvider.useGraphQLForMemberships = true
    }
}
