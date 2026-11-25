<template>
  <!-- One page needs no pager: no "Page 1 of 1" and no two disabled arrows; a list without tabs keeps its summary. -->
  <component
    :is="totalPages > 1 ? 'nav' : 'div'"
    v-if="totalPages > 1 || summary"
    class="pagination"
    :aria-label="totalPages > 1 ? label : undefined"
  >
    <span class="pagination-summary" role="status">
      <span class="pagination-info">{{ summary || pageText }}</span>
      <span v-if="summary && totalPages > 1" class="pagination-position">{{ pageText }}</span>
    </span>

    <div v-if="totalPages > 1" class="pagination-pages">
      <button
        class="btn btn-secondary btn-sm"
        :class="{ 'btn-icon': iconOnly }"
        type="button"
        :aria-label="iconOnly ? t('pagination.previous') : undefined"
        :title="iconOnly ? t('pagination.previous') : undefined"
        :aria-disabled="page <= 0 || undefined"
        @click="goTo(page - 1)"
      >
        <IconGlyph name="chevronLeft" :size="16" />
        <span v-if="!iconOnly">{{ t('pagination.previous') }}</span>
      </button>
      <button
        class="btn btn-secondary btn-sm"
        :class="{ 'btn-icon': iconOnly }"
        type="button"
        :aria-label="iconOnly ? t('pagination.next') : undefined"
        :title="iconOnly ? t('pagination.next') : undefined"
        :aria-disabled="page >= totalPages - 1 || undefined"
        @click="goTo(page + 1)"
      >
        <span v-if="!iconOnly">{{ t('pagination.next') }}</span>
        <IconGlyph name="chevronRight" :size="16" />
      </button>
    </div>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatNumber } from '@/utils/format'

import IconGlyph from './IconGlyph.vue'

interface Props {
  /** 0-based page shown, as the server counts them; the buttons emit `update:page` with the page to show. */
  page: number
  totalPages: number
  /** Optional caller-formatted item summary (e.g. "412 events") for a list without tab counts. */
  summary?: string
  /** Accessible name of the `<nav>` landmark when multiple pages are present. */
  label: string
  /** Chevron-only buttons; "Previous" / "Next" become their accessible names. */
  iconOnly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  summary: undefined,
  iconOnly: false,
})

const emit = defineEmits<{ 'update:page': [page: number] }>()

function goTo(nextPage: number): void {
  if (nextPage < 0 || nextPage >= props.totalPages) return
  emit('update:page', nextPage)
}

const { t, locale } = useI18n()
const pageText = computed(() =>
  t('pagination.pageOf', {
    page: formatNumber(props.page + 1, locale.value),
    total: formatNumber(props.totalPages, locale.value),
  }),
)
</script>
