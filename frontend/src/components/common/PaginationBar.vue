<template>
  <nav class="pagination" :aria-label="label" :data-testid="testId('pagination')">
    <span class="pagination-info" :data-testid="testId('page-label')">{{ infoText }}</span>

    <div class="pagination-pages">
      <button
        class="btn btn-secondary btn-sm"
        :class="{ 'btn-icon': iconOnly }"
        type="button"
        :aria-label="iconOnly ? previousText : undefined"
        :disabled="currentPage <= 1"
        :data-testid="testId('previous-page')"
        @click="emit('update:currentPage', currentPage - 1)"
      >
        <IconGlyph name="chevronLeft" :size="16" />
        <span v-if="!iconOnly">{{ previousText }}</span>
      </button>
      <button
        class="btn btn-secondary btn-sm"
        :class="{ 'btn-icon': iconOnly }"
        type="button"
        :aria-label="iconOnly ? nextText : undefined"
        :disabled="currentPage >= totalPages"
        :data-testid="testId('next-page')"
        @click="emit('update:currentPage', currentPage + 1)"
      >
        <span v-if="!iconOnly">{{ nextText }}</span>
        <IconGlyph name="chevronRight" :size="16" />
      </button>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { paginationMessages } from '@/plugins/paginationMessages'

import IconGlyph from './IconGlyph.vue'

interface Props {
  /** 1-based page shown; the buttons emit `update:currentPage` with the page to show. */
  currentPage: number
  totalPages: number
  /** Summary text; defaults to "Page {currentPage} of {totalPages}". */
  info?: string
  /** Accessible name of the `<nav>` landmark. */
  label: string
  /** Chevron-only buttons; "Previous" / "Next" become their accessible names. */
  iconOnly?: boolean
  /** Adds `<prefix>-pagination`, `-page-label`, `-previous-page` and `-next-page` test ids. */
  testIdPrefix?: string
}

const props = withDefaults(defineProps<Props>(), {
  info: '',
  iconOnly: false,
  testIdPrefix: '',
})

const emit = defineEmits<{ 'update:currentPage': [page: number] }>()

const { t } = useLocalMessages({ pagination: paginationMessages })
const infoText = computed(
  () => props.info || t('pagination.pageOf', { page: props.currentPage, total: props.totalPages }),
)
const previousText = computed(() => t('pagination.previous'))
const nextText = computed(() => t('pagination.next'))

function testId(part: string): string | undefined {
  return props.testIdPrefix ? `${props.testIdPrefix}-${part}` : undefined
}
</script>
