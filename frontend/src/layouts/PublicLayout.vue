<template>
  <div class="public-shell" :class="shellClass">
    <PublicTopNav v-if="!isAuthPage" />
    <div class="page-stack public-shell-content">
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
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
