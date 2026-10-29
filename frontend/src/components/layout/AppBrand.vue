<template>
  <component :is="to ? RouterLink : 'div'" :to="to" class="brand app-brand" :class="{ 'is-compact': compact }">
    <!-- 火, "fire": the spark in the name, stamped like a seal. -->
    <span class="brand-seal seal" aria-hidden="true">火</span>
    <div class="brand-copy">
      <p class="brand-title">Mandarin<span class="accent">Spark</span></p>
      <p v-if="!compact" class="brand-subtitle">{{ t('chrome.tagline') }}</p>
    </div>
  </component>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

interface Props {
  compact?: boolean
  /** Makes the brand a link, e.g. home from the sign-in pages, which have no top navigation. */
  to?: string
}

withDefaults(defineProps<Props>(), {
  compact: false,
  to: undefined,
})

const { t } = useI18n()
</script>

<style scoped>
/* As a link, keep the click area to the brand itself rather than the full row. */
a.app-brand {
  width: fit-content;
  max-width: 100%;
  text-decoration: none;
}
</style>
