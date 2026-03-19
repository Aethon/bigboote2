<template>
  <div class="right-panel">
    <div class="right-panel__tabs" role="tablist">
      <button
        role="tab"
        class="right-panel__tab"
        :class="{ 'right-panel__tab--active': activeTab === 'agents' }"
        :aria-selected="activeTab === 'agents'"
        @click="activeTab = 'agents'"
      >Agents</button>
      <button
        role="tab"
        class="right-panel__tab"
        :class="{ 'right-panel__tab--active': activeTab === 'events' }"
        :aria-selected="activeTab === 'events'"
        @click="activeTab = 'events'"
      >
        Events
        <span v-if="sseConnected" class="right-panel__live-badge">LIVE</span>
      </button>
    </div>

    <div class="right-panel__body">
      <AgentDetailPanel
        v-if="activeTab === 'agents'"
        :agents="agents"
        @pause="$emit('pause', $event)"
        @resume="$emit('resume', $event)"
        @stop="$emit('stop', $event)"
      />
      <EventStreamPanel
        v-else
        :event-log="eventLog"
        :connected="sseConnected"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { AgentStatus } from '@/api/agents'
import type { SseLogEntry } from '@/stores/useAgentStore'
import AgentDetailPanel from './AgentDetailPanel.vue'
import EventStreamPanel from './EventStreamPanel.vue'

defineProps<{
  agents: AgentStatus[]
  eventLog: SseLogEntry[]
  sseConnected: boolean
}>()

defineEmits<{
  pause: [instanceId: string]
  resume: [instanceId: string]
  stop: [instanceId: string]
}>()

const activeTab = ref<'agents' | 'events'>('agents')
</script>

<style scoped>
.right-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.right-panel__tabs {
  display: flex;
  border-bottom: 1px solid #1e2d50;
  flex-shrink: 0;
}

.right-panel__tab {
  flex: 1;
  padding: 0.5rem 0.25rem;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 0.78rem;
  font-weight: 600;
  color: #607d8b;
  transition: color 0.15s, border-color 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
}
.right-panel__tab:hover {
  color: #90caf9;
}
.right-panel__tab--active {
  color: #90caf9;
  border-bottom-color: #90caf9;
}

.right-panel__live-badge {
  font-size: 0.6rem;
  font-weight: 700;
  background: #4caf50;
  color: #fff;
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
  letter-spacing: 0.05em;
}

.right-panel__body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
