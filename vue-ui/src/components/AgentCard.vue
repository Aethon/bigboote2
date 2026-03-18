<template>
  <div class="agent-card" :class="`agent-card--${agent.loopState.toLowerCase()}`">
    <div class="agent-card__header">
      <span class="agent-card__state-dot" aria-hidden="true" />
      <span class="agent-card__name">{{ agent.collaboratorName }}</span>
      <span class="agent-card__state-badge">{{ agent.loopState }}</span>
    </div>

    <div class="agent-card__meta">
      <span class="agent-card__type" title="Agent type">{{ agent.agentTypeId }}</span>
      <span class="agent-card__turn" title="Current turn">Turn {{ agent.currentTurn }}</span>
    </div>

    <div class="agent-card__actions">
      <button
        v-if="agent.loopState === 'RUNNING'"
        class="btn btn--sm btn--warning"
        @click.stop="$emit('pause', agent.instanceId)"
      >Pause</button>
      <button
        v-if="agent.loopState === 'PAUSED'"
        class="btn btn--sm btn--primary"
        @click.stop="$emit('resume', agent.instanceId)"
      >Resume</button>
      <button
        v-if="agent.loopState !== 'STOPPED' && agent.loopState !== 'ERROR'"
        class="btn btn--sm btn--danger"
        @click.stop="$emit('stop', agent.instanceId)"
      >Stop</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AgentStatus } from '@/api/agents'

defineProps<{ agent: AgentStatus }>()
defineEmits<{
  pause: [instanceId: string]
  resume: [instanceId: string]
  stop: [instanceId: string]
}>()
</script>

<style scoped>
.agent-card {
  background: #1a2744;
  border-radius: 6px;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
  border-left: 3px solid #555;
}
.agent-card--running { border-left-color: #4caf50; }
.agent-card--paused  { border-left-color: #ff9800; }
.agent-card--idle    { border-left-color: #90caf9; }
.agent-card--stopped { border-left-color: #666; opacity: 0.65; }
.agent-card--error   { border-left-color: #f44336; }

.agent-card__header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.3rem;
}

.agent-card__state-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #555;
  flex-shrink: 0;
}
.agent-card--running .agent-card__state-dot { background: #4caf50; }
.agent-card--paused  .agent-card__state-dot { background: #ff9800; }
.agent-card--idle    .agent-card__state-dot { background: #90caf9; }
.agent-card--error   .agent-card__state-dot { background: #f44336; }

.agent-card__name {
  font-size: 0.85rem;
  font-weight: 600;
  color: #e0e0e0;
  flex: 1;
}

.agent-card__state-badge {
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #aaa;
}

.agent-card__meta {
  display: flex;
  gap: 1rem;
  font-size: 0.75rem;
  color: #8899bb;
  margin-bottom: 0.5rem;
}

.agent-card__actions {
  display: flex;
  gap: 0.4rem;
}

.btn {
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.6rem;
  transition: opacity 0.15s;
}
.btn:hover { opacity: 0.85; }
.btn--sm { padding: 0.2rem 0.5rem; }
.btn--primary { background: #3949ab; color: #fff; }
.btn--warning { background: #ff9800; color: #fff; }
.btn--danger  { background: #c62828; color: #fff; }
</style>
