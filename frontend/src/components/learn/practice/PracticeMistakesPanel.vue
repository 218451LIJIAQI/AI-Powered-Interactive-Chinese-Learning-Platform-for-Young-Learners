<template>
  <section
    class="practice-stage"
    :aria-labelledby="!mistakes.loading.value && Boolean(mistakes.data.value) && counts.all > 0 ? titleId : undefined"
  >
    <StatePanel
      v-if="mistakes.loading.value && !mistakes.data.value"
      class="practice-stage-state"
      heading-tag="h2"
      tone="loading"
      :title="t('practice.mistakesLoading')"
      :message="t('practice.mistakesLoadingHint')"
    />
    <StatePanel
      v-else-if="mistakes.error.value && !mistakes.data.value"
      class="practice-stage-state"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('practice.mistakesError')"
      :message="mistakes.error.value"
      @retry="mistakes.reload"
    />

    <template v-else-if="counts.all === 0">
      <StatePanel
        class="practice-stage-state"
        heading-tag="h2"
        tone="empty"
        icon="correct"
        :title="t('practice.noMistakesYet')"
        :message="t('practice.noMistakesHint')"
      />
      <div class="practice-footer">
        <div class="practice-footer-end">
          <RouterLink class="btn btn-primary" :to="{ name: 'student-practice', params: { type: 'flashcards' } }">
            {{ t('practice.startWithFlashcards') }}
          </RouterLink>
        </div>
      </div>
    </template>

    <template v-else>
      <!-- The chips carry the counts; the heading names the stage for assistive technology. -->
      <h2 :id="titleId" class="sr-only">{{ t('practice.mistakesToFix', { count: counts.all }, counts.all) }}</h2>
      <FilterChips
        :model-value="source"
        :label="t('practice.mistakeFilter')"
        :options="filters"
        @update:model-value="setSource"
      />

      <StatePanel
        v-if="mistakes.error.value"
        class="practice-stage-state"
        tone="error"
        icon="alert"
        :title="t('practice.mistakesError')"
        :message="mistakes.error.value"
        @retry="mistakes.reload"
      />
      <p v-else-if="mistakes.loading.value || changingSource" class="type-caption" role="status">
        {{ t('practice.mistakesLoading') }}
      </p>
      <ul v-else-if="rows.length" class="practice-mistake-list">
        <li v-for="row in rows" :key="row.id" class="practice-mistake" data-testid="mistake-row">
          <div class="practice-mistake-copy">
            <p class="practice-mistake-question"><CjkText :text="questionOf(row)" /></p>
            <p class="practice-mistake-answers"><CjkText :text="detailOf(row)" /></p>
          </div>
          <span class="practice-mistake-tags">
            <span v-if="row.sureWrong" class="tag is-strong">{{ t('practice.youWereSure') }}</span>
            <span class="tag is-outline">
              {{ row.source === 'LEARN' ? t('nav.learn') : t('nav.practice') }}
            </span>
          </span>
        </li>
      </ul>
      <StatePanel
        v-else
        class="practice-stage-state"
        tone="empty"
        icon="correct"
        :title="t('practice.noneFromSource')"
        :message="t('practice.noneFromSourceHint')"
      />
      <p
        v-if="!mistakes.loading.value && !mistakes.error.value && !changingSource && moreCount > 0"
        class="practice-mistakes-more"
      >
        {{ t('practice.moreMistakes', { more: moreCount }) }}
      </p>

      <div class="practice-footer">
        <p class="practice-mistakes-call">{{ reviewLine }}</p>
        <div class="practice-footer-end">
          <RouterLink class="btn btn-primary" :to="{ name: 'student-practice-review' }" data-testid="start-review">
            <IconGlyph name="retry" :size="16" />{{ t('practice.startReview') }}
          </RouterLink>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { RouterLink } from 'vue-router'

import { mistakesApi } from '@/api/learn'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { MistakeRow, MistakeSource } from '@/types/learn'

import { MISTAKES_REVIEW_SIZE, mistakesReview } from './practiceTypes'

/**
 * The Mistakes tab: the waiting mistakes filtered by where they came from (All · Learn · Practice, each with its
 * count), each with what was asked and either what was answered and the right answer, or the flashcard marked "Still
 * learning" (reviewed as a multiple-choice question). The footer says what the review takes ("Review all 6 · about
 * 3 min · +10 XP"): one review works through up to MISTAKES_REVIEW_SIZE at a time, whatever the filter, and clears the
 * mistakes answered right.
 */
const VISIBLE_ROWS = 6

const { t } = useLocalMessages({ practice: practiceMessages })
const titleId = useId()

const source = ref<MistakeSource | null>(null)
const mistakes = useResource(async () => {
  const requestedSource = source.value
  return { ...(await mistakesApi.list(requestedSource, VISIBLE_ROWS)), source: requestedSource }
})

const changingSource = computed(() => mistakes.data.value?.source !== source.value)
const rows = computed(() => mistakes.data.value?.page.items ?? [])
const moreCount = computed(() => Math.max(0, (mistakes.data.value?.page.totalItems ?? 0) - rows.value.length))

const counts = computed(() => ({
  all: mistakes.data.value?.all ?? 0,
  learn: mistakes.data.value?.learn ?? 0,
  practice: mistakes.data.value?.practice ?? 0,
}))

/** "Review all 6 · about 3 min · +10 XP", or "Review 20 of 25 · …" for a list longer than one review. */
const reviewLine = computed(() => {
  const total = counts.value.all
  const review = mistakesReview(total)
  const values = { ...review, total }
  return total <= MISTAKES_REVIEW_SIZE
    ? t('practice.reviewAll', values, review.count)
    : t('practice.reviewSome', values)
})

const filters = computed(() => [
  { value: null, label: t('practice.filterAll'), count: counts.value.all },
  { value: 'LEARN' as const, label: t('nav.learn'), count: counts.value.learn, testId: 'filter-learn' },
  {
    value: 'PRACTICE' as const,
    label: t('nav.practice'),
    count: counts.value.practice,
    testId: 'filter-practice',
  },
])

function setSource(value: MistakeSource | null): void {
  if (value === source.value) return
  source.value = value
  void mistakes.reload()
}

function questionOf(row: MistakeRow): string {
  switch (row.type) {
    case 'MEANING':
      return t('practice.askMeaning', { hanzi: row.hanzi ?? '' })
    case 'PINYIN':
      return t('practice.askPinyin', { hanzi: row.hanzi ?? '' })
    case 'HANZI':
      return t('practice.askHanzi', { meaning: row.meaning ?? '' })
    case 'LISTEN_CHOOSE':
      return t('practice.askListenChoose')
    case 'LISTEN_BUILD':
      return t('practice.askListenBuild', { hanzi: row.hanzi ?? '' })
    case 'TRANSLATE_BUILD':
      return t('practice.askTranslate', { meaning: row.meaning ?? '' })
    default:
      return row.prompt || t('practice.askListenChoose')
  }
}

/** "You answered 晚上好 · correct: 早上好", or for a flashcard: "Flashcards · marked “Still learning” · 你好 = Hello". */
function detailOf(row: MistakeRow): string {
  if (row.stillLearning) {
    return t('practice.stillLearningRow', { hanzi: row.hanzi ?? '', meaning: row.correctAnswer })
  }
  const answered = row.yourAnswer ? t('practice.youAnswered', { answer: row.yourAnswer }) : t('practice.youSkipped')
  return `${answered} · ${t('practice.correctWas', { answer: row.correctAnswer })}`
}
</script>
