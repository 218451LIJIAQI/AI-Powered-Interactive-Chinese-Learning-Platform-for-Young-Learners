<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('nav.learningPath') }}</h1>
    <AdminContentTabs skip-units />

    <StatePanel
      v-if="path.loading.value"
      heading-tag="h2"
      tone="loading"
      spacious
      :title="t('common.loadingTitle')"
      :message="t('common.loadingMessage')"
    />
    <StatePanel
      v-else-if="path.error.value"
      heading-tag="h2"
      tone="error"
      spacious
      :title="t('common.errorTitle')"
      :message="path.error.value"
      @retry="path.reload"
    />
    <section v-else-if="path.data.value" class="panel admin-path" aria-labelledby="learning-path-title">
      <h2 id="learning-path-title" class="card-title">{{ t('adminLearning.unitsTitle') }}</h2>
      <StatePanel
        v-if="!path.data.value.levels.length"
        tone="empty"
        icon="route"
        :title="t('adminLearning.noUnits')"
        :message="t('adminLearning.noUnitsHint')"
      />
      <section
        v-for="level in path.data.value.levels"
        :key="level.level"
        class="admin-path-level"
        :aria-labelledby="`learning-path-level-${level.level}`"
      >
        <h3 :id="`learning-path-level-${level.level}`" class="page-eyebrow">
          {{ hskLevelName(t, level.hskLevel) }}
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
                <span class="row-list-meta">{{ unitRowMeta(t, unit) }}</span>
              </span>
            </span>
            <span class="row-list-actions">
              <StatusPill :label="t(setStatusKey(unit.status))" :tone="contentStatusTone(unit.status)" />
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                :aria-label="t('adminLearning.openUnit', { number: unit.number, title: unit.title })"
                :data-testid="`open-unit-${unit.number}`"
                @click="openUnit(unit.id)"
              >
                {{ t('common.open') }}
              </button>
            </span>
          </li>
        </ul>
      </section>
    </section>

    <section class="panel admin-table-card">
      <div class="section-header">
        <h2 id="level-check-title" class="card-title title-with-count">
          {{ t('adminLearning.levelCheckTitle') }}
          <span v-if="questionTotal" class="count-badge" aria-hidden="true">{{ formatBadgeCount(questionTotal) }}</span>
          <span v-if="questionTotal" class="sr-only">
            {{ t('adminLearning.levelCheckDescription', { count: formatNumber(questionTotal) }, questionTotal) }}
          </span>
        </h2>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="publishingId !== null"
          data-testid="new-question"
          @click="openQuestion(null)"
        >
          <IconGlyph name="plus" :size="16" />
          <span>{{ t('adminLearning.newQuestion') }}</span>
        </button>
      </div>
      <DataNotice :error="questionActionError" />

      <StatePanel
        v-if="questions.loading.value"
        tone="loading"
        :title="t('adminLearning.questionsLoading')"
        :message="t('adminLearning.questionsLoadingHint')"
      />
      <StatePanel
        v-else-if="questions.error.value"
        tone="error"
        :title="t('adminLearning.questionsError')"
        :message="questions.error.value"
        @retry="questions.reload"
      />
      <StatePanel
        v-else-if="!questionRows.length"
        tone="empty"
        icon="quiz"
        :title="t('adminLearning.noQuestions')"
        :message="t('adminLearning.noQuestionsHint')"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" :disabled="publishingId !== null" @click="openQuestion(null)">
            <IconGlyph name="plus" :size="16" />
            <span>{{ t('adminLearning.newQuestion') }}</span>
          </button>
        </template>
      </StatePanel>
      <template v-else>
        <div
          ref="questionsTableRef"
          class="data-table-wrap admin-table-wrap"
          role="region"
          aria-labelledby="level-check-title"
          tabindex="0"
        >
          <table class="data-table admin-table admin-table-fixed admin-questions-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminLearning.question') }}</th>
                <th scope="col" class="admin-questions-col-skill">{{ t('adminLearning.skill') }}</th>
                <th scope="col" class="admin-questions-col-difficulty">{{ t('adminLearning.difficulty') }}</th>
                <th scope="col" class="admin-questions-col-level">{{ t('adminLearning.level') }}</th>
                <th scope="col" class="admin-questions-col-status">{{ t('adminLearning.status') }}</th>
                <th scope="col" class="col-actions admin-questions-col-actions">
                  <span class="sr-only">{{ t('adminLearning.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in questionRows"
                :key="row.id"
                :class="{ 'is-selected': row.id === questionId }"
                :data-question-id="row.id"
                data-testid="question-row"
                @click="onQuestionRowClick(row.id)"
              >
                <td class="admin-cell-strong"><CjkText :text="row.prompt" /></td>
                <td class="admin-cell-muted">{{ t(SKILL_KEYS[row.skill]) }}</td>
                <td class="admin-cell-muted">{{ t(DIFFICULTY_KEYS[row.difficulty]) }}</td>
                <td class="admin-cell-muted">{{ t('hsk.number', { level: LEVEL_HSK[row.level] }) }}</td>
                <td><StatusPill :label="t(setStatusKey(row.status))" :tone="contentStatusTone(row.status)" /></td>
                <td class="col-actions">
                  <span class="admin-questions-actions">
                    <!-- A draft is not asked in level checks until it is published (A4.06 → A4.16). -->
                    <button
                      v-if="row.status === 'DRAFT'"
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :disabled="publishingId !== null"
                      :aria-busy="publishingId === row.id"
                      :aria-label="t('adminLearning.publishQuestionLabel', { question: row.prompt })"
                      data-testid="publish-question"
                      @click.stop="setQuestionPublished(row.id, true)"
                    >
                      {{ t('adminLearning.publish') }}
                    </button>
                    <button
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :disabled="publishingId !== null"
                      :aria-label="t('adminLearning.openQuestion', { question: row.prompt })"
                      @click.stop="openQuestion(row.id)"
                    >
                      {{ t('common.open') }}
                    </button>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          :page="displayedPage"
          :total-pages="questionPages"
          :label="t('adminLearning.questionPages')"
          icon-only
          @update:page="goToQuestionPage"
        />
      </template>
    </section>

    <UnitDetail v-if="unitId !== null" :unit-id="unitId" @close="unitId = null" @changed="onUnitChanged" />
    <LevelCheckQuestionDrawer
      v-if="questionId !== undefined"
      :question-id="questionId"
      @close="questionId = undefined"
      @saved="onQuestionSaved"
    />
    <AppToast :message="toast" :action="toastAction" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import { syncContentCount } from '@/components/admin/adminContentCounts'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import { contentStatusTone } from '@/components/admin/adminDisplay'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import LevelCheckQuestionDrawer from '@/components/learn/admin/LevelCheckQuestionDrawer.vue'
import UnitDetail from '@/components/learn/admin/UnitDetail.vue'
import { unitRowMeta } from '@/components/learn/admin/unitLessons'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminLearningPathMessages } from '@/plugins/adminLearningPathMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import type { LevelCheckQuestionDetail } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatBadgeCount, formatNumber } from '@/utils/format'
import { hskLevelName } from '@/utils/hsk'
import { DIFFICULTY_KEYS, LEVEL_HSK, setStatusKey, SKILL_KEYS } from '@/utils/learnLabels'
import '@/styles/admin-shared.css'
import '@/styles/learn-admin.css'

/** Eight questions a page (A4.01). */
const QUESTION_PAGE_SIZE = 8

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminLearningPathMessages] })
const { toast, toastAction, showToast, hideToast } = useToast()

