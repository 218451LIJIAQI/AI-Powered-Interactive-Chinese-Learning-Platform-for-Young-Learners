<template>
  <form
    class="panel practice-upload-form"
    data-testid="submission-form-handwriting"
    :aria-labelledby="titleId"
    novalidate
    @submit.prevent="submit"
  >
    <div class="practice-upload-head">
      <span class="practice-upload-icon" aria-hidden="true"><IconGlyph name="pen" :size="22" /></span>
      <div>
        <h2 :id="titleId">{{ t('practiceSubmissions.handwritingTitle') }}</h2>
        <p>{{ t('practiceSubmissions.handwritingHint') }}</p>
      </div>
    </div>
    <p v-if="disabled && disabledMessage" class="practice-upload-disabled" role="note">
      <IconGlyph name="safe" :size="16" />
      <span>{{ disabledMessage }}</span>
    </p>

    <div class="field">
      <label class="field-label" :for="`${baseId}-target`"
        >{{ t('practiceSubmissions.targetCharacter') }}<span class="required-mark" aria-hidden="true">*</span></label
      >
      <input
        :id="`${baseId}-target`"
        v-model="form.targetCharacter"
        class="input cjk-text"
        lang="zh-CN"
        type="text"
        autocomplete="off"
        required
        :maxlength="MAX_TARGET_TEXT_LENGTH"
        placeholder="好"
        :disabled="formDisabled"
        :aria-invalid="Boolean(errors.target) || undefined"
        :aria-describedby="describedBy('target')"
        data-testid="submission-target"
      />
      <small :id="`${baseId}-target-hint`" class="field-hint">
        {{ t('practiceSubmissions.targetCharacterHint', { max: MAX_TARGET_TEXT_LENGTH }) }}
      </small>
      <small v-if="errors.target" :id="`${baseId}-target-error`" class="field-error">{{ errors.target }}</small>
    </div>

    <SubmissionLessonField v-model="form.lessonId" :lessons="lessons" :disabled="formDisabled" :error="errors.lesson" />

    <div class="field">
      <label class="field-label" :for="`${baseId}-file`"
        >{{ t('practiceSubmissions.uploadImageLabel') }}<span class="required-mark" aria-hidden="true">*</span></label
      >
      <input
        :id="`${baseId}-file`"
        ref="fileInput"
        class="input"
        type="file"
        :accept="UPLOAD_ACCEPT.image"
        :disabled="formDisabled"
        :aria-invalid="Boolean(errors.file) || undefined"
        :aria-describedby="describedBy('file')"
        data-testid="submission-file-input"
        @change="selectFile"
      />
      <small :id="`${baseId}-file-hint`" class="field-hint">
        {{ t('practiceSubmissions.uploadImageHint', { size: maxSizeLabel }) }}
      </small>
      <small v-if="errors.file" :id="`${baseId}-file-error`" class="field-error">{{ errors.file }}</small>
    </div>

    <div v-if="form.file" class="practice-upload-preview">
      <img v-if="previewUrl" class="practice-upload-image-preview" :src="previewUrl" :alt="previewAlt" />
      <span class="practice-upload-preview-name">{{
        t('practiceSubmissions.selectedFile', { name: form.file.name, size: formatFileSize(form.file.size) })
      }}</span>
      <button class="btn btn-ghost btn-sm" type="button" :disabled="formDisabled" @click="clearFile">
        {{ t('practiceSubmissions.clearFile') }}
      </button>
    </div>

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
        {{ submitting ? t('practiceSubmissions.uploading') : t('practiceSubmissions.submitImage') }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, useId, watch } from 'vue'

import { aiApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { resolveApiError } from '@/utils/apiError'
import {
  buildWritingFormData,
  formatFileSize,
  isAcceptedUploadType,
  MAX_TARGET_TEXT_LENGTH,
  UPLOAD_ACCEPT,
  validateUpload,
  type PracticeLessonOption,
  type PracticeUploadResult,
  type UploadIssue,
} from '@/utils/practiceUpload'

import { usePracticeSubmissionText } from './practiceSubmissionText'
import SubmissionLessonField from './SubmissionLessonField.vue'

const props = withDefaults(
  defineProps<{
    lessons: readonly PracticeLessonOption[]
    languagePreference: string
    disabled?: boolean
    disabledMessage?: string | null
    initialLessonId?: string
  }>(),
  { disabled: false, disabledMessage: null, initialLessonId: '' },
)
const emit = defineEmits<{ submitted: [result: PracticeUploadResult] }>()

const { t, fieldErrors, maxSizeLabel } = usePracticeSubmissionText()
const baseId = useId()
const titleId = `${baseId}-title`
const fileInput = ref<HTMLInputElement | null>(null)

const form = reactive({
  targetCharacter: '',
  lessonId: props.initialLessonId,
  file: null as File | null,
})
const errors = ref<Partial<Record<UploadIssue['field'], string>>>({})
const formError = ref<string | null>(null)
const submitting = ref(false)
const previewUrl = ref<string | null>(null)
const formDisabled = computed(() => props.disabled || submitting.value)
function describedBy(field: 'target' | 'file'): string {
  return [`${baseId}-${field}-hint`, errors.value[field] ? `${baseId}-${field}-error` : ''].filter(Boolean).join(' ')
}
const previewAlt = computed(() => form.targetCharacter.trim() || t('practiceSubmissions.handwritingTitle'))

watch(
  () => props.initialLessonId,
  (value) => {
    if (!form.lessonId) form.lessonId = value
  },
)

function revokePreview(): void {
  if (previewUrl.value && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = null
}

function setFile(file: File | null): void {
  revokePreview()
  form.file = file
  // Local preview only for images the server accepts (never a tokenised URL).
  if (file && isAcceptedUploadType(file, 'image') && typeof URL.createObjectURL === 'function') {
    previewUrl.value = URL.createObjectURL(file)
  }
}

function selectFile(event: Event): void {
  setFile((event.target as HTMLInputElement | null)?.files?.[0] ?? null)
}

function clearFile(): void {
  setFile(null)
  if (fileInput.value) fileInput.value.value = ''
}

async function submit(): Promise<void> {
  if (formDisabled.value) return
  formError.value = null
  const issues = validateUpload({
    kind: 'image',
    file: form.file,
    target: form.targetCharacter,
    lessonId: form.lessonId,
  })
  errors.value = fieldErrors(issues, 'image')
  if (issues.length || !form.file) {
    formError.value = t('practiceSubmissions.fixErrors')
    return
  }

  submitting.value = true
  try {
    const target = form.targetCharacter.trim()
    const response = await aiApi.writingFeedback(
      buildWritingFormData({
        file: form.file,
        fileName: form.file.name,
        targetCharacter: target,
        languagePreference: props.languagePreference,
        lessonId: form.lessonId,
      }),
    )
    emit('submitted', { kind: 'image', target, response })
    form.targetCharacter = ''
    clearFile()
  } catch (reason) {
    formError.value = resolveApiError(reason, t('practiceSubmissions.uploadErrorImage'))
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(revokePreview)
</script>

<style scoped src="./practiceUploadForm.css"></style>
