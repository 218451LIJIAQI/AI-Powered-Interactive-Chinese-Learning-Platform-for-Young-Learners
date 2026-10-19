<template>
  <form
    class="panel practice-upload-form"
    data-testid="submission-form-pronunciation"
    :aria-labelledby="titleId"
    novalidate
    @submit.prevent="submit"
  >
    <div class="practice-upload-head">
      <span class="practice-upload-icon" aria-hidden="true"><IconGlyph name="mic" :size="22" /></span>
      <div>
        <h2 :id="titleId">{{ t('practiceSubmissions.pronunciationTitle') }}</h2>
        <p>{{ t('practiceSubmissions.pronunciationHint') }}</p>
      </div>
    </div>
    <p class="practice-upload-note">
      <IconGlyph name="info" :size="16" />
      <span>{{ t('practiceSubmissions.audioCapabilityNote') }}</span>
    </p>
    <p v-if="disabled && disabledMessage" class="practice-upload-disabled" role="note">
      <IconGlyph name="safe" :size="16" />
      <span>{{ disabledMessage }}</span>
    </p>

    <div class="field">
      <label class="field-label" :for="`${baseId}-target`"
        >{{ t('practiceSubmissions.targetText') }}<span class="required-mark" aria-hidden="true">*</span></label
      >
      <input
        :id="`${baseId}-target`"
        v-model="form.targetText"
        class="input cjk-text"
        lang="zh-CN"
        type="text"
        autocomplete="off"
        required
        :maxlength="MAX_TARGET_TEXT_LENGTH"
        placeholder="你好"
        :disabled="formDisabled"
        :aria-invalid="Boolean(errors.target) || undefined"
        :aria-describedby="describedBy('target')"
        data-testid="submission-target"
      />
      <small :id="`${baseId}-target-hint`" class="field-hint">
        {{ t('practiceSubmissions.targetTextHint', { max: MAX_TARGET_TEXT_LENGTH }) }}
      </small>
      <small v-if="errors.target" :id="`${baseId}-target-error`" class="field-error">{{ errors.target }}</small>
    </div>

    <div class="practice-upload-grid">
      <div class="field">
        <label class="field-label" :for="`${baseId}-pinyin`">{{ t('practiceSubmissions.pinyin') }}</label>
        <input
          :id="`${baseId}-pinyin`"
          v-model="form.pinyin"
          class="input"
          type="text"
          autocomplete="off"
          :maxlength="MAX_PINYIN_LENGTH"
          placeholder="nǐ hǎo"
          :disabled="formDisabled"
          :aria-invalid="Boolean(errors.pinyin) || undefined"
          :aria-describedby="errors.pinyin ? `${baseId}-pinyin-error` : undefined"
        />
        <small v-if="errors.pinyin" :id="`${baseId}-pinyin-error`" class="field-error">{{ errors.pinyin }}</small>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-tone`">{{ t('practiceSubmissions.tone') }}</label>
        <input
          :id="`${baseId}-tone`"
          v-model="form.tone"
          class="input"
          type="text"
          autocomplete="off"
          :maxlength="MAX_TONE_LENGTH"
          :disabled="formDisabled"
          :aria-invalid="Boolean(errors.tone) || undefined"
          :aria-describedby="describedBy('tone')"
        />
        <small :id="`${baseId}-tone-hint`" class="field-hint">{{ t('practiceSubmissions.toneHint') }}</small>
        <small v-if="errors.tone" :id="`${baseId}-tone-error`" class="field-error">{{ errors.tone }}</small>
      </div>
    </div>

    <SubmissionLessonField v-model="form.lessonId" :lessons="lessons" :disabled="formDisabled" :error="errors.lesson" />

    <AudioRecorderControl
      v-model="form.file"
      :disabled="formDisabled"
      :invalid="Boolean(errors.file)"
      :error-id="errors.file ? `${baseId}-file-error` : null"
      :recorder-options="recorderOptions"
    />
    <small v-if="errors.file" :id="`${baseId}-file-error`" class="field-error">{{ errors.file }}</small>

    <p v-if="formError" class="practice-upload-error" role="alert">
      <IconGlyph name="alert" :size="16" />
      <span>{{ formError }}</span>
    </p>

    <div class="practice-upload-actions">
      <button
        class="btn btn-primary"
        type="submit"
        :disabled="formDisabled"
        :aria-busy="submitting"
        data-testid="submission-submit"
      >
        <IconGlyph name="upload" :size="16" />
        {{ submitting ? t('practiceSubmissions.uploading') : t('practiceSubmissions.submitAudio') }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'

import { aiApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import type { AudioRecorderOptions } from '@/composables/useAudioRecorder'
import { resolveApiError } from '@/utils/apiError'
import {
  buildPronunciationFormData,
  MAX_PINYIN_LENGTH,
  MAX_TARGET_TEXT_LENGTH,
  MAX_TONE_LENGTH,
  validateUpload,
  type PracticeLessonOption,
  type PracticeUploadResult,
  type UploadIssue,
} from '@/utils/practiceUpload'

import AudioRecorderControl from './AudioRecorderControl.vue'
import { usePracticeSubmissionText } from './practiceSubmissionText'
import SubmissionLessonField from './SubmissionLessonField.vue'

const props = withDefaults(
  defineProps<{
    lessons: readonly PracticeLessonOption[]
    languagePreference: string
    /** Preview mode: uploads are disabled (nothing is saved). */
    disabled?: boolean
    disabledMessage?: string | null
    initialLessonId?: string
    recorderOptions?: AudioRecorderOptions
  }>(),
  { disabled: false, disabledMessage: null, initialLessonId: '', recorderOptions: undefined },
)
const emit = defineEmits<{ submitted: [result: PracticeUploadResult] }>()

const { t, fieldErrors } = usePracticeSubmissionText()
const baseId = useId()
const titleId = `${baseId}-title`

const form = reactive({
  targetText: '',
  pinyin: '',
  tone: '',
  lessonId: props.initialLessonId,
  file: null as File | null,
})
const errors = ref<Partial<Record<UploadIssue['field'], string>>>({})
const formError = ref<string | null>(null)
const submitting = ref(false)
const formDisabled = computed(() => props.disabled || submitting.value)

watch(
  () => props.initialLessonId,
  (value) => {
    if (!form.lessonId) form.lessonId = value
  },
)

function describedBy(field: 'target' | 'tone'): string {
  return [`${baseId}-${field}-hint`, errors.value[field] ? `${baseId}-${field}-error` : ''].filter(Boolean).join(' ')
}

async function submit(): Promise<void> {
  if (formDisabled.value) return
  formError.value = null
  const issues = validateUpload({
    kind: 'audio',
    file: form.file,
    target: form.targetText,
    pinyin: form.pinyin,
    tone: form.tone,
    lessonId: form.lessonId,
  })
  errors.value = fieldErrors(issues, 'audio')
  if (issues.length || !form.file) {
    formError.value = t('practiceSubmissions.fixErrors')
    return
  }

  submitting.value = true
  try {
    const target = form.targetText.trim()
    const response = await aiApi.pronunciationFeedback(
      buildPronunciationFormData({
        file: form.file,
        fileName: form.file.name,
        targetText: target,
        pinyin: form.pinyin,
        tone: form.tone,
        languagePreference: props.languagePreference,
        lessonId: form.lessonId,
      }),
    )
    emit('submitted', { kind: 'audio', target, response })
    form.targetText = ''
    form.pinyin = ''
    form.tone = ''
    form.file = null
  } catch (reason) {
    formError.value = resolveApiError(reason, t('practiceSubmissions.uploadErrorAudio'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped src="./practiceUploadForm.css"></style>
