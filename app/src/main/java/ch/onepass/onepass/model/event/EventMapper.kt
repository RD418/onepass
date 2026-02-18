package ch.onepass.onepass.model.event

import ch.onepass.onepass.graphql.GetEventQuery
import ch.onepass.onepass.graphql.GetEventsQuery
import ch.onepass.onepass.graphql.GetFeaturedEventsQuery
import ch.onepass.onepass.model.map.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import kotlinx.datetime.Instant
import java.util.Date

/**
 * Mapper utility to convert GraphQL-generated types to domain models.
 * 
 * This mapper handles the conversion between:
 * - GraphQL DateTime (kotlinx.datetime.Instant) -> Firebase Timestamp
 * - GraphQL Location -> Domain Location (with GeoPoint)
 * - GraphQL Event types -> Domain Event model
 */
object EventMapper {
    /**
     * Converts a kotlinx.datetime.Instant to a Firebase Timestamp.
     */
    private fun Instant.toFirebaseTimestamp(): Timestamp {
        return Timestamp(Date(this.toEpochMilliseconds()))
    }

    /**
     * Converts a GraphQL Location type to domain Location model.
     */
    private fun GetFeaturedEventsQuery.Location.toLocation(): Location {
        return Location(
            name = this.name,
            geopoint = GeoPoint(this.latitude, this.longitude)
        )
    }

    /**
     * Converts a GraphQL Location type from GetEventsQuery to domain Location model.
     */
    private fun GetEventsQuery.Location.toLocation(): Location {
        return Location(
            name = this.name,
            geopoint = GeoPoint(this.latitude, this.longitude)
        )
    }

    /**
     * Converts a GraphQL Location type from GetEventQuery to domain Location model.
     */
    private fun GetEventQuery.Location.toLocation(): Location {
        return Location(
            name = this.name,
            geopoint = GeoPoint(this.latitude, this.longitude)
        )
    }

    /**
     * Converts a GraphQL PricingTier to domain PricingTier model.
     */
    private fun GetFeaturedEventsQuery.PricingTier.toPricingTier(): PricingTier {
        return PricingTier(
            name = this.name,
            price = this.price,
            quantity = this.quantity,
            remaining = this.remaining
        )
    }

    /**
     * Converts a GraphQL PricingTier from GetEventsQuery to domain PricingTier model.
     */
    private fun GetEventsQuery.PricingTier.toPricingTier(): PricingTier {
        return PricingTier(
            name = this.name,
            price = this.price,
            quantity = this.quantity,
            remaining = this.remaining
        )
    }

    /**
     * Converts a GraphQL PricingTier from GetEventQuery to domain PricingTier model.
     */
    private fun GetEventQuery.PricingTier.toPricingTier(): PricingTier {
        return PricingTier(
            name = this.name,
            price = this.price,
            quantity = this.quantity,
            remaining = this.remaining
        )
    }

    /**
     * Converts a FeaturedEvent from GraphQL to domain Event model.
     */
    fun GetFeaturedEventsQuery.FeaturedEvent.toEvent(): Event {
        return Event(
            eventId = this.id,
            title = this.title,
            description = this.description,
            organizerName = this.organizerName,
            status = when (this.status) {
                // Note: GraphQL might return null for status since it's not in the query
                // This is a simplified mapping - adjust based on your actual schema
                else -> EventStatus.PUBLISHED
            },
            location = this.location?.toLocation(),
            startTime = this.startTime.toFirebaseTimestamp(),
            endTime = this.endTime.toFirebaseTimestamp(),
            capacity = this.capacity,
            ticketsRemaining = this.ticketsRemaining,
            currency = this.currency,
            pricingTiers = emptyList(), // Featured events query doesn't fetch pricing tiers
            images = this.images,
            tags = this.tags,
            createdAt = null,
            updatedAt = null,
            deletedAt = null,
            ticketsIssued = 0,
            ticketsRedeemed = 0,
            organizerId = ""
        )
    }

    /**
     * Converts an Event node from GetEventsQuery to domain Event model.
     */
    fun GetEventsQuery.Node.toEvent(): Event {
        return Event(
            eventId = this.id,
            title = this.title,
            description = this.description,
            organizerName = this.organizerName,
            organizerId = "", // Not fetched in this query
            status = when (this.status.name) {
                "DRAFT" -> EventStatus.DRAFT
                "PUBLISHED" -> EventStatus.PUBLISHED
                "CLOSED" -> EventStatus.CLOSED
                "CANCELLED" -> EventStatus.CANCELLED
                else -> EventStatus.PUBLISHED
            },
            location = this.location?.toLocation(),
            startTime = this.startTime.toFirebaseTimestamp(),
            endTime = this.endTime.toFirebaseTimestamp(),
            capacity = this.capacity,
            ticketsRemaining = this.ticketsRemaining,
            currency = this.currency,
            pricingTiers = emptyList(), // Not fetched in list query for performance
            images = this.images,
            tags = this.tags,
            createdAt = null,
            updatedAt = null,
            deletedAt = null,
            ticketsIssued = 0,
            ticketsRedeemed = 0
        )
    }

    /**
     * Converts a full Event from GetEventQuery to domain Event model.
     */
    fun GetEventQuery.Event.toEvent(): Event {
        return Event(
            eventId = this.id,
            title = this.title,
            description = this.description,
            organizerId = this.organizerId,
            organizerName = this.organizerName,
            status = when (this.status.name) {
                "DRAFT" -> EventStatus.DRAFT
                "PUBLISHED" -> EventStatus.PUBLISHED
                "CLOSED" -> EventStatus.CLOSED
                "CANCELLED" -> EventStatus.CANCELLED
                else -> EventStatus.PUBLISHED
            },
            location = this.location?.toLocation(),
            startTime = this.startTime.toFirebaseTimestamp(),
            endTime = this.endTime.toFirebaseTimestamp(),
            capacity = this.capacity,
            ticketsRemaining = this.ticketsRemaining,
            ticketsIssued = this.ticketsIssued,
            ticketsRedeemed = this.ticketsRedeemed,
            currency = this.currency,
            pricingTiers = this.pricingTiers.map { it.toPricingTier() },
            images = this.images,
            tags = this.tags,
            createdAt = this.createdAt.toFirebaseTimestamp(),
            updatedAt = this.updatedAt.toFirebaseTimestamp(),
            deletedAt = this.deletedAt?.toFirebaseTimestamp()
        )
    }
}
