<template>
  <span class="tag is-outline" data-testid="ai-result-label">
    <IconGlyph name="ai" :size="14" />
    {{ short ? t('aiSafety.aiShort') : t('aiSafety.aiAssisted') }}
  </span>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'

/** Marks AI output. `short` is the "AI" tag of the AI Helper. */
defineProps<{ short?: boolean }>()

const { t } = useLocalMessages({ aiSafety: aiSafetyMessages })
</script>
