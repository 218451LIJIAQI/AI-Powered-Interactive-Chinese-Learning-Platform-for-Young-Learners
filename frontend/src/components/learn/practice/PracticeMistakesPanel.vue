<template>
  <div class="practice-mistakes-page">
    <PracticeHead :title="t('practice.mistakesTitle')" :lede="t('practice.mistakesLede')">
      <template #mark>
        <span
          class="seal practice-mistakes-seal"
          :class="{ 'is-outline': counts.all > 0 }"
          lang="zh-CN"
          aria-hidden="true"
          >改</span
        >
      </template>
    </PracticeHead>

    <section
      class="practice-mistakes"
      :class="{ 'is-clear': counts.all === 0 }"
      aria-labelledby="practice-mistakes-title"
    >
      <div class="practice-mistakes-summary">
        <div class="practice-mistakes-count">
          <h2 id="practice-mistakes-title" class="practice-mistakes-title" :class="{ 'sr-only': counts.all === 0 }">
            <template v-if="counts.all > 0">
              <strong class="stat-figure">{{ counts.all }}</strong>
              {{ t('practice.mistakesNoun', {}, counts.all) }}
            </template>
            <template v-else>{{ t('practice.noMistakesYet') }}</template>
          </h2>
          <p v-if="counts.all > 0" class="practice-mistakes-text">{{ t('practice.reviewCallText') }}</p>
        </div>
        <div v-if="counts.all > 0" class="practice-mistakes-actions">
          <RouterLink
            class="btn btn-accent btn-lg btn-block"
            :to="{ name: 'student-practice-review' }"
            data-testid="start-review"
          >
            <IconGlyph name="retry" :size="18" />{{ t('practice.startReview') }}
          </RouterLink>
          <RouterLink
            class="btn btn-link"
            :to="rows.length ? askLink(rows[0]!) : { name: 'student-ai', params: { tab: 'ask' } }"
          >
            <IconGlyph name="ai" :size="16" />{{ t('practice.askSparkBot') }}
          </RouterLink>
        </div>
      </div>

      <SegmentedControl
        v-if="counts.all > 0"
        class="practice-mistakes-filter"
        :model-value="source"
        :label="t('practice.mistakeFilter')"
        :options="
          filters.map((filter) => ({
            value: filter.value,
            label: filter.label,
            testId: `filter-${(filter.value ?? 'all').toLowerCase()}`,
          }))
        "
        @update:model-value="setSource"
      />

      <div class="practice-mistakes-body">
        <StatePanel
          v-if="mistakes.loading.value"
          tone="loading"
          :title="t('practice.mistakesLoading')"
          :message="t('practice.mistakesLoadingHint')"
        />
        <StatePanel
          v-else-if="mistakes.error.value && !rows.length"
          tone="error"
          icon="alert"
          :title="t('practice.mistakesError')"
          :message="mistakes.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="mistakes.reload">{{ t('practice.retry') }}</button>
          </template>
        </StatePanel>
        <StatePanel
          v-else-if="!rows.length"
          tone="empty"
          icon="check"
          :title="t('practice.nothingToFix')"
          :message="t('practice.nothingToFixHint')"
          spacious
        >
          <template #actions>
            <RouterLink class="btn btn-secondary" :to="{ name: 'student-practice', params: { type: 'flashcards' } }">
              {{ t('practice.startWithFlashcards') }}
            </RouterLink>
          </template>
        </StatePanel>
        <template v-else>
          <ul class="practice-mistake-list">
            <li v-for="row in rows" :key="row.id" class="practice-mistake" data-testid="mistake-row">
              <div class="practice-mistake-copy">
                <p class="practice-mistake-question">{{ questionOf(row) }}</p>
                <p class="practice-mistake-answers">
                  <span class="practice-mistake-yours"
                    ><IconGlyph name="incorrect" :size="14" />{{
                      row.yourAnswer ? t('practice.youAnswered', { answer: row.yourAnswer }) : t('practice.youSkipped')
                    }}</span
                  >
                  ·
                  <span class="practice-mistake-right"
                    ><IconGlyph name="correct" :size="14" />{{
                      t('practice.correctWas', { answer: row.correctAnswer })
                    }}</span
                  >
                </p>
              </div>
              <p class="practice-mistake-source">
                {{ row.source === 'LEARN' ? t('practice.sourceLearn') : t('practice.sourcePractice') }} ·
                {{ formatShortDate(row.lastWrongAt) }}
              </p>
              <RouterLink
                class="btn btn-ghost btn-icon btn-sm"
                :to="askLink(row)"
                :aria-label="t('practice.askAboutThis', { question: questionOf(row) })"
              >
                <IconGlyph name="ai" :size="16" />
              </RouterLink>
            </li>
          </ul>
          <PaginationBar
            v-if="totalPages > 1"
            :current-page="page + 1"
            :total-pages="totalPages"
            :label="t('practice.mistakePages')"
            @update:current-page="goToPage($event - 1)"
          />
        </template>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { mistakesApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { MistakeRow, MistakeSource } from '@/types/learn'
import { formatShortDate } from '@/utils/format'

import PracticeHead from './PracticeHead.vue'

/**
 * The Mistakes room: how many wait (one review clears them), filtered by where they came from, each with what was
 * answered and the right answer. SparkBot can explain one.
 */
const PAGE_SIZE = 8

const { t } = useLocalMessages({ practice: practiceMessages, learn: learnMessages })

const source = ref<MistakeSource | null>(null)
const page = ref(0)
const mistakes = useResource(() => mistakesApi.list(source.value, page.value, PAGE_SIZE))
const { items: rows, totalPages, goToPage } = useServerPage(mistakes, page, { select: (data) => data.page })

const counts = computed(() => ({
  all: mistakes.data.value?.all ?? 0,
  learn: mistakes.data.value?.learn ?? 0,
  practice: mistakes.data.value?.practice ?? 0,
}))

const filters = computed(() => [
  { value: null, label: t('practice.filterAll', { count: counts.value.all }) },
  { value: 'LEARN' as const, label: t('practice.filterLearn', { count: counts.value.learn }) },
  { value: 'PRACTICE' as const, label: t('practice.filterPractice', { count: counts.value.practice }) },
])

function setSource(value: MistakeSource | null): void {
  source.value = value
  page.value = 0
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

function askLink(row: MistakeRow): RouteLocationRaw {
  const q = t('practice.askPrefill', {
    question: questionOf(row),
    yours: row.yourAnswer ?? t('practice.nothing'),
    correct: row.correctAnswer,
  })
  return { name: 'student-ai', params: { tab: 'ask' }, query: { q } }
}
</script>
