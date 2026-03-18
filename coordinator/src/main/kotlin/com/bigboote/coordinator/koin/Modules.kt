package com.bigboote.coordinator.koin

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.aggregates.agenttype.AgentTypeCommandHandler
import com.bigboote.coordinator.aggregates.conversation.ConversationCommandHandler
import com.bigboote.coordinator.aggregates.effort.EffortCommandHandler
import com.bigboote.coordinator.auth.BearerTokenValidator
import com.bigboote.coordinator.auth.GatewayTokenValidator
import com.bigboote.coordinator.auth.StubBearerTokenValidator
import com.bigboote.coordinator.auth.TokenGenerator
import com.bigboote.coordinator.auth.TokenStore
import com.bigboote.coordinator.projections.AgentTypeSummaryProjection
import com.bigboote.coordinator.projections.ConversationProjection
import com.bigboote.coordinator.projections.EffortSummaryProjection
import com.bigboote.coordinator.projections.ProjectionRunner
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.coordinator.projections.repositories.ConversationReadRepository
import com.bigboote.coordinator.projections.repositories.EffortReadRepository
import com.bigboote.coordinator.proxy.ProxyRegistry
import com.bigboote.coordinator.proxy.spawn.DockerSpawnStrategy
import com.bigboote.coordinator.proxy.spawn.SpawnStrategy
import com.bigboote.coordinator.reactors.ReactorRunner
import com.bigboote.coordinator.reactors.SpawnReactor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Coordinator Koin modules. All eight modules are declared here so that
 * Application.kt can load them at startup. Modules that depend on code
 * from later phases contain stub/no-op beans and will be populated when
 * their respective phases are implemented.
 *
 * Infrastructure beans (BigbooteConfig, EventStore, DatabaseFactory, KurrentDB
 * clients) are provided by sharedInfraModule from shared-infra — NOT duplicated
 * here. The coordinator's InfrastructureModule is reserved for coordinator-only
 * infra beans.
 *
 * See Architecture doc Section 13.1 for the full target wiring.
 */

val InfrastructureModule = module {
    // DECISION: Shared infra beans (EventStore, DatabaseFactory, BigbooteConfig,
    // KurrentDB clients) are wired in sharedInfraModule and loaded separately in
    // Application.kt. This module holds coordinator-only infra beans.
    single { AggregateRepository(get()) }
}

val AuthModule = module {
    single { TokenStore() }
    single { TokenGenerator() }
    single<BearerTokenValidator> { StubBearerTokenValidator() }
    single { GatewayTokenValidator(get()) }   // delegates to TokenStore
}

val DomainModule = module {
    // Clock.System passed per Architecture doc Section 13.1 for testability.
    single { EffortCommandHandler(get(), Clock.System) }
    single { AgentTypeCommandHandler(get(), Clock.System) }
    single { ConversationCommandHandler(get(), Clock.System) }  // Phase 11
    // Phase 14: DocumentCommandHandler
    // Phase 15: SystemCollaborator
}

val ProjectionModule = module {
    single { EffortSummaryProjection(get()) }
    single { EffortReadRepository() }
    single { AgentTypeSummaryProjection(get()) }
    single { AgentTypeReadRepository() }
    single { ConversationProjection(get()) }       // Phase 11
    single { ConversationReadRepository() }        // Phase 11
    single { ProjectionRunner(get(), get(), get()) }
    // Phase 14+: DocumentListProjection, DocumentReadRepository
}

val ReactorModule = module {
    // Phase 10: SpawnReactor and ReactorRunner
    single {
        SpawnReactor(
            eventStore               = get(),
            agentTypeReadRepository  = get(),
            spawnStrategy            = get(),
            proxyRegistry            = get(),
            coordinatorGatewayUrl    = System.getenv("BIGBOOTE_COORDINATOR_GATEWAY_URL")
                ?: "http://host.docker.internal:8080/internal/v1",
        )
    }
    single { ReactorRunner(get()) }
    // Phase 13+: MessageDeliveryReactor
    // Phase 15+: SystemMessageReactor, EffortLifecycleReactor
}

val ProxyModule = module {
    // Phase 10: ProxyRegistry, shared HttpClient, DockerSpawnStrategy
    single { ProxyRegistry() }
    single {
        // Shared Ktor HTTP client for all DockerAgentProxy instances.
        // Configured with JSON content negotiation to match the agent Control API.
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
    }
    single<SpawnStrategy> { DockerSpawnStrategy(get()) }
}

val MessagingModule = module {
    // Phase 13+: SseEventBroadcaster, NativeMessagingAdapter
}

val ApiModule = module {
    // Phase 12+: AgentGatewayHandler
}
