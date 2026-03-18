<template>
  <div class="view-placeholder">
    <h2>Documents</h2>
    <p class="placeholder-note">Phase 17: DocumentPane — list + view + upload</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ effortId?: string }>()
// Phase 17: load document list, render DocumentCard, wire create/update/delete
</script>

<style scoped>
.view-placeholder { padding: 1.5rem; }
.placeholder-note { color: #888; font-style: italic; }
</style>
