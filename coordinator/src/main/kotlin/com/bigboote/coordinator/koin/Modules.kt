package com.bigboote.coordinator.koin

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
 * infra beans (e.g. AggregateRepository in Phase 5).
 *
 * See Architecture doc Section 13.1 for the full target wiring.
 */

val InfrastructureModule = module {
    // DECISION: Shared infra beans (EventStore, DatabaseFactory, BigbooteConfig,
    // KurrentDB clients) are wired in sharedInfraModule and loaded separately in
    // Application.kt. This module holds coordinator-only infra beans.
    // Phase 5: AggregateRepository
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
