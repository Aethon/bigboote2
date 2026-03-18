<template>
  <div class="lights-on">
    <h1 class="lights-on__title">Bigboote</h1>
    <p class="lights-on__subtitle">Multi-agent coordination in progress</p>
    <p class="placeholder-note">Phase 19: EffortOrb · AgentActivityRing · SystemMetricsBar · LiveEventTicker</p>
  </div>
</template>

<script setup lang="ts">
// Phase 19: full-viewport demo surface — public route, no AppShell, no auth guard.
// Will render EffortOrb animated rings, SystemMetricsBar, and LiveEventTicker.
</script>

<style scoped>
.lights-on {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #0a0a1a;
  color: #e0e0ff;
  font-family: 'IBM Plex Mono', monospace;
  text-align: center;
}

.lights-on__title {
  font-size: 3rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  margin: 0 0 0.5rem;
}

.lights-on__subtitle {
  font-size: 1rem;
  color: #8888cc;
  margin: 0 0 2rem;
}

.placeholder-note {
  font-size: 0.8rem;
  color: #555;
  font-style: italic;
}
</style>
