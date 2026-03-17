package com.bigboote.coordinator.koin

import com.bigboote.events.eventstore.EventStore
import com.bigboote.infra.config.BigbooteConfig
import com.bigboote.infra.db.DatabaseFactory
import com.bigboote.infra.eventstore.KurrentEventStore
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import org.koin.dsl.module

/**
 * Coordinator Koin modules. All eight modules are declared here so that
 * Application.kt can load them at startup. Modules that depend on code
 * from later phases contain stub/no-op beans and will be populated when
 * their respective phases are implemented.
 *
 * See Architecture doc Section 13.1 for the full target wiring.
 */

val InfrastructureModule = module {
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

    // DECISION: AggregateRepository will be added in Phase 5 when EffortCommandHandler is built.
}

val AuthModule = module {
    // Phase 7: TokenStore, TokenGenerator, BearerTokenValidator, GatewayTokenValidator, AuthPlugin
}

val DomainModule = module {
    // Phase 5+: EffortCommandHandler, AgentTypeCommandHandler, ConversationCommandHandler,
    //           DocumentCommandHandler, SystemCollaborator
}

val ProjectionModule = module {
    // Phase 5+: EffortSummaryProjection, AgentInstanceStatusProjection, ConversationProjection,
    //           DocumentListProjection, read repositories, ProjectionRunner
}

val ReactorModule = module {
    // Phase 10+: SpawnReactor, EffortLifecycleReactor, MessageDeliveryReactor,
    //            SystemMessageReactor, ReactorRunner
}

val ProxyModule = module {
    // Phase 10+: ProxyRegistry, DockerSpawnStrategy, SpawnStrategyFactory
}

val MessagingModule = module {
    // Phase 13+: SseEventBroadcaster, NativeMessagingAdapter
}

val ApiModule = module {
    // Phase 12+: AgentGatewayHandler
}
