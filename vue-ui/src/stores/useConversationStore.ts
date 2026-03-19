import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listConversations,
  getMessages,
  type ConversationSummary,
  type MessageResponse,
} from '@/api/conversations'
import type { WsMessageEnvelope } from '@/composables/useMessagingSocket'

/**
 * Pinia store for conversation and message state within an Effort.
 *
 * Holds the list of conversations (channels + DMs) and a per-convId message
 * cache. New messages received via the WebSocket composable should be fed in
 * via `appendMessage`.
 *
 * Unread counts are tracked as simple integers incremented when a message
 * arrives for a convId that is not the currently active one. Callers are
 * responsible for calling `markRead(convId)` when a conversation is opened.
 */
export const useConversationStore = defineStore('conversations', () => {
  // ------------------------------------------------------------------ state

  /** All conversations for the current effort, keyed by convId. */
  const conversations = ref<ConversationSummary[]>([])

  /** Message cache: convId → ordered array of messages */
  const messagesByConvId = ref<Record<string, MessageResponse[]>>({})

  /** Unread counts: convId → count */
  const unreadByConvId = ref<Record<string, number>>({})

  const loading = ref(false)
  const error = ref<string | null>(null)

  // ------------------------------------------------------------------ actions

  /**
   * Load conversations for the given effort and populate `conversations`.
   * Does NOT load messages — those are fetched lazily by `fetchMessages`.
   */
  async function fetchConversations(effortId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await listConversations(effortId)
      conversations.value = res.conversations
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load conversations'
    } finally {
      loading.value = false
    }
  }

  /**
   * Load (or reload) messages for a single conversation.
   * Results are merged into `messagesByConvId` — existing messages for
   * the convId are replaced.
   */
  async function fetchMessages(effortId: string, convId: string): Promise<void> {
    try {
      const res = await getMessages(effortId, convId)
      messagesByConvId.value[convId] = res.messages
    } catch (e) {
      // Non-fatal: leave existing cache in place.
      console.error(`Failed to load messages for ${convId}:`, e)
    }
  }

  /**
   * Append a WebSocket message envelope to the local cache.
   * Called by EffortWorkspaceView whenever a WS frame arrives.
   */
  function appendMessage(envelope: WsMessageEnvelope): void {
    const { convId, messageId, from, body, postedAt } = envelope
    if (!messagesByConvId.value[convId]) {
      messagesByConvId.value[convId] = []
    }
    // Deduplicate by messageId in case of duplicate delivery.
    const existing = messagesByConvId.value[convId]
    if (!existing.some((m) => m.messageId === messageId)) {
      existing.push({ messageId, from, body, postedAt })
    }
    // Bump unread counter (callers reset via markRead when they view the conv).
    unreadByConvId.value[convId] = (unreadByConvId.value[convId] ?? 0) + 1
  }

  /** Reset the unread counter for a conversation when it is viewed. */
  function markRead(convId: string): void {
    unreadByConvId.value[convId] = 0
  }

  /**
   * Helper: derive a human-readable label for a convId.
   *   "conv:#general"  →  "#general"
   *   "conv:@alice+@dev" →  "alice + dev"  (DM)
   */
  function convLabel(convId: string): string {
    const inner = convId.replace(/^conv:/, '')
    if (inner.startsWith('#')) return inner
    // DM: strip leading @ and join with ' + '
    return inner
      .split('+')
      .map((s) => s.replace(/^@/, ''))
      .join(' + ')
  }

  return {
    conversations,
    messagesByConvId,
    unreadByConvId,
    loading,
    error,
    fetchConversations,
    fetchMessages,
    appendMessage,
    markRead,
    convLabel,
  }
})
