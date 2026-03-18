<template>
  <div class="conv-pane">
    <!-- Conversation header -->
    <header class="conv-pane__header">
      <span class="conv-pane__icon">{{ isChannel ? '#' : '@' }}</span>
      <h2 class="conv-pane__title">{{ title }}</h2>
      <span
        class="conv-pane__ws-dot"
        :class="wsConnected ? 'conv-pane__ws-dot--on' : 'conv-pane__ws-dot--off'"
        :title="wsConnected ? 'Connected' : 'Disconnected'"
      />
    </header>

    <!-- Message list -->
    <MessageList
      :messages="messages"
      class="conv-pane__messages"
    />

    <!-- Composer -->
    <MessageComposer
      v-if="resolvedConvId"
      :conv-id="resolvedConvId"
      :connected="wsConnected"
      :placeholder="`Message ${isChannel ? '#' : '@'}${title}`"
      @send="handleSend"
    />
    <div v-else class="conv-pane__no-conv">
      Conversation not found — it may not have been created yet.
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, watch } from 'vue'
import type { Ref } from 'vue'
import { useConversationStore } from '@/stores/useConversationStore'
import type { WsPostRequest } from '@/composables/useMessagingSocket'
import MessageList from '@/components/MessageList.vue'
import MessageComposer from '@/components/MessageComposer.vue'

// Props come from the router (EffortChannel: convName, EffortDm: collaborator)
const props = defineProps<{ convName?: string; collaborator?: string }>()

// Injected from EffortWorkspaceView
const effortId = inject<string>('effortId', '')
const wsSend = inject<(req: WsPostRequest) => void>('wsSend', () => {})
const wsConnected = inject<Ref<boolean>>('wsConnected', { value: false } as Ref<boolean>)

const convStore = useConversationStore()

// ------------------------------------------------------------------ derived data
const isChannel = computed(() => !!props.convName)

const title = computed(() => props.convName ?? props.collaborator ?? '')

/**
 * Resolve the full convId from the conversations list.
 *   channel "general" → "conv:#general"
 *   dm      "alice"   → "conv:@alice+@<otherMember>" (found by lookup)
 */
const resolvedConvId = computed<string | null>(() => {
  if (props.convName) {
    const target = `conv:#${props.convName}`
    return convStore.conversations.find((c) => c.convId === target)?.convId ?? target
  }
  if (props.collaborator) {
    // Find a DM conversation whose convId contains the collaborator name.
    const hit = convStore.conversations.find(
      (c) => !c.convId.includes(':#') && c.convId.includes(`@${props.collaborator}`),
    )
    return hit?.convId ?? null
  }
  return null
})

const messages = computed(() =>
  resolvedConvId.value ? (convStore.messagesByConvId[resolvedConvId.value] ?? []) : [],
)

// ------------------------------------------------------------------ actions
function handleSend(convId: string, body: string): void {
  wsSend({ convId, body })
}

// ------------------------------------------------------------------ lifecycle
async function loadMessages(): Promise<void> {
  if (!effortId || !resolvedConvId.value) return
  await convStore.fetchMessages(effortId, resolvedConvId.value)
  convStore.markRead(resolvedConvId.value)
}

onMounted(loadMessages)

// Reload when the route changes (switching channels/DMs within the same workspace)
watch([() => props.convName, () => props.collaborator], loadMessages)

// Mark read when we receive new messages while this pane is active
watch(messages, () => {
  if (resolvedConvId.value) convStore.markRead(resolvedConvId.value)
})
</script>

<style scoped>
.conv-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
}

.conv-pane__header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.conv-pane__icon {
  font-size: 1rem;
  color: #777;
  font-weight: 700;
}

.conv-pane__title {
  font-size: 0.975rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  flex: 1;
}

.conv-pane__ws-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.conv-pane__ws-dot--on  { background: #4caf50; }
.conv-pane__ws-dot--off { background: #bdbdbd; }

.conv-pane__messages {
  flex: 1;
  min-height: 0;
}

.conv-pane__no-conv {
  padding: 1rem;
  color: #aaa;
  font-size: 0.875rem;
  font-style: italic;
  text-align: center;
}
</style>
