import { ref, onUnmounted } from 'vue'
import { useAuth } from '@/composables/useAuth'

/** Outbound message payload sent over the WebSocket. */
export interface WsPostRequest {
  /** convId string, e.g. "conv:#general" or "conv:@alice+@dev" */
  convId: string
  /** Message body text. */
  body: string
}

/** Inbound message envelope delivered from the coordinator. */
export interface WsMessageEnvelope {
  messageId: string
  convId: string
  from: string
  body: string
  postedAt: string
}

/**
 * WebSocket composable — connects to WS /api/v1/efforts/{effortId}/messaging.
 *
 * **Phase 16 status:** skeleton only.
 * Establishes the WebSocket connection with the Bearer token passed as a
 * query parameter (same strategy as the SSE composable — browser WS API
 * does not support custom headers). Full message dispatch into the
 * ConversationStore will be wired in Phase 17.
 *
 * The connection is closed and cleaned up when the owning component unmounts.
 *
 * Usage:
 * ```ts
 * const { messages, send, connected, error } = useMessagingSocket(effortId)
 * await send({ convId: 'conv:#general', body: 'Hello!' })
 * ```
 */
export function useMessagingSocket(effortId: string) {
  const messages = ref<WsMessageEnvelope[]>([])
  const connected = ref(false)
  const error = ref<string | null>(null)
  let ws: WebSocket | null = null

  async function connect() {
    const { getAccessToken } = useAuth()
    const token = await getAccessToken()

    // Determine WebSocket URL — Vite proxy rewrites /api → ws://localhost:8080 for WS.
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const url = `${protocol}//${host}/api/v1/efforts/${effortId}/messaging?token=${encodeURIComponent(token)}`

    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      error.value = null
    }

    ws.onmessage = (ev: MessageEvent) => {
      try {
        const envelope = JSON.parse(ev.data as string) as WsMessageEnvelope
        messages.value.push(envelope)
      } catch {
        // Ignore unparseable frames; they may be control messages.
      }
    }

    ws.onclose = () => {
      connected.value = false
    }

    ws.onerror = () => {
      connected.value = false
      error.value = 'WebSocket error — check console for details'
    }
  }

  /** Send a message to a conversation. No-op if the socket is not open. */
  function send(req: WsPostRequest): void {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(req))
    }
  }

  function disconnect() {
    ws?.close()
    ws = null
    connected.value = false
  }

  // Auto-connect on composable creation and clean up on unmount.
  connect()
  onUnmounted(disconnect)

  return { messages, connected, error, send, disconnect }
}
