<template>
  <div class="public-shell">
    <PublicTopNav />
    <div id="main-content" class="page-stack public-shell-content">
      <LandingPage />
    </div>
  </div>
</template>

<script setup lang="ts">
import PublicTopNav from '@/components/layout/PublicTopNav.vue'
import LandingPage from '@/pages/public/LandingPage.vue'
</script>
