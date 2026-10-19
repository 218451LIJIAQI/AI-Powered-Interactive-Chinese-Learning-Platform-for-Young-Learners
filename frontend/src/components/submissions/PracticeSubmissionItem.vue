<template>
  <li class="panel practice-submission-item" data-testid="submission-item" :data-submission-id="item.id">
    <div class="practice-submission-head">
      <span class="tag">
        <IconGlyph :name="mediaKind === 'audio' ? 'mic' : 'pen'" :size="14" />
        {{ statusLabel('submissionType', item.submissionType) }}
      </span>
      <StatusPill
        :label="statusLabel('reviewStatus', item.reviewStatus)"
        :tone="item.reviewStatus === 'REVIEWED' ? 'success' : 'warning'"
        data-testid="submission-review-status"
      />
    </div>

    <p class="practice-submission-target">
      <span class="practice-submission-muted">{{ t('practiceSubmissions.targetLabel') }}:</span>
      <strong class="cjk-text" lang="zh-CN">{{ item.targetText || '--' }}</strong>
    </p>

    <div class="practice-submission-meta">
      <RouterLink v-if="item.lessonId && item.lessonTitle" :to="lessonRoute(item.lessonId)">
        {{ t('practiceSubmissions.lessonLabel', { title: item.lessonTitle }) }}
      </RouterLink>
      <span v-else-if="item.lessonTitle">{{ t('practiceSubmissions.lessonLabel', { title: item.lessonTitle }) }}</span>
      <span v-else>{{ t('practiceSubmissions.noLesson') }}</span>
      <span>{{ t('practiceSubmissions.submittedOn', { date: formatDateTime(item.submittedAt) }) }}</span>
      <span v-if="item.reviewedAt">{{
        t('practiceSubmissions.reviewedOn', { date: formatDateTime(item.reviewedAt) })
      }}</span>
      <span v-if="item.fileName">{{
        t('practiceSubmissions.fileInfo', { name: item.fileName, size: formatFileSize(item.fileSize) })
      }}</span>
    </div>

    <AuthorizedMedia
      :kind="mediaKind"
      :loader="loadFile"
      :target="item.targetText"
      :disabled="previewMode"
      :disabled-message="t('practiceSubmissions.previewMedia')"
    />

    <section class="practice-submission-ai" :aria-label="t('practiceSubmissions.aiFeedback')">
      <div class="practice-submission-section-head">
        <strong>{{ t('practiceSubmissions.aiFeedback') }}</strong>
        <AiResultLabel feedback />
        <span class="chip practice-submission-ai-status" :class="`is-${item.aiFeedbackStatus.toLowerCase()}`">
          {{ statusLabel('aiFeedbackStatus', item.aiFeedbackStatus) }}
        </span>
      </div>
      <p v-if="item.aiFeedbackText" class="practice-submission-ai-text">{{ item.aiFeedbackText }}</p>
      <small class="practice-submission-muted">{{ item.aiNotice || t('practiceSubmissions.notScore') }}</small>
      <AiReportButton
        v-if="item.aiFeedbackStatus === 'AVAILABLE' && item.aiFeedbackText"
        :feature-key="mediaKind === 'audio' ? 'PRONUNCIATION_FEEDBACK' : 'WRITING_FEEDBACK'"
        :response-snapshot="item.aiFeedbackText"
        :prompt-snapshot="item.targetText"
        :preview="previewMode"
      />
    </section>

    <section
      class="practice-submission-teacher"
      :class="{ 'is-reviewed': item.teacherFeedback.length }"
      data-testid="submission-teacher-feedback"
    >
      <h3>{{ t('practiceSubmissions.teacherFeedbackTitle') }}</h3>
      <ul v-if="item.teacherFeedback.length" class="practice-submission-feedback-list">
        <li v-for="feedback in item.teacherFeedback" :key="feedback.id">
          <p>{{ feedback.message }}</p>
          <small class="practice-submission-muted">{{
            t('practiceSubmissions.teacherFeedbackMeta', {
              teacher: feedback.teacherName,
              type: statusLabel('feedbackType', feedback.feedbackType || 'GENERAL'),
              date: formatDateTime(feedback.createdAt),
            })
          }}</small>
        </li>
      </ul>
      <p v-else class="practice-submission-muted">{{ t('practiceSubmissions.noTeacherFeedback') }}</p>
    </section>
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { studentApi } from '@/api/services'
import AiReportButton from '@/components/ai/AiReportButton.vue'
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import AuthorizedMedia from '@/components/common/AuthorizedMedia.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
import type { PracticeSubmissionResponse } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { formatFileSize, submissionMediaKind } from '@/utils/practiceUpload'
import { lessonRoute } from '@/utils/progressSummary'

import { usePracticeSubmissionText } from './practiceSubmissionText'

const props = withDefaults(defineProps<{ item: PracticeSubmissionResponse; previewMode?: boolean }>(), {
  previewMode: false,
})

const { t } = usePracticeSubmissionText()
const { statusLabel } = useStatusLabels()

const mediaKind = computed(() => submissionMediaKind(props.item.submissionType))

/** Protected file fetched as a Blob through the http client (bearer token in the header, never in the URL). */
function loadFile() {
  return studentApi.practiceSubmissionFile(props.item.id)
}
</script>

<style scoped>
.practice-submission-item {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.practice-submission-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.practice-submission-target {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2);
  margin: 0;
  overflow-wrap: anywhere;
}

.practice-submission-target strong {
  color: var(--text-primary);
  font-size: var(--font-size-2xl);
  line-height: var(--line-height-snug);
}

.practice-submission-muted {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.practice-submission-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1-5) var(--space-4);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.practice-submission-meta a {
  color: var(--text-link);
  font-weight: var(--font-weight-bold);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.practice-submission-meta a:hover {
  color: var(--text-link-hover);
}

.practice-submission-ai,
.practice-submission-teacher {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.practice-submission-teacher.is-reviewed {
  border-color: var(--color-success-border);
  border-left: var(--border-width-thick) solid var(--color-success);
  background: var(--surface-card);
}

.practice-submission-section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.practice-submission-section-head > strong,
.practice-submission-teacher h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-extrabold);
}

.practice-submission-ai-text {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  white-space: pre-line;
}

.practice-submission-ai-status.is-failed {
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
}

.practice-submission-ai-status.is-unavailable {
  background: var(--color-ink-100);
  color: var(--color-ink-700);
}

.practice-submission-feedback-list {
  display: grid;
  gap: var(--space-2-5);
  margin: 0;
  padding: 0;
  list-style: none;
}

.practice-submission-feedback-list p {
  margin: 0 0 var(--space-0-5);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  white-space: pre-line;
}

.practice-submission-feedback-list small {
  display: block;
}
</style>
