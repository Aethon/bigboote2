<template>
  <article class="at-card" @click="$emit('click', agentType.agentTypeId)">
    <header class="at-card__header">
      <span class="at-card__icon" aria-hidden="true">⚙</span>
      <h3 class="at-card__name">{{ agentType.name }}</h3>
      <span class="at-card__version" title="Version">v{{ agentType.version }}</span>
    </header>

    <p class="at-card__description">{{ agentType.description || 'No description.' }}</p>

    <div class="at-card__image" :title="`Docker image: ${agentType.dockerImage}`">
      <span class="at-card__image-icon" aria-hidden="true">🐳</span>
      <code class="at-card__image-tag">{{ agentType.dockerImage }}</code>
    </div>

    <footer class="at-card__footer">
      <time class="at-card__date" :datetime="agentType.createdAt">
        Created {{ formatDate(agentType.createdAt) }}
      </time>
      <div class="at-card__actions" @click.stop>
        <RouterLink
          class="at-card__btn at-card__btn--ghost"
          :to="{ name: 'AgentTypeHistory', params: { id: agentType.agentTypeId } }"
          title="Event history"
        >History</RouterLink>
        <RouterLink
          class="at-card__btn at-card__btn--primary"
          :to="{ name: 'AgentTypeEdit', params: { id: agentType.agentTypeId } }"
          title="Edit agent type"
        >Edit</RouterLink>
      </div>
    </footer>
  </article>
</template>

<script setup lang="ts">
import type { AgentTypeSummary } from '@/api/agentTypes'

defineProps<{ agentType: AgentTypeSummary }>()
defineEmits<{ click: [agentTypeId: string] }>()

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}
</script>

<style scoped>
.at-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1rem;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.at-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-color: #b0b0b0;
}

.at-card__header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.at-card__icon {
  font-size: 1rem;
  flex-shrink: 0;
}

.at-card__name {
  font-size: 1rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.at-card__version {
  font-size: 0.7rem;
  font-weight: 600;
  background: #e8eaf6;
  color: #3949ab;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  flex-shrink: 0;
}

.at-card__description {
  font-size: 0.875rem;
  color: #555;
  margin: 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.at-card__image {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  background: #f5f5f5;
  border-radius: 4px;
  padding: 0.3rem 0.6rem;
  overflow: hidden;
}

.at-card__image-icon { font-size: 0.85rem; flex-shrink: 0; }

.at-card__image-tag {
  font-size: 0.75rem;
  color: #555;
  font-family: 'IBM Plex Mono', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.at-card__footer {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: auto;
}

.at-card__date {
  font-size: 0.75rem;
  color: #999;
  flex: 1;
}

.at-card__actions {
  display: flex;
  gap: 0.35rem;
}

.at-card__btn {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.65rem;
  border-radius: 4px;
  text-decoration: none;
  transition: opacity 0.15s;
}
.at-card__btn:hover { opacity: 0.8; }
.at-card__btn--primary { background: #3949ab; color: #fff; }
.at-card__btn--ghost {
  background: transparent;
  color: #3949ab;
  border: 1px solid #c5cae9;
}
</style>
