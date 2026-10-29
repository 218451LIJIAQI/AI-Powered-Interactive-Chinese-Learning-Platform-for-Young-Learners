<template>
  <p class="plan-spark-reply" aria-live="polite">
    <span class="plan-spark-avatar" :title="t('plan.setup.sparkBot')" aria-hidden="true">
      <IconGlyph name="magic" :size="16" />
    </span>
    <span class="plan-spark-bubble">{{ text }}</span>
  </p>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'

/** A short friendly reply under a setup question (fixed copy for each choice; nothing here is generated). */
defineProps<{ text: string }>()

const { t } = useLocalMessages({ plan: planMessages })
</script>
