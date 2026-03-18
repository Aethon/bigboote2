import { ref, onUnmounted } from 'vue'
import { useAuth } from '@/composables/useAuth'

/**
 * A single Server-Sent Event received from the coordinator's SSE stream.
 * Matches the format emitted by SseRoutes.kt (Phase 13).
 */
export interface EffortSseEvent {
  /** Raw data string from the SSE message. */
  data: string
  /** Last-Event-ID value, used to resume from a checkpoint after reconnect. */
  lastEventId: string
}

/**
 * SSE composable — connects to GET /api/v1/efforts/{effortId}/stream.
 *
 * **Phase 16 status:** skeleton only.
 * The composable sets up the EventSource, pushes events into the `events`
 * ref, and tears down cleanly when the owning component unmounts. Full event
 * parsing and store hydration will be wired in Phase 17.
 *
 * Reconnection is handled automatically by the browser's EventSource API.
 * The `Last-Event-ID` header is set on reconnect using the most recently
 * received event ID so the coordinator can resume from the correct position.
 *
 * Usage:
 * ```ts
 * const { events, connected, error } = useEffortEventStream(effortId)
 * ```
 */
export function useEffortEventStream(effortId: string) {
  const events = ref<EffortSseEvent[]>([])
  const connected = ref(false)
  const error = ref<string | null>(null)
  let source: EventSource | null = null

  async function connect() {
    const { getAccessToken } = useAuth()

    // EventSource does not support custom headers directly in the browser.
    // DECISION (Phase 16): pass the token as a query parameter for the SSE
    // connection. The coordinator will be updated in a future phase to accept
    // ?token=<value> as an alternative to the Authorization header for SSE.
    // For the stub (dev-token) this is sufficient for local development.
    const token = await getAccessToken()
    const url = `/api/v1/efforts/${effortId}/stream?token=${encodeURIComponent(token)}`

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

  // Auto-connect on composable creation and clean up on unmount.
  connect()
  onUnmounted(disconnect)

  return { events, connected, error, disconnect }
}
