<template>
  <div class="view-placeholder">
    <h2>Conversation: {{ convName ?? collaborator }}</h2>
    <p class="placeholder-note">Phase 17: MessageList + MessageBubble + MessageComposer</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ convName?: string; collaborator?: string }>()
// Phase 17: load conversation messages, wire MessageComposer to useMessagingSocket
</script>

<style scoped>
.view-placeholder { padding: 1.5rem; }
.placeholder-note { color: #888; font-style: italic; }
</style>
