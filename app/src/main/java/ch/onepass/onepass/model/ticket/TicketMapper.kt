package ch.onepass.onepass.model.ticket

import ch.onepass.onepass.graphql.GetListedTicketsQuery
import ch.onepass.onepass.graphql.GetMyTicketsQuery
import ch.onepass.onepass.graphql.GetTicketQuery
import com.google.firebase.Timestamp
import kotlinx.datetime.Instant
import java.util.Date

/**
 * Mapper utility to convert GraphQL-generated ticket types to domain models.
 *
 * This mapper handles the conversion between:
 * - GraphQL Ticket types -> Domain Ticket model
 * - GraphQL DateTime (kotlinx.datetime.Instant) -> Firebase Timestamp
 * - GraphQL TicketState enum -> Domain TicketState enum
 */
object TicketMapper {
    /**
     * Converts a kotlinx.datetime.Instant to a Firebase Timestamp.
     */
    private fun Instant.toFirebaseTimestamp(): Timestamp {
        return Timestamp(Date(this.toEpochMilliseconds()))
    }

    /**
     * Maps a GraphQL TicketState name to the domain TicketState enum.
     */
    private fun mapTicketState(stateName: String): TicketState {
        return when (stateName) {
            "ISSUED" -> TicketState.ISSUED
            "LISTED" -> TicketState.LISTED
            "TRANSFERRED" -> TicketState.TRANSFERRED
            "REDEEMED" -> TicketState.REDEEMED
            "REVOKED" -> TicketState.REVOKED
            else -> TicketState.ISSUED
        }
    }

    /**
     * Converts a Ticket from GetTicketQuery to domain Ticket model.
     */
    fun GetTicketQuery.Ticket.toTicket(): Ticket {
        return Ticket(
            ticketId = this.id,
            eventId = this.eventId,
            ownerId = this.ownerId,
            tierId = this.tierId,
            state = mapTicketState(this.state.name),
            purchasePrice = this.purchasePrice,
            listingPrice = this.listingPrice,
            currency = this.currency,
            transferLock = this.transferLock,
            version = this.version,
            issuedAt = this.issuedAt.toFirebaseTimestamp(),
            expiresAt = this.expiresAt?.toFirebaseTimestamp(),
            listedAt = this.listedAt?.toFirebaseTimestamp(),
            deletedAt = this.deletedAt?.toFirebaseTimestamp()
        )
    }

    /**
     * Converts a MyTicket from GetMyTicketsQuery to domain Ticket model.
     */
    fun GetMyTicketsQuery.MyTicket.toTicket(): Ticket {
        return Ticket(
            ticketId = this.id,
            eventId = this.eventId,
            ownerId = this.ownerId,
            tierId = this.tierId,
            state = mapTicketState(this.state.name),
            purchasePrice = this.purchasePrice,
            listingPrice = this.listingPrice,
            currency = this.currency,
            transferLock = this.transferLock,
            version = this.version,
            issuedAt = this.issuedAt.toFirebaseTimestamp(),
            expiresAt = this.expiresAt?.toFirebaseTimestamp(),
            listedAt = this.listedAt?.toFirebaseTimestamp(),
            deletedAt = this.deletedAt?.toFirebaseTimestamp()
        )
    }

    /**
     * Converts a ListedTicket from GetListedTicketsQuery to domain Ticket model.
     */
    fun GetListedTicketsQuery.ListedTicket.toTicket(): Ticket {
        return Ticket(
            ticketId = this.id,
            eventId = this.eventId,
            ownerId = this.ownerId,
            tierId = this.tierId,
            state = mapTicketState(this.state.name),
            purchasePrice = this.purchasePrice,
            listingPrice = this.listingPrice,
            currency = this.currency,
            transferLock = this.transferLock,
            version = this.version,
            issuedAt = this.issuedAt.toFirebaseTimestamp(),
            expiresAt = this.expiresAt?.toFirebaseTimestamp(),
            listedAt = this.listedAt?.toFirebaseTimestamp(),
            deletedAt = null
        )
    }
}
