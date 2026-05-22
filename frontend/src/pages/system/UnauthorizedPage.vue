<template>
  <section class="system-page system-page--unauthorized">
    <span class="system-bg-orb system-bg-orb--one" />
    <span class="system-bg-orb system-bg-orb--two" />
    <div class="surface-card system-card">
      <div class="system-card-copy">
        <AppBrand />

        <div class="system-kicker">
          <IconGlyph name="safe" :size="18" />
          <span>Protected workspace</span>
        </div>

        <h1>This space needs the right role.</h1>
        <p>Sign in with the correct account or return to a learner-friendly area that matches your access.</p>

        <div class="system-action-row">
          <RouterLink class="btn btn-primary" to="/auth">Go to sign in</RouterLink>
          <RouterLink class="btn btn-ghost" to="/">Return home</RouterLink>
        </div>

        <div class="system-link-grid">
          <RouterLink to="/student/home">
            <strong>Student area</strong>
            <span>Lessons, rewards, and AI helper.</span>
          </RouterLink>
          <RouterLink to="/teacher/dashboard">
            <strong>Teacher area</strong>
            <span>Classes, content, and feedback.</span>
          </RouterLink>
        </div>
      </div>

      <div class="system-visual">
        <span class="system-code">403</span>
        <span class="system-cloud system-cloud--one" />
        <span class="system-cloud system-cloud--two" />
        <span class="system-pagoda" />
        <PandaIllustration class="system-panda" :size="220" outfit="#ff9f2f" accent="#6f4cff" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
</script>
