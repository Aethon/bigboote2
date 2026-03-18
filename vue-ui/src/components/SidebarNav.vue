<template>
  <nav class="sidebar-nav">
    <!-- Effort name / back link -->
    <div class="sidebar-nav__effort-header">
      <RouterLink class="sidebar-nav__back" :to="{ name: 'EffortList' }" title="Back to efforts">
        ‹ All Efforts
      </RouterLink>
      <h2 class="sidebar-nav__effort-name">{{ effortName }}</h2>
    </div>

    <!-- Channels -->
    <section class="sidebar-nav__section">
      <h3 class="sidebar-nav__section-title">Channels</h3>
      <ul class="sidebar-nav__list">
        <li v-for="conv in channelConvs" :key="conv.convId">
          <RouterLink
            class="sidebar-nav__item"
            :class="{ 'sidebar-nav__item--active': isActive(conv.convId) }"
            :to="{ name: 'EffortChannel', params: { effortId, convName: channelName(conv.convId) } }"
          >
            <span class="sidebar-nav__icon">#</span>
            {{ channelName(conv.convId) }}
            <span v-if="unread(conv.convId)" class="sidebar-nav__badge">{{ unread(conv.convId) }}</span>
          </RouterLink>
        </li>
      </ul>
    </section>

    <!-- Direct Messages -->
    <section class="sidebar-nav__section">
      <h3 class="sidebar-nav__section-title">Direct Messages</h3>
      <ul class="sidebar-nav__list">
        <li v-for="conv in dmConvs" :key="conv.convId">
          <RouterLink
            class="sidebar-nav__item"
            :class="{ 'sidebar-nav__item--active': isActive(conv.convId) }"
            :to="{ name: 'EffortDm', params: { effortId, collaborator: dmLabel(conv.convId) } }"
          >
            <span class="sidebar-nav__icon">@</span>
            {{ dmLabel(conv.convId) }}
            <span v-if="unread(conv.convId)" class="sidebar-nav__badge">{{ unread(conv.convId) }}</span>
          </RouterLink>
        </li>
        <li v-if="dmConvs.length === 0" class="sidebar-nav__empty">No DMs yet</li>
      </ul>
    </section>

    <!-- Documents link -->
    <section class="sidebar-nav__section">
      <h3 class="sidebar-nav__section-title">Docs</h3>
      <ul class="sidebar-nav__list">
        <li>
          <RouterLink
            class="sidebar-nav__item"
            :class="{ 'sidebar-nav__item--active': route.name === 'EffortDocuments' }"
            :to="{ name: 'EffortDocuments', params: { effortId } }"
          >
            <span class="sidebar-nav__icon">📄</span>
            Documents
          </RouterLink>
        </li>
      </ul>
    </section>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { ConversationSummary } from '@/api/conversations'

const props = defineProps<{
  effortId: string
  effortName: string
  conversations: ConversationSummary[]
  unreadByConvId: Record<string, number>
}>()

const route = useRoute()

const channelConvs = computed(() =>
  props.conversations.filter((c) => c.convId.includes(':#')),
)

const dmConvs = computed(() =>
  props.conversations.filter((c) => !c.convId.includes(':#')),
)

function channelName(convId: string): string {
  // "conv:#general" → "general"
  return convId.replace(/^conv:#/, '')
}

function dmLabel(convId: string): string {
  // "conv:@alice+@dev" → "alice+dev"
  return convId
    .replace(/^conv:/, '')
    .split('+')
    .map((s) => s.replace(/^@/, ''))
    .join('+')
}

function unread(convId: string): number {
  return props.unreadByConvId[convId] ?? 0
}

function isActive(convId: string): boolean {
  // Match against current route convName or collaborator param.
  const name = channelName(convId)
  const dm = dmLabel(convId)
  return route.params.convName === name || route.params.collaborator === dm
}
</script>

<style scoped>
.sidebar-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow-y: auto;
}

.sidebar-nav__effort-header {
  padding: 0.75rem 1rem 0.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 0.5rem;
}

.sidebar-nav__back {
  font-size: 0.75rem;
  color: #8899bb;
  text-decoration: none;
  display: block;
  margin-bottom: 0.3rem;
}
.sidebar-nav__back:hover { color: #fff; }

.sidebar-nav__effort-name {
  font-size: 0.9rem;
  font-weight: 700;
  color: #e0e0e0;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-nav__section {
  padding: 0 0.5rem 0.5rem;
}

.sidebar-nav__section-title {
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #607d8b;
  padding: 0.4rem 0.5rem 0.25rem;
  margin: 0;
}

.sidebar-nav__list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.sidebar-nav__item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.5rem;
  border-radius: 4px;
  font-size: 0.83rem;
  color: #99aabb;
  text-decoration: none;
  transition: background 0.12s, color 0.12s;
  cursor: pointer;
}
.sidebar-nav__item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #e0e0e0;
}
.sidebar-nav__item--active {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-weight: 600;
}

.sidebar-nav__icon {
  font-size: 0.75rem;
  flex-shrink: 0;
  width: 1rem;
  text-align: center;
  opacity: 0.7;
}

.sidebar-nav__badge {
  margin-left: auto;
  background: #f44336;
  color: #fff;
  font-size: 0.65rem;
  font-weight: 700;
  padding: 0.1rem 0.35rem;
  border-radius: 999px;
  min-width: 1.1rem;
  text-align: center;
}

.sidebar-nav__empty {
  font-size: 0.78rem;
  color: #546e7a;
  padding: 0.2rem 0.5rem;
  font-style: italic;
}
</style>
