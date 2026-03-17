package com.bigboote.agent.koin

import com.bigboote.agent.control.v1.AgentControlHandler
import com.bigboote.agent.gateway.AgentGatewayClient
import com.bigboote.agent.gateway.GatewayConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.koin.dsl.module

val AgentServiceModule = module {
    single { GatewayConfig.fromEnvironment() }
    single { AgentGatewayClient(get(), HttpClient(CIO)) }
    single { AgentControlHandler() }

    // DECISION: Loop components (AgentLoopStepper, KurrentEventStream, ConversationSseClient)
    // stubbed out for Phase 8. Will be wired in Phase 9 (loop integration).
}