const path = useResource(adminLearningApi.path)
watch(
  path.data,
  (loaded) => {
    if (loaded) syncContentCount('units', loaded.unitCount)
  },
  { immediate: true },
)

const questionPage = ref(0)
const questions = useResource(() => adminLearningApi.questions(questionPage.value, QUESTION_PAGE_SIZE))
const {
  items: questionRows,
  displayedPage,
  totalItems: questionTotal,
  totalPages: questionPages,
  goToPage: goToQuestionPage,
} = useServerPage(questions, questionPage)

// One unit, in a drawer

const unitId = ref<number | null>(null)

function openUnit(id: number): void {
  if (toastAction.value) hideToast()
  unitId.value = id
}

function onUnitChanged(message: string): void {
  showToast(message)
  void path.reload()
}

// One level check question, in a drawer (a null id is a new question)

const questionId = ref<number | null>()
const questionsTableRef = ref<HTMLElement | null>(null)
/** Why the last Publish of a row, or its Undo, failed (the page's notice: no drawer is open then). */
const questionActionError = ref<string | null>(null)
/** The row whose Publish (or its Undo) is on its way: one at a time. */
const publishingId = ref<number | null>(null)

function onQuestionRowClick(id: number): void {
  if (window.getSelection()?.toString()) return
  openQuestion(id)
}

function openQuestion(id: number | null): void {
  if (publishingId.value !== null) return
  if (toastAction.value) hideToast()
  questionId.value = id
}

/**
 * A new question is saved as a draft unless its Status says otherwise: a draft waits for Publish on its row (A4.06).
 * The toast says how the question was kept; publishing a new or draft question can be taken back with Undo.
 */
function onQuestionSaved(question: LevelCheckQuestionDetail): void {
  const published = question.status === 'PUBLISHED'
  const isNew = typeof questionId.value !== 'number'
  const wasDraft = !isNew && questionRows.value.find((row) => row.id === question.id)?.status === 'DRAFT'
  questionId.value = undefined
  questionActionError.value = null
  if ((isNew || wasDraft) && published) showPublished(question.id)
  else if (isNew) showToast(t('adminLearning.questionAddedDraft'))
  else showToast(t(published ? 'adminLearning.questionSaved' : 'adminLearning.questionSavedDraft'))
  void questions.reload()
}

/** "Question published. It can now appear in level checks.", with the Undo that makes it a draft again. */
function showPublished(id: number): void {
  showToast(t('adminLearning.questionPublished'), {
    label: t('common.undo'),
    run: () => void setQuestionPublished(id, false),
  })
}

/**
 * Publishes a draft question from its row, as it is stored (A4.16), or, as the Undo of Publish, makes it a draft again
 * so new learners are not asked it; then lists the questions again.
 */
async function setQuestionPublished(id: number, publish: boolean): Promise<void> {
  if (publishingId.value !== null || questionId.value !== undefined) return
  const hadFocus = Boolean(document.activeElement && questionsTableRef.value?.contains(document.activeElement))
  hideToast()
  publishingId.value = id
  questionActionError.value = null
  try {
    await (publish ? adminLearningApi.publishQuestion(id) : adminLearningApi.unpublishQuestion(id))
    await questions.reload()
    if (publish) showPublished(id)
    else showToast(t('adminLearning.questionUnpublished'))
    if (hadFocus && (!document.activeElement || document.activeElement === document.body)) {
      await nextTick()
      const rowBtn = questionsTableRef.value?.querySelector<HTMLButtonElement>(
        `tr[data-question-id="${id}"] .admin-questions-actions button:last-child`,
      )
      ;(rowBtn ?? questionsTableRef.value)?.focus()
    }
  } catch (reason) {
    const failed = publish ? 'adminLearning.questionPublishFailed' : 'adminLearning.saveFailed'
    questionActionError.value = resolveApiError(reason, t(failed))
  } finally {
    publishingId.value = null
  }
}
</script>
