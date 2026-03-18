<template>
  <div class="app-shell">
    <AppNav />
    <main class="app-shell__content">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import AppNav from './AppNav.vue'
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  font-family: 'IBM Plex Sans', system-ui, sans-serif;
}

.app-shell__content {
  flex: 1;
  overflow: auto;
}
</style>
