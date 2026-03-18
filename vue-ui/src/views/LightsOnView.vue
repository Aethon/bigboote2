<template>
  <div class="lights-on">
    <!-- Noise grain overlay -->
    <div class="lights-on__grain" aria-hidden="true" />

    <!-- Top bar: wordmark + live clock -->
    <header class="lights-on__topbar">
      <span class="lights-on__wordmark">BIGBOOTE</span>
      <span class="lights-on__clock" aria-label="Current time">{{ clock }}</span>
    </header>

    <!-- Centre: Active Efforts Grid -->
    <main class="lights-on__centre">
      <template v-if="activeEfforts.length > 0">
        <div class="lights-on__orb-grid">
          <EffortOrb
            v-for="effort in activeEfforts"
            :key="effort.effortId"
            :effort="effort"
          />
        </div>
      </template>
      <div v-else class="lights-on__idle">
        <span class="lights-on__idle-ring" aria-hidden="true" />
        <p class="lights-on__idle-text">No active efforts</p>
      </div>
    </main>

    <!-- Bottom: metrics bar + live ticker -->
    <footer class="lights-on__bottom">
      <!-- System Metrics Bar -->
      <div class="lights-on__metrics" role="status" aria-label="System metrics">
        <div class="metric">
          <span class="metric__value">{{ metrics.activeEfforts }}</span>
          <span class="metric__label">Active Efforts</span>
        </div>
        <div class="metric__divider" aria-hidden="true" />
        <div class="metric">
          <span class="metric__value">{{ metrics.totalAgents }}</span>
          <span class="metric__label">Collaborators</span>
        </div>
        <div class="metric__divider" aria-hidden="true" />
        <div class="metric">
          <span class="metric__value">{{ metrics.totalEfforts }}</span>
          <span class="metric__label">Total Efforts</span>
        </div>
        <div class="metric__divider" aria-hidden="true" />
        <div class="metric">
          <span class="metric__value">{{ metrics.pausedEfforts }}</span>
          <span class="metric__label">Paused</span>
        </div>
      </div>

      <!-- Live Event Ticker -->
      <LiveEventTicker :items="tickerItems" :speed="55" class="lights-on__ticker" />
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { listEfforts, type EffortSummary } from '@/api/efforts'
import EffortOrb from '@/components/EffortOrb.vue'
import LiveEventTicker from '@/components/LiveEventTicker.vue'
import type { TickerItem } from '@/components/LiveEventTicker.vue'

// ------------------------------------------------------------------ live clock

const clock = ref('')

function updateClock(): void {
  clock.value = new Date().toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

// ------------------------------------------------------------------ efforts polling

const allEfforts = ref<EffortSummary[]>([])
const tickerItems = ref<TickerItem[]>([])

const activeEfforts = computed(() =>
  allEfforts.value.filter((e) => e.status === 'active'),
)

const metrics = computed(() => ({
  activeEfforts: activeEfforts.value.length,
  totalAgents: allEfforts.value.reduce((sum, e) => sum + e.collaboratorCount, 0),
  totalEfforts: allEfforts.value.length,
  pausedEfforts: allEfforts.value.filter((e) => e.status === 'paused').length,
}))

/** Track previous snapshot to detect state changes for the ticker. */
const prevStatusMap = ref<Record<string, string>>({})

async function pollEfforts(): Promise<void> {
  try {
    const res = await listEfforts()
    const efforts = res.efforts
    const newItems: TickerItem[] = []

    for (const effort of efforts) {
      const prev = prevStatusMap.value[effort.effortId]
      if (prev !== undefined && prev !== effort.status) {
        newItems.push({
          text: `${effort.name} → ${effort.status}`,
          type: 'effort',
          ts: new Date().toISOString(),
        })
      }
    }

    // First load: seed ticker with current active efforts
    if (Object.keys(prevStatusMap.value).length === 0 && efforts.length > 0) {
      const active = efforts.filter((e) => e.status === 'active')
      if (active.length > 0) {
        newItems.push(
          ...active.map((e) => ({
            text: `${e.name} is active`,
            type: 'effort' as const,
            ts: new Date().toISOString(),
          })),
        )
      } else {
        newItems.push({
          text: 'System online — no active efforts',
          type: 'system',
          ts: new Date().toISOString(),
        })
      }
    }

    allEfforts.value = efforts
    prevStatusMap.value = Object.fromEntries(efforts.map((e) => [e.effortId, e.status]))

    if (newItems.length > 0) {
      // Keep the ticker bounded to the last 30 items
      tickerItems.value = [...tickerItems.value, ...newItems].slice(-30)
    }
  } catch {
    // Silently ignore poll errors — this is an ambient display
  }
}

// ------------------------------------------------------------------ lifecycle

let clockTimer: ReturnType<typeof setInterval> | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)

  pollEfforts()
  pollTimer = setInterval(pollEfforts, 15_000)
})

