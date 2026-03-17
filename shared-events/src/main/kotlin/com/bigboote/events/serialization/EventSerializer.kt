package com.bigboote.events.serialization

import com.eventstore.dbclient.EventData
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Serializes domain event instances into KurrentDB EventData.
 *
 * Uses EventRegistry to look up the event type name and KSerializer,
 * then encodes the event as JSON. The event type name is stored as
 * KurrentDB's native eventType field — it is NOT duplicated inside
 * the data payload.
 */
object EventSerializer {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Serialize a domain event to an EventData suitable for appending to KurrentDB.
     *
     * @param event The domain event instance (must be registered in EventRegistry)
     * @return EventData with eventType set to the registry name and JSON payload
     */
    fun serialize(event: Any): EventData {
        val eventType = EventRegistry.eventTypeOf(event)

        val serializer = EventRegistry.serializerFor(eventType)
            ?: throw IllegalArgumentException("No serializer found for event type: $eventType")

        // Safe: serializer and event are looked up from the same EventRegistry entry,
        // so the KSerializer<out Foo> always matches the runtime type of event.
        @Suppress("UNCHECKED_CAST")
        val jsonBytes = json.encodeToString(serializer as kotlinx.serialization.KSerializer<Any>, event)
            .toByteArray(Charsets.UTF_8)

        return EventData.builderAsJson(eventType, jsonBytes)
            .eventId(UUID.randomUUID())
            .build()
    }
}
