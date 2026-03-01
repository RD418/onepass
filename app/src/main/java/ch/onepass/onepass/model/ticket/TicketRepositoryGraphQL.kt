package ch.onepass.onepass.model.ticket

import android.util.Log
import ch.onepass.onepass.graphql.GetListedTicketsQuery
import ch.onepass.onepass.graphql.GetMyTicketsQuery
import ch.onepass.onepass.graphql.GetTicketQuery
import ch.onepass.onepass.graphql.ListTicketMutation
import ch.onepass.onepass.graphql.UnlistTicketMutation
import ch.onepass.onepass.graphql.type.ListTicketInput
import ch.onepass.onepass.model.ticket.TicketMapper.toTicket
import ch.onepass.onepass.network.ApolloClientProvider
import com.apollographql.apollo3.exception.ApolloException
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * GraphQL-backed implementation of [TicketRepository].
 *
 * This repository uses Apollo GraphQL client to fetch and mutate ticket data from the backend API,
 * replacing Firebase Firestore calls with type-safe GraphQL queries and mutations.
 *
 * Key features:
 * - Type-safe GraphQL queries and mutations with compile-time validation
 * - Automatic authentication via Firebase Auth tokens
 * - Conversion from GraphQL types to domain models
 * - Flow-based reactive data streams
 *
 * Schema notes:
 * - This implementation emits data once per flow (not real-time like Firebase snapshot listeners).
 * - Active/expired ticket filtering is done client-side, matching Firebase behavior.
 * - `createTicket`, `updateTicket`, `deleteTicket` and `purchaseListedTicket` have no direct
 *   GraphQL equivalents — ticket creation goes through the payment flow (PaymentRepository)
 *   and these operations remain unsupported.
 */
class TicketRepositoryGraphQL : TicketRepository {
    private val apolloClient = ApolloClientProvider.getClient()

