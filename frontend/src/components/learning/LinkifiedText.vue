<template>
  <template v-for="(run, index) in runs" :key="index">
    <a v-if="run.href" class="lesson-link" :href="run.href" target="_blank" rel="noopener noreferrer">
      {{ run.text }}<span class="sr-only"> {{ newTabLabel }}</span>
    </a>
    <CjkText v-else :text="run.text" />
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import CjkText from '@/components/learning/CjkText.vue'
import { learningContextMessages } from '@/plugins/learningContextMessages'
import { splitLinkRuns } from '@/utils/linkify'

/**
 * Lesson text with plain-text http(s) URLs turned into safe external links (FR-CONT-05).
 * Uses text interpolation only (no v-html), so teacher-typed markup is never executed.
 */
const props = withDefaults(defineProps<{ text?: string | null }>(), { text: '' })

const { t } = useI18n({
  messages: {
    en: { learningContext: learningContextMessages.en },
    ms: { learningContext: learningContextMessages.ms },
    'zh-CN': { learningContext: learningContextMessages['zh-CN'] },
  },
})

const runs = computed(() => splitLinkRuns(props.text))
const newTabLabel = computed(() => t('learningContext.opensInNewTab'))
</script>

<style scoped>
.lesson-link {
  color: var(--text-link);
  font-weight: var(--font-weight-semibold);
  text-decoration: underline;
  text-underline-offset: 2px;
  overflow-wrap: anywhere;
}

.lesson-link:hover {
  color: var(--text-link-hover);
}
</style>
