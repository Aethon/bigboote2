<template>
  <div class="at-history">
    <header class="at-history__header">
      <RouterLink class="at-history__back" :to="{ name: 'AgentTypeEdit', params: { id } }">
        ‹ Back to {{ agentTypeName }}
      </RouterLink>
      <h1 class="at-history__title">Event History</h1>
      <div class="at-history__meta">
        <code class="at-history__id">{{ id }}</code>
        <span
          class="at-history__conn-dot"
          :class="connected ? 'at-history__conn-dot--on' : 'at-history__conn-dot--off'"
          :title="connected ? 'SSE connected' : 'SSE disconnected'"
        />
        <span class="at-history__conn-label">{{ connected ? 'LIVE' : 'Connecting…' }}</span>
      </div>
    </header>

    <!-- Toolbar -->
    <div class="at-history__toolbar">
      <input
        v-model="filterQuery"
        class="at-history__search"
        type="search"
        placeholder="Filter events…"
        aria-label="Filter events"
      />
      <span class="at-history__count">
        {{ filteredEvents.length }} event{{ filteredEvents.length !== 1 ? 's' : '' }}
      </span>
      <button
        v-if="filterQuery"
        class="at-history__clear-btn"
        type="button"
        @click="filterQuery = ''"
      >Clear</button>
    </div>

    <!-- Event log -->
    <div ref="logRef" class="at-history__log">
      <div v-if="eventLog.length === 0" class="at-history__empty">
        <template v-if="connected">Waiting for events…</template>
        <template v-else>Connecting to event stream…</template>
      </div>

      <div v-else-if="filteredEvents.length === 0" class="at-history__empty">
        No events match "{{ filterQuery }}"
      </div>

      <EventRow
        v-for="entry in filteredEvents"
        :key="entry.eventId + entry.receivedAt"
        :entry="entry"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useAgentTypeStore } from '@/stores/useAgentTypeStore'
import { useAgentTypeEventStream } from '@/composables/useAgentTypeEventStream'
import type { SseLogEntry } from '@/stores/useAgentStore'
import EventRow from '@/components/EventRow.vue'

const props = defineProps<{ id: string }>()

// ------------------------------------------------------------------ SSE stream
const { events: sseEvents, connected } = useAgentTypeEventStream(props.id)

// Convert raw SSE events into SseLogEntry objects (bounded to 500 entries).
const eventLog = ref<SseLogEntry[]>([])

watch(
  () => sseEvents.value.length,
  (newLen, oldLen) => {
    for (let i = oldLen; i < newLen; i++) {
      const ev = sseEvents.value[i]
      if (eventLog.value.length >= 500) eventLog.value.shift()
      eventLog.value.push({
        eventId: ev.lastEventId || String(i),
        data: ev.data,
        receivedAt: new Date().toISOString(),
      })
    }
  },
)

// ------------------------------------------------------------------ filtering
const filterQuery = ref('')

const filteredEvents = computed(() => {
  const q = filterQuery.value.trim().toLowerCase()
  if (!q) return eventLog.value
  return eventLog.value.filter(
    (e) => e.data.toLowerCase().includes(q) || e.eventId.toLowerCase().includes(q),
  )
})

// ------------------------------------------------------------------ auto-scroll (only when not filtering)
const logRef = ref<HTMLElement | null>(null)

watch(
  () => eventLog.value.length,
  async () => {
    if (filterQuery.value) return
    await nextTick()
    if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
  },
)

// ------------------------------------------------------------------ breadcrumb name
const store = useAgentTypeStore()
const agentTypeName = computed(() => store.currentAgentType?.name ?? props.id)

onMounted(() => {
  if (!store.currentAgentType || store.currentAgentType.agentTypeId !== props.id) {
    store.fetchAgentType(props.id)
  }
})
</script>

<style scoped>
.at-history {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  padding: 1.25rem 1.5rem;
  background: #0a1628;
  color: #c0d0e0;
}

.at-history__header {
  flex-shrink: 0;
  margin-bottom: 0.75rem;
}

.at-history__back {
  display: inline-block;
  font-size: 0.8rem;
  color: #607d8b;
  text-decoration: none;
  margin-bottom: 0.3rem;
}
.at-history__back:hover { color: #90caf9; }

.at-history__title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #e0e8f0;
  margin: 0 0 0.4rem;
}

.at-history__meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
}

.at-history__id {
  font-family: 'IBM Plex Mono', monospace;
  color: #546e7a;
  font-size: 0.72rem;
  background: #0d1e33;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
}

.at-history__conn-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.at-history__conn-dot--on  { background: #4caf50; }
.at-history__conn-dot--off { background: #f44336; }

.at-history__conn-label {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: #607d8b;
}

.at-history__toolbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
  margin-bottom: 0.5rem;
}

.at-history__search {
  flex: 1;
  max-width: 340px;
  border: 1px solid #1e3050;
  border-radius: 5px;
  padding: 0.35rem 0.65rem;
  font-size: 0.8rem;
  font-family: 'IBM Plex Mono', monospace;
  background: #0d1e33;
  color: #a0b8cc;
  outline: none;
  transition: border-color 0.15s;
}
.at-history__search:focus { border-color: #3949ab; }
.at-history__search::placeholder { color: #3d5a70; }

.at-history__count {
  font-size: 0.75rem;
  color: #546e7a;
}

.at-history__clear-btn {
  background: none;
  border: none;
  font-size: 0.75rem;
  color: #607d8b;
  cursor: pointer;
  padding: 0;
}
.at-history__clear-btn:hover { color: #90caf9; }

.at-history__log {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  border: 1px solid #1a2d44;
  border-radius: 6px;
  padding: 0.25rem 0.5rem;
  background: #091422;
}

.at-history__empty {
  padding: 1.5rem;
  text-align: center;
  font-size: 0.82rem;
  color: #3d5a70;
  font-style: italic;
}
</style>
