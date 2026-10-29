<template>
  <header class="focus-topbar">
    <button
      type="button"
      class="btn btn-ghost btn-icon focus-topbar-close"
      :aria-label="closeLabel"
      data-testid="focus-close"
      @click="emit('close')"
    >
      <IconGlyph name="close" :size="22" />
    </button>
    <div v-if="progress !== null" class="focus-topbar-progress">
      <ProgressBar :progress="progress" :label="progressLabel || label" />
    </div>
    <span class="focus-topbar-label">{{ label }}</span>
  </header>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'

/**
 * The one top bar of focus-mode learning tasks (lesson player, mistakes review, setup, class activities, lesson
 * quiz): close, a progress bar and a short label. The page decides what close does (usually a "Leave?" dialog).
 */
interface Props {
  /** Short location label, e.g. "Mistakes review · 1 of 6" or "Lesson quiz · 8:20 left". */
  label: string
  /** Accessible name of the close button, e.g. "Leave the lesson". */
  closeLabel: string
  /** 0–100, or null for no bar. */
  progress?: number | null
  progressLabel?: string
}

withDefaults(defineProps<Props>(), { progress: null, progressLabel: '' })

const emit = defineEmits<{
  close: []
}>()
</script>