    companion object {
        private const val TAG = "TicketRepositoryGraphQL"
    }

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Get all tickets for the current user.
     *
     * Note: Uses `myTickets` query — auth token determines the user.
     * The [userId] parameter is not used in the GraphQL implementation.
     */
    override fun getTicketsByUser(userId: String): Flow<List<Ticket>> = flow {
        try {
            val response = apolloClient.query(
                GetMyTicketsQuery(filters = null)
            ).execute()

            if (response.hasErrors()) {
                Log.e(TAG, "GraphQL errors: ${response.errors}")
                throw Exception("Failed to fetch tickets: ${response.errors?.firstOrNull()?.message}")
            }

            val tickets = response.data?.myTickets?.map { it.toTicket() }
                ?.sortedByDescending { it.issuedAt?.seconds ?: 0 }
                ?: emptyList()
            emit(tickets)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching tickets by user", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Get only active (not expired/redeemed/revoked/listed) tickets that can be used.
     * Filters client-side to match Firebase behavior.
     */
    override fun getActiveTickets(userId: String): Flow<List<Ticket>> = flow {
        try {
            val response = apolloClient.query(
                GetMyTicketsQuery(filters = null)
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch active tickets: ${response.errors?.firstOrNull()?.message}")
            }

            val now = Timestamp.now()
            val tickets = response.data?.myTickets?.map { it.toTicket() }
                ?.filter { ticket ->
                    ticket.state in listOf(TicketState.ISSUED, TicketState.TRANSFERRED) &&
                        (ticket.expiresAt == null || now.seconds <= ticket.expiresAt.seconds)
                }
                ?.sortedByDescending { it.issuedAt?.seconds ?: 0 }
                ?: emptyList()
            emit(tickets)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching active tickets", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Get expired or redeemed tickets.
     * Filters client-side to match Firebase behavior.
     */
    override fun getExpiredTickets(userId: String): Flow<List<Ticket>> = flow {
        try {
            val response = apolloClient.query(
                GetMyTicketsQuery(filters = null)
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch expired tickets: ${response.errors?.firstOrNull()?.message}")
            }

            val now = Timestamp.now()
            val tickets = response.data?.myTickets?.map { it.toTicket() }
                ?.filter { ticket ->
                    ticket.state in listOf(TicketState.REDEEMED, TicketState.REVOKED) ||
                        (ticket.expiresAt != null && now.seconds > ticket.expiresAt.seconds)
                }
                ?.sortedByDescending { it.issuedAt?.seconds ?: 0 }
                ?: emptyList()
            emit(tickets)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching expired tickets", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Get tickets that the user has listed for sale.
     */
    override fun getListedTicketsByUser(userId: String): Flow<List<Ticket>> = flow {
        try {
            val response = apolloClient.query(
                GetMyTicketsQuery(filters = null)
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch listed tickets: ${response.errors?.firstOrNull()?.message}")
            }

            val tickets = response.data?.myTickets?.map { it.toTicket() }
                ?.filter { ticket -> ticket.state == TicketState.LISTED }
                ?.sortedByDescending { it.listedAt?.seconds ?: 0 }
                ?: emptyList()
            emit(tickets)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching listed tickets by user", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Get a single ticket by ID.
     */
    override fun getTicketById(ticketId: String): Flow<Ticket?> = flow {
        try {
            val response = apolloClient.query(GetTicketQuery(ticketId)).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch ticket: ${response.errors?.firstOrNull()?.message}")
            }

            val ticket = response.data?.ticket?.toTicket()
            emit(ticket)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching ticket by ID: $ticketId", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Get all tickets listed for sale on the marketplace.
     */
    override fun getListedTickets(): Flow<List<Ticket>> = flow {
        try {
            val response = apolloClient.query(
                GetListedTicketsQuery(eventId = null)
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch listed tickets: ${response.errors?.firstOrNull()?.message}")
            }

            val tickets = response.data?.listedTickets?.map { it.toTicket() }
                ?.filter { it.listingPrice != null }
                ?.sortedByDescending { it.listedAt?.seconds ?: 0 }
                ?: emptyList()
            emit(tickets)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching listed tickets", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    /**
     * Get listed tickets for a specific event.
     */
    override fun getListedTicketsByEvent(eventId: String): Flow<List<Ticket>> = flow {
        try {
            val response = apolloClient.query(
                GetListedTicketsQuery(eventId = eventId)
            ).execute()

            if (response.hasErrors()) {
                throw Exception("Failed to fetch listed tickets for event: ${response.errors?.firstOrNull()?.message}")
            }

            val tickets = response.data?.listedTickets?.map { it.toTicket() }
                ?.filter { it.listingPrice != null }
                ?.sortedBy { it.listingPrice }
                ?: emptyList()
            emit(tickets)
        } catch (e: ApolloException) {
            Log.e(TAG, "Network error fetching listed tickets by event", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------------------

    /**
     * Lists a ticket for sale on the marketplace at the given asking price.
     *
     * Calls the `listTicket` GraphQL mutation with the ticket ID and price.
     */
    override suspend fun listTicketForSale(ticketId: String, askingPrice: Double): Result<Unit> = try {
        require(askingPrice > 0) { "Asking price must be greater than 0" }

        val input = ListTicketInput(
            ticketId = ticketId,
            listingPrice = askingPrice
        )

        val response = apolloClient.mutation(ListTicketMutation(input)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to list ticket: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error listing ticket: $ticketId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }

    /**
     * Cancels a ticket listing and returns it to the ISSUED state.
     *
     * Calls the `unlistTicket` GraphQL mutation with the ticket ID.
     */
    override suspend fun cancelTicketListing(ticketId: String): Result<Unit> = try {
        val response = apolloClient.mutation(UnlistTicketMutation(ticketId)).execute()

        if (response.hasErrors()) {
            Result.failure(Exception("Failed to cancel ticket listing: ${response.errors?.firstOrNull()?.message}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: ApolloException) {
        Log.e(TAG, "Network error cancelling ticket listing: $ticketId", e)
        Result.failure(Exception("Network error: ${e.message}"))
    }

    /**
     * Direct ticket creation is not supported via GraphQL.
     *
     * Tickets are created through the payment flow (`PaymentRepository.createPaymentIntent`)
     * and the backend processes ticket issuance automatically.
     */
    override suspend fun createTicket(ticket: Ticket): Result<String> {
        return Result.failure(
            UnsupportedOperationException(
                "createTicket via GraphQL is not supported — tickets are issued automatically " +
                    "by the backend after a successful payment via PaymentRepository"
            )
        )
    }

    /**
     * Direct ticket state updates are not supported via GraphQL.
     *
     * Ticket state transitions are managed by the backend as side effects of other operations
     * (e.g., purchase, list, redeem).
     */
    override suspend fun updateTicket(ticket: Ticket): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "updateTicket via GraphQL is not supported — use specific domain operations " +
                    "(listTicketForSale, cancelTicketListing, etc.) instead"
            )
        )
    }

    /**
     * Direct ticket deletion is not supported via GraphQL.
     *
     * Tickets are soft-deleted by the backend as part of other operations (e.g., revocation).
     */
    override suspend fun deleteTicket(ticketId: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "deleteTicket via GraphQL is not supported — ticket lifecycle is managed by the backend"
            )
        )
    }
// TODO: Is this needed if we are actually using the old payment flow ? remove it or adapt to use this instead.  
    /**
     * Purchasing a listed ticket from the marketplace is handled by [PaymentRepository].
     *
     * The purchase flow creates a payment intent which, upon successful payment, transfers
     * the ticket ownership on the backend automatically.
     */
    override suspend fun purchaseListedTicket(ticketId: String, buyerId: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException(
                "purchaseListedTicket via GraphQL is not supported — use " +
                    "PaymentRepository.createMarketplacePaymentIntent to initiate marketplace purchases"
            )
        )
    }
}
