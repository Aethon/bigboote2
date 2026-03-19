import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAgentStatus, pauseAgent, resumeAgent, stopAgent, type AgentStatus } from '@/api/agents'

/**
 * Pinia store for agent instance state within an Effort.
 *
 * Agent statuses are initially loaded by polling the REST API, then kept
 * up-to-date via SSE events parsed from `useEffortEventStream`. SSE event
 * parsing will be implemented in Phase 18; for now the store is seeded
 * by `fetchAgentStatus` calls.
 *
 * `applyEvent` is the hook that Phase 18 will call to push live state
 * changes from the SSE stream into the store without a round-trip.
 */
export const useAgentStore = defineStore('agents', () => {
  // ------------------------------------------------------------------ state

  /** Agent statuses keyed by instanceId. */
  const agentsByInstanceId = ref<Record<string, AgentStatus>>({})

  /** Raw SSE event log (last 200 events). Displayed in EventStreamPanel. */
  const eventLog = ref<SseLogEntry[]>([])

  const loading = ref(false)
  const error = ref<string | null>(null)

  // ------------------------------------------------------------------ actions

  /** Fetch the current status for a single agent instance. */
  async function fetchAgentStatus(effortId: string, agentId: string): Promise<void> {
    try {
      const status = await getAgentStatus(effortId, agentId)
      agentsByInstanceId.value[agentId] = status
    } catch (e) {
      console.error(`Failed to fetch agent status for ${agentId}:`, e)
    }
  }

  /**
   * Ingest a raw SSE event string from `useEffortEventStream`.
   * Appends to eventLog and, if the payload is a known AgentEvent shape,
   * updates `agentsByInstanceId` in-place.
   *
   * Full event-type dispatch will be wired in Phase 18; this stub records
   * the raw data so EventStreamPanel can display the stream immediately.
   */
  function applyEvent(data: string, eventId: string): void {
    // Keep the log bounded.
    if (eventLog.value.length >= 200) {
      eventLog.value.shift()
    }
    const entry: SseLogEntry = { eventId, data, receivedAt: new Date().toISOString() }
    eventLog.value.push(entry)

    // Opportunistic parse — try to read agentStatus updates.
    try {
      const parsed = JSON.parse(data) as Partial<AgentEventPayload>
      if (parsed.type === 'agent.status' && parsed.instanceId && parsed.loopState) {
        const existing = agentsByInstanceId.value[parsed.instanceId]
        if (existing) {
          agentsByInstanceId.value[parsed.instanceId] = {
            ...existing,
            loopState: parsed.loopState,
            currentTurn: parsed.currentTurn ?? existing.currentTurn,
            lastActivityAt: parsed.lastActivityAt ?? existing.lastActivityAt,
            streamPosition: parsed.streamPosition ?? existing.streamPosition,
          }
        }
      }
    } catch {
      // Not JSON or unknown shape — already logged above, nothing more to do.
    }
  }

  async function pause(effortId: string, agentId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await pauseAgent(effortId, agentId)
      await fetchAgentStatus(effortId, agentId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : `Failed to pause agent ${agentId}`
    } finally {
      loading.value = false
    }
  }

  async function resume(effortId: string, agentId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await resumeAgent(effortId, agentId)
      await fetchAgentStatus(effortId, agentId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : `Failed to resume agent ${agentId}`
    } finally {
      loading.value = false
    }
  }

  async function stop(effortId: string, agentId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await stopAgent(effortId, agentId)
      await fetchAgentStatus(effortId, agentId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : `Failed to stop agent ${agentId}`
    } finally {
      loading.value = false
    }
  }

  return {
    agentsByInstanceId,
    eventLog,
    loading,
    error,
    fetchAgentStatus,
    applyEvent,
    pause,
    resume,
    stop,
  }
})

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

export interface SseLogEntry {
  eventId: string
  data: string
  receivedAt: string
}

interface AgentEventPayload {
  type: string
  instanceId: string
  loopState: AgentStatus['loopState']
  currentTurn: number
  lastActivityAt: string | null
  streamPosition: number
}
