<template>
  <div class="at-form-view">
    <header class="at-form-view__header">
      <RouterLink class="at-form-view__back" :to="{ name: 'AgentTypeList' }">‹ Agent Types</RouterLink>
      <h1 class="at-form-view__title">{{ isEdit ? 'Edit Agent Type' : 'New Agent Type' }}</h1>
    </header>

    <!-- Loading state for edit mode -->
    <div v-if="store.loading && isEdit" class="at-form-view__loading">Loading…</div>
    <div v-else-if="store.error" class="at-form-view__error">{{ store.error }}</div>

    <form v-else class="at-form" @submit.prevent="handleSubmit">

      <!-- Name — editable on create, locked on edit -->
      <div class="field">
        <label class="field__label" for="field-name">
          Name <span class="field__required">*</span>
        </label>
        <input
          id="field-name"
          v-model="form.name"
          class="field__input"
          :class="{ 'field__input--locked': isEdit }"
          type="text"
          required
          :disabled="isEdit"
          placeholder="e.g. researcher"
          pattern="[a-z0-9_-]+"
          title="Lowercase letters, digits, hyphens and underscores only"
        />
        <p v-if="isEdit" class="field__hint">Name cannot be changed after creation.</p>
        <p v-else class="field__hint">Lowercase letters, digits, hyphens, and underscores only.</p>
      </div>

      <!-- Description -->
      <div class="field">
        <label class="field__label" for="field-desc">Description</label>
        <textarea
          id="field-desc"
          v-model="form.description"
          class="field__input field__textarea"
          rows="4"
          placeholder="What does this agent do?"
        />
      </div>

      <!-- Docker image -->
      <div class="field">
        <label class="field__label" for="field-image">
          Docker Image <span class="field__required">*</span>
        </label>
        <input
          id="field-image"
          v-model="form.dockerImage"
          class="field__input field__mono"
          type="text"
          required
          placeholder="registry/image:tag"
        />
        <p class="field__hint">
          Full image reference including tag, e.g.
          <code>ghcr.io/acme/researcher:1.2.0</code>
        </p>
      </div>

      <!-- Error banner -->
      <div v-if="submitError" class="at-form__error">{{ submitError }}</div>

      <div class="at-form__actions">
        <RouterLink class="btn btn--secondary" :to="{ name: 'AgentTypeList' }">Cancel</RouterLink>
        <button v-if="isEdit" type="button" class="btn btn--ghost" @click="goToHistory">
          View History
        </button>
        <button type="submit" class="btn btn--primary" :disabled="submitting">
          {{ submitting ? 'Saving…' : (isEdit ? 'Save Changes' : 'Create Agent Type') }}
        </button>
      </div>

    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentTypeStore } from '@/stores/useAgentTypeStore'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const store = useAgentTypeStore()

const isEdit = computed(() => !!props.id)

const form = reactive({ name: '', description: '', dockerImage: '' })
const submitting = ref(false)
const submitError = ref<string | null>(null)

// ------------------------------------------------------------------ populate form on edit

function populateForm(): void {
  const at = store.currentAgentType
  if (at) {
    form.name = at.name
    form.description = at.description
    form.dockerImage = at.dockerImage
  }
}

onMounted(async () => {
  if (props.id) {
    await store.fetchAgentType(props.id)
    populateForm()
  }
})

// Re-populate if the store updates (e.g. after a save round-trip)
watch(() => store.currentAgentType, populateForm)

// ------------------------------------------------------------------ submit

async function handleSubmit(): Promise<void> {
  submitError.value = null
  submitting.value = true
  try {
    if (isEdit.value && props.id) {
      await store.update(props.id, {
        description: form.description.trim() || undefined,
        dockerImage: form.dockerImage.trim(),
      })
      // Stay on form after edit so the user sees the confirmed state.
    } else {
      const agentTypeId = await store.create({
        name: form.name.trim(),
        description: form.description.trim(),
        dockerImage: form.dockerImage.trim(),
      })
      router.push({ name: 'AgentTypeEdit', params: { id: agentTypeId } })
    }
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : 'Save failed'
  } finally {
    submitting.value = false
  }
}

function goToHistory(): void {
  if (props.id) {
    router.push({ name: 'AgentTypeHistory', params: { id: props.id } })
  }
}
</script>

<style scoped>
.at-form-view {
  padding: 1.5rem 2rem;
  max-width: 600px;
  margin: 0 auto;
}

.at-form-view__header {
  margin-bottom: 1.5rem;
}

.at-form-view__back {
  display: inline-block;
  font-size: 0.82rem;
  color: #7986cb;
  text-decoration: none;
  margin-bottom: 0.5rem;
}
.at-form-view__back:hover { color: #3949ab; }

.at-form-view__title {
  font-size: 1.4rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.at-form-view__loading,
.at-form-view__error {
  padding: 1.5rem;
  text-align: center;
  color: #888;
  font-style: italic;
}
.at-form-view__error { color: #f44336; font-style: normal; }

.at-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field__label {
  font-size: 0.875rem;
  font-weight: 600;
  color: #333;
}
.field__required { color: #f44336; }

.field__input {
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
}
.field__input:focus { border-color: #3949ab; }
.field__input:disabled,
.field__input--locked {
  background: #f5f5f5;
  color: #888;
  cursor: not-allowed;
}

.field__textarea { resize: vertical; min-height: 6rem; }

.field__mono { font-family: 'IBM Plex Mono', monospace; }

.field__hint {
  font-size: 0.75rem;
  color: #888;
  margin: 0;
}
.field__hint code {
  background: #f0f0f0;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  font-size: 0.72rem;
}

.at-form__error {
  background: #fce4ec;
  color: #b71c1c;
  padding: 0.6rem 0.85rem;
  border-radius: 6px;
  font-size: 0.875rem;
}

.at-form__actions {
  display: flex;
  gap: 0.75rem;
  padding-top: 0.25rem;
  align-items: center;
}

.btn {
  border: none;
  border-radius: 6px;
  padding: 0.5rem 1.25rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  transition: opacity 0.15s;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn--primary   { background: #3949ab; color: #fff; }
.btn--primary:hover:not(:disabled) { opacity: 0.88; }
.btn--secondary { background: #f0f0f0; color: #333; }
.btn--secondary:hover { background: #e0e0e0; }
.btn--ghost {
  background: transparent;
  color: #3949ab;
  border: 1px solid #c5cae9;
  margin-left: auto;
}
.btn--ghost:hover { background: #e8eaf6; }
</style>
