<template>
  <HintPanel
    variant="class"
    :text="text"
    :offered="offered"
    :loading="loading"
    :error="error"
    :footnote="t('classPlayer.hint.changeInAccount')"
    :report-context="context"
    :hint-key="hintId"
    :reported="reported"
    :own-toast="false"
    @show="emit('show')"
    @reported="onReported"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import HintPanel from '@/components/learning/HintPanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'

/**
 * Focus-mode class hint adapter (lesson matching and quizzes): delegates rendering and reporting to `HintPanel`,
 * while the parent owns the reported IDs and confirmation toast outside the conditionally mounted footer slot.
 */
const props = defineProps<{
  offered: boolean
  hintId: number | null
  text: string | null
  loading: boolean
  error: string | null
  context: string
  reportedIds: ReadonlySet<number>
}>()
const emit = defineEmits<{ show: []; reported: [hintId: number] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const reported = computed(() => props.hintId !== null && props.reportedIds.has(props.hintId))

function onReported(): void {
  if (props.hintId === null) return
  emit('reported', props.hintId)
}
</script>
