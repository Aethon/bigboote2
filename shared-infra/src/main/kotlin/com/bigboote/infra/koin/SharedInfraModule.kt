package com.bigboote.infra.koin

import com.bigboote.events.eventstore.EventStore
import com.bigboote.infra.config.BigbooteConfig
import com.bigboote.infra.db.DatabaseFactory
import com.bigboote.infra.eventstore.KurrentEventStore
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import org.koin.dsl.module

/**
 * Koin module providing shared infrastructure beans used by both
 * coordinator and agent-service.
 */
val sharedInfraModule = module {

    single<BigbooteConfig> { BigbooteConfig.fromEnvironment() }

    single<EventStoreDBClient> {
        val config = get<BigbooteConfig>()
        val settings = EventStoreDBConnectionString.parseOrThrow(config.kurrent.connectionString)
        EventStoreDBClient.create(settings)
    }

    single<EventStoreDBPersistentSubscriptionsClient> {
        val config = get<BigbooteConfig>()
        val settings = EventStoreDBConnectionString.parseOrThrow(config.kurrent.connectionString)
        EventStoreDBPersistentSubscriptionsClient.create(settings)
    }

    single<EventStore> {
        KurrentEventStore(
            client = get(),
            persistentSubscriptionsClient = get(),
        )
    }

    single<DatabaseFactory> {
        val config = get<BigbooteConfig>()
        DatabaseFactory(config.database)
    }
}
