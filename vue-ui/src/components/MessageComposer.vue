<template>
  <form class="composer" @submit.prevent="handleSubmit">
    <textarea
      ref="textareaRef"
      v-model="draft"
      class="composer__input"
      :placeholder="placeholder"
      rows="1"
      :disabled="!connected"
      @keydown.enter.exact.prevent="handleSubmit"
      @input="autoResize"
    />
    <button
      type="submit"
      class="composer__send"
      :disabled="!connected || !draft.trim()"
      aria-label="Send message"
    >
      <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
        <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
      </svg>
    </button>
  </form>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

const props = defineProps<{
  convId: string
  connected: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  send: [convId: string, body: string]
}>()

const draft = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

function handleSubmit(): void {
  const body = draft.value.trim()
  if (!body || !props.connected) return
  emit('send', props.convId, body)
  draft.value = ''
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
}

function autoResize(): void {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}
</script>

<style scoped>
.composer {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-top: 1px solid #e0e0e0;
  background: #fff;
}

.composer__input {
  flex: 1;
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  font-family: inherit;
  font-size: 0.875rem;
  line-height: 1.4;
  resize: none;
  outline: none;
  transition: border-color 0.15s;
  overflow-y: auto;
  min-height: 2.25rem;
  max-height: 120px;
}
.composer__input:focus {
  border-color: #3949ab;
}
.composer__input:disabled {
  background: #f5f5f5;
  color: #aaa;
  cursor: not-allowed;
}

.composer__send {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: none;
  border-radius: 6px;
  background: #3949ab;
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s, opacity 0.15s;
}
.composer__send:hover:not(:disabled) {
  background: #283593;
}
.composer__send:disabled {
  background: #c5cae9;
  cursor: not-allowed;
}
</style>