onUnmounted(() => {
  if (clockTimer !== null) clearInterval(clockTimer)
  if (pollTimer !== null) clearInterval(pollTimer)
})
</script>

<style scoped>
/* ------------------------------------------------------------------ layout */

.lights-on {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background: #0a1628;
  color: #e0e8f8;
  overflow: hidden;
}

/* Animated noise grain overlay */
.lights-on__grain {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  opacity: 0.035;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E");
  background-repeat: repeat;
  background-size: 128px 128px;
  animation: grain 0.5s steps(1) infinite;
}

@keyframes grain {
  0%  { background-position: 0 0; }
  10% { background-position: -5px -10px; }
  20% { background-position: -15px 5px; }
  30% { background-position: 7px -25px; }
  40% { background-position: -5px 25px; }
  50% { background-position: -15px 10px; }
  60% { background-position: 15px 0; }
  70% { background-position: 0 15px; }
  80% { background-position: 3px 35px; }
  90% { background-position: -10px 10px; }
  100%{ background-position: 0 0; }
}

/* ------------------------------------------------------------------ top bar */

.lights-on__topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 2rem 0.75rem;
  flex-shrink: 0;
}

.lights-on__wordmark {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 1.1rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #e0e8f8;
  text-transform: uppercase;
  opacity: 0.9;
}

.lights-on__clock {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 1.2rem;
  font-weight: 400;
  letter-spacing: 0.08em;
  color: #90a4ae;
  opacity: 0.85;
}

/* ------------------------------------------------------------------ centre */

.lights-on__centre {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem 3rem;
  min-height: 0;
}

.lights-on__orb-grid {
  display: flex;
  flex-wrap: wrap;
  gap: clamp(1.5rem, 4vw, 4rem);
  align-items: center;
  justify-content: center;
}

.lights-on__idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
}

.lights-on__idle-ring {
  display: block;
  width: clamp(80px, 12vw, 140px);
  height: clamp(80px, 12vw, 140px);
  border-radius: 50%;
  border: 2px solid rgba(144, 164, 174, 0.2);
  animation: idle-pulse 3s ease-in-out infinite;
}

.lights-on__idle-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.9rem;
  color: #37474f;
  letter-spacing: 0.08em;
  margin: 0;
}

@keyframes idle-pulse {
  0%,100% { opacity: 0.3; transform: scale(1); }
  50%      { opacity: 0.7; transform: scale(1.04); }
}

/* ------------------------------------------------------------------ bottom */

.lights-on__bottom {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding: 0.75rem 2rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

/* Metrics bar */
.lights-on__metrics {
  display: flex;
  align-items: center;
  gap: 0;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 2rem 0 0;
  gap: 0.15rem;
}

.metric__value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 700;
  color: #90caf9;
  line-height: 1;
  letter-spacing: -0.02em;
}

.metric__label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #37474f;
}

.metric__divider {
  width: 1px;
  height: 2.5rem;
  background: rgba(255, 255, 255, 0.08);
  margin-right: 2rem;
  flex-shrink: 0;
}

/* Ticker */
.lights-on__ticker {
  width: 100%;
}
</style>
