<template>
  <div class="workspace">
    <aside class="workspace__sidebar">
      <p class="placeholder-note">Phase 17: SidebarNav (channels · DMs · agents · docs)</p>
    </aside>
    <section class="workspace__center">
      <RouterView />
    </section>
    <aside class="workspace__right">
      <p class="placeholder-note">Phase 17: RightPanel (agents · events)</p>
    </aside>
  </div>
</template>

<script setup lang="ts">
defineProps<{ effortId: string }>()
// Phase 17: mount useEffortEventStream + useMessagingSocket, wire SSE/WS lifecycle
</script>

<style scoped>
.workspace {
  display: flex;
  height: 100%;
  overflow: hidden;
}
.workspace__sidebar {
  width: 240px;
  min-width: 240px;
  background: #16213e;
  color: #ccc;
  padding: 1rem;
  overflow-y: auto;
}
.workspace__center {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}
.workspace__right {
  width: 320px;
  min-width: 320px;
  background: #0f3460;
  color: #ccc;
  padding: 1rem;
  overflow-y: auto;
}
.placeholder-note { color: #888; font-style: italic; font-size: 0.85rem; }
</style>
