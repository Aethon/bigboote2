<template>
  <article class="effort-card" :class="`effort-card--${effort.status}`" @click="$emit('click', effort.effortId)">
    <header class="effort-card__header">
      <span class="effort-card__status-dot" aria-hidden="true" />
      <h3 class="effort-card__name">{{ effort.name }}</h3>
      <span class="effort-card__status-badge">{{ effort.status }}</span>
    </header>

    <p class="effort-card__goal">{{ effort.goal }}</p>

    <footer class="effort-card__footer">
      <span class="effort-card__lead" title="Lead">{{ effort.leadName }}</span>
      <span class="effort-card__collaborators">
        {{ effort.collaboratorCount }} collaborator{{ effort.collaboratorCount !== 1 ? 's' : '' }}
      </span>
      <time class="effort-card__date" :datetime="effort.createdAt">
        {{ formatDate(effort.createdAt) }}
      </time>
    </footer>
  </article>
</template>

<script setup lang="ts">
import type { EffortSummary } from '@/api/efforts'

defineProps<{ effort: EffortSummary }>()
defineEmits<{ click: [effortId: string] }>()

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}
</script>

<style scoped>
.effort-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1rem;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.effort-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-color: #b0b0b0;
}

/* Status border accent */
.effort-card--created  { border-left: 4px solid #9e9e9e; }
.effort-card--active   { border-left: 4px solid #4caf50; }
.effort-card--paused   { border-left: 4px solid #ff9800; }
.effort-card--closed   { border-left: 4px solid #f44336; opacity: 0.75; }

.effort-card__header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}

.effort-card__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.effort-card--created  .effort-card__status-dot { background: #9e9e9e; }
.effort-card--active   .effort-card__status-dot { background: #4caf50; }
.effort-card--paused   .effort-card__status-dot { background: #ff9800; }
.effort-card--closed   .effort-card__status-dot { background: #f44336; }

.effort-card__name {
  font-size: 1rem;
  font-weight: 600;
  color: #1a1a2e;
  flex: 1;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.effort-card__status-badge {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  background: #f0f0f0;
  color: #555;
}
.effort-card--active .effort-card__status-badge {
  background: #e8f5e9;
  color: #2e7d32;
}
.effort-card--paused .effort-card__status-badge {
  background: #fff3e0;
  color: #e65100;
}
.effort-card--closed .effort-card__status-badge {
  background: #fce4ec;
  color: #b71c1c;
}

.effort-card__goal {
  font-size: 0.875rem;
  color: #555;
  margin: 0 0 0.75rem;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.effort-card__footer {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.78rem;
  color: #888;
}

.effort-card__lead {
  font-weight: 500;
  color: #555;
}

.effort-card__collaborators,
.effort-card__date {
  margin-left: auto;
}
.effort-card__collaborators { margin-left: auto; }
.effort-card__date { margin-left: 0.25rem; }
</style>
