<template>
  <section
    class="panel teacher-practice-review"
    data-testid="practice-review-section"
    aria-labelledby="practice-review-title"
  >
    <div class="section-header">
      <div class="teacher-practice-review-heading">
        <h2 id="practice-review-title" class="section-title">{{ t('teacherFeedback.practiceTitle') }}</h2>
        <p class="section-description">{{ t('teacherFeedback.practiceDescription') }}</p>
      </div>
      <div class="section-header-actions">
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          :aria-busy="resource.loading.value"
          :disabled="resource.loading.value"
          @click="reload"
        >
          <IconGlyph name="retry" :size="14" aria-hidden="true" />
          {{ t('teacherFeedback.practiceRefresh') }}
        </button>
      </div>
    </div>

    <div class="teacher-practice-review-toolbar">
      <div class="tabs" role="group" :aria-label="t('teacherFeedback.practiceFilterAria')">
        <button
          v-for="option in PRACTICE_REVIEW_FILTERS"
          :key="option"
          class="tab-button"
          :class="{ 'is-active': filter === option }"
          type="button"
          :aria-pressed="filter === option"
          :data-testid="`practice-review-filter-${option.toLowerCase()}`"
          @click="selectFilter(option)"
        >
          {{ filterLabel(option) }}
        </button>
      </div>
      <p v-if="previewMode" class="teacher-practice-review-note" data-testid="practice-review-preview-note">
        <IconGlyph name="info" :size="16" aria-hidden="true" />
        {{ t('teacherFeedback.practicePreviewNote') }}
      </p>
    </div>

    <p v-if="successMessage" class="inline-feedback teacher-practice-review-success" role="status">
      <IconGlyph name="status" :size="16" aria-hidden="true" />
      {{ successMessage }}
    </p>

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="reload">
      <ul v-if="items.length" class="teacher-practice-review-list">
        <li
          v-for="item in items"
          :key="item.id"
          class="teacher-practice-review-item"
          data-testid="practice-review-item"
        >
          <div class="teacher-practice-review-item-head">
            <div class="teacher-practice-review-who">
              <h3 class="teacher-practice-review-name">{{ item.studentName }}</h3>
              <span class="teacher-practice-review-muted">
                <IconGlyph
                  :name="practiceMediaKind(item) === 'audio' ? 'mic' : 'image'"
                  :size="14"
                  aria-hidden="true"
                />
                {{ statusLabel('submissionType', item.submissionType) }}
              </span>
            </div>
            <StatusPill
              :label="statusLabel('reviewStatus', item.reviewStatus)"
              :tone="item.reviewStatus === 'REVIEWED' ? 'success' : 'warning'"
              :icon="item.reviewStatus === 'REVIEWED' ? 'status' : 'history'"
            />
          </div>

          <div class="teacher-practice-review-body">
            <div class="teacher-practice-review-info">
              <dl class="teacher-practice-review-meta">
                <div>
                  <dt>{{ t('teacherFeedback.practiceTarget') }}</dt>
                  <dd>
                    <span v-if="item.targetText" class="cjk-text teacher-practice-review-target" lang="zh-CN">
                      {{ item.targetText }}
                    </span>
                    <span v-else>--</span>
                  </dd>
                </div>
                <div>
                  <dt>{{ t('teacherFeedback.practiceLesson') }}</dt>
                  <dd>{{ item.lessonTitle || t('teacherFeedback.practiceNoLesson') }}</dd>
                </div>
                <div>
                  <dt>{{ t('teacherFeedback.practiceSubmitted') }}</dt>
                  <dd>{{ formatDateTime(item.submittedAt) }}</dd>
                </div>
                <div v-if="item.reviewedAt">
                  <dt>{{ t('teacherFeedback.practiceReviewed') }}</dt>
                  <dd>{{ formatDateTime(item.reviewedAt) }}</dd>
                </div>
              </dl>

              <AuthorizedMedia
                :kind="practiceMediaKind(item)"
                :loader="() => teacherApi.practiceSubmissionFile(item.id)"
                :target="item.targetText"
                :disabled="previewMode"
                :disabled-message="t('teacherFeedback.practiceMediaPreview')"
              />

              <div class="teacher-practice-review-ai">
                <div class="teacher-practice-review-ai-head">
                  <AiResultLabel feedback />
                  <span class="type-caption">{{ statusLabel('aiFeedbackStatus', item.aiFeedbackStatus) }}</span>
                </div>
                <p class="teacher-practice-review-ai-text">
                  {{ item.aiFeedbackText || t('teacherFeedback.practiceNoAiText') }}
                </p>
                <span class="type-caption">{{ item.aiNotice }}</span>
              </div>

              <div class="teacher-practice-review-history">
                <h4 class="teacher-practice-review-subtitle">{{ t('teacherFeedback.practiceTeacherFeedback') }}</h4>
                <ul v-if="item.teacherFeedback.length" class="teacher-practice-review-feedback">
                  <li v-for="entry in item.teacherFeedback" :key="entry.id">
                    <div class="teacher-practice-review-feedback-head">
                      <strong>{{ entry.teacherName }} · {{ statusLabel('feedbackType', entry.feedbackType) }}</strong>
                      <span class="type-caption">{{ formatDateTime(entry.createdAt) }}</span>
                    </div>
                    <p>{{ entry.message }}</p>
                  </li>
                </ul>
                <span v-else class="teacher-practice-review-muted">
                  {{ t('teacherFeedback.practiceNoTeacherFeedback') }}
                </span>
              </div>
            </div>

            <form
              class="teacher-practice-review-form"
              data-testid="practice-review-form"
              novalidate
              @submit.prevent="submitReview(item)"
            >
              <div class="field">
                <label class="field-label" :for="`practice-review-type-${item.id}`">
                  {{ t('teacherFeedback.practiceFeedbackType') }}
                </label>
                <select
                  :id="`practice-review-type-${item.id}`"
                  v-model="draftFor(item.id).feedbackType"
                  class="select"
                  :disabled="fieldsDisabled()"
                >
                  <option v-for="type in TEACHER_FEEDBACK_TYPES" :key="type" :value="type">
                    {{ statusLabel('feedbackType', type) }}
                  </option>
                </select>
              </div>
              <div class="field">
                <label class="field-label" :for="`practice-review-message-${item.id}`">
                  {{ t('teacherFeedback.practiceMessage') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <textarea
                  :id="`practice-review-message-${item.id}`"
                  v-model="draftFor(item.id).message"
                  class="textarea"
                  rows="4"
                  required
                  :maxlength="PRACTICE_FEEDBACK_MAX_LENGTH"
                  :placeholder="t('teacherFeedback.practiceMessagePlaceholder')"
                  :aria-invalid="Boolean(draftFor(item.id).error) || undefined"
                  :aria-describedby="
                    draftFor(item.id).error
                      ? `practice-review-error-${item.id} practice-review-counter-${item.id}`
                      : `practice-review-counter-${item.id}`
                  "
                  :disabled="fieldsDisabled()"
                  data-testid="practice-review-message"
                />
                <div class="teacher-practice-review-field-foot">
                  <small
                    v-if="draftFor(item.id).error"
                    :id="`practice-review-error-${item.id}`"
                    class="field-error"
                    role="alert"
                  >
                    {{ draftFor(item.id).error }}
                  </small>
                  <small v-else class="field-hint">{{ t('teacherFeedback.practiceLengthHint') }}</small>
                  <small :id="`practice-review-counter-${item.id}`" class="field-hint teacher-practice-review-counter">
                    {{
                      t('teacherFeedback.practiceCounter', {
                        count: draftFor(item.id).message.length,
                        max: PRACTICE_FEEDBACK_MAX_LENGTH,
                      })
                    }}
                  </small>
                </div>
              </div>
              <div class="teacher-practice-review-submit">
                <small v-if="previewMode" class="field-hint">{{ t('teacherFeedback.practicePreviewNotSaved') }}</small>
                <button
                  class="btn btn-primary btn-sm"
                  type="submit"
                  :aria-busy="submittingId === item.id"
                  :disabled="submitDisabled()"
                  data-testid="practice-review-submit"
                >
                  {{ submittingId === item.id ? t('teacherFeedback.sending') : t('teacherFeedback.practiceSubmit') }}
                </button>
              </div>
            </form>
          </div>
        </li>
      </ul>

      <StatePanel
        v-else
        compact
        icon="practice"
        :title="t('teacherFeedback.practiceEmpty')"
        :message="t('teacherFeedback.practiceEmptyHint')"
      />

      <PaginationBar
        v-if="totalPages > 1"
        :label="t('teacherFeedback.practiceTitle')"
        :info="t('teacherFeedback.practicePageOf', { page: page + 1, total: totalPages })"
        :previous-label="t('teacherFeedback.practicePrevious')"
        :next-label="t('teacherFeedback.practiceNext')"
        :previous-disabled="page <= 0"
        :next-disabled="page >= totalPages - 1"
        @previous="goToPage(page - 1)"
        @next="goToPage(page + 1)"
      />
    </ResourceSection>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { teacherApi } from '@/api/services'
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import AuthorizedMedia from '@/components/common/AuthorizedMedia.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { teacherPracticeSubmissions } from '@/demo/data'
import { teacherFeedbackMessages } from '@/plugins/teacherFeedbackMessages'
import type { PageResponse, PracticeSubmissionResponse, TeacherFeedbackType } from '@/types/api'
import { isForbiddenApiError, resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import {
  PRACTICE_FEEDBACK_MAX_LENGTH,
  PRACTICE_FEEDBACK_MIN_LENGTH,
  PRACTICE_REVIEW_FILTERS,
  TEACHER_FEEDBACK_TYPES,
  pagePracticeSubmissions,
  practiceMediaKind,
  replacePracticeSubmission,
  validatePracticeFeedbackMessage,
  type PracticeReviewFilter,
} from '@/utils/teacherPracticeReview'

/**
 * Teacher review of learners' pronunciation / handwriting practice submissions (D7 / D7a). Files are fetched as
 * Blobs through the authorised endpoint; the AI note is labelled AI-assisted (not a score).
 */
const props = withDefaults(defineProps<{ locked?: boolean; pageSize?: number }>(), { locked: false, pageSize: 5 })
const emit = defineEmits<{ forbidden: []; reviewed: [submission: PracticeSubmissionResponse] }>()

const { t } = useI18n({
  messages: {
    en: { teacherFeedback: teacherFeedbackMessages.en },
    ms: { teacherFeedback: teacherFeedbackMessages.ms },
    'zh-CN': { teacherFeedback: teacherFeedbackMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

interface ReviewDraft {
  feedbackType: TeacherFeedbackType
  message: string
  error: string | null
}

const filter = ref<PracticeReviewFilter>('SUBMITTED')
const page = ref(0)
const submittingId = ref<number | null>(null)
const successMessage = ref<string | null>(null)
const drafts = reactive<Record<number, ReviewDraft>>({})

const emptyPage = (): PageResponse<PracticeSubmissionResponse> => ({
  items: [],
  page: 0,
  size: props.pageSize,
  totalItems: 0,
  totalPages: 1,
})

const resource = useResource<PageResponse<PracticeSubmissionResponse>>(
  async () => {
    if (props.locked) return emptyPage()
    try {
      return await teacherApi.practiceSubmissions({
        reviewStatus: filter.value,
        page: page.value,
        size: props.pageSize,
      })
    } catch (reason) {
      if (isForbiddenApiError(reason)) emit('forbidden')
      throw reason
    }
  },
  () => pagePracticeSubmissions(teacherPracticeSubmissions, filter.value, page.value, props.pageSize),
)

const previewMode = computed(() => resource.usingFallback.value)
const items = computed(() => resource.data.value?.items ?? [])
const totalPages = computed(() => Math.max(1, resource.data.value?.totalPages ?? 1))

function filterLabel(option: PracticeReviewFilter): string {
  if (option === 'SUBMITTED') return t('teacherFeedback.practiceFilterSubmitted')
  if (option === 'REVIEWED') return t('teacherFeedback.practiceFilterReviewed')
  return t('teacherFeedback.practiceFilterAll')
}

function emptyDraft(): ReviewDraft {
  return { feedbackType: 'GENERAL', message: '', error: null }
}

// One draft per visible submission, created before render so the template never mutates state.
watch(
  items,
  (list) => {
    for (const item of list) {
      if (!drafts[item.id]) drafts[item.id] = emptyDraft()
    }
  },
  { immediate: true },
)

function draftFor(id: number): ReviewDraft {
  return drafts[id] ?? (drafts[id] = emptyDraft())
}

/** Typing stays possible in preview (to try the counter / validation); only sending is disabled there. */
function fieldsDisabled(): boolean {
  return props.locked || submittingId.value !== null
}

function submitDisabled(): boolean {
  return fieldsDisabled() || previewMode.value
}

function reload(): void {
  successMessage.value = null
  void resource.reload()
}

function selectFilter(option: PracticeReviewFilter): void {
  if (filter.value === option) return
  filter.value = option
  page.value = 0
  reload()
}

function goToPage(next: number): void {
  page.value = Math.min(Math.max(0, next), totalPages.value - 1)
  reload()
}

function validationMessage(message: string): string | null {
  switch (validatePracticeFeedbackMessage(message)) {
    case 'required':
      return t('teacherFeedback.practiceErrorRequired')
    case 'tooShort':
      return t('teacherFeedback.practiceErrorTooShort', { min: PRACTICE_FEEDBACK_MIN_LENGTH })
    case 'tooLong':
      return t('teacherFeedback.practiceErrorTooLong', { max: PRACTICE_FEEDBACK_MAX_LENGTH })
    default:
      return null
  }
}

async function submitReview(item: PracticeSubmissionResponse): Promise<void> {
  const draft = draftFor(item.id)
  successMessage.value = null
  draft.error = validationMessage(draft.message)
  if (draft.error || submittingId.value !== null) return
  if (previewMode.value || props.locked) {
    draft.error = t('teacherFeedback.practicePreviewNotSaved')
    return
  }

  submittingId.value = item.id
  try {
    const updated = await teacherApi.reviewPracticeSubmission(item.id, {
      feedbackType: draft.feedbackType,
      message: draft.message.trim(),
    })
    if (resource.data.value) resource.data.value = replacePracticeSubmission(resource.data.value, updated)
    drafts[item.id] = { feedbackType: 'GENERAL', message: '', error: null }
    successMessage.value = t('teacherFeedback.practiceSaved', { name: updated.studentName })
    emit('reviewed', updated)
  } catch (reason) {
    if (isForbiddenApiError(reason)) {
      emit('forbidden')
      return
    }
    draft.error = resolveApiError(reason, t('teacherFeedback.practiceSaveError'))
  } finally {
    submittingId.value = null
  }
}
</script>

<style scoped>
.teacher-practice-review {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-practice-review-heading {
  flex: 1 1 420px;
  min-width: 0;
}

.teacher-practice-review-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.teacher-practice-review-note {
  display: inline-flex;
  align-items: flex-start;
  gap: var(--space-1-5);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-practice-review-success {
  margin: 0;
}

.teacher-practice-review-list,
.teacher-practice-review-feedback {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-practice-review-list {
  gap: var(--space-3);
}

.teacher-practice-review-item {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.teacher-practice-review-item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2) var(--space-3);
}

.teacher-practice-review-who {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.teacher-practice-review-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.teacher-practice-review-muted {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  color: var(--text-secondary);
  font-size: var(--font-size-md);
}

.teacher-practice-review-body {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.teacher-practice-review-info {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.teacher-practice-review-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.teacher-practice-review-meta dt {
  color: var(--text-secondary);
  font-size: var(--type-caption-size);
  font-weight: var(--font-weight-bold);
}

.teacher-practice-review-meta dd {
  margin: var(--space-0-5) 0 0;
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.teacher-practice-review-target {
  font-size: var(--font-size-xl);
}

.teacher-practice-review-ai {
  display: grid;
  gap: var(--space-1-5);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.teacher-practice-review-ai-head,
.teacher-practice-review-feedback-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-2);
}

.teacher-practice-review-ai-head {
  justify-content: flex-start;
}

.teacher-practice-review-ai-text,
.teacher-practice-review-feedback p {
  margin: 0;
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.teacher-practice-review-history {
  display: grid;
  gap: var(--space-2);
}

.teacher-practice-review-subtitle {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-extrabold);
}

.teacher-practice-review-feedback {
  gap: var(--space-2);
}

.teacher-practice-review-feedback li {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-2-5) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
}

.teacher-practice-review-form {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.teacher-practice-review-form .field {
  align-content: start;
}

.teacher-practice-review-field-foot {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.teacher-practice-review-counter {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.teacher-practice-review-submit {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2) var(--space-3);
}

@media (max-width: 1200px) {
  .teacher-practice-review-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .teacher-practice-review-item {
    padding: var(--space-4);
  }

  .teacher-practice-review-submit {
    justify-content: stretch;
  }

  .teacher-practice-review-submit > .btn {
    width: 100%;
  }
}
</style>
