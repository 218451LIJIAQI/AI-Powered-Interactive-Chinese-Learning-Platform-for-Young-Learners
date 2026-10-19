<template>
  <section
    class="panel practice-feedback-result"
    data-testid="submission-result"
    :aria-labelledby="titleId"
    tabindex="-1"
  >
    <div class="practice-feedback-head">
      <h2 :id="titleId">{{ t('practiceSubmissions.resultTitle') }}</h2>
      <AiResultLabel v-if="aiProduced" feedback />
    </div>
    <p class="practice-feedback-target">
      <strong class="cjk-text" lang="zh-CN">{{ heading }}</strong>
    </p>
    <p class="practice-feedback-status">
      <span class="chip">{{ t('practiceSubmissions.resultStatus', { status: statusText }) }}</span>
    </p>
    <p v-if="feedbackText" class="practice-feedback-text">{{ feedbackText }}</p>
    <p v-else class="practice-feedback-muted">{{ t('practiceSubmissions.noFeedbackText') }}</p>
    <p v-if="supportText" class="practice-feedback-text practice-feedback-muted">{{ supportText }}</p>
    <small v-if="result.response.notice" class="practice-feedback-muted">{{ result.response.notice }}</small>
    <p class="practice-feedback-note">
      <IconGlyph name="status" :size="16" />
      <span>{{
        result.kind === 'audio'
          ? t('practiceSubmissions.audioUnavailableNote')
          : t('practiceSubmissions.imageSavedNote')
      }}</span>
    </p>
    <p class="practice-feedback-saved" data-testid="submission-result-saved">
      {{
        result.response.submissionId
          ? t('practiceSubmissions.resultSaved', { id: result.response.submissionId })
          : t('practiceSubmissions.resultSavedNoId')
      }}
      <span class="practice-feedback-muted">{{ t('practiceSubmissions.viewList') }}</span>
    </p>
    <small class="practice-feedback-muted">{{ t('practiceSubmissions.notScore') }}</small>
    <AiReportButton
      v-if="snapshot && aiProduced"
      :feature-key="result.kind === 'audio' ? 'PRONUNCIATION_FEEDBACK' : 'WRITING_FEEDBACK'"
      :response-snapshot="snapshot"
      :prompt-snapshot="result.target"
    />
    <AiDisclaimer :show-guidance-link="true" />
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import AiReportButton from '@/components/ai/AiReportButton.vue'
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
import type { PracticeUploadResult } from '@/utils/practiceUpload'

import { usePracticeSubmissionText } from './practiceSubmissionText'

/** AI-assisted feedback returned right after an upload ("AI-assisted, not a score", D19). */
const props = defineProps<{ result: PracticeUploadResult }>()

const { t } = usePracticeSubmissionText()
const { statusLabel } = useStatusLabels()
const titleId = `${useId()}-result`

const heading = computed(() =>
  props.result.kind === 'audio'
    ? t('practiceSubmissions.resultAudio', { target: props.result.target })
    : t('practiceSubmissions.resultImage', { target: props.result.target }),
)

/** The upload endpoints answer SUCCESS / UNAVAILABLE / FAILED; show them with the practice AI-status labels. */
const responseStatus = computed(() => (props.result.response.status || 'UNAVAILABLE').toUpperCase())
const statusText = computed(() =>
  statusLabel('aiFeedbackStatus', responseStatus.value === 'SUCCESS' ? 'AVAILABLE' : responseStatus.value),
)
/** Only SUCCESS text came from the AI; UNAVAILABLE / FAILED texts are fixed server messages (not reportable). */
const aiProduced = computed(() => responseStatus.value === 'SUCCESS')

const feedbackText = computed(() => {
  const result = props.result
  return result.kind === 'audio'
    ? result.response.feedback || result.response.pronunciationComment || ''
    : result.response.feedback || result.response.clarityComment || ''
})

const supportText = computed(() => {
  const result = props.result
  return result.kind === 'audio'
    ? [result.response.toneComment, result.response.improvementTips].filter(Boolean).join('\n')
    : result.response.improvementTips || ''
})

const snapshot = computed(() =>
  [feedbackText.value, supportText.value]
    .map((part) => part.trim())
    .filter(Boolean)
    .join('\n'),
)
</script>

<style scoped>
.practice-feedback-result {
  display: grid;
  gap: var(--space-3);
  border-left: var(--border-width-thick) solid var(--color-primary-500);
}

.practice-feedback-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.practice-feedback-result h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.practice-feedback-target,
.practice-feedback-status,
.practice-feedback-saved {
  margin: 0;
  overflow-wrap: anywhere;
}

.practice-feedback-target strong {
  color: var(--text-primary);
  font-size: var(--font-size-2xl);
}

.practice-feedback-saved {
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.practice-feedback-text {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  white-space: pre-line;
}

.practice-feedback-muted {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.practice-feedback-note {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--color-info-border);
  border-radius: var(--radius-sm);
  background: var(--color-info-soft);
  color: var(--color-info-text);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.practice-feedback-note .icon-glyph {
  margin-top: var(--space-0-5);
}
</style>
