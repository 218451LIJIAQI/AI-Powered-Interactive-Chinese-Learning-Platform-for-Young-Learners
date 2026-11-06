<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminLearning.pathPageTitle')" hide-title />
    <AdminContentTabs />
    <DataNotice :error="actionError" />

    <ResourceSection class="page-stack" :loading="path.loading.value" :error="path.error.value" @retry="path.reload">
      <section v-if="path.data.value" class="panel admin-path" aria-labelledby="learning-path-title">
        <h2 id="learning-path-title" class="card-title">
          {{
            t('adminLearning.pathHeading', {
              units: path.data.value.unitCount,
              lessons: path.data.value.lessonCount,
            })
          }}
        </h2>
        <section
          v-for="level in path.data.value.levels"
          :key="level.level"
          class="admin-path-level"
          :aria-labelledby="`learning-path-level-${level.level}`"
        >
          <h3 :id="`learning-path-level-${level.level}`" class="admin-overline">
            {{
              t('adminLearning.levelHeading', {
                level: level.level,
                name: level.level === 1 ? t('adminLearning.beginner1') : t('adminLearning.beginner2'),
                hsk: level.hskLevel,
                lessons: level.lessonCount,
              })
            }}
          </h3>
          <ul class="row-list">
            <li v-for="unit in level.units" :key="unit.id" :data-testid="`unit-row-${unit.number}`">
              <span class="admin-path-unit">
                <span class="tag is-strong admin-path-tag">{{
                  t('adminLearning.unitTag', { number: unit.number })
                }}</span>
                <span class="row-list-copy">
                  <span class="row-list-title">
                    {{ unit.title }} · <span class="cjk-text" lang="zh-CN">{{ unit.titleZh }}</span>
                  </span>
                  <span class="row-list-meta">
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
              </span>
              <span class="row-list-actions">
                <StatusPill
                  :label="unit.status === 'PUBLISHED' ? t('adminLearning.published') : t('adminLearning.draft')"
                  :tone="unit.status === 'PUBLISHED' ? 'success' : 'outline'"
                />
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :aria-label="t('adminLearning.openUnit', { number: unit.number, title: unit.title })"
                  :data-testid="`open-unit-${unit.number}`"
                  @click="unitId = unit.id"
                >
                  {{ t('adminLearning.open') }}
                </button>
              </span>
            </li>
          </ul>
        </section>
      </section>

      <section class="panel admin-table-card" aria-labelledby="level-check-title">
        <div class="admin-section-head">
          <div class="admin-list-head">
            <h2 id="level-check-title" class="card-title">{{ t('adminLearning.levelCheckTitle') }}</h2>
            <p class="admin-meta">
              {{ t('adminLearning.levelCheckDescription', { count: questions.data.value?.totalItems ?? 0 }) }}
            </p>
          </div>
          <button class="btn btn-primary" type="button" data-testid="new-question" @click="openQuestion(null)">
            {{ t('adminLearning.newQuestion') }}
          </button>
        </div>

        <StatePanel
          v-if="questions.loading.value"
          tone="loading"
          :title="t('adminLearning.questionsLoading')"
          :message="t('adminLearning.questionsLoadingHint')"
        />
        <StatePanel
          v-else-if="questions.error.value && !questionRows.length"
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
        <StatePanel
          v-else-if="!questionRows.length"
          tone="empty"
          icon="quiz"
          :title="t('adminLearning.noQuestions')"
          :message="t('adminLearning.noQuestionsHint')"
        />
        <div
          v-else
          class="data-table-wrap admin-table-wrap"
          role="region"
          :aria-label="t('adminLearning.levelCheckTitle')"
          tabindex="0"
        >
          <table class="data-table admin-table admin-questions-table">
            <thead>
              <tr>
                <th scope="col" class="admin-questions-col-prompt">{{ t('adminLearning.question') }}</th>
                <th scope="col">{{ t('adminLearning.skill') }}</th>
                <th scope="col">{{ t('adminLearning.difficulty') }}</th>
                <th scope="col">{{ t('adminLearning.level') }}</th>
                <th scope="col">{{ t('adminLearning.status') }}</th>
                <th scope="col" class="col-actions admin-col-open">
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
                <td class="admin-cell-strong">{{ row.prompt }}</td>
                <td class="admin-cell-muted">{{ t(skillKey(row.skill)) }}</td>
                <td class="admin-cell-muted">{{ t(difficultyKey(row.difficulty)) }}</td>
                <td class="admin-cell-muted">
                  {{ row.level === 'BEGINNER_1' ? t('adminLearning.beginner1') : t('adminLearning.beginner2') }}
                </td>
                <td>
                  <StatusPill
                    :label="row.status === 'PUBLISHED' ? t('adminLearning.published') : t('adminLearning.draft')"
                    :tone="row.status === 'PUBLISHED' ? 'success' : 'outline'"
                  />
                </td>
                <td class="col-actions">
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :aria-label="t('adminLearning.openQuestion', { question: row.prompt })"
                    @click.stop="openQuestion(row.id)"
                  >
                    {{ t('adminLearning.open') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          v-if="questionPages > 1"
          :current-page="questionPage + 1"
          :total-pages="questionPages"
          :label="t('adminLearning.questionPages')"
          :info="t('adminLearning.showing', questionRange)"
          icon-only
          @update:current-page="goToQuestionPage($event - 1)"
        />
      </section>
    </ResourceSection>

    <UnitDetail :unit-id="unitId" @close="unitId = null" @changed="onUnitChanged" @failed="showError" />
    <LevelCheckQuestionDrawer
      :open="questionOpen"
      :question-id="questionId"
      @close="questionOpen = false"
      @saved="onQuestionSaved"
    />
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
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
import { useToast } from '@/composables/useToast'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type { LevelCheckQuestionDetail } from '@/types/learn'
import { formatNumber } from '@/utils/format'
import { difficultyKey, skillKey } from '@/utils/learnLabels'
import '@/styles/ops-admin.css'
import '@/styles/learn-admin.css'

const QUESTION_PAGE_SIZE = 10

const { t } = useLocalMessages({ adminLearning: adminLearningMessages })
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()

const path = useResource(() => adminLearningApi.path())
const questionPage = ref(0)
const questions = useResource(() => adminLearningApi.questions(questionPage.value, QUESTION_PAGE_SIZE))
const {
  items: questionRows,
  totalPages: questionPages,
  range: questionRange,
  goToPage: goToQuestionPage,
} = useServerPage(questions, questionPage)

// ----- one unit, in a drawer -----

const unitId = ref<number | null>(null)

function onUnitChanged(_: unknown, message: string): void {
  clearFeedback()
  showToast(message)
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
  clearFeedback()
  showToast(
    question.status === 'PUBLISHED' ? t('adminLearning.questionSavedPublished') : t('adminLearning.questionSavedDraft'),
  )
  void questions.reload()
  void path.reload()
}
</script>
