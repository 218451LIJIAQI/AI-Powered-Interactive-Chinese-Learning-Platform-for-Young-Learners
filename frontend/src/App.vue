<template>
  <RouterView />
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, START_LOCATION, useRoute, useRouter } from 'vue-router'

import { applyDocumentTitle } from '@/router/pageTitle'

const { locale } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()

// FRLANG02: switching the interface language re-localises the browser tab title.
watch(locale, () => applyDocumentTitle(route))

// On a new page the next Tab starts in its content. A selected section tab keeps its place for further navigation.
// Initial load stays on the document start so the skip-to-main link remains the first Tab stop.
onBeforeUnmount(
  router.afterEach(async (to, from, failure) => {
    if (failure || from === START_LOCATION || to.path === from.path || to.hash) return
    const tab = document.activeElement?.closest<HTMLAnchorElement>('.route-tabs a[href]')
    const followsTab = tab?.pathname === to.path
    await nextTick()
    if (route.path !== to.path || route.hash || document.activeElement?.closest('[aria-modal="true"]')) return
    const main = document.getElementById('main-content')
    const target = followsTab ? main?.querySelector<HTMLElement>('.route-tab[aria-current="page"]') : null
    ;(target ?? main)?.focus({ preventScroll: true })
  }),
)
</script>
