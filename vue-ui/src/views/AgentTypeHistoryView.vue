<template>
  <div class="view-placeholder">
    <h1>Agent Type History: {{ id }}</h1>
    <p class="placeholder-note">Phase 18: EventStreamFeed reused for /agenttype:{id} stream</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ id: string }>()
// Phase 18: reuse EventStreamFeed with /agenttype:{id} SSE stream
</script>

<style scoped>
.view-placeholder { padding: 2rem; }
.placeholder-note { color: #888; font-style: italic; }
</style>
