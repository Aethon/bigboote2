<template>
  <div class="event-panel">
    <div class="event-panel__header">
      <h3 class="event-panel__title">Event Stream</h3>
      <span
        class="event-panel__dot"
        :class="connected ? 'event-panel__dot--connected' : 'event-panel__dot--disconnected'"
        :title="connected ? 'SSE connected' : 'SSE disconnected'"
      />
      <span class="event-panel__count">{{ eventLog.length }}</span>
    </div>

    <div ref="logRef" class="event-panel__log">
      <div v-if="eventLog.length === 0" class="event-panel__empty">
        Waiting for events…
      </div>
      <EventRow
        v-for="entry in eventLog"
        :key="entry.eventId + entry.receivedAt"
        :entry="entry"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { SseLogEntry } from '@/stores/useAgentStore'
import EventRow from './EventRow.vue'

const props = defineProps<{
  eventLog: SseLogEntry[]
  connected: boolean
}>()

const logRef = ref<HTMLElement | null>(null)

watch(
  () => props.eventLog.length,
  async () => {
    await nextTick()
    if (logRef.value) {
      logRef.value.scrollTop = logRef.value.scrollHeight
    }
  },
)
</script>

<style scoped>
.event-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.event-panel__header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.5rem 0.4rem;
  border-bottom: 1px solid #1e2d50;
  flex-shrink: 0;
}

.event-panel__title {
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #607d8b;
  margin: 0;
  flex: 1;
}

.event-panel__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.event-panel__dot--connected    { background: #4caf50; }
.event-panel__dot--disconnected { background: #f44336; }

.event-panel__count {
  font-size: 0.7rem;
  color: #546e7a;
}

.event-panel__log {
  flex: 1;
  overflow-y: auto;
  padding: 0.25rem 0.25rem;
}

.event-panel__empty {
  font-size: 0.78rem;
  color: #546e7a;
  font-style: italic;
  padding: 0.5rem 0.25rem;
}
</style>
