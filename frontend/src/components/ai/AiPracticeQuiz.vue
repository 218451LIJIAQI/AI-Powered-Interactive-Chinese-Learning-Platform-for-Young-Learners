<template>
  <div class="ai-practice" data-testid="ai-practice-quiz">
    <p class="ai-practice-note" role="note" data-testid="ai-practice-not-saved">
      <IconGlyph name="info" :size="16" />
      <span>{{ t('studentAi.practiceNotSaved') }}</span>
    </p>

    <p v-if="!questions.length" class="ai-practice-muted">{{ t('studentAi.quizNoQuestions') }}</p>

    <template v-else>
      <ol class="ai-practice-list">
        <li
          v-for="(question, index) in questions"
          :key="`${quiz.title}-${index}`"
          class="ai-practice-item"
          :class="statusClass(index)"
          data-testid="ai-quiz-question"
        >
          <span class="ai-practice-number">{{ t('studentAi.questionNumber', { number: index + 1 }) }}</span>
          <strong class="ai-practice-prompt"><CjkText :text="question.questionText" /></strong>

          <div
            v-if="question.options.length"
            class="ai-practice-options"
            role="group"
            :aria-label="t('studentAi.answerChoices', { number: index + 1 })"
          >
            <button
              v-for="(option, optionIndex) in question.options"
              :key="optionIndex"
              type="button"
              class="ai-practice-option"
              :class="optionClass(question, index, optionIndex)"
              :aria-pressed="answers[index] === optionIndex"
              :disabled="Boolean(result)"
              data-testid="ai-quiz-option"
              @click="selectOption(index, optionIndex)"
            >
              <span class="ai-practice-letter" aria-hidden="true">{{ optionLetter(optionIndex) }}</span>
              <CjkText :text="option" />
            </button>
          </div>
          <input
            v-else
            v-model="answers[index]"
            class="input ai-practice-input"
            type="text"
            autocomplete="off"
            :placeholder="t('studentAi.typeAnswer')"
            :aria-label="t('studentAi.typeAnswerAria', { number: index + 1 })"
            :disabled="Boolean(result)"
            data-testid="ai-quiz-text-answer"
          />

          <div v-if="result" class="ai-practice-feedback" data-testid="ai-quiz-feedback">
            <strong class="ai-practice-status">
              <IconGlyph :name="statusIcon(result.statuses[index])" :size="16" />
              {{ statusLabel(result.statuses[index]) }}
            </strong>
            <span v-if="question.correctAnswer">
              <CjkText :text="t('studentAi.correctAnswer', { answer: question.correctAnswerDisplay })" />
            </span>
            <small v-if="question.pinyin">{{ t('studentAi.pinyinLabel', { text: question.pinyin }) }}</small>
            <small v-if="question.translation">
              <CjkText :text="t('studentAi.translationLabel', { text: question.translation })" />
            </small>
            <small v-if="question.explanation">
              <CjkText :text="t('studentAi.explanation', { text: question.explanation })" />
            </small>
          </div>
        </li>
      </ol>

      <div class="ai-practice-actions">
        <button v-if="!result" class="btn btn-primary" type="button" data-testid="ai-quiz-check" @click="checkAnswers">
          {{ t('studentAi.checkAnswers') }}
        </button>
        <button v-else class="btn btn-secondary" type="button" data-testid="ai-quiz-reset" @click="reset">
          {{ t('studentAi.tryAgain') }}
        </button>
        <strong class="ai-practice-score" role="status" aria-live="polite" data-testid="ai-quiz-score">
          <template v-if="result">{{
            t('studentAi.quizScore', { score: result.score, total: result.total })
          }}</template>
        </strong>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { studentAiHelperMessages } from '@/plugins/studentAiHelperMessages'
import type { AiQuizGeneratorResponse } from '@/types/api'
import {
  preparePracticeQuiz,
  scorePracticeQuiz,
  type PracticeAnswerStatus,
  type PracticeQuizQuestion,
  type PracticeQuizResult,
} from '@/utils/aiPracticeQuiz'

/**
 * Temporary self-practice for an AI-generated quiz (FR-AIGEN-03): answers are checked locally, never sent to the
 * API, saved, or counted as marks.
 */
