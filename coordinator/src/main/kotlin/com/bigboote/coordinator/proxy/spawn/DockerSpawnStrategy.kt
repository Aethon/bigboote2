package com.bigboote.coordinator.proxy.spawn

import com.bigboote.coordinator.proxy.agent.AgentProxy
import com.bigboote.coordinator.proxy.agent.DockerAgentProxy
import com.bigboote.domain.values.AgentId
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger(DockerSpawnStrategy::class.java)

/**
 * [SpawnStrategy] implementation that launches agent containers via the local Docker daemon.
 *
 * Shells out to the `docker` CLI (via [ProcessBuilder]) rather than pulling in a
 * Docker SDK dependency. This keeps Phase 10 dependency-free while providing a
 * working local development experience. Phase 20 will provide
 * FlyMachineSpawnStrategy for production.
 *
 * **Spawn flow:**
 * 1. `docker run -d --rm -p 0:8081 -e BIGBOOTE_GATEWAY_TOKEN=... -e BIGBOOTE_AGENT_TOKEN=... <image>`
 *    — publishes a random host port mapped to container port 8081
 *    (the agent-service default for `BIGBOOTE_CONTROL_PORT`).
 * 2. `docker port <containerId> 8081` — resolves the assigned host port.
 * 3. Returns a [DockerAgentProxy] with `controlUrl = "http://127.0.0.1:<port>/control/v1"`.
 *
 * **Teardown:** calls `docker stop <containerId>`. The `--rm` flag ensures the
 * container is removed automatically after stop.
 *
 * [httpClient] is shared across all proxy instances (provided via Koin).
 *
 * See Architecture doc Section 10.1.
 */
class DockerSpawnStrategy(
    private val httpClient: HttpClient,
) : SpawnStrategy {

    /** Maps agentId.value → Docker containerId for lifecycle management. */
    private val containerIds = ConcurrentHashMap<String, String>()

    override suspend fun spawn(config: SpawnConfig): AgentProxy = withContext(Dispatchers.IO) {
        logger.info(
            "DockerSpawnStrategy: spawning agent {} from image '{}'",
            config.agentId, config.dockerImage,
        )

        // Start the container in detached mode with a random host port mapped to the
        // agent's control port (8081). --rm ensures automatic cleanup on stop.
        val runProcess = ProcessBuilder(
            "docker", "run", "-d", "--rm",
            "-p", "0:8081",
            "-e", "BIGBOOTE_GATEWAY_TOKEN=${config.gatewayToken}",
            "-e", "BIGBOOTE_AGENT_TOKEN=${config.agentToken}",
            config.dockerImage,
        ).redirectErrorStream(true).start()

        val runOutput = runProcess.inputStream.bufferedReader().readText().trim()
        val runExit = runProcess.waitFor()

        if (runExit != 0) {
            throw RuntimeException(
                "docker run failed (exit $runExit) for agent ${config.agentId}: $runOutput"
            )
        }

        // docker run -d prints the full container ID on the last line
        val containerId = runOutput.lines().last().trim()
        containerIds[config.agentId.value] = containerId
        logger.debug("DockerSpawnStrategy: container started: {}", containerId.take(12))

        // Resolve the randomly-assigned host port for container port 8081.
        // Output format: "0.0.0.0:49153\n:::49153" (or just "0.0.0.0:49153")
        val portProcess = ProcessBuilder("docker", "port", containerId, "8081")
            .redirectErrorStream(true).start()
        val portOutput = portProcess.inputStream.bufferedReader().readText().trim()
        portProcess.waitFor()

        val port = portOutput.lines()
            .firstOrNull { it.isNotBlank() }
            ?.substringAfterLast(":")
            ?.trim()
            ?: throw RuntimeException(
                "Could not determine host port for agent ${config.agentId} container $containerId"
            )

        val controlUrl = "http://127.0.0.1:$port/control/v1"
        logger.info(
            "DockerSpawnStrategy: agent {} reachable at {}",
            config.agentId, controlUrl,
        )

        DockerAgentProxy(
            agentId          = config.agentId,
            controlUrl       = controlUrl,
            collaboratorName = config.collaboratorName,
            effortId         = config.effortId,
            agentToken       = config.agentToken,
            httpClient       = httpClient,
        )
    }

    override suspend fun teardown(agentId: AgentId) {
        val containerId = containerIds.remove(agentId.value) ?: run {
            logger.debug("DockerSpawnStrategy: teardown called for unknown agent {}, no-op", agentId)
            return
        }
        logger.info("DockerSpawnStrategy: stopping container {} for agent {}", containerId.take(12), agentId)
        withContext(Dispatchers.IO) {
            ProcessBuilder("docker", "stop", containerId)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }
}
