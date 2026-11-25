<template>
  <PracticeHint
    :hint="hint"
    :type="isListeningQuestion(question) ? 'LISTENING' : null"
    :options="question.options.map((option) => option.text)"
    :report-context="reportContext"
    :own-toast="false"
    @reported="emit('reported')"
  >
    <p v-if="question.answerKind === 'CHOICE'" class="focus-footer-note">
      {{ t('common.choiceKeysHint', { count: question.options.length }) }}
    </p>
  </PracticeHint>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import type { QuestionView } from '@/types/learn'

import { isListeningQuestion } from './listeningQuestion'
import PracticeHint from './practice/PracticeHint.vue'
import type { StuckHint } from './practice/useStuckHint'

defineProps<{ question: QuestionView; hint: StuckHint; reportContext: string }>()
const emit = defineEmits<{ reported: [] }>()
const { t } = useI18n({ useScope: 'global' })
</script>
