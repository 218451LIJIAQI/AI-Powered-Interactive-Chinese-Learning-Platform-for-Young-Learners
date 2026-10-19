<template>
  <nav v-if="visibleItems.length" class="learning-breadcrumb" :aria-label="t('learningContext.breadcrumb')">
    <ol class="breadcrumb">
      <li v-for="(item, index) in visibleItems" :key="`${item.kind}-${index}`" data-testid="learning-breadcrumb-item">
        <span v-if="kindLabel(item.kind)" class="learning-breadcrumb-kind">{{ kindLabel(item.kind) }}</span>
        <RouterLink v-if="item.to && index < visibleItems.length - 1" :to="item.to" class="learning-breadcrumb-link">
          <CjkText :text="item.label" />
        </RouterLink>
        <span v-else :aria-current="index === visibleItems.length - 1 ? 'page' : undefined">
          <CjkText :text="item.label" />
        </span>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import CjkText from '@/components/learning/CjkText.vue'
import { learningContextMessages } from '@/plugins/learningContextMessages'

type BreadcrumbKind = 'section' | 'course' | 'module' | 'lesson' | 'quiz' | 'activity'

export interface BreadcrumbItem {
  kind: BreadcrumbKind
  label?: string | null
  to?: RouteLocationRaw
}

/**
 * FR-LRN-02: shows the learner where the current lesson, quiz or practice activity sits
 * (Course / Module / Lesson / ...). Items without a label are skipped; the last item is the current page.
 */
const props = defineProps<{ items: BreadcrumbItem[] }>()

const { t } = useI18n({
  messages: {
    en: { learningContext: learningContextMessages.en },
    ms: { learningContext: learningContextMessages.ms },
    'zh-CN': { learningContext: learningContextMessages['zh-CN'] },
  },
})

const visibleItems = computed(() =>
  props.items
    .filter((item) => Boolean(item.label?.trim()))
    .map((item) => ({ ...item, label: (item.label ?? '').trim() })),
)

function kindLabel(kind: BreadcrumbKind): string {
  if (kind === 'section') return ''
  return t(`learningContext.${kind}`)
}
</script>

<style scoped>
.learning-breadcrumb li {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-1);
  min-width: 0;
  overflow-wrap: anywhere;
}

.learning-breadcrumb-kind {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}
</style>
