<template>
  <div class="public-shell" :class="shellClass">
    <PublicTopNav v-if="!isAuthPage" />
    <div id="main-content" class="page-stack public-shell-content">
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import PublicTopNav from '@/components/layout/PublicTopNav.vue'

const AUTH_PATHS: ReadonlySet<string> = new Set(['/auth', '/forgot-password', '/reset-password'])

const route = useRoute()

const isAuthPage = computed<boolean>(() => AUTH_PATHS.has(route.path))

const shellClass = computed(() => ({
  'public-shell--auth': isAuthPage.value,
  'public-shell--catalog': route.path.startsWith('/courses'),
  'public-shell--payment': route.path.startsWith('/payment'),
}))
</script>
