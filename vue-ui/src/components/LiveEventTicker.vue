<template>
  <div class="ticker" aria-live="polite" aria-label="Live event feed">
    <span class="ticker__label">LIVE</span>
    <div class="ticker__track-wrap">
      <div
        ref="trackRef"
        class="ticker__track"
        :class="{ 'ticker__track--animate': items.length > 0 }"
        :style="trackStyle"
      >
        <!-- Duplicate the list so the scroll loop is seamless -->
        <span
          v-for="(item, i) in doubled"
          :key="`${i}-${item.text}`"
          class="ticker__item"
          :class="`ticker__item--${item.type}`"
        >
          <span class="ticker__item-bullet" aria-hidden="true">{{ bullet(item.type) }}</span>
          {{ item.text }}
          <span class="ticker__item-sep" aria-hidden="true">·</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'

export interface TickerItem {
  text: string
  /** Visual category — drives colour and bullet icon. */
  type: 'effort' | 'agent' | 'message' | 'system' | 'default'
  /** ISO timestamp (not displayed, used for keying). */
  ts: string
}

const props = defineProps<{
  items: TickerItem[]
  /** Pixels per second. Defaults to 60. */
  speed?: number
}>()

const speed = computed(() => props.speed ?? 60)

// ------------------------------------------------------------------ DOM measurement
const trackRef = ref<HTMLElement | null>(null)
const trackWidth = ref(0)

async function measureTrack(): Promise<void> {
  await nextTick()
  if (trackRef.value) {
    // Width of the first half (original items only) for animation duration.
    trackWidth.value = trackRef.value.scrollWidth / 2
  }
}

// Duplicate items for seamless looping
const doubled = computed(() => [...props.items, ...props.items])

const trackStyle = computed(() => {
  if (trackWidth.value === 0 || props.items.length === 0) return {}
  const duration = trackWidth.value / speed.value
  return { '--track-width': `${trackWidth.value}px`, '--duration': `${duration}s` }
})

watch(() => props.items, measureTrack, { deep: false })
onMounted(measureTrack)

// ------------------------------------------------------------------ bullet icons by type
function bullet(type: TickerItem['type']): string {
  const map: Record<TickerItem['type'], string> = {
    effort:  '◉',
    agent:   '⚙',
    message: '✉',
    system:  '⚡',
    default: '▸',
  }
  return map[type] ?? '▸'
}
</script>

<style scoped>
.ticker {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  height: 2rem;
  overflow: hidden;
  width: 100%;
}

.ticker__label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: #4caf50;
  background: rgba(76, 175, 80, 0.15);
  border: 1px solid rgba(76, 175, 80, 0.4);
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
  flex-shrink: 0;
}

.ticker__track-wrap {
  flex: 1;
  overflow: hidden;
  position: relative;
  /* Fade edges */
  mask-image: linear-gradient(to right, transparent 0%, black 4%, black 96%, transparent 100%);
  -webkit-mask-image: linear-gradient(to right, transparent 0%, black 4%, black 96%, transparent 100%);
}

.ticker__track {
  display: inline-flex;
  align-items: center;
  gap: 0;
  white-space: nowrap;
  will-change: transform;
}

.ticker__track--animate {
  animation: ticker-scroll var(--duration, 20s) linear infinite;
}

.ticker__item {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.78rem;
  color: #7a9ab8;
  padding: 0 0.75rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.ticker__item--effort  { color: #90caf9; }
.ticker__item--agent   { color: #80cbc4; }
.ticker__item--message { color: #a5d6a7; }
.ticker__item--system  { color: #fff176; }
.ticker__item--default { color: #7a9ab8; }

.ticker__item-bullet {
  opacity: 0.7;
  font-size: 0.65rem;
}

.ticker__item-sep {
  opacity: 0.3;
  margin-left: 0.5rem;
}

@keyframes ticker-scroll {
  from { transform: translateX(0); }
  to   { transform: translateX(calc(-1 * var(--track-width, 0px))); }
}
</style>
