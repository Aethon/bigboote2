<template>
  <div class="event-row">
    <span class="event-row__id" :title="`Event ID: ${entry.eventId}`">{{ shortId }}</span>
    <span class="event-row__time">{{ formatTime(entry.receivedAt) }}</span>
    <span class="event-row__type" :class="`event-row__type--${eventType}`">{{ eventType }}</span>
    <pre class="event-row__data" :title="entry.data">{{ preview }}</pre>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SseLogEntry } from '@/stores/useAgentStore'

const props = defineProps<{ entry: SseLogEntry }>()

const shortId = computed(() => props.entry.eventId.slice(-6) || '—')

const eventType = computed(() => {
  try {
    const parsed = JSON.parse(props.entry.data) as { type?: string }
    return parsed.type ?? 'raw'
  } catch {
    return 'raw'
  }
})

const preview = computed(() => {
  try {
    const parsed = JSON.parse(props.entry.data)
    const str = JSON.stringify(parsed, null, 0)
    return str.length > 120 ? str.slice(0, 117) + '...' : str
  } catch {
    return props.entry.data.slice(0, 120)
  }
})

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
</script>

<style scoped>
.event-row {
  display: grid;
  grid-template-columns: 3rem 5.5rem auto 1fr;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.3rem 0;
  border-bottom: 1px solid #1e2d50;
  font-size: 0.72rem;
  font-family: 'IBM Plex Mono', monospace;
  color: #a0b0cc;
}

.event-row__id {
  color: #546e8a;
  user-select: all;
}

.event-row__time {
  color: #607d8b;
  white-space: nowrap;
}

.event-row__type {
  font-weight: 600;
  color: #90caf9;
  white-space: nowrap;
}
.event-row__type--raw          { color: #78909c; }
.event-row__type--agent\.status { color: #4caf50; }
.event-row__type--effort\.started,
.event-row__type--effort\.paused,
.event-row__type--effort\.closed { color: #ffa726; }

.event-row__data {
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #7d94ab;
}
</style>
