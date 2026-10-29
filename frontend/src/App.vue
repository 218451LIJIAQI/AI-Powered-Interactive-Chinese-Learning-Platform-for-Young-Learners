<template>
  <RouterView />
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'

import { applyDocumentTitle } from '@/router/pageTitle'

const { locale } = useI18n({ useScope: 'global' })
const route = useRoute()

// FRLANG02: switching the interface language re-localises the browser tab title.
watch(locale, () => applyDocumentTitle(route))
</script>
