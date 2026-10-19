<template>
  <div class="activity-result-region" role="status" aria-live="polite">
    <section
      v-if="result"
      class="activity-result"
      :class="result.passed ? 'is-passed' : 'is-not-yet'"
      data-testid="activity-result"
      :aria-labelledby="headingId"
    >
      <div class="activity-result-head">
        <span class="activity-result-icon" aria-hidden="true">
          <IconGlyph :name="result.passed ? 'status' : 'retry'" :size="22" />
        </span>
        <div>
          <small class="activity-result-kicker">{{ t('studentPractice.resultHeading') }}</small>
          <h3 :id="headingId">{{ headline }}</h3>
        </div>
      </div>

      <p v-if="graded" class="activity-result-score" data-testid="activity-result-score">
        {{ t('studentPractice.resultScore', { correct: result.correctCount, total: result.totalCount }) }}
      </p>

      <p class="activity-result-xp" data-testid="activity-result-xp">{{ xpMessage }}</p>
      <p v-if="outcome === 'earned' && result.firstCompletion" class="activity-result-note">
        {{ t('studentPractice.xpFirstCompletion') }}
      </p>
      <p v-if="type === 'TRACING_EXERCISE'" class="activity-result-note">
        {{ t('studentPractice.tracingNotGraded') }}
      </p>

      <div v-if="graded && rows.length" class="activity-result-items">
        <h4>{{ t('studentPractice.resultItemsHeading') }}</h4>
        <ul>
          <li
            v-for="row in rows"
            :key="row.key"
            class="activity-result-item"
            :class="row.correct ? 'is-correct' : 'is-wrong'"
            data-testid="activity-result-item"
          >
            <span class="activity-result-item-label" :lang="cjkLang(row.label)">{{
              row.label ?? t('studentPractice.resultUnknownItem', { key: row.key })
            }}</span>
            <span class="activity-result-chip">
              <IconGlyph :name="row.correct ? 'status' : 'alert'" :size="14" />
              {{ row.correct ? t('studentPractice.resultItemCorrect') : t('studentPractice.resultItemNotYet') }}
            </span>
            <p v-if="row.explanation" class="activity-result-explanation">
              <strong>{{ t('studentPractice.resultExplanation') }}:</strong>
              <span :lang="cjkLang(row.explanation)">{{ row.explanation }}</span>
            </p>
          </li>
        </ul>
      </div>

      <p v-if="graded && !result.passed" class="activity-result-note">{{ t('studentPractice.resultAnswersHidden') }}</p>

      <div class="activity-result-actions">
        <button type="button" class="btn btn-secondary" data-testid="activity-retry" @click="emit('retry')">
          <IconGlyph name="retry" :size="16" />
          <span>{{ t('studentPractice.tryAgain') }}</span>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import type { StudentActivityCompletionResponse } from '@/types/api'
import { cjkLang, completionOutcome, type ActivityResultRow } from '@/utils/activityCompletion'
import { formatNumber } from '@/utils/format'

import { usePracticeI18n } from './usePracticeI18n'

const props = withDefaults(
  defineProps<{
    result: StudentActivityCompletionResponse | null
    rows?: ActivityResultRow[]
    type: string
    preview?: boolean
  }>(),
  { rows: () => [], preview: false },
)

const emit = defineEmits<{ retry: [] }>()

const { t } = usePracticeI18n()
const headingId = `activity-result-${useId()}`

/** Flashcards and tracing are review/practice completions without per-item grading. */
const graded = computed(() => props.type !== 'FLASHCARD' && props.type !== 'TRACING_EXERCISE')
const outcome = computed(() => (props.result ? completionOutcome(props.result, props.preview) : null))

const headline = computed(() => {
  const result = props.result
  if (!result) return ''
  if (!result.passed) return t('studentPractice.resultNotYet')
  if (props.type === 'FLASHCARD') return t('studentPractice.resultReviewed')
  if (props.type === 'TRACING_EXERCISE') return t('studentPractice.resultPracticeSaved')
  return t('studentPractice.resultPassed')
})

const xpMessage = computed(() => {
  const result = props.result
  if (!result) return ''
  switch (outcome.value) {
    case 'preview':
      return t('studentPractice.xpPreview')
    case 'notYet':
      return t('studentPractice.xpNotYet')
    case 'earned':
      return t('studentPractice.xpEarned', { points: result.earnedPoints, total: formatNumber(result.totalPoints) })
    case 'completed':
      return t('studentPractice.xpCompleted')
    default:
      return t('studentPractice.xpAlreadyCompleted')
  }
})
</script>

<style scoped>
.activity-result {
  display: grid;
  gap: var(--space-2-5);
  padding: var(--space-4);
  border: 1px solid var(--border-default);
  border-left-width: 4px;
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  text-align: left;
}

.activity-result.is-passed {
  border-color: var(--color-success-border);
  border-left-color: var(--color-success);
  background: linear-gradient(180deg, var(--color-success-soft), var(--surface-card));
}

.activity-result.is-not-yet {
  border-color: var(--color-warning-border);
  border-left-color: var(--color-warning);
  background: linear-gradient(180deg, var(--color-warning-soft), var(--surface-card));
}

.activity-result-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.activity-result-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.activity-result-kicker {
  color: var(--text-secondary);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.activity-result-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-round);
  background: var(--color-warning-strong);
  color: var(--color-white);
}

.is-passed .activity-result-icon {
  background: var(--color-success-strong);
}

.activity-result-score {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
}

.activity-result-xp,
.activity-result-note {
  margin: 0;
  color: var(--text-secondary);
  line-height: var(--line-height-normal);
}

.activity-result-note {
  font-size: var(--font-size-sm);
}

.activity-result-items h4 {
  margin: 0 0 var(--space-1-5);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.activity-result-items ul {
  display: grid;
  gap: var(--space-1-5);
  margin: 0;
  padding: 0;
  list-style: none;
}

.activity-result-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-2-5);
  padding: var(--space-2) var(--space-2-5);
  border: 1px solid var(--border-subtle);
  border-left-width: 3px;
  border-radius: var(--radius-sm);
  background: var(--surface-card);
}

.activity-result-item.is-correct {
  border-left-color: var(--color-success);
}

.activity-result-item.is-wrong {
  border-left-color: var(--color-warning);
}

.activity-result-item-label {
  min-width: 0;
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.activity-result-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.is-correct .activity-result-chip {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.is-wrong .activity-result-chip {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.activity-result-explanation {
  flex-basis: 100%;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.activity-result-explanation strong {
  margin-right: var(--space-1);
}

.activity-result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2-5);
}
</style>
