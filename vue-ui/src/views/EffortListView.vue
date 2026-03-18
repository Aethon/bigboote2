<template>
  <div class="effort-list-view">
    <!-- Header -->
    <header class="effort-list-view__header">
      <h1 class="effort-list-view__title">Efforts</h1>
      <button class="btn btn--primary" @click="showCreateModal = true">+ New Effort</button>
    </header>

    <!-- Status filter tabs -->
    <div class="effort-list-view__filters" role="tablist" aria-label="Filter by status">
      <button
        v-for="f in FILTERS"
        :key="f.value"
        role="tab"
        class="filter-tab"
        :class="{ 'filter-tab--active': activeFilter === f.value }"
        :aria-selected="activeFilter === f.value"
        @click="activeFilter = f.value"
      >
        {{ f.label }}
        <span class="filter-tab__count">{{ countByStatus(f.value) }}</span>
      </button>
    </div>

    <!-- Loading / error states -->
    <div v-if="store.loading" class="effort-list-view__state">Loading efforts…</div>
    <div v-else-if="store.error" class="effort-list-view__state effort-list-view__state--error">
      {{ store.error }}
    </div>

    <!-- Cards grid -->
    <div v-else class="effort-list-view__grid">
      <EffortCard
        v-for="effort in filteredEfforts"
        :key="effort.effortId"
        :effort="effort"
        @click="navigateToEffort"
      />
      <div v-if="filteredEfforts.length === 0" class="effort-list-view__empty">
        No efforts match this filter.
      </div>
    </div>

    <!-- Create modal -->
    <CreateEffortModal
      v-if="showCreateModal"
      :on-submit="handleCreateEffort"
      @close="showCreateModal = false"
      @created="handleCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useEffortStore } from '@/stores/useEffortStore'
import type { EffortSummary } from '@/api/efforts'
import EffortCard from '@/components/EffortCard.vue'
import CreateEffortModal from '@/components/CreateEffortModal.vue'

const store = useEffortStore()
const router = useRouter()

type FilterValue = 'all' | EffortSummary['status']

const FILTERS: { value: FilterValue; label: string }[] = [
  { value: 'all',     label: 'All' },
  { value: 'created', label: 'Created' },
  { value: 'active',  label: 'Active' },
  { value: 'paused',  label: 'Paused' },
  { value: 'closed',  label: 'Closed' },
]

const activeFilter = ref<FilterValue>('all')
const showCreateModal = ref(false)

const filteredEfforts = computed(() => {
  if (activeFilter.value === 'all') return store.efforts
  return store.efforts.filter((e) => e.status === activeFilter.value)
})

function countByStatus(filter: FilterValue): number {
  if (filter === 'all') return store.efforts.length
  return store.efforts.filter((e) => e.status === filter).length
}

function navigateToEffort(effortId: string): void {
  router.push({ name: 'EffortWorkspace', params: { effortId } })
}

async function handleCreateEffort(req: Parameters<typeof store.createNewEffort>[0]): Promise<string> {
  return store.createNewEffort(req)
}

function handleCreated(effortId: string): void {
  router.push({ name: 'EffortWorkspace', params: { effortId } })
}

onMounted(() => {
  store.fetchEfforts()
})
</script>

<style scoped>
.effort-list-view {
  padding: 1.5rem 2rem;
  max-width: 960px;
  margin: 0 auto;
}

.effort-list-view__header {
  display: flex;
  align-items: center;
  margin-bottom: 1.25rem;
}

.effort-list-view__title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  flex: 1;
}

.effort-list-view__filters {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 1.25rem;
  border-bottom: 1px solid #e0e0e0;
  padding-bottom: 0;
}

.filter-tab {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.85rem;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  color: #777;
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -1px;
}
.filter-tab:hover { color: #333; }
.filter-tab--active {
  color: #3949ab;
  border-bottom-color: #3949ab;
  font-weight: 600;
}

.filter-tab__count {
  font-size: 0.72rem;
  background: #f0f0f0;
  color: #666;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  font-weight: 600;
}
.filter-tab--active .filter-tab__count {
  background: #e8eaf6;
  color: #3949ab;
}

.effort-list-view__state {
  padding: 2rem;
  text-align: center;
  color: #888;
  font-style: italic;
}
.effort-list-view__state--error { color: #f44336; font-style: normal; }

.effort-list-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.effort-list-view__empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #aaa;
  padding: 2rem;
  font-style: italic;
}

.btn {
  border: none;
  border-radius: 6px;
  padding: 0.5rem 1.25rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn--primary   { background: #3949ab; color: #fff; }
.btn--primary:hover { background: #283593; }
</style>
