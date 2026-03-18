import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  listEfforts,
  getEffort,
  createEffort,
  startEffort,
  pauseEffort,
  resumeEffort,
  closeEffort,
  type EffortSummary,
  type EffortDetail,
  type CreateEffortRequest,
} from '@/api/efforts'

/**
 * Pinia store for Effort state.
 *
 * Holds the list of efforts and the currently selected effort detail.
 * Exposes actions that delegate to the efforts API and update reactive state.
 *
 * Hydration is lazy — data is fetched on demand when the relevant view mounts.
 * Real-time updates will be applied via the SSE composable (Phase 17).
 *
 * See UX Design doc Section 3 and Phase 16 scaffold spec.
 */
export const useEffortStore = defineStore('efforts', () => {
  // ------------------------------------------------------------------ state
  const efforts = ref<EffortSummary[]>([])
  const currentEffort = ref<EffortDetail | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // ------------------------------------------------------------------ getters
  const effortById = computed(() => (id: string) =>
    efforts.value.find((e) => e.effortId === id) ?? null,
  )

  const activeEfforts = computed(() =>
    efforts.value.filter((e) => e.status === 'active'),
  )

  // ------------------------------------------------------------------ actions

  async function fetchEfforts(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await listEfforts()
      efforts.value = res.efforts
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load efforts'
    } finally {
      loading.value = false
    }
  }

  async function fetchEffort(effortId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      currentEffort.value = await getEffort(effortId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : `Failed to load effort ${effortId}`
    } finally {
      loading.value = false
    }
  }

  async function createNewEffort(req: CreateEffortRequest): Promise<string> {
    const res = await createEffort(req)
    await fetchEfforts()  // refresh list
    return res.effortId
  }

  async function start(effortId: string): Promise<void> {
    await startEffort(effortId)
    await fetchEffort(effortId)
  }

  async function pause(effortId: string): Promise<void> {
    await pauseEffort(effortId)
    await fetchEffort(effortId)
  }

  async function resume(effortId: string): Promise<void> {
    await resumeEffort(effortId)
    await fetchEffort(effortId)
  }

  async function close(effortId: string): Promise<void> {
    await closeEffort(effortId)
    await fetchEffort(effortId)
  }

  return {
    efforts,
    currentEffort,
    loading,
    error,
    effortById,
    activeEfforts,
    fetchEfforts,
    fetchEffort,
    createNewEffort,
    start,
    pause,
    resume,
    close,
  }
})
