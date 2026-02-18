package ch.onepass.onepass.network

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton provider for Apollo GraphQL client.
 *
 * This class manages the lifecycle of the Apollo client and ensures only one instance
 * exists throughout the application. The client is configured with:
 * - Firebase authentication via AuthInterceptor
 * - Configurable endpoint URL
 * - Proper timeout settings
 * - OkHttp for networking
 *
 * Usage:
 * ```kotlin
 * val apolloClient = ApolloClientProvider.getClient()
 * val response = apolloClient.query(GetEventsQuery()).execute()
 * ```
 */
object ApolloClientProvider {
    @Volatile
    private var apolloClient: ApolloClient? = null

    /**
     * Gets or creates the Apollo client instance.
     * Thread-safe singleton implementation using double-checked locking.
     *
     * @return Configured ApolloClient instance
     */
    fun getClient(): ApolloClient {
        return apolloClient ?: synchronized(this) {
            apolloClient ?: createApolloClient().also { apolloClient = it }
        }
    }

    /**
     * Creates a new Apollo client with all necessary configurations.
     */
    private fun createApolloClient(): ApolloClient {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .connectTimeout(GraphQLConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(GraphQLConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(GraphQLConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return ApolloClient.Builder()
            .serverUrl(GraphQLConfig.endpoint)
            .okHttpClient(okHttpClient)
            .build()
    }

    /**
     * Closes the Apollo client and releases resources.
     * Should be called when the app is shutting down or when you need to reinitialize
     * the client with different settings.
     */
    fun closeClient() {
        synchronized(this) {
            apolloClient?.close()
            apolloClient = null
        }
    }

    /**
     * Forces recreation of the Apollo client on next access.
     * Useful when configuration changes (e.g., endpoint URL change).
     */
    fun resetClient() {
        closeClient()
    }
}
