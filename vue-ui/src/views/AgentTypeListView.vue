<template>
  <div class="at-list">
    <header class="at-list__header">
      <h1 class="at-list__title">Agent Types</h1>
      <RouterLink class="btn btn--primary" :to="{ name: 'AgentTypeNew' }">+ New Agent Type</RouterLink>
    </header>

    <!-- Search / filter bar -->
    <div class="at-list__toolbar">
      <input
        v-model="searchQuery"
        class="at-list__search"
        type="search"
        placeholder="Filter by name or image…"
        aria-label="Search agent types"
      />
      <span class="at-list__count">{{ filteredAgentTypes.length }} type{{ filteredAgentTypes.length !== 1 ? 's' : '' }}</span>
    </div>

    <!-- Loading / error -->
    <div v-if="store.loading" class="at-list__state">Loading agent types…</div>
    <div v-else-if="store.error" class="at-list__state at-list__state--error">{{ store.error }}</div>

    <!-- Card grid -->
    <div v-else class="at-list__grid">
      <AgentTypeCard
        v-for="at in filteredAgentTypes"
        :key="at.agentTypeId"
        :agent-type="at"
        @click="navigateTo"
      />
      <div v-if="filteredAgentTypes.length === 0" class="at-list__empty">
        <template v-if="searchQuery">No agent types match "{{ searchQuery }}".</template>
        <template v-else>
          No agent types yet.
          <RouterLink :to="{ name: 'AgentTypeNew' }">Create the first one.</RouterLink>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentTypeStore } from '@/stores/useAgentTypeStore'
import AgentTypeCard from '@/components/AgentTypeCard.vue'

const store = useAgentTypeStore()
const router = useRouter()

const searchQuery = ref('')

const filteredAgentTypes = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return store.agentTypes
  return store.agentTypes.filter(
    (at) =>
      at.name.toLowerCase().includes(q) ||
      at.dockerImage.toLowerCase().includes(q) ||
      at.description.toLowerCase().includes(q),
  )
})

function navigateTo(agentTypeId: string): void {
  router.push({ name: 'AgentTypeEdit', params: { id: agentTypeId } })
}

onMounted(() => {
  store.fetchAgentTypes()
})
</script>

<style scoped>
.at-list {
  padding: 1.5rem 2rem;
  max-width: 960px;
  margin: 0 auto;
}

.at-list__header {
  display: flex;
  align-items: center;
  margin-bottom: 1.25rem;
}

.at-list__title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  flex: 1;
}

.at-list__toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.at-list__search {
  flex: 1;
  max-width: 360px;
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  padding: 0.45rem 0.75rem;
  font-size: 0.875rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
}
.at-list__search:focus { border-color: #3949ab; }

.at-list__count {
  font-size: 0.82rem;
  color: #999;
}

.at-list__state {
  padding: 2rem;
  text-align: center;
  color: #888;
  font-style: italic;
}
.at-list__state--error { color: #f44336; font-style: normal; }

.at-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.at-list__empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #aaa;
  padding: 2rem;
  font-style: italic;
}
.at-list__empty a { color: #3949ab; }

.btn {
  border: none;
  border-radius: 6px;
  padding: 0.5rem 1.25rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: opacity 0.15s;
  display: inline-flex;
  align-items: center;
}
.btn--primary   { background: #3949ab; color: #fff; }
.btn--primary:hover { opacity: 0.88; }
</style>
