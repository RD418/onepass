package ch.onepass.onepass.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds Firebase authentication token to GraphQL requests.
 *
 * This interceptor automatically fetches the current user's Firebase ID token
 * and adds it as a Bearer token in the Authorization header for all GraphQL requests.
 *
 * The token is fetched fresh each time to ensure it's valid (Firebase handles caching internally).
 */
class AuthInterceptor : Interceptor {
    companion object {
        private const val TAG = "AuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Get current Firebase user
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        // If no user is authenticated, proceed without auth header
        if (currentUser == null) {
            Log.d(TAG, "No authenticated user, proceeding without auth token")
            return chain.proceed(originalRequest)
        }

        return try {
            // Fetch the Firebase ID token (force refresh = false, Firebase handles caching)
            // Using runBlocking here is acceptable in an interceptor context
            val token = runBlocking {
                currentUser.getIdToken(false).await().token
            }

            if (token == null) {
                Log.w(TAG, "Failed to get ID token, proceeding without auth")
                chain.proceed(originalRequest)
            } else {
                // Add Authorization header with Bearer token
                val authenticatedRequest = originalRequest.newBuilder()
                    .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
                    .build()
                
                Log.d(TAG, "Added auth token to request: ${originalRequest.url}")
                chain.proceed(authenticatedRequest)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Firebase token: ${e.message}", e)
            // Proceed without auth token on error
            chain.proceed(originalRequest)
        }
    }
}
