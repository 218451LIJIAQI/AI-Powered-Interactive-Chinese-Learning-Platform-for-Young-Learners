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
import IconGlyph from '@/components/common/IconGlyph.vue'

withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    icon?: string
    submitLabel?: string
    submittingLabel?: string
    submitting?: boolean
    disabled?: boolean
  }>(),
  {
    subtitle: '',
    icon: 'dashboard',
    submitLabel: 'Save',
    submittingLabel: 'Saving...',
    submitting: false,
    disabled: false,
  },
)

defineEmits<{
  submit: []
}>()
</script>
