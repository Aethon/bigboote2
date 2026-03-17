package com.bigboote.events.serialization

import com.eventstore.dbclient.RecordedEvent
import kotlinx.serialization.json.Json

/**
 * Deserializes KurrentDB RecordedEvent instances back into domain event objects.
 *
 * Uses the eventType field from the RecordedEvent to look up the correct
 * KSerializer in EventRegistry, then decodes the JSON payload.
 */
object EventDeserializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Deserialize a KurrentDB RecordedEvent into a domain event instance.
     *
     * @param recordedEvent The event read from KurrentDB
     * @return The deserialized domain event object
     * @throws IllegalArgumentException if the event type is not registered
     */
    fun deserialize(recordedEvent: RecordedEvent): Any {
        val eventType = recordedEvent.eventType
        val serializer = EventRegistry.serializerFor(eventType)
            ?: throw IllegalArgumentException("Unknown event type: $eventType")

        val jsonString = String(recordedEvent.eventData, Charsets.UTF_8)
        return json.decodeFromString(serializer, jsonString)
    }
}
