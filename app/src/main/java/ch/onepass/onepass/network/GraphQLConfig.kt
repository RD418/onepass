package ch.onepass.onepass.network

import ch.onepass.onepass.BuildConfig

/**
 * Configuration object for GraphQL client settings.
 *
 * This object manages the GraphQL endpoint URL configuration, which can be:
 * - Set in local.properties as GRAPHQL_URL
 * - For Android emulator: Use http://10.0.2.2:4000/graphql (maps to host localhost)
 * - For physical device: Use your computer's local IP (e.g., http://192.168.1.100:4000/graphql)
 * - For production: Use deployed backend URL (e.g., https://api.onepass.ch/graphql)
 */
object GraphQLConfig {
    /**
     * The GraphQL endpoint URL.
     * Default is set for Android emulator during development.
     * Override this in local.properties with GRAPHQL_URL property.
     */
    val endpoint: String
        get() = BuildConfig.GRAPHQL_URL.ifBlank {
            // Default for Android emulator (maps to host's localhost)
            "http://10.0.2.2:4000/graphql"
        }

    /**
     * Connection timeout in seconds
     */
    const val TIMEOUT_SECONDS = 30L
}
