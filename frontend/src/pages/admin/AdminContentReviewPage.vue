<template>
  <div class="admin-review-page page-stack">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminClasses.review.title')" :description="t('adminClasses.review.lede')" />
      <AdminContentTabs :review-queue="queue.data.value?.total ?? null" />
    </div>
    <DataNotice :error="actionError" :success="actionSuccess" />

    <ResourceSection :loading="queue.loading.value" :error="queue.error.value" @retry="queue.reload">
      <StatePanel
        v-if="queue.data.value && !queue.data.value.total && filter === 'ALL'"
        tone="empty"
        icon="check"
        spacious
        :title="t('adminClasses.review.emptyTitle')"
        :message="t('adminClasses.review.emptyMessage')"
      />
      <div v-else class="admin-inbox">
        <section class="admin-sheet admin-queue" aria-labelledby="admin-review-waiting">
          <div class="admin-sheet-head">
            <div>
              <h2 id="admin-review-waiting" class="admin-sheet-title">{{ t('adminClasses.review.waiting') }}</h2>
              <p class="admin-sheet-caption">{{ t('adminClasses.review.oldestFirst') }}</p>
            </div>
            <SegmentedControl
              v-model="filter"
              :label="t('adminClasses.review.filterLabel')"
              :options="FILTERS.map((value) => ({ value, label: t(FILTER_KEYS[value]) }))"
            />
          </div>
          <p v-if="!items.length" class="admin-queue-empty">{{ t('adminClasses.review.emptyFiltered') }}</p>
          <ul v-else class="admin-queue-list">
            <li v-for="item in items" :key="keyOf(item)">
              <button
                type="button"
                class="admin-queue-item"
                :aria-pressed="keyOf(item) === selectedKey"
                :data-testid="`review-item-${keyOf(item)}`"
                @click="select(item)"
              >
                <span class="admin-kind-mark" aria-hidden="true">
                  <IconGlyph :name="item.type === 'CLASS' ? 'classes' : 'learn'" :size="16" />
                </span>
                <span class="admin-queue-item-copy">
                  <strong>{{ item.title }}</strong>
                  <span class="admin-queue-item-tags">
                    <span class="tag">{{
                      item.type === 'CLASS' ? t('adminClasses.review.typeClass') : t('adminClasses.review.typeLesson')
                    }}</span>
                    <span class="chip">{{ t(KIND_KEYS[item.kind]) }}</span>
                    <span v-if="item.changesQuiz" class="tag">{{ t('adminClasses.review.changesQuiz') }}</span>
                  </span>
                  <span class="admin-queue-item-meta">
                    {{
                      item.kind === 'NEW_CLASS' && item.lessonTitles.length
                        ? t('adminClasses.review.withLessons', { lessons: item.lessonTitles.join(', ') })
                        : item.classTitle
                    }}
                  </span>
                  <span class="admin-queue-item-meta">
                    {{
                      t('adminClasses.review.teacherOn', {
                        teacher: item.teacherName,
                        date: formatDateTime(item.submittedAt),
                      })
                    }}
                  </span>
                </span>
              </button>
            </li>
          </ul>
        </section>

        <div class="admin-reader-slot">
          <StatePanel
            v-if="!selected"
            tone="empty"
            icon="checklist"
            spacious
            :title="t('adminClasses.review.waiting')"
            :message="t('adminClasses.review.chooseItem')"
          />
          <StatePanel
            v-else-if="detail.loading.value && !reviewDetail"
            tone="loading"
            spacious
            :title="t('classes.loading')"
            :message="selected.title"
          />
          <StatePanel
            v-else-if="detail.error.value && !reviewDetail"
            tone="error"
            icon="alert"
            spacious
            :title="t('classes.loadError')"
            :message="detail.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('classes.retry') }}</button>
            </template>
          </StatePanel>
          <section v-else-if="reviewDetail" class="panel admin-reader" aria-labelledby="admin-review-title">
            <header class="admin-reader-head">
              <div class="admin-reader-heading">
                <h2 id="admin-review-title">{{ reviewDetail.item.title }}</h2>
                <p>{{ summaryLine }}</p>
                <div class="admin-reader-tags">
                  <span class="tag">{{
                    reviewDetail.item.type === 'CLASS'
                      ? t('adminClasses.review.typeClass')
                      : t('adminClasses.review.typeLesson')
                  }}</span>
                  <span class="chip">{{ t(KIND_KEYS[reviewDetail.item.kind]) }}</span>
                  <StatusPill :label="t('adminClasses.review.inReview')" tone="info" />
                </div>
              </div>
              <RouterLink
                class="btn btn-link"
                :to="{ name: 'admin-teacher-classes', query: { keyword: reviewDetail.item.classTitle } }"
              >
                {{ t('adminClasses.review.openInTeacherClasses') }}
                <IconGlyph name="chevronRight" :size="14" />
              </RouterLink>
            </header>

            <section
              v-if="reviewDetail.classPreview"
              class="admin-reader-section"
              aria-labelledby="admin-review-preview-title"
            >
              <div class="admin-review-section-head">
                <h3 id="admin-review-preview-title" class="admin-reader-label">
                  {{ t('adminClasses.review.previewTitle') }}
                </h3>
                <span class="type-caption">{{ t('adminClasses.review.previewHint') }}</span>
              </div>
              <div class="admin-review-preview">
                <h4 class="admin-review-preview-title">{{ reviewDetail.classPreview.title }}</h4>
                <p v-if="reviewDetail.classPreview.description">{{ reviewDetail.classPreview.description }}</p>
                <p class="type-caption">
                  {{
                    t('adminClasses.review.teacherLine', {
                      teacher: reviewDetail.classPreview.teacherName,
                      units: t('classes.units', reviewDetail.classPreview.unitCount),
                      lessons: t('classes.lessons', reviewDetail.classPreview.lessonCount),
                    })
                  }}
                </p>
                <dl class="admin-facts">
                  <div>
                    <dt>{{ t('adminClasses.review.difficulty') }}</dt>
                    <dd>{{ reviewDetail.classPreview.levelName ?? t('adminClasses.review.notSet') }}</dd>
                  </div>
                  <div>
                    <dt>{{ t('adminClasses.review.topic') }}</dt>
                    <dd>{{ reviewDetail.classPreview.topicName ?? t('adminClasses.review.notSet') }}</dd>
                  </div>
                  <div>
                    <dt>{{ t('adminClasses.review.learningType') }}</dt>
                    <dd>
                      {{
                        reviewDetail.classPreview.learningType
                          ? t(LEARNING_TYPE_KEYS[reviewDetail.classPreview.learningType])
                          : t('adminClasses.review.notSet')
                      }}
                    </dd>
                  </div>
                </dl>
                <template v-if="reviewDetail.classPreview.lessons.length">
                  <h5 class="admin-reader-label">{{ t('adminClasses.review.lessons') }}</h5>
                  <ul class="admin-review-list">
                    <li v-for="lesson in reviewDetail.classPreview.lessons" :key="lesson.id">
                      <IconGlyph name="learn" :size="16" />
                      <span class="admin-cell-title">
                        <strong>{{ lesson.title }}</strong>
                        <span>{{ t('adminClasses.review.lessonUnit', { unit: lesson.unitTitle }) }}</span>
                      </span>
                    </li>
                  </ul>
                </template>
              </div>
              <p v-if="reviewDetail.item.kind === 'NEW_CLASS'" class="type-caption">
                {{ t('adminClasses.review.newClassNote') }}
                <template v-if="reviewDetail.notInReview.length">
                  {{ t('adminClasses.review.notInReview', { titles: reviewDetail.notInReview.join(', ') }) }}
                </template>
              </p>
            </section>

            <section
              v-if="
                reviewDetail.changes.length ||
                reviewDetail.item.kind === 'LESSON_REVISION' ||
                reviewDetail.item.kind === 'CLASS_DETAILS'
              "
              class="admin-reader-section"
              aria-labelledby="admin-review-changes-title"
            >
              <h3 id="admin-review-changes-title" class="admin-reader-label">
                {{ t('adminClasses.review.changesTitle') }}
              </h3>
              <ContentChangeList v-if="reviewDetail.changes.length" :changes="reviewDetail.changes" />
              <p v-else class="type-caption">{{ t('adminClasses.review.noChanges') }}</p>
            </section>

            <section
              v-if="reviewDetail.lessons.length"
              class="admin-reader-section"
              aria-labelledby="admin-review-lessons-title"
            >
              <h3 id="admin-review-lessons-title" class="admin-reader-label">
                {{
                  reviewDetail.lessons.length > 1
                    ? t('adminClasses.review.lessonsInReview')
                    : t('adminClasses.review.lessonInReview')
                }}
              </h3>
              <article v-for="lesson in reviewDetail.lessons" :key="lesson.lessonId" class="admin-review-lesson">
                <p class="type-caption">
                  {{ t('adminClasses.review.lessonPath', { classTitle: lesson.classTitle, unit: lesson.unitTitle }) }}
                </p>
                <h4 class="admin-review-preview-title"><CjkText :text="lesson.title" /></h4>
                <div class="admin-review-block">
                  <h5 class="admin-reader-label">{{ t('adminClasses.review.content') }}</h5>
                  <p><CjkText :text="lesson.summary || t('adminClasses.review.noSummary')" /></p>
                  <p v-if="lesson.videoTitle" class="admin-review-video type-caption">
                    <IconGlyph name="video" :size="14" />
                    <span
                      >{{ t('adminClasses.review.video', { title: lesson.videoTitle })
                      }}<template v-if="lesson.videoDurationSeconds">
                        · {{ formatClock(lesson.videoDurationSeconds) }}</template
                      ></span
                    >
                  </p>
                  <KeyPhraseCards v-if="lesson.keyPhrases.length" :phrases="lesson.keyPhrases" />
                </div>
                <div class="admin-review-block">
                  <h5 class="admin-reader-label">
                    {{
                      t('adminClasses.review.practice', {
                        activities: t('classes.activities', lesson.activities.length),
                      })
                    }}
                  </h5>
                  <ol class="admin-review-list">
                    <li v-for="(activity, index) in lesson.activities" :key="index">
                      <span class="admin-review-step" aria-hidden="true">{{ index + 1 }}</span>
                      <span class="admin-cell-title">
                        <strong><CjkText :text="activity.title" /></strong>
                        <span
                          >{{ t(ACTIVITY_TYPE_KEYS[activity.type])
                          }}<template v-if="activity.stage"> · {{ t(STAGE_KEYS[activity.stage]) }}</template></span
                        >
                      </span>
                      <span v-if="activity.isNew" class="chip">{{ t('adminClasses.review.newItem') }}</span>
                    </li>
                  </ol>
                  <p class="type-caption">{{ t('classes.stageHint') }}</p>
                </div>
                <ul v-if="lesson.quizzes.length" class="admin-review-list">
                  <li v-for="(quiz, index) in lesson.quizzes" :key="`q-${index}`">
                    <IconGlyph name="quiz" :size="16" />
                    <span class="admin-cell-title">
                      <strong>{{ t('adminClasses.review.quiz', { title: quiz.title }) }}</strong>
                      <span>
                        {{
                          t('adminClasses.review.quizFacts', {
                            questions: t('classes.questions', quiz.questionCount),
                            explained: quiz.everyQuestionExplained
                              ? t('adminClasses.review.explained')
                              : t('adminClasses.review.notExplained'),
                          })
                        }}<template v-if="quiz.stage"> · {{ t(STAGE_KEYS[quiz.stage]) }}</template>
                      </span>
                    </span>
                    <span v-if="quiz.isNew" class="chip">{{ t('adminClasses.review.newItem') }}</span>
                  </li>
                </ul>
              </article>
            </section>

            <section class="admin-reader-section" aria-labelledby="admin-review-history-title">
              <h3 id="admin-review-history-title" class="admin-reader-label">{{ t('adminClasses.review.history') }}</h3>
              <ContentHistory :events="reviewDetail.history" />
            </section>

            <footer class="admin-reader-decision">
              <p>{{ t('adminClasses.review.footer', { teacher: reviewDetail.item.teacherName }) }}</p>
              <button
                type="button"
                class="btn btn-secondary"
                data-testid="request-changes"
                @click="openDialog('changes')"
              >
                <IconGlyph name="send" :size="16" />{{ t('adminClasses.review.requestChanges') }}
              </button>
              <button type="button" class="btn btn-primary" data-testid="approve" @click="openDialog('approve')">
                <IconGlyph name="check" :size="16" />{{ t('adminClasses.review.approve') }}
              </button>
            </footer>
          </section>
        </div>
      </div>
    </ResourceSection>

    <ConfirmDialog
      :open="dialog === 'approve'"
      :title="t('adminClasses.review.approveTitle', { title: reviewDetail?.item.title ?? '' })"
      :message="reviewDetail ? t(APPROVE_KEYS[reviewDetail.item.kind], { teacher: reviewDetail.item.teacherName }) : ''"
      :confirm-label="t('adminClasses.review.approve')"
      :busy="busy"
      :error="dialogError"
      test-id="approve-dialog"
      @cancel="dialog = null"
      @confirm="approve"
    />
    <ConfirmDialog
      :open="dialog === 'changes'"
      :title="t('adminClasses.review.changesTitle2', { title: reviewDetail?.item.title ?? '' })"
      :message="reviewDetail ? t('adminClasses.review.changesMessage', { teacher: reviewDetail.item.teacherName }) : ''"
      :confirm-label="t('adminClasses.review.requestChanges')"
      :busy="busy"
      :confirm-disabled="!reason.trim()"
      :error="dialogError"
      test-id="changes-dialog"
      @cancel="dialog = null"
      @confirm="requestChanges"
    >
      <div class="field">
        <label class="field-label" for="review-reason">
          {{ t('adminClasses.review.reason') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          id="review-reason"
          v-model="reason"
          class="textarea"
          rows="4"
          maxlength="1000"
          required
          :placeholder="t('adminClasses.review.reasonPlaceholder')"
          aria-describedby="review-reason-hint"
          data-testid="review-reason"
        />
        <p id="review-reason-hint" class="field-hint">{{ t('adminClasses.review.reasonHint') }}</p>
      </div>
    </ConfirmDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { adminClassesApi } from '@/api/classes'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ContentChangeList from '@/components/classes/ContentChangeList.vue'
import ContentHistory from '@/components/classes/ContentHistory.vue'
import KeyPhraseCards from '@/components/classes/KeyPhraseCards.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import CjkText from '@/components/learning/CjkText.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ReviewKind, ReviewQueueItem } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { ACTIVITY_TYPE_KEYS, LEARNING_TYPE_KEYS, STAGE_KEYS } from '@/utils/classes'
import { formatClock, formatDateTime } from '@/utils/format'
import '@/styles/classes.css'
import '@/styles/ops-admin.css'

const { t } = useLocalMessages({ adminClasses: adminClassesMessages, classes: classesMessages })
const { actionError, actionSuccess, showSuccess } = useActionFeedback()

type Filter = 'ALL' | 'CLASS' | 'LESSON' | 'QUIZ'
const FILTERS: readonly Filter[] = ['ALL', 'CLASS', 'LESSON', 'QUIZ']
const FILTER_KEYS: Record<Filter, string> = {
  ALL: 'adminClasses.review.filters.ALL',
  CLASS: 'adminClasses.review.filters.CLASS',
  LESSON: 'adminClasses.review.filters.LESSON',
  QUIZ: 'adminClasses.review.filters.QUIZ',
}
const KIND_KEYS: Record<ReviewKind, string> = {
  NEW_CLASS: 'adminClasses.review.kind.NEW_CLASS',
  CLASS_DETAILS: 'adminClasses.review.kind.CLASS_DETAILS',
  NEW_LESSON: 'adminClasses.review.kind.NEW_LESSON',
  LESSON_REVISION: 'adminClasses.review.kind.LESSON_REVISION',
}
const SUMMARY_KEYS: Record<ReviewKind, string> = {
  NEW_CLASS: 'adminClasses.review.summary.NEW_CLASS',
  CLASS_DETAILS: 'adminClasses.review.summary.CLASS_DETAILS',
  NEW_LESSON: 'adminClasses.review.summary.NEW_LESSON',
  LESSON_REVISION: 'adminClasses.review.summary.LESSON_REVISION',
}
const APPROVE_KEYS: Record<ReviewKind, string> = {
  NEW_CLASS: 'adminClasses.review.approveMessage.NEW_CLASS',
  CLASS_DETAILS: 'adminClasses.review.approveMessage.CLASS_DETAILS',
  NEW_LESSON: 'adminClasses.review.approveMessage.NEW_LESSON',
  LESSON_REVISION: 'adminClasses.review.approveMessage.LESSON_REVISION',
}

const filter = ref<Filter>('ALL')
const queue = useResource(() => adminClassesApi.queue(filter.value))
const items = computed(() => queue.data.value?.items ?? [])
watch(filter, () => void queue.reload())

const selected = ref<ReviewQueueItem | null>(null)
const selectedKey = computed(() => (selected.value ? keyOf(selected.value) : null))
const detail = useResource(
  () =>
    selected.value!.type === 'CLASS'
      ? adminClassesApi.classReview(selected.value!.classId)
      : adminClassesApi.lessonReview(selected.value!.lessonId!),
  false,
)
const reviewDetail = computed(() =>
  detail.data.value && selected.value && keyOf(detail.data.value.item) === keyOf(selected.value)
    ? detail.data.value
    : null,
)

function keyOf(item: ReviewQueueItem): string {
  return item.type === 'CLASS' ? `class-${item.classId}` : `lesson-${item.lessonId}`
}

function select(item: ReviewQueueItem): void {
  selected.value = item
  void detail.reload()
}

/** The oldest item is opened first; after a decision the next one is. */
watch(items, (current) => {
  if (!current.length) {
    selected.value = null
    return
  }
  if (!selected.value || !current.some((item) => keyOf(item) === selectedKey.value)) select(current[0]!)
})

const summaryLine = computed(() => {
  const current = reviewDetail.value
  if (!current) return ''
  return t(SUMMARY_KEYS[current.item.kind], {
    lessons: t('classes.lessons', current.item.lessonTitles.length),
    classTitle: current.item.classTitle,
    teacher: current.item.teacherName,
    date: formatDateTime(current.item.submittedAt),
  })
})

// ----- decisions -----

const dialog = ref<'approve' | 'changes' | null>(null)
const dialogError = ref<string | null>(null)
const reason = ref('')
const busy = ref(false)

function openDialog(kind: 'approve' | 'changes'): void {
  dialogError.value = null
  reason.value = ''
  dialog.value = kind
}

async function decide(action: () => Promise<unknown>, success: string, fallback: string): Promise<void> {
  busy.value = true
  dialogError.value = null
  try {
    await action()
    dialog.value = null
    selected.value = null
    showSuccess(success)
    await queue.reload()
  } catch (failure) {
    dialogError.value = resolveApiError(failure, fallback)
  } finally {
    busy.value = false
  }
}

function targetOf(item: ReviewQueueItem): { type: 'CLASS' | 'LESSON'; id: number } {
  return item.type === 'CLASS' ? { type: 'CLASS', id: item.classId } : { type: 'LESSON', id: item.lessonId! }
}

function approve(): Promise<void> {
  const item = reviewDetail.value!.item
  const target = targetOf(item)
  return decide(
    () => adminClassesApi.approve(target.type, target.id),
    t('adminClasses.review.approved', { title: item.title }),
    t('adminClasses.review.approveError'),
  )
}

function requestChanges(): Promise<void> {
  const item = reviewDetail.value!.item
  const target = targetOf(item)
  return decide(
    () => adminClassesApi.requestChanges(target.type, target.id, reason.value.trim()),
    t('adminClasses.review.changesSent', { teacher: item.teacherName }),
    t('adminClasses.review.changesError'),
  )
}
</script>
