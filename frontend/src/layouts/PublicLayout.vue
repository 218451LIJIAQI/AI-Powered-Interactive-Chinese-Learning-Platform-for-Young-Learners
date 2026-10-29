<template>
  <div class="public-shell" :class="{ 'public-shell--auth': isAuthPage }">
    <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
    <PublicTopNav v-if="!isAuthPage" />
    <main id="main-content" class="page-stack public-shell-content" tabindex="-1">
      <RouterView />
    </main>
    <PublicFooter v-if="!isAuthPage" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'

import PublicTopNav from '@/components/layout/PublicTopNav.vue'
import PublicFooter from '@/components/public/PublicFooter.vue'
import '@/styles/ops-public.css'

/** The sign-in and recovery pages fill the window with their own split layout (AuthShell), without header and footer. */
const AUTH_PATHS: ReadonlySet<string> = new Set(['/auth', '/forgot-password', '/reset-password'])

const route = useRoute()
const { t } = useI18n()

const isAuthPage = computed<boolean>(() => AUTH_PATHS.has(route.path))
</script>
