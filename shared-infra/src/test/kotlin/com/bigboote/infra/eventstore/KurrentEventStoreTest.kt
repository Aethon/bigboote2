package com.bigboote.infra.eventstore

import com.bigboote.domain.aggregates.EventContext
import com.bigboote.domain.events.EffortEvent
import com.bigboote.domain.events.EffortEvent.EffortCreated
import com.bigboote.domain.events.EffortEvent.EffortStarted
import com.bigboote.domain.values.*
import com.bigboote.events.eventstore.ExpectedVersion
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Integration test for KurrentEventStore using TestContainers.
 * Verifies append and read-back of events against a real KurrentDB instance.
 *
 * Uses the typed [StreamName] hierarchy — stream names are now constructed via
 * [StreamName.Effort] rather than the deprecated [com.bigboote.events.streams.StreamNames].
 * Event payloads no longer carry [EffortId] (removed in stream-names change).
 */
class KurrentEventStoreTest : StringSpec({

    val container = GenericContainer(DockerImageName.parse("eventstore/eventstore:24.10"))
        .withExposedPorts(2113)
        .withEnv("EVENTSTORE_INSECURE", "true")
        .withEnv("EVENTSTORE_MEM_DB", "true")
        .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
        .withEnv("EVENTSTORE_RUN_PROJECTIONS", "None")

    lateinit var store: KurrentEventStore
    lateinit var client: EventStoreDBClient
    lateinit var persistentClient: EventStoreDBPersistentSubscriptionsClient

    beforeSpec {
        container.start()
        val connectionString = "esdb://${container.host}:${container.getMappedPort(2113)}?tls=false"
        val settings = EventStoreDBConnectionString.parseOrThrow(connectionString)
        client = EventStoreDBClient.create(settings)
        persistentClient = EventStoreDBPersistentSubscriptionsClient.create(settings)
        store = KurrentEventStore(client, persistentClient)
    }

    afterSpec {
        client.shutdown()
        persistentClient.shutdown()
        container.stop()
    }

    "append EffortCreated and read it back" {
        val now = Clock.System.now()
        val effortId = EffortId.generate()
        val event = EffortCreated(
            name = "Integration test effort",
            goal = "Verify KurrentEventStore append and read",
            collaborators = listOf(
                CollaboratorSpec(
                    name = CollaboratorName.Individual("alice"),
                    type = CollaboratorType.EXTERNAL,
                ),
            ),
            leadName = CollaboratorName.Individual("alice"),
            createdAt = now,
        )

        val streamName = StreamName.Effort(effortId)

        // Append
        val appendResult = store.appendToStream(
            streamName = streamName,
            events = listOf(event),
            expectedVersion = ExpectedVersion.NoStream,
        )
        appendResult.nextExpectedVersion shouldBeGreaterThanOrEqual 0L

        // Read back
        val readResult = store.readStreamForward(EffortEvent::class, streamName)
        readResult.events shouldHaveSize 1

        val entry = readResult.events.first()
        entry.streamName shouldBe streamName
        entry.context shouldBe EventContext(0L, 0L)

        val deserialized = entry.event
        deserialized.shouldBeInstanceOf<EffortCreated>()
        deserialized.name shouldBe "Integration test effort"
        deserialized.goal shouldBe "Verify KurrentEventStore append and read"
        deserialized.collaborators shouldHaveSize 1
        deserialized.leadName shouldBe CollaboratorName.Individual("alice")
    }

    "append multiple events and read with fromVersion" {
        val now = Clock.System.now()
        val effortId = EffortId.generate()
        val streamName = StreamName.Effort(effortId)

        val created = EffortCreated(
            name = "Multi-event test",
            goal = "Test multiple appends",
            collaborators = emptyList(),
            leadName = CollaboratorName.Individual("dev"),
            createdAt = now,
        )
        val started = EffortStarted(
            occurredAt = now,
        )

        store.appendToStream(streamName, listOf(created), ExpectedVersion.NoStream)
        store.appendToStream(streamName, listOf(started), ExpectedVersion.Exact(0))

        // Read all
        val allEvents = store.readStreamForward(EffortEvent::class, streamName)
        allEvents.events shouldHaveSize 2

        // Read from version 1
        val fromOne = store.readStreamForward(EffortEvent::class, streamName, fromVersion = 1)
        fromOne.events shouldHaveSize 1
        fromOne.events.first().event.shouldBeInstanceOf<EffortStarted>()
    }

    "readStreamForward on non-existent stream returns empty" {
        val nonExistentStream = StreamName.Effort(EffortId("effort:nonexistent-stream-12345"))
        val result = store.readStreamForward(EffortEvent::class, nonExistentStream)
        result.events shouldHaveSize 0
        result.lastStreamPosition shouldBe -1L
    }
})
