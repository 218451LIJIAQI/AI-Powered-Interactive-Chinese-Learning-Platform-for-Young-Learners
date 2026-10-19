<template>
  <form class="panel form-grid" @submit.prevent="emit('submit')">
    <div class="card-section-head">
      <div class="card-section-title">
        <div class="media-badge small" aria-hidden="true">
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
    <div class="form-actions">
      <button
        class="btn"
        :class="`btn-${submitVariant}`"
        type="submit"
        :disabled="submitDisabled"
        :aria-busy="submitting"
      >
        {{ currentSubmitLabel }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  title: string
  subtitle?: string
  icon?: string
  submitLabel: string
  submittingLabel: string
  submitting?: boolean
  disabled?: boolean
  /** Demote the submit button when a view already has a primary action (one primary per view). */
  submitVariant?: 'primary' | 'secondary'
}

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
  icon: 'dashboard',
  submitting: false,
  disabled: false,
  submitVariant: 'primary',
})

const emit = defineEmits<{
  submit: []
}>()

const currentSubmitLabel = computed(() => (props.submitting ? props.submittingLabel : props.submitLabel))
const submitDisabled = computed(() => props.submitting || props.disabled)
</script>
