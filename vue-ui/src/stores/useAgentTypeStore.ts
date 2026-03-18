import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listAgentTypes,
  getAgentType,
  createAgentType,
  updateAgentType,
  type AgentTypeSummary,
  type CreateAgentTypeRequest,
  type UpdateAgentTypeRequest,
} from '@/api/agentTypes'

/**
 * Pinia store for Agent Type admin state.
 *
 * Holds the full list of agent types and the currently selected detail.
 * Actions map 1-to-1 onto the agentTypes API; mutations to the list are
 * applied optimistically where safe, then confirmed by a full re-fetch.
 */
export const useAgentTypeStore = defineStore('agentTypes', () => {
  // ------------------------------------------------------------------ state

  const agentTypes = ref<AgentTypeSummary[]>([])
  const currentAgentType = ref<AgentTypeSummary | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // ------------------------------------------------------------------ actions

  async function fetchAgentTypes(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await listAgentTypes()
      agentTypes.value = res.agentTypes
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load agent types'
    } finally {
      loading.value = false
    }
  }

  async function fetchAgentType(agentTypeId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      currentAgentType.value = await getAgentType(agentTypeId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : `Failed to load agent type ${agentTypeId}`
    } finally {
      loading.value = false
    }
  }

  /**
   * Create a new agent type. Returns the newly created agentTypeId.
   * Re-fetches the list on success so the grid is always fresh.
   */
  async function create(req: CreateAgentTypeRequest): Promise<string> {
    const res = await createAgentType(req)
    await fetchAgentTypes()
    return res.agentTypeId
  }

  /**
   * Update an existing agent type. Re-fetches the current detail so form
   * fields reflect the server-confirmed state after save.
   */
  async function update(agentTypeId: string, req: UpdateAgentTypeRequest): Promise<void> {
    await updateAgentType(agentTypeId, req)
    await fetchAgentType(agentTypeId)
  }

  return {
    agentTypes,
    currentAgentType,
    loading,
    error,
    fetchAgentTypes,
    fetchAgentType,
    create,
    update,
  }
})
