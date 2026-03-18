<template>
  <div
    class="orb-wrap"
    :class="`orb-wrap--${effort.status}`"
    role="img"
    :aria-label="`Effort: ${effort.name}, status: ${effort.status}`"
  >
    <!-- SVG orbital diagram -->
    <svg class="orb" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
      <!-- Background circle -->
      <circle cx="50" cy="50" r="46" :fill="bgColor" />

      <!-- Outer glow ring (pulsing) -->
      <circle
        cx="50"
        cy="50"
        r="43"
        fill="none"
        :stroke="statusColor"
        stroke-width="3"
        class="orb__outer-ring"
        :class="`orb__outer-ring--${effort.status}`"
        opacity="0.8"
      />

      <!-- Mid ring — overall effort fill -->
      <circle
        cx="50"
        cy="50"
        r="35"
        fill="none"
        :stroke="statusColor"
        stroke-width="1.5"
        opacity="0.25"
        stroke-dasharray="220"
        stroke-dashoffset="0"
      />

      <!-- Agent arc segments at inner radius -->
      <g v-if="agentSegments.length > 0">
        <path
          v-for="(seg, i) in agentSegments"
          :key="i"
          :d="seg.d"
          fill="none"
          :stroke="seg.color"
          stroke-width="4"
          stroke-linecap="round"
          :class="seg.active ? 'orb__agent-ring--active' : ''"
          opacity="0.85"
        />
      </g>

      <!-- Centre initials -->
      <text
        x="50"
        y="50"
        text-anchor="middle"
        dominant-baseline="central"
        :fill="statusColor"
        font-family="'IBM Plex Mono', monospace"
        font-size="14"
        font-weight="700"
        opacity="0.9"
      >{{ initials }}</text>
    </svg>

    <!-- Labels below the orb -->
    <div class="orb-wrap__labels">
      <p class="orb-wrap__name">{{ effort.name }}</p>
      <p class="orb-wrap__elapsed">{{ elapsed }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import type { EffortSummary } from '@/api/efforts'

const props = defineProps<{
  effort: EffortSummary
  /**
   * Optional per-agent LoopStates, ordered by collaborator.
   * When omitted, segments are derived from collaboratorCount with a default state.
   */
  agentLoopStates?: Array<'IDLE' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'ERROR'>
}>()

// ------------------------------------------------------------------ colors

const STATUS_COLORS: Record<string, string> = {
  created: '#9e9e9e',
  active:  '#4caf50',
  paused:  '#ff9800',
  closed:  '#ef5350',
}

const LOOP_STATE_COLORS: Record<string, string> = {
  IDLE:    '#90caf9',
  RUNNING: '#4caf50',
  PAUSED:  '#ff9800',
  STOPPED: '#616161',
  ERROR:   '#ef5350',
}

const statusColor = computed(() => STATUS_COLORS[props.effort.status] ?? '#9e9e9e')
const bgColor = computed(() => `${statusColor.value}14`)  // ~8% opacity fill

// ------------------------------------------------------------------ initials

const initials = computed(() => {
  return props.effort.name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? '')
    .join('')
})

// ------------------------------------------------------------------ agent arc segments

/**
 * Compute SVG arc path data for N evenly-spaced ring segments at r=28.
 * Each segment spans ~80% of its angular slot, leaving a gap between them.
 */
const agentSegments = computed(() => {
  const R = 28
  const cx = 50
  const cy = 50

  const loopStates = props.agentLoopStates
  const n = loopStates ? loopStates.length : Math.min(props.effort.collaboratorCount, 8)
  if (n === 0) return []

  const gapDeg = n === 1 ? 0 : 6
  const slotDeg = 360 / n
  const arcDeg = slotDeg - gapDeg

  return Array.from({ length: n }, (_, i) => {
    const startDeg = i * slotDeg - 90       // -90 so we start at top
    const endDeg = startDeg + arcDeg

    const startRad = (startDeg * Math.PI) / 180
    const endRad = (endDeg * Math.PI) / 180

    const x1 = cx + R * Math.cos(startRad)
    const y1 = cy + R * Math.sin(startRad)
    const x2 = cx + R * Math.cos(endRad)
    const y2 = cy + R * Math.sin(endRad)

    const largeArc = arcDeg > 180 ? 1 : 0
    const d = `M ${x1.toFixed(2)} ${y1.toFixed(2)} A ${R} ${R} 0 ${largeArc} 1 ${x2.toFixed(2)} ${y2.toFixed(2)}`

    const state = loopStates?.[i] ?? (props.effort.status === 'active' ? 'RUNNING' : 'IDLE')
    return {
      d,
      color: LOOP_STATE_COLORS[state] ?? '#9e9e9e',
      active: state === 'RUNNING',
    }
  })
})

// ------------------------------------------------------------------ elapsed time clock

const elapsed = ref('')

function computeElapsed(): void {
  const created = new Date(props.effort.createdAt).getTime()
  const now = Date.now()
  const secs = Math.floor((now - created) / 1000)
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  elapsed.value = `${h}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`
}

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  computeElapsed()
  timer = setInterval(computeElapsed, 1000)
})

onUnmounted(() => {
  if (timer !== null) clearInterval(timer)
})
</script>

<style scoped>
.orb-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  cursor: default;
  user-select: none;
}

.orb {
  width: clamp(100px, 14vw, 160px);
  height: clamp(100px, 14vw, 160px);
  overflow: visible;
  filter: drop-shadow(0 0 8px v-bind(statusColor));
}

/* Outer ring pulse — CSS animation varies by status */
.orb__outer-ring {
  transform-origin: 50px 50px;
}

.orb__outer-ring--active {
  animation: pulse-active 2s ease-in-out infinite;
}

.orb__outer-ring--paused {
  animation: pulse-paused 3s ease-in-out infinite;
}

.orb__outer-ring--created {
  animation: pulse-slow 4s ease-in-out infinite;
}

/* Active agent arc segments spin slowly */
.orb__agent-ring--active {
  transform-box: fill-box;
  transform-origin: 50% 50%;
  animation: spin-seg 8s linear infinite;
}

/* Label area */
.orb-wrap__labels {
  text-align: center;
}

.orb-wrap__name {
  font-family: 'IBM Plex Mono', monospace;
  font-size: clamp(0.7rem, 1.2vw, 0.9rem);
  font-weight: 600;
  color: #e0e8f8;
  margin: 0 0 0.15rem;
  max-width: 14ch;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.orb-wrap__elapsed {
  font-family: 'IBM Plex Mono', monospace;
  font-size: clamp(0.6rem, 1vw, 0.75rem);
  color: #607d8b;
  margin: 0;
}

/* Keyframes */
@keyframes pulse-active {
  0%,100% { opacity: 0.8; r: 43; }
  50%      { opacity: 1.0; r: 44.5; }
}

@keyframes pulse-paused {
  0%,100% { opacity: 0.6; }
  50%      { opacity: 0.95; }
}

@keyframes pulse-slow {
  0%,100% { opacity: 0.35; }
  50%      { opacity: 0.65; }
}

@keyframes spin-seg {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
