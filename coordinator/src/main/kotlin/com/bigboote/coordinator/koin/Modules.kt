package com.bigboote.coordinator.koin

import org.koin.dsl.module

// DECISION: Stub Koin modules for bootstrap phase. Each will be populated in its respective implementation phase.

val InfrastructureModule = module {
    // Phase 3: KurrentDB client, Postgres/HikariCP DataSource, Exposed database
}

val AuthModule = module {
    // Phase 5: Auth provider (stub/auth0), JWT verifier
}

val DomainModule = module {
    // Phase 4: Aggregates, command handlers
}

val ProjectionModule = module {
    // Phase 6: Read-model projectors
}

val ReactorModule = module {
    // Phase 7: Event reactors (Spawn, Notify, etc.)
}

val ProxyModule = module {
    // Phase 8: Agent Gateway proxy registry
}

val MessagingModule = module {
    // Phase 10: Message routing, conversation management
}

val ApiModule = module {
    // Phase 5: Route handlers, DTOs
}
