<template>
  <nav class="app-nav">
    <div class="app-nav__logo">Bigboote</div>
    <ul class="app-nav__links">
      <li>
        <RouterLink to="/efforts" active-class="app-nav__link--active">
          Efforts
        </RouterLink>
      </li>
      <li>
        <RouterLink to="/agent-types" active-class="app-nav__link--active">
          Agent Types
        </RouterLink>
      </li>
      <li>
        <RouterLink to="/overview" active-class="app-nav__link--active">
          Overview
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>

<script setup lang="ts">
// AppNav renders the top-level navigation links.
// Active route highlighting is handled by Vue Router's active-class.
</script>

<style scoped>
.app-nav {
  display: flex;
  flex-direction: column;
  width: 200px;
  min-width: 200px;
  background: #1a1a2e;
  color: #e0e0e0;
  padding: 1rem 0;
}

.app-nav__logo {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 1.25rem;
  font-weight: 700;
  padding: 0.5rem 1rem 1.5rem;
  color: #fff;
  letter-spacing: 0.05em;
}

.app-nav__links {
  list-style: none;
  margin: 0;
  padding: 0;
}

.app-nav__links li a {
  display: block;
  padding: 0.6rem 1rem;
  color: #b0b0c0;
  text-decoration: none;
  font-size: 0.9rem;
  transition: background 0.15s, color 0.15s;
}

.app-nav__links li a:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.app-nav__link--active {
  background: rgba(255, 255, 255, 0.1) !important;
  color: #fff !important;
  font-weight: 600;
}
</style>
