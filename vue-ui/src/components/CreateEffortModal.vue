<template>
  <div class="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="modal-title" @click.self="$emit('close')">
    <div class="modal">
      <header class="modal__header">
        <h2 id="modal-title" class="modal__title">New Effort</h2>
        <button class="modal__close" aria-label="Close" @click="$emit('close')">✕</button>
      </header>

      <form class="modal__body" @submit.prevent="handleSubmit">
        <!-- Name -->
        <label class="field">
          <span class="field__label">Name <span class="field__required">*</span></span>
          <input v-model="form.name" class="field__input" type="text" required placeholder="e.g. Q3 pricing rollout" />
        </label>

        <!-- Goal -->
        <label class="field">
          <span class="field__label">Goal <span class="field__required">*</span></span>
          <textarea
            v-model="form.goal"
            class="field__input field__textarea"
            required
            rows="3"
            placeholder="Describe what this effort should accomplish"
          />
        </label>

        <!-- Lead -->
        <label class="field">
          <span class="field__label">Lead <span class="field__required">*</span></span>
          <input
            v-model="form.leadName"
            class="field__input"
            type="text"
            required
            placeholder="@alice"
            pattern="@\S+"
            title="Must start with @"
          />
        </label>

        <!-- Collaborators (simple tag-list entry) -->
        <div class="field">
          <span class="field__label">Collaborators</span>
          <div class="tag-row">
            <span
              v-for="(c, i) in collaboratorTags"
              :key="i"
              class="tag"
              :class="c.type === 'AGENT' ? 'tag--agent' : 'tag--human'"
            >
              {{ c.name }}
              <button type="button" class="tag__remove" @click="removeCollaborator(i)">✕</button>
            </span>
            <input
              v-model="collabInput"
              class="field__input tag-input"
              type="text"
              placeholder="@name or #agentTypeId"
              @keydown.enter.prevent="addCollaborator"
              @keydown.comma.prevent="addCollaborator"
            />
          </div>
          <p class="field__hint">
            Enter <code>@name</code> for humans, <code>#agentTypeId</code> for agents. Press Enter or comma to add.
          </p>
        </div>

        <p v-if="errorMsg" class="modal__error">{{ errorMsg }}</p>

        <footer class="modal__footer">
          <button type="button" class="btn btn--secondary" @click="$emit('close')">Cancel</button>
          <button type="submit" class="btn btn--primary" :disabled="submitting">
            {{ submitting ? 'Creating…' : 'Create Effort' }}
          </button>
        </footer>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { CollaboratorSpec, CreateEffortRequest } from '@/api/efforts'

const emit = defineEmits<{ close: []; created: [effortId: string] }>()

const props = defineProps<{
  onSubmit: (req: CreateEffortRequest) => Promise<string>
}>()

const form = reactive({ name: '', goal: '', leadName: '@' })
const collabInput = ref('')
const collaboratorTags = ref<CollaboratorSpec[]>([])
const submitting = ref(false)
const errorMsg = ref<string | null>(null)

function addCollaborator(): void {
  const raw = collabInput.value.trim()
  if (!raw) return
  if (raw.startsWith('@')) {
    collaboratorTags.value.push({ name: raw, type: 'HUMAN' })
  } else if (raw.startsWith('#')) {
    const agentTypeId = raw.slice(1)
    collaboratorTags.value.push({ name: raw, type: 'AGENT', agentTypeId })
  }
  collabInput.value = ''
}

function removeCollaborator(i: number): void {
  collaboratorTags.value.splice(i, 1)
}

async function handleSubmit(): Promise<void> {
  errorMsg.value = null
  submitting.value = true
  try {
    // Flush any pending collaborator input.
    if (collabInput.value.trim()) addCollaborator()

    const req: CreateEffortRequest = {
      name: form.name.trim(),
      goal: form.goal.trim(),
      leadName: form.leadName.trim(),
      collaborators: collaboratorTags.value,
    }
    const effortId = await props.onSubmit(req)
    emit('created', effortId)
    emit('close')
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : 'Failed to create effort'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: #fff;
  border-radius: 10px;
  width: 100%;
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.28);
}

.modal__header {
  display: flex;
  align-items: center;
  padding: 1.25rem 1.5rem 1rem;
  border-bottom: 1px solid #e0e0e0;
}

.modal__title {
  font-size: 1.1rem;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  flex: 1;
}

.modal__close {
  background: none;
  border: none;
  font-size: 1rem;
  cursor: pointer;
  color: #888;
  padding: 0.25rem;
}
.modal__close:hover { color: #333; }

.modal__body {
  padding: 1.25rem 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.field__label {
  font-size: 0.83rem;
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

.field__textarea { resize: vertical; min-height: 5rem; }

.field__hint {
  font-size: 0.75rem;
  color: #888;
  margin: 0;
}
.field__hint code {
  background: #f0f0f0;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  padding: 0.35rem 0.5rem;
  min-height: 2.5rem;
  align-items: center;
}
.tag-input {
  border: none;
  outline: none;
  padding: 0.15rem 0.25rem;
  flex: 1;
  min-width: 8rem;
  font-size: 0.875rem;
}

.tag {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 500;
}
.tag--human { background: #e8f0fe; color: #283593; }
.tag--agent { background: #e8f5e9; color: #2e7d32; }

.tag__remove {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 0.65rem;
  color: inherit;
  opacity: 0.6;
  padding: 0;
  line-height: 1;
}
.tag__remove:hover { opacity: 1; }

.modal__error {
  color: #f44336;
  font-size: 0.83rem;
  margin: 0;
  background: #fce4ec;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
}

.modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 0.25rem;
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
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn--primary   { background: #3949ab; color: #fff; }
.btn--primary:hover:not(:disabled) { background: #283593; }
.btn--secondary { background: #f0f0f0; color: #333; }
.btn--secondary:hover { background: #e0e0e0; }
</style>
