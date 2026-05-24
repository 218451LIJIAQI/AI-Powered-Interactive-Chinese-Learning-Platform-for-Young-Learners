<template>
  <main class="system-page system-page--not-found" aria-label="Page not found">
    <span class="system-bg-orb system-bg-orb--one" />
    <span class="system-bg-orb system-bg-orb--two" />
    <div class="surface-card system-card">
      <div class="system-card-copy">
        <AppBrand />

        <div class="system-kicker">
          <IconGlyph name="search" :size="18" />
          <span>Route not found</span>
        </div>

        <h1>Oops, this lesson path is missing.</h1>
        <p>The page you opened does not exist in the current MandarinSpark frontend map.</p>

        <div class="system-action-row">
          <RouterLink class="btn btn-primary" to="/">Return home</RouterLink>
          <RouterLink class="btn btn-ghost" to="/courses">Explore courses</RouterLink>
        </div>

        <div class="system-link-grid">
          <RouterLink to="/auth">
            <strong>Sign in</strong>
            <span>Return to your learning workspace.</span>
          </RouterLink>
          <RouterLink to="/courses">
            <strong>Course catalog</strong>
            <span>Browse Mandarin lessons and packages.</span>
          </RouterLink>
        </div>
      </div>

      <div class="system-visual">
        <span class="system-code">404</span>
        <span class="system-cloud system-cloud--one" />
        <span class="system-cloud system-cloud--two" />
        <span class="system-pagoda" />
        <PandaIllustration class="system-panda" :size="220" outfit="#6f4cff" accent="#ffcc2f" />
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
/**
 * 404 — final catch-all route. Friendly copy + cross-links into the
 * three persona dashboards so a deep-linked URL that fails route
 * normalisation does not strand the user.
 */
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
</script>
