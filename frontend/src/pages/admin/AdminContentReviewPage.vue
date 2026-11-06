<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminClasses.review.title')" hide-title />
    <AdminContentTabs :review-queue="queue.data.value?.total ?? null" />
    <DataNotice :error="actionError" />

    <ResourceSection :loading="queue.loading.value" :error="queue.error.value" @retry="queue.reload">
      <div v-if="queue.data.value && !queue.data.value.total" class="panel">
        <StatePanel
          tone="empty"
          icon="check"
          spacious
          :title="t('adminClasses.review.emptyTitle')"
          :message="t('adminClasses.review.emptyMessage')"
        >
          <template #actions>
            <RouterLink class="btn btn-secondary" :to="{ name: 'admin-teacher-classes' }">
              {{ t('adminClasses.review.openInTeacherClasses') }}
            </RouterLink>
          </template>
        </StatePanel>
      </div>
      <div v-else class="list-detail admin-list-detail">
        <section class="panel admin-list" aria-labelledby="admin-review-waiting">
          <header class="admin-list-head">
            <h2 id="admin-review-waiting" class="card-title">{{ t('adminClasses.review.waiting') }}</h2>
            <p class="admin-meta">{{ t('adminClasses.review.oldestFirst') }}</p>
          </header>
          <FilterChips v-model="filter" :label="t('adminClasses.review.filterLabel')" :options="filterOptions" />
          <p v-if="!items.length" class="admin-meta">{{ t('adminClasses.review.emptyFiltered') }}</p>
          <ul v-else class="admin-select-list">
            <li v-for="item in items" :key="keyOf(item)">
              <button
                type="button"
                class="select-card"
                :aria-pressed="keyOf(item) === selectedKey"
                :data-testid="`review-item-${keyOf(item)}`"
                @click="select(item)"
              >
                <strong class="admin-select-title">{{ item.title }}</strong>
                <span class="admin-tags">
                  <span class="tag is-outline">{{ typeLabel(item) }}</span>
                  <span class="tag" :class="{ 'is-strong': item.kind === 'LESSON_REVISION' }">
                    {{ t(KIND_KEYS[item.kind]) }}
                  </span>
                </span>
                <span class="admin-meta">
                  {{
                    item.kind === 'NEW_CLASS' && item.lessonTitles.length
                      ? t('adminClasses.review.withLessons', { lessons: item.lessonTitles.join(', ') })
                      : item.classTitle
                  }}
                </span>
                <span class="admin-meta">
                  {{
                    t('adminClasses.review.teacherOn', {
                      teacher: item.teacherName,
                      date: formatDateTime(item.submittedAt),
                    })
                  }}
                </span>
              </button>
            </li>
          </ul>
        </section>

        <div class="admin-detail-slot">
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
            :title="t('adminClasses.review.loading')"
            :message="selected.title"
          />
          <StatePanel
            v-else-if="detail.error.value && !reviewDetail"
            tone="error"
            icon="alert"
            spacious
            :title="t('adminClasses.review.loadError')"
            :message="detail.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="detail.reload">
                {{ t('adminClasses.review.retry') }}
              </button>
            </template>
          </StatePanel>
          <section v-else-if="reviewDetail" class="panel admin-detail" aria-labelledby="admin-review-title">
            <header class="admin-detail-head">
              <div class="admin-detail-heading">
                <h2 id="admin-review-title" class="admin-detail-title">{{ reviewDetail.item.title }}</h2>
                <div class="admin-tags">
                  <span class="tag is-outline">{{ typeLabel(reviewDetail.item) }}</span>
                  <span class="tag" :class="{ 'is-strong': reviewDetail.item.kind === 'LESSON_REVISION' }">
                    {{ t(KIND_KEYS[reviewDetail.item.kind]) }}
                  </span>
                  <StatusPill :label="t('adminClasses.review.inReview')" tone="strong" />
                </div>
                <p class="admin-meta">{{ summaryLine }}</p>
              </div>
              <RouterLink
                class="btn btn-ghost btn-sm admin-link-action"
                :to="{
                  name: 'admin-teacher-classes',
                  query: { keyword: reviewDetail.item.classTitle ?? reviewDetail.item.title },
                }"
              >
                {{ t('adminClasses.review.openInTeacherClasses') }}
                <IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </header>

            <section
              v-if="reviewDetail.changes.length || isUpdate"
              class="admin-review-changes"
              aria-labelledby="admin-review-changes-title"
            >
              <h3 id="admin-review-changes-title" class="admin-section-title admin-review-changes-title">
                <IconGlyph name="layers" :size="16" />
                {{ t('adminClasses.review.changesTitle') }}
              </h3>
              <ContentChangeList
                v-if="reviewDetail.changes.length"
                class="admin-changes"
                :changes="reviewDetail.changes"
              />
              <p v-else class="admin-meta">{{ t('adminClasses.review.noChanges') }}</p>
              <p v-if="reviewDetail.item.kind === 'LESSON_REVISION'" class="callout is-compact">
                <IconGlyph name="info" :size="16" />
                <span>{{ t('adminClasses.review.staysLive') }}</span>
              </p>
            </section>

            <section v-if="classPreview" class="admin-section" aria-labelledby="admin-review-preview-title">
              <div class="admin-section-head">
                <h3 id="admin-review-preview-title" class="admin-section-title">
                  {{ t('adminClasses.review.previewTitle') }}
                </h3>
                <span class="admin-meta">{{ t('adminClasses.review.previewHint') }}</span>
              </div>
              <div class="admin-sunken admin-review-preview">
                <div class="admin-review-preview-head">
                  <h4 class="admin-review-preview-title">{{ classPreview.title }}</h4>
                  <p class="admin-meta">
                    {{
                      t('adminClasses.review.teacherLine', {
                        teacher: classPreview.teacherName,
                        units: t('adminClasses.review.units', classPreview.unitCount),
                        lessons: t('adminClasses.review.lessonsCount', classPreview.lessonCount),
                      })
                    }}
                  </p>
                </div>
                <p v-if="classPreview.description" class="admin-review-text">{{ classPreview.description }}</p>
                <dl class="admin-review-facts">
                  <div>
                    <dt>{{ t('adminClasses.review.theme') }}</dt>
                    <dd>{{ classPreview.topicName ?? t('adminClasses.review.notSet') }}</dd>
                  </div>
                  <div>
                    <dt>{{ t('adminClasses.review.bestAfter') }}</dt>
                    <dd>
                      {{
                        classPreview.bestAfterUnit
                          ? t('adminClasses.review.bestAfterUnit', { unit: classPreview.bestAfterUnit })
                          : t('adminClasses.review.notSet')
                      }}
                    </dd>
                  </div>
                </dl>
                <div v-if="classPreview.lessons.length" class="admin-section">
                  <h5 class="admin-review-block-title">{{ t('adminClasses.review.lessons') }}</h5>
                  <ul class="row-list">
                    <li v-for="lesson in classPreview.lessons" :key="lesson.id">
                      <span class="row-list-copy">
                        <span class="row-list-title">{{ lesson.title }}</span>
                        <span class="row-list-meta">{{
                          t('adminClasses.review.lessonUnit', { unit: lesson.unitTitle })
                        }}</span>
                      </span>
                    </li>
                  </ul>
                </div>
              </div>
              <p v-if="reviewDetail.notInReview.length" class="admin-meta">
                {{ t('adminClasses.review.notInReview', { titles: reviewDetail.notInReview.join(', ') }) }}
              </p>
            </section>

            <section
              v-if="reviewDetail.lessons.length"
              class="admin-section"
              aria-labelledby="admin-review-lessons-title"
            >
              <div v-if="classPreview" class="admin-section-head">
                <h3 id="admin-review-lessons-title" class="admin-overline">
                  {{
                    reviewDetail.lessons.length > 1
                      ? t('adminClasses.review.lessonsInReview')
                      : t('adminClasses.review.lessonInReview')
                  }}
                </h3>
              </div>
              <div v-else class="admin-section-head">
                <h3 id="admin-review-lessons-title" class="admin-section-title">
                  {{ t('adminClasses.review.previewTitle') }}
                </h3>
                <span class="admin-meta">{{ t('adminClasses.review.previewHint') }}</span>
              </div>
              <article
                v-for="lesson in reviewDetail.lessons"
                :key="lesson.lessonId"
                class="admin-sunken admin-review-lesson"
              >
                <div class="admin-review-preview-head">
                  <p class="admin-meta">
                    {{ t('adminClasses.review.lessonPath', { classTitle: lesson.classTitle, unit: lesson.unitTitle }) }}
                  </p>
                  <h4 class="admin-review-preview-title"><CjkText :text="lesson.title" /></h4>
                </div>
                <div class="admin-section">
                  <h5 class="admin-review-block-title">{{ t('adminClasses.review.content') }}</h5>
                  <p v-if="lesson.summary" class="admin-review-text"><CjkText :text="lesson.summary" /></p>
                  <p v-if="lesson.videoTitle" class="admin-review-video admin-meta">
                    <IconGlyph name="video" :size="14" />
                    <span
                      >{{ t('adminClasses.review.video', { title: lesson.videoTitle })
                      }}<template v-if="lesson.videoDurationSeconds">
                        · {{ formatClock(lesson.videoDurationSeconds) }}</template
                      ></span
                    >
                  </p>
                  <ul v-if="lesson.keyPhrases.length" class="admin-phrases">
                    <li
                      v-for="(phrase, index) in lesson.keyPhrases"
                      :key="`${phrase.chinese}-${index}`"
                      class="admin-phrase"
                    >
                      <strong class="hanzi" lang="zh-CN">{{ phrase.chinese }}</strong>
                      <span class="admin-meta">{{ phrase.pinyin }}</span>
                      <span class="admin-meta">{{ phrase.meaning }}</span>
                    </li>
                  </ul>
                </div>
                <div class="admin-section">
                  <h5 class="admin-review-block-title">
                    {{
                      t('adminClasses.review.practice', {
                        activities: t('adminClasses.review.activities', lesson.activities.length),
                      })
                    }}
                  </h5>
                  <ol class="row-list">
                    <li v-for="(activity, index) in lesson.activities" :key="index">
                      <span class="admin-review-activity">
                        <span class="admin-step" aria-hidden="true">{{ index + 1 }}</span>
                        <span class="row-list-copy">
                          <span class="row-list-title"><CjkText :text="activity.title" /></span>
                          <span class="row-list-meta">{{ t(ACTIVITY_TYPE_KEYS[activity.type]) }}</span>
                        </span>
                      </span>
                      <StatusPill v-if="activityBadge(activity)" :label="activityBadge(activity)" tone="neutral" />
                    </li>
                    <li v-for="(quiz, index) in lesson.quizzes" :key="`q-${index}`">
                      <span class="row-list-copy">
                        <span class="row-list-title">{{ t('adminClasses.review.quiz', { title: quiz.title }) }}</span>
                        <span class="row-list-meta">
                          {{ t('adminClasses.review.questions', quiz.questionCount) }}
                          <template v-if="!quiz.everyQuestionExplained">
                            · {{ t('adminClasses.review.notExplained') }}
                          </template>
                        </span>
                      </span>
                      <StatusPill v-if="quizBadge(quiz)" :label="quizBadge(quiz)" tone="neutral" />
                    </li>
                  </ol>
                </div>
              </article>
            </section>

            <section class="admin-section" aria-labelledby="admin-review-history-title">
              <h3 id="admin-review-history-title" class="admin-section-title">
                {{ t('adminClasses.review.history') }}
              </h3>
              <AdminContentHistory :events="reviewDetail.history" />
            </section>

            <footer class="card-footer">
              <button
                type="button"
                class="btn btn-secondary"
                data-testid="request-changes"
                @click="openDialog('changes')"
              >
                <IconGlyph name="send" :size="16" />
                <span>{{ t('adminClasses.review.requestChanges') }}</span>
              </button>
              <button type="button" class="btn btn-primary" data-testid="approve" @click="openDialog('approve')">
                <IconGlyph name="check" :size="16" />
                <span>{{ t('adminClasses.review.approve') }}</span>
              </button>
            </footer>
          </section>
        </div>
      </div>
    </ResourceSection>

    <AdminDecisionDialog
      :open="dialog === 'approve'"
      :title="t('adminClasses.review.approveTitle', { title: reviewDetail?.item.title ?? '' })"
      :subtitle="dialogSubtitle"
      :confirm-label="t('adminClasses.review.approve')"
      confirm-icon="check"
      :busy="busy"
      :error="dialogError"
      test-id="approve-dialog"
      @cancel="dialog = null"
      @confirm="approve"
    >
      <div v-if="reviewDetail" class="callout">
        <IconGlyph name="check" :size="16" />
        <div class="admin-callout-copy">
          <strong>{{ t(APPROVE_KEYS[reviewDetail.item.kind]) }}</strong>
          <span class="admin-meta">{{
            t('adminClasses.review.teacherNotified', { teacher: reviewDetail.item.teacherName })
          }}</span>
        </div>
      </div>
    </AdminDecisionDialog>
    <AdminDecisionDialog
      :open="dialog === 'changes'"
      :title="t('adminClasses.review.changesDialogTitle')"
      :subtitle="dialogSubtitle"
      :confirm-label="t('adminClasses.review.requestChanges')"
      confirm-icon="send"
      :busy="busy"
      :error="dialogError"
      test-id="changes-dialog"
      @cancel="dialog = null"
      @confirm="requestChanges"
    >
      <p v-if="reviewDetail" class="callout">
        <IconGlyph name="send" :size="16" />
        <span>{{ t('adminClasses.review.changesMessage', { teacher: reviewDetail.item.teacherName }) }}</span>
      </p>
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
          data-autofocus
          :aria-invalid="Boolean(reasonError)"
          :aria-describedby="reasonError ? 'review-reason-error' : 'review-reason-hint'"
          data-testid="review-reason"
        />
        <p id="review-reason-hint" class="field-hint">
          {{ t('adminClasses.review.reasonHint', { teacher: reviewDetail?.item.teacherName ?? '' }) }}
        </p>
        <p v-if="reasonError" id="review-reason-error" class="field-error" role="alert">{{ reasonError }}</p>
      </div>
    </AdminDecisionDialog>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { adminClassesApi } from '@/api/classes'
