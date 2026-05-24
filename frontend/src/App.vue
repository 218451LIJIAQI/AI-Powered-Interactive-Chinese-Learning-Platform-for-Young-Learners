<template>
  <RouterView />
</template>

<script setup lang="ts">
/**
 * Root application shell — intentionally minimal: it only mounts the
 * Vue Router outlet so every concrete page is a route. Persona-specific
 * chrome (top nav, side nav, auth gating) is implemented inside the
 * layout components that the router selects per-route via
 * {@code route.meta.layout}.
 *
 * Keeping the root empty also makes the auth-invalidation handler in
 * `main.ts` cheap — it can swap the active route without juggling
 * shared state in this component.
 */
import { RouterView } from 'vue-router'
</script>