const props = defineProps<{ quiz: AiQuizGeneratorResponse }>()

const { t } = useI18n({
  messages: {
    en: { studentAi: studentAiHelperMessages.en },
    ms: { studentAi: studentAiHelperMessages.ms },
    'zh-CN': { studentAi: studentAiHelperMessages['zh-CN'] },
  },
})

const questions = computed(() => preparePracticeQuiz(props.quiz))
const answers = ref<Array<number | string | null>>([])
const result = ref<PracticeQuizResult | null>(null)

const STATUS_KEYS: Record<PracticeAnswerStatus, string> = {
  correct: 'studentAi.answerCorrect',
  incorrect: 'studentAi.answerIncorrect',
  unanswered: 'studentAi.answerUnanswered',
  noKey: 'studentAi.answerNoKey',
}

function reset(): void {
  answers.value = questions.value.map(() => null)
  result.value = null
}

watch(questions, reset, { immediate: true })

function selectOption(questionIndex: number, optionIndex: number): void {
  if (result.value) return
  answers.value[questionIndex] = optionIndex
}

function checkAnswers(): void {
  result.value = scorePracticeQuiz(questions.value, answers.value)
}

function optionLetter(index: number): string {
  return String.fromCharCode(65 + index)
}

function statusLabel(status: PracticeAnswerStatus | undefined): string {
  return status ? t(STATUS_KEYS[status]) : ''
}

function statusIcon(status: PracticeAnswerStatus | undefined): 'status' | 'alert' | 'info' {
  if (status === 'correct') return 'status'
  if (status === 'incorrect' || status === 'unanswered') return 'alert'
  return 'info'
}

function statusClass(index: number): string {
  const status = result.value?.statuses[index]
  if (status === 'correct') return 'is-correct'
  if (status === 'incorrect' || status === 'unanswered') return 'is-incorrect'
  return ''
}

function optionClass(question: PracticeQuizQuestion, questionIndex: number, optionIndex: number): string[] {
  const classes: string[] = []
  if (answers.value[questionIndex] === optionIndex) classes.push('is-selected')
  if (result.value && question.correctOptionIndexes.includes(optionIndex)) classes.push('is-answer')
  return classes
}
</script>

<style scoped>
.ai-practice {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.ai-practice-note,
.ai-practice-muted {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.ai-practice-note {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.ai-practice-note .icon-glyph {
  margin-top: var(--space-0-5);
}

.ai-practice-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-practice-item {
  display: grid;
  gap: var(--space-2-5);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.ai-practice-item.is-correct {
  border-color: var(--color-success-border);
  background: var(--color-success-soft);
}

.ai-practice-item.is-incorrect {
  border-color: var(--color-danger-border);
  background: var(--color-danger-soft);
}

.ai-practice-number {
  color: var(--text-brand);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.ai-practice-prompt {
  color: var(--text-primary);
  font-size: var(--font-size-base);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.ai-practice-options {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 180px), 1fr));
  gap: var(--space-2);
}

.ai-practice-option {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  min-width: 0;
  min-height: var(--tap-target-min);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--font-size-md);
  text-align: left;
  overflow-wrap: anywhere;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ai-practice-option:not(:disabled):hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.ai-practice-option:disabled {
  cursor: default;
}

.ai-practice-option.is-selected {
  border-color: var(--color-primary-500);
  background: var(--surface-selected);
  font-weight: var(--font-weight-bold);
}

.ai-practice-option.is-answer {
  border-color: var(--color-success);
  box-shadow: inset 0 0 0 1px var(--color-success);
}

.ai-practice-letter {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: var(--space-6);
  height: var(--space-6);
  border-radius: var(--radius-round);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.ai-practice-option.is-selected .ai-practice-letter {
  background: var(--color-primary-500);
  color: var(--text-on-primary);
}

.ai-practice-feedback {
  display: grid;
  gap: var(--space-1);
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.ai-practice-status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  color: var(--text-primary);
}

.is-correct .ai-practice-status {
  color: var(--color-success-text);
}

.is-incorrect .ai-practice-status {
  color: var(--color-danger-text);
}

.ai-practice-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.ai-practice-score {
  color: var(--text-brand);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
}
</style>
