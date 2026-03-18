<template>
  <div class="agent-panel">
    <h3 class="agent-panel__title">Agents</h3>
    <div v-if="agents.length === 0" class="agent-panel__empty">
      No agents running
    </div>
    <AgentCard
      v-for="agent in agents"
      :key="agent.instanceId"
      :agent="agent"
      @pause="$emit('pause', $event)"
      @resume="$emit('resume', $event)"
      @stop="$emit('stop', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import type { AgentStatus } from '@/api/agents'
import AgentCard from './AgentCard.vue'

defineProps<{ agents: AgentStatus[] }>()
defineEmits<{
  pause: [instanceId: string]
  resume: [instanceId: string]
  stop: [instanceId: string]
}>()
</script>

<style scoped>
.agent-panel {
  padding: 0.5rem;
}

.agent-panel__title {
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #607d8b;
  padding: 0.4rem 0.25rem 0.4rem;
  margin: 0 0 0.5rem;
  border-bottom: 1px solid #1e2d50;
}

.agent-panel__empty {
  font-size: 0.8rem;
  color: #546e7a;
  font-style: italic;
  padding: 0.5rem 0.25rem;
}
</style>
