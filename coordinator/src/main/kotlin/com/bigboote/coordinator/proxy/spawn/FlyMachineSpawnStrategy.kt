package com.bigboote.coordinator.proxy.spawn

import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.coordinator.proxy.agent.FlyMachineAgentProxy
import com.bigboote.domain.values.AgentId
import com.bigboote.infra.config.FlyConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger(FlyMachineSpawnStrategy::class.java)

/**
 * [SpawnStrategy] implementation that launches agent instances on Fly.io using
 * the Fly Machines REST API.
 *
 * **Spawn flow:**
 * 1. `POST https://api.machines.dev/v1/apps/{app}/machines` — creates a new machine
 *    from the agent Docker image, passing gateway/agent tokens as env vars.
 * 2. `GET  .../machines/{id}/wait?state=started` — polls until the machine is running
 *    (Fly blocks the request for up to 60 s by default).
 * 3. Returns a [FlyMachineAgentProxy] with the Fly private-network control URL:
 *    `http://{machineId}.vm.{appName}.internal:8081/control/v1`
 *
 * **Teardown:** `DELETE .../machines/{id}?force=true` — force-stops and destroys the
 * machine immediately.
 *
 * The [httpClient] is the shared Ktor client from Koin (configured with
 * ContentNegotiation JSON).  All requests to the Fly API carry
 * `Authorization: Bearer <apiToken>`.
 *
 * See Architecture doc Section 20.1.
 */
class FlyMachineSpawnStrategy(
    private val flyConfig: FlyConfig,
    private val httpClient: HttpClient,
) : SpawnStrategy {

    /** Maps agentId.value → Fly machine ID for lifecycle management. */
    private val machineIds = ConcurrentHashMap<String, String>()

    private val apiBase = "https://api.machines.dev/v1"

    override suspend fun spawn(config: SpawnConfig): AgentProxy {
        logger.info(
            "FlyMachineSpawnStrategy: spawning agent {} from image '{}' in app '{}' region '{}'",
            config.agentId, config.dockerImage, flyConfig.appName, flyConfig.region,
        )

        // ── Step 1: Create the machine ─────────────────────────────────────
        val createResponse: FlyMachineResponse = httpClient.post(
            "$apiBase/apps/${flyConfig.appName}/machines"
        ) {
            header(HttpHeaders.Authorization, "Bearer ${flyConfig.apiToken}")
            contentType(ContentType.Application.Json)
            setBody(
                FlyCreateMachineRequest(
                    config = FlyMachineConfig(
                        image = config.dockerImage,
                        env = mapOf(
                            "BIGBOOTE_GATEWAY_TOKEN" to config.gatewayToken,
                            "BIGBOOTE_AGENT_TOKEN"   to config.agentToken,
                        ),
                    ),
                    region = flyConfig.region,
                )
            )
        }.body()

        val machineId = createResponse.id
        machineIds[config.agentId.value] = machineId
        logger.debug("FlyMachineSpawnStrategy: machine created: {}", machineId)

        // ── Step 2: Wait for the machine to reach "started" state ──────────
        // The Fly wait endpoint blocks until the machine transitions or times out.
        httpClient.get("$apiBase/apps/${flyConfig.appName}/machines/$machineId/wait") {
            header(HttpHeaders.Authorization, "Bearer ${flyConfig.apiToken}")
            parameter("state",   "started")
            parameter("timeout", "60")
        }
        logger.info(
            "FlyMachineSpawnStrategy: agent {} machine {} started",
            config.agentId, machineId,
        )

        // ── Step 3: Build the proxy with the Fly private-network URL ───────
        val controlUrl = "http://$machineId.vm.${flyConfig.appName}.internal:8081/control/v1"
        return FlyMachineAgentProxy(
            agentId          = config.agentId,
            controlUrl       = controlUrl,
            collaboratorName = config.collaboratorName,
            effortId         = config.effortId,
            agentToken       = config.agentToken,
            httpClient       = httpClient,
        )
    }

    override suspend fun teardown(agentId: AgentId) {
        val machineId = machineIds.remove(agentId.value) ?: run {
            logger.debug(
                "FlyMachineSpawnStrategy: teardown called for unknown agent {}, no-op",
                agentId,
            )
            return
        }
        logger.info(
            "FlyMachineSpawnStrategy: destroying machine {} for agent {}",
            machineId, agentId,
        )
        httpClient.delete("$apiBase/apps/${flyConfig.appName}/machines/$machineId") {
            header(HttpHeaders.Authorization, "Bearer ${flyConfig.apiToken}")
            parameter("force", "true")
        }
    }
}

// ---- Fly Machines API wire DTOs --------------------------------------------------------

@Serializable
private data class FlyCreateMachineRequest(
    val config: FlyMachineConfig,
    val region: String,
)

@Serializable
private data class FlyMachineConfig(
    val image: String,
    val env: Map<String, String> = emptyMap(),
)

/**
 * Minimal response from `POST /apps/{app}/machines`.
 * The full response contains many more fields; we only need [id].
 */
@Serializable
data class FlyMachineResponse(
    val id: String,
    val state: String? = null,
)
