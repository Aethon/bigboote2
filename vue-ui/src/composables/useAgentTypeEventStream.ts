import { ref, onUnmounted } from 'vue'
import { useAuth } from '@/composables/useAuth'

/**
 * A single Server-Sent Event received from the coordinator's
 * /api/v1/agent-types/{agentTypeId}/stream endpoint.
 *
 * This mirrors EffortSseEvent from useEffortEventStream — both surfaces
 * use the same SSE wire format (SseRoutes.kt).
 */
export interface AgentTypeSseEvent {
  /** Raw data string from the SSE message. */
  data: string
  /** Last-Event-ID value, used to resume after reconnect. */
  lastEventId: string
}

/**
 * SSE composable — connects to GET /api/v1/agent-types/{agentTypeId}/stream.
 *
 * Receives the full event history for a single agent type and streams live
 * updates. Used by AgentTypeHistoryView to display the raw event log.
 *
 * The browser's EventSource API handles reconnection automatically.
 * The `Last-Event-ID` header is set on reconnect via the most recently
 * received event ID.
 *
 * Usage:
 * ```ts
 * const { events, connected, error } = useAgentTypeEventStream(agentTypeId)
 * ```
 */
export function useAgentTypeEventStream(agentTypeId: string) {
  const events = ref<AgentTypeSseEvent[]>([])
  const connected = ref(false)
  const error = ref<string | null>(null)
  let source: EventSource | null = null

  async function connect() {
    const { getAccessToken } = useAuth()

    // EventSource does not support custom headers in the browser; pass the
    // token as a query parameter (same strategy as useEffortEventStream).
    const token = await getAccessToken()
    const url = `/api/v1/agent-types/${encodeURIComponent(agentTypeId)}/stream?token=${encodeURIComponent(token)}`

    source = new EventSource(url)

    source.onopen = () => {
      connected.value = true
      error.value = null
    }

    source.onmessage = (ev: MessageEvent) => {
      events.value.push({
        data: ev.data as string,
        lastEventId: ev.lastEventId,
      })
    }

    source.onerror = () => {
      connected.value = false
      error.value = 'SSE connection error — browser will retry automatically'
    }
  }

  function disconnect() {
    source?.close()
    source = null
    connected.value = false
  }

  // Auto-connect on composable creation; clean up on unmount.
  connect()
  onUnmounted(disconnect)

  return { events, connected, error, disconnect }
}