import AdminContentHistory from '@/components/admin/AdminContentHistory.vue'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import AdminDecisionDialog from '@/components/admin/AdminDecisionDialog.vue'
import ContentChangeList from '@/components/classes/ContentChangeList.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import CjkText from '@/components/learning/CjkText.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import type { ClassActivityType, ReviewKind, ReviewLessonPreview, ReviewQueueItem } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, formatDateTime } from '@/utils/format'
import '@/styles/ops-admin.css'

const { t } = useLocalMessages({ adminClasses: adminClassesMessages })
const route = useRoute()
const { actionError, clearFeedback } = useActionFeedback()
const { toast, showToast } = useToast()

type Filter = 'ALL' | 'CLASS' | 'LESSON'

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
const ACTIVITY_TYPE_KEYS: Record<ClassActivityType, string> = {
  FLASHCARDS: 'adminClasses.review.activityType.FLASHCARDS',
  TRACING: 'adminClasses.review.activityType.TRACING',
  MATCHING: 'adminClasses.review.activityType.MATCHING',
}

const filter = ref<Filter>('ALL')
const queue = useResource(() => adminClassesApi.queue('ALL'))
const allItems = computed(() => queue.data.value?.items ?? [])
const items = computed(() =>
  filter.value === 'ALL' ? allItems.value : allItems.value.filter((item) => item.type === filter.value),
)
const filterOptions = computed(() => [
  { value: 'ALL' as Filter, label: t('adminClasses.review.filters.ALL'), count: allItems.value.length },
  {
    value: 'CLASS' as Filter,
    label: t('adminClasses.review.filters.CLASS'),
    count: allItems.value.filter((item) => item.type === 'CLASS').length,
  },
  {
    value: 'LESSON' as Filter,
    label: t('adminClasses.review.filters.LESSON'),
    count: allItems.value.filter((item) => item.type === 'LESSON').length,
  },
])

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
const classPreview = computed(() => reviewDetail.value?.classPreview ?? null)
const isUpdate = computed(() => {
  const kind = reviewDetail.value?.item.kind
  return kind === 'LESSON_REVISION' || kind === 'CLASS_DETAILS'
})

