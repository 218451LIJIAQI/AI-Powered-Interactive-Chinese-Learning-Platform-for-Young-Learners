<template>
  <div class="public-shell" :class="shellClass">
    <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
    <PublicTopNav v-if="!isAuthPage" />
    <div id="main-content" class="page-stack public-shell-content" tabindex="-1">
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'

import PublicTopNav from '@/components/layout/PublicTopNav.vue'

const AUTH_PATHS: ReadonlySet<string> = new Set(['/auth', '/forgot-password', '/reset-password'])

const route = useRoute()
const { t } = useI18n()

const isAuthPage = computed<boolean>(() => AUTH_PATHS.has(route.path))

const shellClass = computed(() => ({
  'public-shell--auth': isAuthPage.value,
}))
</script>
