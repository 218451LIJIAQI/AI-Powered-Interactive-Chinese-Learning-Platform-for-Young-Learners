<template>
  <div class="practice-player listening-player">
    <div class="listening-audio">
      <audio
        v-if="safeAudioUrl"
        controls
        preload="metadata"
        :src="safeAudioUrl"
        data-testid="listening-audio"
        :aria-label="t('studentPractice.listeningAudio')"
      />
      <p v-else class="practice-note is-warning">{{ t('studentPractice.listeningNoAudio') }}</p>
    </div>

    <p v-if="exercise.prompt" class="listening-prompt" :lang="cjkLang(exercise.prompt)">{{ exercise.prompt }}</p>

    <div v-if="exercise.freeResponse" class="field listening-free">
      <label :for="`${baseId}-free`">{{ t('studentPractice.listeningFreeLabel') }}</label>
      <textarea
        :id="`${baseId}-free`"
        v-model="responseText"
        class="textarea"
        rows="4"
        maxlength="2000"
        data-testid="listening-answer"
        :disabled="locked"
        :aria-describedby="`${baseId}-free-help`"
      />
      <small :id="`${baseId}-free-help`" class="helper-text">{{ t('studentPractice.listeningFreeHelp') }}</small>
    </div>

    <p v-else-if="!exercise.questions.length" class="practice-note">{{ t('studentPractice.listeningNoQuestions') }}</p>

    <ol v-else class="listening-questions">
      <li v-for="question in exercise.questions" :key="question.index" class="listening-question">
        <p :id="questionId(question.index)" class="listening-question-text">
          <span class="practice-section-title">{{
            t('studentPractice.questionNumber', { number: question.index + 1 })
          }}</span>
          <span :lang="cjkLang(question.questionText)">{{ question.questionText }}</span>
        </p>

        <div
          v-if="question.options.length"
          class="listening-options"
          role="group"
          :aria-labelledby="questionId(question.index)"
        >
          <button
            v-for="(option, optionIndex) in question.options"
            :key="optionIndex"
            type="button"
            class="practice-choice"
            data-testid="listening-answer"
            :aria-pressed="answers[question.index] === option"
            :disabled="locked"
            @click="choose(question.index, option)"
          >
            <span :class="{ 'cjk-text practice-cjk': cjkLang(option) }" :lang="cjkLang(option)">{{ option }}</span>
          </button>
        </div>

        <div v-else class="field">
          <label :for="`${questionId(question.index)}-input`">{{ t('studentPractice.listeningAnswerLabel') }}</label>
          <input
            :id="`${questionId(question.index)}-input`"
            class="input"
            type="text"
            maxlength="200"
            autocomplete="off"
            data-testid="listening-answer"
            :value="answers[question.index] ?? ''"
            :placeholder="t('studentPractice.listeningAnswerPlaceholder')"
            :disabled="locked"
            @input="typeAnswer(question.index, $event)"
          />
        </div>
      </li>
    </ol>

    <div class="practice-submit-row">
      <span v-if="!exercise.freeResponse" class="practice-progress">{{
        t('studentPractice.progressAnswered', { done: answeredCount, total: exercise.questions.length })
      }}</span>
      <span v-else />
      <button
        type="button"
        class="btn btn-primary"
        data-testid="activity-submit"
        :disabled="!ready || locked || submitting"
        :aria-describedby="ready ? undefined : `${baseId}-hint`"
        @click="submit"
      >
        {{ submitting ? t('studentPractice.submitting') : t('studentPractice.submitAnswers') }}
      </button>
    </div>
    <p v-if="!ready && !locked" :id="`${baseId}-hint`" class="practice-progress">
      {{ t('studentPractice.submitHint') }}
    </p>

    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import type { StudentListeningExerciseResponse } from '@/types/api'
import {
  buildListeningCompletion,
  cjkLang,
  isListeningReady,
  type ActivitySubmission,
  type ListeningAnswers,
} from '@/utils/activityCompletion'
import { safeHttpUrl } from '@/utils/url'

import { usePracticeI18n } from './usePracticeI18n'

const props = defineProps<{
  exercise: StudentListeningExerciseResponse
  locked?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ActivitySubmission] }>()

const { t } = usePracticeI18n()
const baseId = `listening-${useId()}`
const answers = ref<ListeningAnswers>({})
const responseText = ref('')
const announcement = ref('')

const safeAudioUrl = computed(() => safeHttpUrl(props.exercise.audioUrl, { allowRelative: true }))
const ready = computed(() => isListeningReady(props.exercise, answers.value, responseText.value))
const answeredCount = computed(
  () => props.exercise.questions.filter((question) => (answers.value[question.index] ?? '').trim()).length,
)

function questionId(index: number): string {
  return `${baseId}-q-${index}`
}

function choose(index: number, option: string): void {
  answers.value = { ...answers.value, [index]: option }
  announcement.value = t('studentPractice.progressAnswered', {
    done: answeredCount.value,
    total: props.exercise.questions.length,
  })
}

function typeAnswer(index: number, event: Event): void {
  answers.value = { ...answers.value, [index]: (event.target as HTMLInputElement).value }
}

function submit(): void {
  if (!ready.value || props.locked) return
  emit('submit', {
    type: 'LISTENING_EXERCISE',
    body: buildListeningCompletion(props.exercise, answers.value, responseText.value),
  })
}
</script>

<style scoped src="./practicePlayer.css"></style>
<style scoped>
.listening-audio audio {
  width: 100%;
}

.listening-prompt {
  margin: 0;
  padding: var(--space-3) var(--space-3-5);
  border-radius: var(--radius-md);
  background: var(--surface-sunken);
  color: var(--text-primary);
  line-height: var(--line-height-relaxed);
}

.listening-questions {
  display: grid;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.listening-question {
  display: grid;
  gap: var(--space-2);
}

.listening-question-text {
  display: grid;
  gap: var(--space-0-5);
  margin: 0;
  font-weight: var(--font-weight-bold);
}

.listening-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
