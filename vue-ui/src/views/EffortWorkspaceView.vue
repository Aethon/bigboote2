<template>
  <div class="workspace">
    <!-- Left sidebar: channels / DMs / docs -->
    <aside class="workspace__sidebar">
      <SidebarNav
        :effort-id="effortId"
        :effort-name="effortName"
        :conversations="convStore.conversations"
        :unread-by-conv-id="convStore.unreadByConvId"
      />
    </aside>

    <!-- Centre: child route (ConversationPane / DocumentPane) -->
    <section class="workspace__center">
      <RouterView />
    </section>

    <!-- Right panel: agents + event stream -->
    <aside class="workspace__right">
      <RightPanel
        :agents="agentList"
        :event-log="agentStore.eventLog"
        :sse-connected="sseConnected"
        @pause="handlePause"
        @resume="handleResume"
        @stop="handleStop"
      />
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, onMounted, watch } from 'vue'
import { useConversationStore } from '@/stores/useConversationStore'
import { useAgentStore } from '@/stores/useAgentStore'
import { useEffortStore } from '@/stores/useEffortStore'
import { useEffortEventStream } from '@/composables/useEffortEventStream'
import { useMessagingSocket } from '@/composables/useMessagingSocket'
import SidebarNav from '@/components/SidebarNav.vue'
import RightPanel from '@/components/RightPanel.vue'

const props = defineProps<{ effortId: string }>()

// ------------------------------------------------------------------ stores
const convStore = useConversationStore()
const agentStore = useAgentStore()
const effortStore = useEffortStore()

// ------------------------------------------------------------------ derived data
const effortName = computed(
  () => effortStore.currentEffort?.name ?? effortStore.effortById(props.effortId)?.name ?? props.effortId,
)
const agentList = computed(() => Object.values(agentStore.agentsByInstanceId))

// ------------------------------------------------------------------ SSE stream
const { connected: sseConnected, events: sseEvents } = useEffortEventStream(props.effortId)

// Feed SSE events into the agent store as they arrive.
// Watch by length so we get reliable old/new counts even when array is mutated in-place.
watch(
  () => sseEvents.value.length,
  (newLen, oldLen) => {
    for (let i = oldLen; i < newLen; i++) {
      const ev = sseEvents.value[i]
      agentStore.applyEvent(ev.data, ev.lastEventId)
    }
  },
)

// ------------------------------------------------------------------ WebSocket
const { send: wsSend, connected: wsConnected, messages: wsMessages } = useMessagingSocket(props.effortId)

// Feed WS messages into the conversation store as they arrive.
watch(
  () => wsMessages.value.length,
  (newLen, oldLen) => {
    for (let i = oldLen; i < newLen; i++) {
      convStore.appendMessage(wsMessages.value[i])
    }
  },
)

/**
 * Provide `effortId`, `wsSend`, and `wsConnected` to child route components
 * (ConversationPane) via inject so they don't need prop drilling through RouterView.
 */
provide('effortId', props.effortId)
provide('wsSend', wsSend)
provide('wsConnected', wsConnected)

// ------------------------------------------------------------------ lifecycle
onMounted(async () => {
  await Promise.all([
    convStore.fetchConversations(props.effortId),
    effortStore.fetchEffort(props.effortId),
  ])
})

// ------------------------------------------------------------------ agent control
async function handlePause(instanceId: string): Promise<void> {
  await agentStore.pause(props.effortId, instanceId)
}
async function handleResume(instanceId: string): Promise<void> {
  await agentStore.resume(props.effortId, instanceId)
}
async function handleStop(instanceId: string): Promise<void> {
  await agentStore.stop(props.effortId, instanceId)
}
</script>

<style scoped>
.workspace {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.workspace__sidebar {
  width: 220px;
  min-width: 220px;
  background: #16213e;
  color: #ccc;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.workspace__center {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.workspace__right {
  width: 300px;
  min-width: 300px;
  background: #0f3460;
  color: #ccc;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
