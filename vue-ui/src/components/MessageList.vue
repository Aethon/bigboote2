<template>
  <div ref="listRef" class="message-list">
    <div v-if="messages.length === 0" class="message-list__empty">
      No messages yet — be the first to say hello!
    </div>
    <MessageBubble
      v-for="msg in messages"
      :key="msg.messageId"
      :message="msg"
      :current-user="currentUser"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { MessageResponse } from '@/api/conversations'
import MessageBubble from './MessageBubble.vue'

const props = defineProps<{
  messages: MessageResponse[]
  currentUser?: string
}>()

const listRef = ref<HTMLElement | null>(null)

/** Scroll to the bottom whenever the message list grows. */
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
)
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
}

.message-list__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #aaa;
  font-size: 0.875rem;
  font-style: italic;
}
</style>
