<template>
  <section class="practice-stage" :aria-labelledby="counts.all > 0 ? titleId : undefined">
    <StatePanel
      v-if="mistakes.loading.value"
      class="practice-stage-state"
      tone="loading"
      :title="t('practice.mistakesLoading')"
      :message="t('practice.mistakesLoadingHint')"
    />
    <StatePanel
      v-else-if="mistakes.error.value && !rows.length"
      class="practice-stage-state"
      tone="error"
      icon="alert"
      :title="t('practice.mistakesError')"
      :message="mistakes.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="mistakes.reload">{{ t('practice.retry') }}</button>
      </template>
    </StatePanel>

    <template v-else-if="counts.all === 0">
      <StatePanel
        class="practice-stage-state"
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
      <header class="practice-mistakes-head">
        <div class="practice-stage-head">
          <h2 :id="titleId" class="practice-stage-title">
            {{ t('practice.mistakesToFix', { count: counts.all }, counts.all) }}
          </h2>
          <p class="practice-stage-meta">{{ t('practice.reviewCallText') }}</p>
        </div>
        <FilterChips
          :model-value="source"
          :label="t('practice.mistakeFilter')"
          :options="filters"
          @update:model-value="setSource"
        />
      </header>

      <ul v-if="rows.length" class="practice-mistake-list">
        <li v-for="row in rows" :key="row.id" class="practice-mistake" data-testid="mistake-row">
          <div class="practice-mistake-copy">
            <p class="practice-mistake-question">{{ questionOf(row) }}</p>
            <p class="practice-mistake-answers">
              {{ row.yourAnswer ? t('practice.youAnswered', { answer: row.yourAnswer }) : t('practice.youSkipped') }}
              · {{ t('practice.correctWas', { answer: row.correctAnswer }) }}
            </p>
          </div>
          <span class="tag is-outline">
            {{ row.source === 'LEARN' ? t('practice.sourceLearn') : t('practice.sourcePractice') }} ·
            {{ formatShortDate(row.lastWrongAt) }}
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
      <p v-if="moreCount > 0" class="practice-mistakes-more">
        {{
          counts.all <= REVIEW_SIZE
            ? t('practice.moreInReview', { more: moreCount, total: counts.all })
            : t('practice.moreMistakes', { more: moreCount })
        }}
      </p>

      <div class="practice-footer">
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
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { MistakeRow, MistakeSource } from '@/types/learn'
import { formatShortDate } from '@/utils/format'

/**
 * The Mistakes tab: how many wait (one review clears them), the latest few with what was answered and the right answer,
 * filtered by where they came from. The review is where the whole list is worked through.
 */
const VISIBLE_ROWS = 4
/** The server's limit (MistakeService): a longer list reaches the review over several rounds. */
const REVIEW_SIZE = 20

const { t } = useLocalMessages({ practice: practiceMessages, learn: learnMessages })
const titleId = useId()

const source = ref<MistakeSource | null>(null)
const mistakes = useResource(() => mistakesApi.list(source.value, 0, VISIBLE_ROWS))

const rows = computed(() => mistakes.data.value?.page.items ?? [])
const moreCount = computed(() => Math.max(0, (mistakes.data.value?.page.totalItems ?? 0) - rows.value.length))

const counts = computed(() => ({
  all: mistakes.data.value?.all ?? 0,
  learn: mistakes.data.value?.learn ?? 0,
  practice: mistakes.data.value?.practice ?? 0,
}))

const filters = computed(() => [
  { value: null, label: t('practice.filterAll'), count: counts.value.all, testId: 'filter-all' },
  { value: 'LEARN' as const, label: t('practice.sourceLearn'), count: counts.value.learn, testId: 'filter-learn' },
  {
    value: 'PRACTICE' as const,
    label: t('practice.sourcePractice'),
    count: counts.value.practice,
    testId: 'filter-practice',
  },
])

function setSource(value: MistakeSource | null): void {
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
      return t('learn.instructionHanzi', { meaning: row.meaning ?? '' })
    case 'LISTEN_CHOOSE':
      return t('practice.askListenChoose')
    case 'LISTEN_BUILD':
      return t('practice.askListenBuild', { hanzi: row.hanzi ?? '' })
    case 'TRANSLATE_BUILD':
      return t('practice.askTranslate', { meaning: row.meaning ?? '' })
    default:
      return row.prompt ?? ''
  }
}
</script>
