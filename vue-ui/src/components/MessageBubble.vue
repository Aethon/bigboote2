<template>
  <div class="bubble" :class="{ 'bubble--self': isSelf, 'bubble--agent': isAgent }">
    <header class="bubble__header">
      <span class="bubble__from">{{ message.from }}</span>
      <time class="bubble__time" :datetime="message.postedAt">{{ formatTime(message.postedAt) }}</time>
    </header>
    <p class="bubble__body">{{ message.body }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageResponse } from '@/api/conversations'

const props = defineProps<{
  message: MessageResponse
  /** The current user's collaborator name (e.g. "@alice"), used to right-align own messages. */
  currentUser?: string
}>()

const isSelf = computed(() => !!props.currentUser && props.message.from === props.currentUser)
const isAgent = computed(() => !props.message.from.startsWith('@'))

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.bubble {
  display: flex;
  flex-direction: column;
  max-width: 75%;
  gap: 0.2rem;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  background: #f0f0f0;
  align-self: flex-start;
  word-break: break-word;
}

.bubble--self {
  align-self: flex-end;
  background: #1a1a2e;
  color: #fff;
}

.bubble--agent {
  background: #e8f0fe;
  border-left: 3px solid #3949ab;
}

.bubble__header {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.bubble__from {
  font-size: 0.75rem;
  font-weight: 600;
  color: #3949ab;
}
.bubble--self .bubble__from {
  color: #90caf9;
}

.bubble__time {
  font-size: 0.7rem;
  color: #aaa;
  margin-left: auto;
}
.bubble--self .bubble__time {
  color: #ccc;
}

.bubble__body {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.45;
}
</style>