function keyOf(item: ReviewQueueItem): string {
  return item.type === 'CLASS' ? `class-${item.classId}` : `lesson-${item.lessonId}`
}

function typeLabel(item: ReviewQueueItem): string {
  return item.type === 'CLASS' ? t('adminClasses.review.typeClass') : t('adminClasses.review.typeLesson')
}

function select(item: ReviewQueueItem): void {
  selected.value = item
  void detail.reload()
}

/** The item the address names (the dashboard links to it), else the oldest; after a decision the next one opens. */
watch(items, (current) => {
  if (!current.length) {
    selected.value = null
    return
  }
  if (selected.value && current.some((item) => keyOf(item) === selectedKey.value)) return
  select(current.find((item) => keyOf(item) === route.query.item) ?? current[0]!)
})

const summaryLine = computed(() => {
  const current = reviewDetail.value
  if (!current) return ''
  return t(SUMMARY_KEYS[current.item.kind], {
    lessons: t('adminClasses.review.lessonsCount', current.item.lessonTitles.length),
    classTitle: current.item.classTitle,
    teacher: current.item.teacherName,
    date: formatDateTime(current.item.submittedAt),
  })
})

/** What the dialogs say the decision is about. */
const dialogSubtitle = computed(() => {
  const current = reviewDetail.value
  if (!current) return ''
  return t('adminClasses.review.dialogSubtitle', {
    kind: t(KIND_KEYS[current.item.kind]),
    title: current.item.classTitle ?? current.item.title,
    teacher: current.item.teacherName,
  })
})

