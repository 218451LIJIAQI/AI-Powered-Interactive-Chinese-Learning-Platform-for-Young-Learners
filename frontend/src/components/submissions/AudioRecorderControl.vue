<template>
  <div class="audio-recorder" data-testid="submission-recorder">
    <fieldset
      v-if="recorder.supported"
      class="audio-recorder-box"
      :class="`is-${recorder.status.value}`"
      :disabled="disabled"
    >
      <legend>{{ t('practiceSubmissions.recordTitle') }}</legend>
      <div class="audio-recorder-controls">
        <button
          v-if="!recorder.isBusy.value"
          class="btn btn-sm"
          :class="recorder.status.value === 'recorded' ? 'btn-secondary' : 'btn-primary'"
          type="button"
          data-testid="submission-record"
          @click="startRecording"
        >
          <IconGlyph :name="recorder.status.value === 'recorded' ? 'retry' : 'mic'" :size="16" />
          {{
            recorder.status.value === 'recorded' ? t('practiceSubmissions.rerecord') : t('practiceSubmissions.record')
          }}
        </button>
        <button
          v-else
          class="btn btn-danger btn-sm"
          type="button"
          data-testid="submission-stop"
          :disabled="recorder.status.value === 'requesting'"
          :aria-busy="recorder.status.value === 'requesting'"
          @click="recorder.stop()"
        >
          <IconGlyph name="stop" :size="16" />
          {{ t('practiceSubmissions.stop') }}
        </button>
        <p class="audio-recorder-status" role="status" aria-live="polite" data-testid="submission-recorder-status">
          <span v-if="recorder.isRecording.value" class="audio-recorder-dot" aria-hidden="true" />
          <IconGlyph v-else-if="recorder.status.value === 'recorded'" name="status" :size="16" />
          {{ statusText }}
        </p>
      </div>
      <audio
        v-if="recorder.previewUrl.value"
        class="audio-recorder-preview"
        :src="recorder.previewUrl.value"
        controls
        preload="metadata"
        :aria-label="t('practiceSubmissions.recordingPreview')"
      />
      <small class="audio-recorder-note">{{ t('practiceSubmissions.recordingLocalNote') }}</small>
    </fieldset>

    <p
      v-if="fallbackMessage"
      class="audio-recorder-fallback"
      :role="recorder.supported ? 'alert' : 'note'"
      data-testid="submission-recorder-fallback"
    >
      <IconGlyph name="alert" :size="16" />
      <span>{{ fallbackMessage }}</span>
    </p>

    <div class="field">
      <label class="field-label" :for="fileInputId">{{
        recorder.supported ? t('practiceSubmissions.uploadAudioLabel') : t('practiceSubmissions.uploadAudioOnlyLabel')
      }}</label>
      <input
        :id="fileInputId"
        ref="fileInput"
        class="input"
        type="file"
        :accept="UPLOAD_ACCEPT.audio"
        :disabled="disabled || recorder.isBusy.value"
        :aria-invalid="invalid || undefined"
        :aria-describedby="describedBy"
        data-testid="submission-file-input"
        @change="selectFile"
      />
      <small :id="`${fileInputId}-hint`" class="field-hint">
        {{ t('practiceSubmissions.uploadAudioHint', { size: maxSizeLabel }) }}
      </small>
    </div>

    <div v-if="modelValue" class="audio-recorder-selected">
      <IconGlyph name="audio" :size="18" />
      <span>{{ selectedLabel }}</span>
      <button class="btn btn-ghost btn-sm" type="button" :disabled="disabled" @click="clear">
        {{ t('practiceSubmissions.clearFile') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useAudioRecorder, type AudioRecorderOptions } from '@/composables/useAudioRecorder'
import { formatFileSize, formatRecordingTime, UPLOAD_ACCEPT } from '@/utils/practiceUpload'

import { usePracticeSubmissionText } from './practiceSubmissionText'

/**
 * Pronunciation audio input: in-browser recording (MediaRecorder, feature-detected) with a file-upload
 * fallback. v-model is the File that will be uploaded (the recording or the chosen file).
 */
const props = withDefaults(
  defineProps<{
    modelValue: File | null
    disabled?: boolean
    invalid?: boolean
    errorId?: string | null
    recorderOptions?: AudioRecorderOptions
  }>(),
  { disabled: false, invalid: false, errorId: null, recorderOptions: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [file: File | null] }>()

const { t, maxSizeLabel } = usePracticeSubmissionText()
const recorder = useAudioRecorder(props.recorderOptions)
const fileInputId = `${useId()}-audio-file`
const fileInput = ref<HTMLInputElement | null>(null)

const describedBy = computed(() => [`${fileInputId}-hint`, props.errorId].filter(Boolean).join(' '))

const statusText = computed(() => {
  const max = formatRecordingTime(recorder.maxSeconds)
  switch (recorder.status.value) {
    case 'requesting':
      return t('practiceSubmissions.requesting')
    case 'recording':
      return t('practiceSubmissions.recordingStatus', {
        time: formatRecordingTime(recorder.elapsedSeconds.value),
        max,
      })
    case 'recorded':
      return t('practiceSubmissions.recordedStatus', { time: formatRecordingTime(recorder.elapsedSeconds.value) })
    case 'idle':
      return t('practiceSubmissions.idleStatus', { max })
    default:
      return ''
  }
})

const fallbackMessage = computed(() => {
  switch (recorder.error.value) {
    case 'unsupported':
      return t('practiceSubmissions.recorderUnsupported')
    case 'denied':
      return t('practiceSubmissions.recorderDenied')
    case 'noDevice':
      return t('practiceSubmissions.recorderNoDevice')
    case 'failed':
      return t('practiceSubmissions.recorderFailed')
    case 'empty':
      return t('practiceSubmissions.recorderEmpty')
    default:
      return ''
  }
})

const selectedLabel = computed(() => {
  const file = props.modelValue
  if (!file) return ''
  const values = { name: file.name, size: formatFileSize(file.size) }
  return file === recorder.recording.value
    ? t('practiceSubmissions.usingRecording', values)
    : t('practiceSubmissions.selectedFile', values)
})

function clearFileInput(): void {
  if (fileInput.value) fileInput.value.value = ''
}

async function startRecording(): Promise<void> {
  clearFileInput()
  emit('update:modelValue', null)
  await recorder.start()
}

function selectFile(event: Event): void {
  const file = (event.target as HTMLInputElement | null)?.files?.[0] ?? null
  if (recorder.recording.value) recorder.reset()
  emit('update:modelValue', file)
}

function clear(): void {
  recorder.reset()
  clearFileInput()
  emit('update:modelValue', null)
}

watch(
  () => recorder.recording.value,
  (file) => {
    if (file) emit('update:modelValue', file)
  },
)

// The parent clears the selection after a successful upload.
watch(
  () => props.modelValue,
  (file) => {
    if (file) return
    clearFileInput()
    if (recorder.recording.value) recorder.reset()
  },
)
</script>

<style scoped>
.audio-recorder {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.audio-recorder-box {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
  margin: 0;
  padding: var(--space-3) var(--space-4) var(--space-4);
  border: 1px dashed var(--color-primary-300);
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.audio-recorder-box.is-recording {
  border-style: solid;
  border-color: var(--color-danger-border);
  background: var(--color-danger-soft);
}

.audio-recorder-box.is-recorded {
  border-style: solid;
  border-color: var(--color-success-border);
  background: var(--color-success-soft);
}

.audio-recorder-box:disabled {
  border-color: var(--border-default);
  background: var(--surface-sunken);
}

.audio-recorder-box legend {
  padding: 0 var(--space-1-5);
  color: var(--text-primary);
  font-size: var(--type-label-size);
  font-weight: var(--font-weight-bold);
}

.audio-recorder-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.audio-recorder-status {
  display: inline-flex;
  flex: 1 1 200px;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  min-height: var(--control-height-sm);
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.is-recording .audio-recorder-status {
  color: var(--color-danger-text);
}

.is-recorded .audio-recorder-status {
  color: var(--color-success-text);
}

.audio-recorder-dot {
  flex: 0 0 auto;
  width: var(--space-2-5);
  height: var(--space-2-5);
  border-radius: var(--radius-round);
  background: var(--color-danger);
  animation: audio-recorder-pulse 1.2s ease-in-out infinite;
}

.audio-recorder-preview {
  width: 100%;
  max-width: 360px;
}

.audio-recorder-note {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
}

.audio-recorder-fallback {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.audio-recorder-fallback .icon-glyph {
  margin-top: var(--space-0-5);
}

.audio-recorder-selected {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.audio-recorder-selected > .icon-glyph {
  color: var(--text-brand);
}

.audio-recorder-selected > span {
  flex: 1 1 160px;
  min-width: 0;
}

@keyframes audio-recorder-pulse {
  50% {
    opacity: 0.3;
  }
}

@media (prefers-reduced-motion: reduce) {
  .audio-recorder-dot {
    animation: none;
  }
}
</style>
