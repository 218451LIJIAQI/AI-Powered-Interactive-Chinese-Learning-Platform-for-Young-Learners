<template>
  <div class="public-shell" :class="shellClass">
    <PublicTopNav v-if="!isAuthPage" />
    <div id="main-content" class="page-stack public-shell-content">
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Unauthenticated layout — wraps the landing, catalogue, auth and
 * payment pages.
 *
 * Hides the top nav on the auth pages so the sign-in / forgot /
 * reset chrome owns the viewport, and applies route-aware
 * variant classes (`public-shell--auth`, `--catalog`, `--payment`)
 * for surface-specific theming.
 */
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import PublicTopNav from '@/components/layout/PublicTopNav.vue'

const route = useRoute()
const isAuthPage = computed(() => route.path === '/auth' || route.path === '/forgot-password' || route.path === '/reset-password')
const isCatalogPage = computed(() => route.path.startsWith('/courses'))
const isPaymentPage = computed(() => route.path.startsWith('/payment'))
const shellClass = computed(() => ({
  'public-shell--auth': isAuthPage.value,
  'public-shell--catalog': isCatalogPage.value,
  'public-shell--payment': isPaymentPage.value,
}))
</script>
