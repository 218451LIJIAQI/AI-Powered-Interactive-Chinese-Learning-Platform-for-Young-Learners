<template>
  <form class="panel form-grid" @submit.prevent="$emit('submit')">
    <div class="card-section-head">
      <div class="card-section-title">
        <div class="media-badge small">
          <IconGlyph :name="icon" />
        </div>
        <div class="card-section-copy">
          <strong>{{ title }}</strong>
          <span v-if="subtitle" class="muted">{{ subtitle }}</span>
        </div>
      </div>
      <slot name="header-end" />
    </div>
    <slot />
    <button class="btn btn-primary" type="submit" :disabled="submitting || disabled">
      {{ submitting ? submittingLabel : submitLabel }}
    </button>
  </form>
</template>

<script setup lang="ts">
/**
 * Themed card wrapping a form inside a dashboard page. Standardises
 * the header (icon badge + title + optional subtitle), the body
 * slot, and the primary submit button with its `submitting` /
 * `disabled` states — keeping the look of every "add lesson",
 * "edit profile", "send notification" form consistent.
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  title: string
  subtitle?: string
  icon?: string
  submitLabel?: string
  submittingLabel?: string
  submitting?: boolean
  disabled?: boolean
}

withDefaults(defineProps<Props>(), {
  subtitle: '',
  icon: 'dashboard',
  submitLabel: 'Save',
  submittingLabel: 'Saving...',
  submitting: false,
  disabled: false,
})

defineEmits<{
  submit: []
}>()
</script>
