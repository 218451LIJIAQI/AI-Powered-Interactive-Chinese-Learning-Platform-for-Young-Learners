<template>
  <div class="page-stack">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminLearning.pathPageTitle')" :description="t('adminLearning.pathDescription')" />
      <AdminContentTabs />
    </div>
    <DataNotice :success="actionSuccess" :error="actionError" />

    <ResourceSection class="page-stack" :loading="path.loading.value" :error="path.error.value" @retry="path.reload">
      <div v-if="path.data.value" class="admin-path">
        <section class="admin-sheet" aria-labelledby="learning-path-title">
          <div class="admin-sheet-head">
            <div>
              <h2 id="learning-path-title" class="admin-sheet-title">{{ t('adminLearning.units') }}</h2>
              <p class="admin-sheet-caption">
                {{
                  t('adminLearning.pathCounts', {
                    units: path.data.value.unitCount,
                    lessons: path.data.value.lessonCount,
                  })
                }}
              </p>
            </div>
          </div>
          <section
            v-for="level in path.data.value.levels"
            :key="level.level"
            class="admin-path-level"
            :aria-labelledby="`learning-path-level-${level.level}`"
          >
            <h3 :id="`learning-path-level-${level.level}`" class="admin-path-level-title">
              {{
                t('adminLearning.levelHeading', {
                  level: level.level,
                  name: level.level === 1 ? t('adminLearning.beginner1') : t('adminLearning.beginner2'),
                  hsk: level.hskLevel,
                  lessons: level.lessonCount,
                })
              }}
            </h3>
            <ul class="admin-queue-list">
              <li v-for="unit in level.units" :key="unit.id" :data-testid="`unit-row-${unit.number}`">
                <button
                  class="admin-queue-item admin-path-unit"
                  type="button"
                  :aria-current="unit.id === selectedUnitId ? 'true' : undefined"
                  :data-testid="`open-unit-${unit.number}`"
                  @click="selectUnit(unit.id)"
                >
                  <span class="admin-path-mark" :class="{ 'is-draft': unit.status !== 'PUBLISHED' }" aria-hidden="true">
                    {{ unit.number }}
                  </span>
                  <span class="admin-queue-item-copy">
                    <strong>
                      {{ unit.title }} · <span class="admin-path-zh" lang="zh-CN">{{ unit.titleZh }}</span>
                    </strong>
                    <span class="admin-queue-item-meta">
                      {{ t('adminLearning.lessonsHsk', { lessons: unit.lessonCount, hsk: unit.hskLevel }) }}
                      <template v-if="unit.status === 'PUBLISHED'">
                        ·
                        {{
                          t(
                            'adminLearning.studentsStarted',
                            { count: formatNumber(unit.studentsStarted) },
                            unit.studentsStarted,
                          )
                        }}
                      </template>
                    </span>
                  </span>
                  <StatusPill
                    :label="unit.status === 'PUBLISHED' ? t('adminLearning.published') : t('adminLearning.draft')"
                    :tone="unit.status === 'PUBLISHED' ? 'success' : 'neutral'"
                  />
                </button>
              </li>
            </ul>
          </section>
        </section>

        <div ref="detailRef" class="admin-path-detail" tabindex="-1">
          <UnitDetail :unit-id="selectedUnitId" @changed="onUnitChanged" @failed="showError" />
        </div>
      </div>

      <section class="admin-sheet" aria-labelledby="level-check-title">
        <div class="admin-sheet-head">
          <div>
            <h2 id="level-check-title" class="admin-sheet-title">{{ t('adminLearning.levelCheckTitle') }}</h2>
            <p class="admin-sheet-caption">
              {{ t('adminLearning.levelCheckDescription', { count: questions.data.value?.totalItems ?? 0 }) }}
            </p>
            <p class="admin-sheet-caption">{{ t('adminLearning.levelCheckNote') }}</p>
          </div>
          <button class="btn btn-primary" type="button" data-testid="new-question" @click="openQuestion(null)">
            <IconGlyph name="plus" :size="16" />{{ t('adminLearning.newQuestion') }}
          </button>
        </div>

        <div v-if="questions.loading.value" class="admin-sheet-body">
          <StatePanel
            tone="loading"
            :title="t('adminLearning.questionsLoading')"
            :message="t('adminLearning.questionsLoadingHint')"
          />
        </div>
        <div v-else-if="questions.error.value && !questionRows.length" class="admin-sheet-body">
          <StatePanel
            tone="error"
            icon="alert"
            :title="t('adminLearning.questionsError')"
            :message="questions.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="questions.reload">
                {{ t('adminLearning.retry') }}
              </button>
            </template>
          </StatePanel>
        </div>
        <div v-else-if="!questionRows.length" class="admin-sheet-body">
          <StatePanel
            tone="empty"
            icon="quiz"
            :title="t('adminLearning.noQuestions')"
            :message="t('adminLearning.noQuestionsHint')"
          />
        </div>
        <div v-else class="data-table-wrap" role="region" :aria-label="t('adminLearning.levelCheckTitle')" tabindex="0">
          <table class="data-table admin-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminLearning.question') }}</th>
                <th scope="col">{{ t('adminLearning.skill') }}</th>
                <th scope="col">{{ t('adminLearning.difficulty') }}</th>
                <th scope="col">{{ t('adminLearning.level') }}</th>
                <th scope="col">{{ t('adminLearning.status') }}</th>
                <th scope="col" class="col-actions">
                  <span class="sr-only">{{ t('adminLearning.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in questionRows"
                :key="row.id"
                :class="{ 'is-selected': questionOpen && row.id === questionId }"
                @click="openQuestion(row.id)"
              >
                <td class="admin-learning-question-cell">{{ row.prompt }}</td>
                <td>{{ t(skillKey(row.skill)) }}</td>
                <td>{{ t(difficultyKey(row.difficulty)) }}</td>
                <td class="admin-learning-stage-cell">
                  {{ row.level === 'BEGINNER_1' ? t('adminLearning.beginner1') : t('adminLearning.beginner2') }}
                </td>
                <td>
                  <StatusPill
                    :label="row.status === 'PUBLISHED' ? t('adminLearning.published') : t('adminLearning.draft')"
                    :tone="row.status === 'PUBLISHED' ? 'success' : 'neutral'"
                  />
                </td>
                <td class="col-actions">
                  <button
                    class="btn btn-ghost btn-icon btn-sm"
                    type="button"
                    :aria-label="t('adminLearning.openQuestion', { question: row.prompt })"
                    @click.stop="openQuestion(row.id)"
                  >
                    <IconGlyph name="chevronRight" :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="admin-sheet-foot">
          <PaginationBar
            v-if="questionPages > 1"
            :current-page="questionPage + 1"
            :total-pages="questionPages"
            :label="t('adminLearning.questionPages')"
            :info="t('adminLearning.showing', questionRange)"
            icon-only
            @update:current-page="goToQuestionPage($event - 1)"
          />
        </div>
      </section>
    </ResourceSection>

    <LevelCheckQuestionDrawer
      :open="questionOpen"
      :question-id="questionId"
      @close="questionOpen = false"
      @saved="onQuestionSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import LevelCheckQuestionDrawer from '@/components/learn/admin/LevelCheckQuestionDrawer.vue'
import UnitDetail from '@/components/learn/admin/UnitDetail.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type { LevelCheckQuestionDetail } from '@/types/learn'
import { formatNumber } from '@/utils/format'
import { difficultyKey, skillKey } from '@/utils/learnLabels'
import '@/styles/learn-admin.css'

const QUESTION_PAGE_SIZE = 10

const { t } = useLocalMessages({ adminLearning: adminLearningMessages })
const { actionError, actionSuccess, showError, showSuccess } = useActionFeedback()

const path = useResource(() => adminLearningApi.path())
const questionPage = ref(0)
const questions = useResource(() => adminLearningApi.questions(questionPage.value, QUESTION_PAGE_SIZE))
const {
  items: questionRows,
  totalPages: questionPages,
  range: questionRange,
  goToPage: goToQuestionPage,
} = useServerPage(questions, questionPage)

// ----- the unit beside the path (the first one until another is chosen) -----

const selectedUnitId = ref<number | null>(null)
const detailRef = ref<HTMLElement | null>(null)

watch(
  () => path.data.value,
  (value) => {
    if (selectedUnitId.value === null) selectedUnitId.value = value?.levels[0]?.units[0]?.id ?? null
  },
)

async function selectUnit(id: number): Promise<void> {
  selectedUnitId.value = id
  await nextTick()
  detailRef.value?.focus({ preventScroll: true })
}

function onUnitChanged(_: unknown, message: string): void {
  showSuccess(message)
  void path.reload()
}

// ----- level check questions -----

const questionOpen = ref(false)
const questionId = ref<number | null>(null)

function openQuestion(id: number | null): void {
  questionId.value = id
  questionOpen.value = true
}

function onQuestionSaved(question: LevelCheckQuestionDetail): void {
  questionOpen.value = false
  showSuccess(
    question.status === 'PUBLISHED' ? t('adminLearning.questionSavedPublished') : t('adminLearning.questionSavedDraft'),
  )
  void questions.reload()
  void path.reload()
}
</script>
