<template>
  <component
    :is="label ? 'nav' : 'div'"
    class="pagination"
    :aria-label="label || undefined"
    :data-testid="testId('pagination')"
  >
    <slot name="start" />
    <span class="pagination-info" :data-testid="testId('page-label')">{{ info }}</span>

    <div class="pagination-pages">
      <template v-if="pageCount">
        <button
          v-for="page in pageCount"
          :key="page"
          class="btn btn-sm btn-icon"
          :class="currentPage === page ? 'btn-primary' : 'btn-secondary'"
          type="button"
          :aria-label="pageLabel ? pageLabel(page) : undefined"
          :aria-current="currentPage === page ? 'page' : undefined"
          @click="emit('select', page)"
        >
          {{ page }}
        </button>
      </template>

      <template v-else>
        <button
          class="btn btn-secondary btn-sm"
          :class="{ 'btn-icon': iconOnly }"
          type="button"
          :aria-label="iconOnly ? previousLabel : undefined"
          :disabled="previousDisabled"
          :data-testid="testId('previous-page')"
          @click="emit('previous')"
        >
          <IconGlyph name="chevronLeft" :size="16" />
          <span v-if="!iconOnly">{{ previousLabel }}</span>
        </button>
        <span v-if="status" class="pagination-status">{{ status }}</span>
        <button
          class="btn btn-secondary btn-sm"
          :class="{ 'btn-icon': iconOnly }"
          type="button"
          :aria-label="iconOnly ? nextLabel : undefined"
          :disabled="nextDisabled"
          :data-testid="testId('next-page')"
          @click="emit('next')"
        >
          <span v-if="!iconOnly">{{ nextLabel }}</span>
          <IconGlyph name="chevronRight" :size="16" />
        </button>
      </template>
    </div>
  </component>
</template>

<script setup lang="ts">
import IconGlyph from './IconGlyph.vue'

interface Props {
  /** Summary text, e.g. "Page 2 of 5". */
  info: string
  /** Accessible name; when set the bar is a `<nav>` landmark. */
  label?: string
  previousLabel?: string
  nextLabel?: string
  previousDisabled?: boolean
  nextDisabled?: boolean
  /** Chevron-only buttons; the labels become their accessible names. */
  iconOnly?: boolean
  /** Short text between the buttons (hidden on phones). */
  status?: string
  /** Numbered mode: one button per page (1-based) instead of previous / next; emits `select`. */
  pageCount?: number
  currentPage?: number
  pageLabel?: (page: number) => string
  /** Adds `<prefix>-pagination`, `-page-label`, `-previous-page` and `-next-page` test ids. */
  testIdPrefix?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: '',
  previousLabel: '',
  nextLabel: '',
  previousDisabled: false,
  nextDisabled: false,
  iconOnly: false,
  status: '',
  pageCount: 0,
  currentPage: 1,
  pageLabel: undefined,
  testIdPrefix: '',
})

const emit = defineEmits<{
  previous: []
  next: []
  select: [page: number]
}>()

function testId(part: string): string | undefined {
  return props.testIdPrefix ? `${props.testIdPrefix}-${part}` : undefined
}
</script>
