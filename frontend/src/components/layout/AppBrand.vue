<template>
  <div class="brand app-brand" :class="{ 'is-compact': compact }">
    <div class="brand-copy">
      <h2 class="brand-title">
        Mandarin<span class="accent">Spark</span><IconGlyph name="star" :size="15" class="brand-star" />
      </h2>
      <p v-if="!compact" class="brand-subtitle">{{ t('chrome.tagline') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  compact?: boolean
}

withDefaults(defineProps<Props>(), {
  compact: false,
})

const { t } = useI18n()
</script>
