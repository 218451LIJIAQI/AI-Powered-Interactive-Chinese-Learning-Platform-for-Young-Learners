<template>
  <form class="panel form-grid" @submit.prevent="emit('submit')">
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
    <button class="btn btn-primary" type="submit" :disabled="submitDisabled">
      {{ currentSubmitLabel }}
    </button>
  </form>
</template>

<script setup lang="ts">
import { computed } from 'vue'

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

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
  icon: 'dashboard',
  submitLabel: 'Save',
  submittingLabel: 'Saving...',
  submitting: false,
  disabled: false,
})

const emit = defineEmits<{
  submit: []
}>()

const currentSubmitLabel = computed(() => (props.submitting ? props.submittingLabel : props.submitLabel))
const submitDisabled = computed(() => props.submitting || props.disabled)
</script>
