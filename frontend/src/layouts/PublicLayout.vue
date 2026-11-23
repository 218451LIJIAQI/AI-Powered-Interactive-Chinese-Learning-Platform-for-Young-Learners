<template>
  <div class="public-shell" :class="{ 'public-shell--auth': isAuthPage }">
    <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
    <PublicTopNav v-if="!isAuthPage" />
    <main id="main-content" class="page-stack public-shell-content" tabindex="-1">
      <RouterView />
    </main>
    <!-- Outside <main>, so it is the page's footer landmark. -->
    <PublicFooter v-if="route.meta.footer" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'

import PublicFooter from '@/components/layout/PublicFooter.vue'
import PublicTopNav from '@/components/layout/PublicTopNav.vue'
import '@/styles/ops-public.css'

const route = useRoute()
const { t } = useI18n()

/** The sign-in and recovery pages use the full-window AuthShell. */
const isAuthPage = computed<boolean>(() => Boolean(route.meta.authShell))
</script>
