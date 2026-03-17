package com.bigboote.coordinator.koin

import com.bigboote.coordinator.aggregates.AggregateRepository
import com.bigboote.coordinator.aggregates.agenttype.AgentTypeCommandHandler
import com.bigboote.coordinator.aggregates.effort.EffortCommandHandler
import com.bigboote.coordinator.auth.BearerTokenValidator
import com.bigboote.coordinator.auth.GatewayTokenValidator
import com.bigboote.coordinator.auth.StubBearerTokenValidator
import com.bigboote.coordinator.auth.TokenGenerator
import com.bigboote.coordinator.auth.TokenStore
import com.bigboote.coordinator.projections.AgentTypeSummaryProjection
import com.bigboote.coordinator.projections.EffortSummaryProjection
import com.bigboote.coordinator.projections.ProjectionRunner
import com.bigboote.coordinator.projections.repositories.AgentTypeReadRepository
import com.bigboote.coordinator.projections.repositories.EffortReadRepository
import kotlinx.datetime.Clock
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
    // Phase 11: ConversationCommandHandler
    // Phase 14: DocumentCommandHandler
    // Phase 15: SystemCollaborator
}

val ProjectionModule = module {
    single { EffortSummaryProjection(get()) }
    single { EffortReadRepository() }
    single { AgentTypeSummaryProjection(get()) }
    single { AgentTypeReadRepository() }
    single { ProjectionRunner(get(), get()) }
    // Phase 11+: ConversationProjection, ConversationReadRepository
    // Phase 14+: DocumentListProjection, DocumentReadRepository
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
