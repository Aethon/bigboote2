<template>
  <div class="view-placeholder">
    <h1>{{ id ? 'Edit Agent Type' : 'New Agent Type' }}</h1>
    <p class="placeholder-note">Phase 18: AgentTypeForm — create and edit; name locked on edit</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ id?: string }>()
// Phase 18: AgentTypeForm with create/update, name field locked on edit
</script>

<style scoped>
.view-placeholder { padding: 2rem; }
.placeholder-note { color: #888; font-style: italic; }
</style>