function changed(kind: string, title: string): boolean {
  return (reviewDetail.value?.changes ?? []).some((change) => change.kind === kind && change.title === title)
}

/** "New" or "Updated" beside the activities an update touches. */
function activityBadge(activity: ReviewLessonPreview['activities'][number]): string {
  if (activity.isNew) return t('adminClasses.review.newItem')
  return changed('ACTIVITY_CHANGED', activity.title) ? t('adminClasses.review.updatedItem') : ''
}

function quizBadge(quiz: ReviewLessonPreview['quizzes'][number]): string {
  if (quiz.isNew) return t('adminClasses.review.newItem')
  return changed('QUESTIONS_CHANGED', quiz.title) ? t('adminClasses.review.updatedItem') : ''
}

// ----- decisions -----

const dialog = ref<'approve' | 'changes' | null>(null)
const dialogError = ref<string | null>(null)
const reason = ref('')
const reasonError = ref<string | null>(null)
const busy = ref(false)

function openDialog(kind: 'approve' | 'changes'): void {
  dialogError.value = null
  reasonError.value = null
  reason.value = ''
  dialog.value = kind
}

async function decide(action: () => Promise<unknown>, success: string, fallback: string): Promise<void> {
  busy.value = true
  dialogError.value = null
  clearFeedback()
  try {
    await action()
    dialog.value = null
    selected.value = null
    showToast(success)
    notifyProgressChanged()
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

async function requestChanges(): Promise<void> {
  const item = reviewDetail.value!.item
  const target = targetOf(item)
  const text = reason.value.trim()
  reasonError.value = text ? null : t('adminClasses.review.reasonMissing')
  if (!text) return
  await decide(
    () => adminClassesApi.requestChanges(target.type, target.id, text),
    t('adminClasses.review.changesSent', { teacher: item.teacherName }),
    t('adminClasses.review.changesError'),
  )
}
</script>
