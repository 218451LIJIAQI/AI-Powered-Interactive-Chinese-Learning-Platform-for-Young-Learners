<template>
  <div class="ai-voice">
    <AiVoiceResult
      v-if="result"
      ref="resultView"
      :result="result"
      :reported="reportedIds.has(result.check.id)"
      :ai-notice="aiNotice"
      @report="report"
      @try-again="tryAgain"
    />

    <section v-else class="panel" :aria-labelledby="`${baseId}-form`">
      <form class="ai-check-form" @submit.prevent="submit">
        <h2 :id="`${baseId}-form`" class="card-title">{{ t('aiHelper.checkTitle') }}</h2>

        <div class="ai-check-row">
          <div class="field">
            <span class="field-label" aria-hidden="true">{{ t('aiHelper.checkType') }}</span>
            <SegmentedControl
              :model-value="type"
              :options="checkTypeOptions"
              :label="t('aiHelper.checkType')"
              :disabled="locked"
              @update:model-value="setType"
            />
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-lesson`">{{ t('aiHelper.lessonOptional') }}</label>
            <select :id="`${baseId}-lesson`" v-model="lessonId" class="select" :disabled="locked">
              <option :value="null">{{ t('aiHelper.noLesson') }}</option>
              <option v-for="lesson in lessons" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
            </select>
            <p v-if="lessonsResource.error.value" class="field-error" role="alert">
              {{ lessonsResource.error.value }}
              <button class="btn btn-link btn-sm" type="button" @click="lessonsResource.reload">
                {{ t('common.retry') }}
              </button>
            </p>
            <p v-else-if="!lessonsResource.loading.value && !lessons.length" class="field-hint">
              {{ t('aiHelper.noLessonsHint') }}
            </p>
          </div>
        </div>

        <div class="field">
          <label class="field-label" :for="`${baseId}-target`">
            {{ t('aiHelper.targetText') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${baseId}-target`"
            ref="targetInput"
            v-model="target"
            class="input cjk-text"
            lang="zh-CN"
            required
            autocomplete="off"
            :maxlength="MAX_PRACTICE_TARGET_LENGTH"
            :disabled="locked"
            :placeholder="TARGET_FIELD[type].placeholder"
            :aria-describedby="`${baseId}-target-hint`"
          />
          <p :id="`${baseId}-target-hint`" class="field-hint">{{ t(TARGET_FIELD[type].hintKey) }}</p>
          <div v-if="phraseChips" class="ai-topics" role="group" :aria-label="phraseChips.label">
            <span class="ai-topics-label" aria-hidden="true">{{ phraseChips.label }}</span>
            <div lang="zh-CN">
              <FilterChips
                :model-value="target.trim()"
                :options="phraseChips.options"
                size="sm"
                :disabled="locked"
                @update:model-value="target = $event"
              />
            </div>
          </div>
        </div>

        <div v-if="type === 'RECORDING'" class="field">
          <span class="field-label">{{ t('aiHelper.recordInBrowser') }}</span>
          <div v-if="recorder.supported" class="ai-recorder">
            <button
              v-if="!recorder.isBusy.value"
              class="btn btn-secondary"
              type="button"
              :disabled="!typeAvailable || Boolean(blocked) || checking"
              data-testid="ai-record"
              @click="mic.request"
            >
              <IconGlyph name="mic" :size="16" />
              {{ recorder.recording.value ? t('aiHelper.recordAgain') : t('aiHelper.startRecording') }}
            </button>
            <button
              v-else
              class="btn btn-danger-soft"
              type="button"
              data-testid="ai-record-stop"
              @click="recorder.stop()"
            >
              <IconGlyph name="stop" :size="16" />
              {{ t('aiHelper.stop') }}
            </button>
            <p class="field-hint" role="status" data-testid="ai-recorder-status">
              <!-- The clock is for the eye: a screen reader is not told every second. -->
              <template v-if="recorder.isRecording.value">
                {{ t('aiHelper.recordingNow') }}
                <span aria-hidden="true">{{ formatClock(recorder.elapsedSeconds.value) }}</span>
                {{ recordingLine }}
              </template>
              <template v-else>{{ recorderStatus }}</template>
            </p>
          </div>
          <p v-else class="callout is-compact">
            <IconGlyph name="info" :size="16" /> {{ t('aiHelper.recorderUnsupported') }}
          </p>
          <div v-if="recorder.previewUrl.value && (!chosenFile || chosenFileError)" class="ai-recording">
            <IconGlyph name="audio" :size="18" />
            <div class="row-list-copy">
              <strong class="ai-recording-name">{{ t('aiHelper.yourRecording') }}</strong>
              <span class="row-list-meta">
                {{ recorder.recording.value?.name }} · {{ formatClock(recorder.elapsedSeconds.value) }}
              </span>
            </div>
            <audio
              ref="previewAudio"
              :src="recorder.previewUrl.value"
              preload="metadata"
              @play="previewPlaying = true"
              @pause="previewPlaying = false"
              @ended="previewPlaying = false"
            />
            <div class="btn-group is-sm">
              <button class="btn btn-ghost btn-sm" type="button" :disabled="checking" @click="togglePreview">
                <IconGlyph :name="previewPlaying ? 'stop' : 'play'" :size="16" />
                {{ previewPlaying ? t('aiHelper.pause') : t('aiHelper.play') }}
              </button>
              <button class="btn btn-ghost btn-sm" type="button" :disabled="checking" @click="removeRecording">
                <IconGlyph name="trash" :size="16" />
                {{ t('aiHelper.remove') }}
              </button>
            </div>
          </div>
          <p v-if="recordingError" class="field-error" role="alert">
            <IconGlyph name="alert" :size="16" />
            {{ recordingError }}
          </p>
        </div>

        <div class="field">
          <label class="field-label" :for="`${baseId}-file`">
            {{ type === 'RECORDING' ? t('aiHelper.chooseAudio') : t('aiHelper.choosePhoto') }}
          </label>
          <div class="ai-file-row">
            <label class="ai-file" :class="{ 'is-invalid': chosenFileError }">
              <input
                :id="`${baseId}-file`"
                ref="fileInput"
                class="sr-only"
                type="file"
                :accept="uploadAccept(type === 'RECORDING' ? 'audio' : 'image')"
                :disabled="locked"
                :aria-invalid="Boolean(chosenFileError) || undefined"
                :aria-describedby="chosenFileError ? `${baseId}-file-error` : `${baseId}-file-hint`"
                @change="chooseFile"
              />
              <span class="btn btn-sunken btn-sm" aria-hidden="true">
                <IconGlyph name="upload" :size="16" />
                {{ t('aiHelper.chooseFile') }}
              </span>
              <span class="ai-file-name">{{ fileLabel ?? t('aiHelper.noFileChosen') }}</span>
            </label>
            <button
              v-if="fileLabel"
              class="btn btn-ghost btn-icon btn-sm"
              type="button"
              :aria-label="t('aiHelper.remove')"
              :disabled="checking"
              @click="clearFile"
            >
              <IconGlyph name="close" :size="16" />
            </button>
          </div>
          <p v-if="chosenFileError" :id="`${baseId}-file-error`" class="field-error" role="alert">
            <IconGlyph name="alert" :size="16" />
            {{ chosenFileError }}
          </p>
          <p :id="`${baseId}-file-hint`" class="field-hint">{{ fileHint }}</p>
          <p v-if="type === 'HANDWRITING'" class="field-hint">{{ t('aiHelper.photoGuidance') }}</p>
          <img v-if="photoUrl" class="ai-photo-preview" :src="photoUrl" :alt="t('aiHelper.photoPreview')" />
        </div>

        <p v-if="status && !blocked && !typeAvailable" class="callout is-compact is-warning">
          {{ type === 'RECORDING' ? t('aiHelper.recordingUnavailable') : t('aiHelper.handwritingUnavailable') }}
        </p>
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>

        <p v-if="type === 'RECORDING' && typeAvailable" class="field-hint">{{ t('aiHelper.recordingDataNotice') }}</p>

        <div class="card-footer ai-check-submit">
          <p v-if="checking" class="field-hint ai-check-status" role="status">{{ checkingLine }}</p>
          <button class="btn btn-primary" type="submit" :disabled="!canCheck" :aria-busy="checking">
            <IconGlyph v-if="!checking" name="send" :size="16" />
            {{
              checking
                ? t('aiHelper.checking')
                : t(type === 'RECORDING' ? 'aiHelper.checkPronunciation' : 'aiHelper.checkWithSparkbot')
            }}
          </button>
        </div>
      </form>
    </section>

    <AiPracticeCheckHistory
      ref="historyRef"
      :viewing-check-id="result?.check.id ?? null"
      :reported-ids="reportedIds"
      :ai-notice="aiNotice"
      :drawer-suppressed="Boolean(reportTarget)"
      @report="report"
      @deleted="onCheckDeleted"
      @toast="(message, action) => emit('toast', message, action)"
    />
  </div>

  <ConfirmDialog
    v-if="mic.asking.value"
    :title="t('aiHelper.micAskTitle')"
    :message="t('aiHelper.micAskVoice')"
    :confirm-label="t('common.allowMicrophone')"
    :cancel-label="t('common.notNow')"
    @confirm="mic.confirm"
    @cancel="mic.cancel"
  />
  <AiReportDialog
    v-if="reportTarget"
    :payload="reportTarget.payload"
    :title="t(reportTarget.titleKey)"
    :subtitle="reportTarget.leadKey ? t(reportTarget.leadKey) : undefined"
    :attached="t(reportTarget.attachedKey)"
    @close="reportTarget = null"
    @sent="onReported"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onDeactivated, reactive, ref, shallowRef, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/common/SegmentedControl.vue'
import { useAudioRecorder } from '@/composables/useAudioRecorder'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMicPermission } from '@/composables/useMicPermission'
import { useResource } from '@/composables/useResource'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiVoiceMessages } from '@/plugins/aiVoiceMessages'
import { appLocale } from '@/plugins/i18n'
import type { AiHelperStatus, PracticeCheck, PracticeCheckLesson, PracticeCheckType } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, formatFileSize } from '@/utils/format'
import { MAX_PRACTICE_TARGET_LENGTH } from '@/utils/practiceUpload'
import { serverMessageOr } from '@/utils/serverMessages'
import { uploadAccept, uploadFileIssue } from '@/utils/uploadFormat'

import AiPracticeCheckHistory from './AiPracticeCheckHistory.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiVoiceResult, { type PracticeCheckView } from './AiVoiceResult.vue'
import '@/styles/ai-voice.css'
import type { AiBlockedReason, AiReportTarget } from './aiHelper'

const props = defineProps<{
  blocked: AiBlockedReason
  status: AiHelperStatus | null
  aiNotice: string
}>()
const emit = defineEmits<{
  used: []
  reported: []
  toast: [message: string, action?: { label: string; run: () => void }]
}>()

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiVoiceMessages] })
const baseId = useId()
const isUnmounted = useIsUnmounted()
const recorder = useAudioRecorder()
const mic = useMicPermission(startRecording)

const TARGET_FIELD: Readonly<Record<PracticeCheckType, { placeholder: string; hintKey: string }>> = {
  RECORDING: { placeholder: '谢谢', hintKey: 'aiHelper.targetHintRecording' },
  HANDWRITING: { placeholder: '好', hintKey: 'aiHelper.targetHintHandwriting' },
}

const type = ref<PracticeCheckType>('RECORDING')
const checkTypeOptions = computed<SegmentedOption<PracticeCheckType>[]>(() => [
  { value: 'RECORDING', label: t('aiHelper.recording'), icon: 'mic' },
  { value: 'HANDWRITING', label: t('aiHelper.handwriting'), icon: 'pen' },
])
const lessonId = ref<number | null>(null)
const target = ref('')
const targetInput = ref<HTMLInputElement | null>(null)
const chosenFile = shallowRef<File | null>(null)
const photoUrl = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const previewAudio = ref<HTMLAudioElement | null>(null)
const previewPlaying = ref(false)
const error = ref<string | null>(null)
const checking = ref(false)
const result = shallowRef<PracticeCheckView | null>(null)
const reportTarget = ref<AiReportTarget | null>(null)
const reportedIds = reactive(new Set<number>())
const resultView = ref<InstanceType<typeof AiVoiceResult> | null>(null)
const historyRef = ref<InstanceType<typeof AiPracticeCheckHistory> | null>(null)

const lessonsResource = useResource(aiHelperApi.practiceCheckLessons)
const lessons = computed<PracticeCheckLesson[]>(() => lessonsResource.data.value ?? [])

const typeAvailable = computed(() =>
  type.value === 'RECORDING'
    ? Boolean(props.status?.speechCheckAvailable)
    : Boolean(props.status?.handwritingCheckAvailable),
)
const locked = computed(() => checking.value || recorder.isBusy.value)

const phraseChips = computed(() => {
  const lesson = lessons.value.find((item) => item.id === lessonId.value)
  if (!lesson) return null
  const texts = lesson.phrases.map((phrase) => phrase.chinese.trim()).filter(Boolean)
  if (!texts.length) return null
  return {
    label: t('aiHelper.phrasesFrom', { lesson: lesson.title }),
    options: [...new Set(texts)].map((text) => ({ value: text, label: text })),
  }
})
const upload = computed(() => chosenFile.value ?? (type.value === 'RECORDING' ? recorder.recording.value : null))
const maxBytes = computed(() =>
  type.value === 'RECORDING' ? props.status?.audioUploadMaxBytes : props.status?.imageUploadMaxBytes,
)
const fileHint = computed(() => {
  const audio = type.value === 'RECORDING'
  if (maxBytes.value === undefined) return t(audio ? 'aiHelper.audioFormats' : 'aiHelper.photoFormats')
  return t(audio ? 'aiHelper.audioFileHint' : 'aiHelper.photoFileHint', { max: formatFileSize(maxBytes.value) })
})
const fileLabel = computed(() =>
  chosenFile.value ? `${chosenFile.value.name} · ${formatFileSize(chosenFile.value.size)}` : null,
)

function formatUploadIssue(file: File): string | null {
  const issue = uploadFileIssue(file, type.value === 'RECORDING' ? 'audio' : 'image', maxBytes.value)
  if (!issue) return null
  if (issue === 'fileTooLarge') {
    return t('aiHelper.fileTooLarge', { size: formatFileSize(file.size), max: formatFileSize(maxBytes.value) })
  }
  if (issue === 'fileEmpty') return t('aiHelper.fileEmpty')
  return type.value === 'RECORDING' ? t('aiHelper.audioWrongType') : t('aiHelper.photoWrongType')
}

const chosenFileError = computed(() => (chosenFile.value ? formatUploadIssue(chosenFile.value) : null))
const recordingError = computed(() =>
  !chosenFile.value && type.value === 'RECORDING' && recorder.recording.value
    ? formatUploadIssue(recorder.recording.value)
    : null,
)
const fileError = computed(() => chosenFileError.value ?? recordingError.value)
const canCheck = computed(
  () =>
    !props.blocked &&
    typeAvailable.value &&
    !locked.value &&
    !!target.value.trim() &&
    !!upload.value &&
    !fileError.value,
)

watch(recorder.previewUrl, () => {
  previewPlaying.value = false
})
watch(
  () => (type.value === 'HANDWRITING' && !chosenFileError.value ? chosenFile.value : null),
  (photo) => {
    revokePhoto()
    if (photo) photoUrl.value = URL.createObjectURL(photo)
  },
)

const recordingLine = computed(() => {
  const say = target.value.trim()
  return [say ? t('aiHelper.recordingSay', { target: say }) : '', t('aiHelper.recordingStays')]
    .filter(Boolean)
    .join(' ')
})

const recorderStatus = computed(() => {
  if (recorder.status.value === 'denied') return t('aiHelper.recorderDenied')
  if (recorder.status.value === 'error') return t('aiHelper.recorderFailed')
  return t('aiHelper.recordingStaysHere')
})

const checkingLine = computed(() => {
  if (type.value !== 'RECORDING') return t('aiHelper.checkingPhoto')
  return chosenFile.value ? t('aiHelper.checkingFile') : t('aiHelper.checkingRecording')
})

function setType(next: PracticeCheckType): void {
  if (next === type.value) return
  mic.cancel()
  type.value = next
  clearFile()
  recorder.reset()
  error.value = null
}

function revokePhoto(): void {
  if (photoUrl.value) URL.revokeObjectURL(photoUrl.value)
  photoUrl.value = null
}

function clearFile(): void {
  chosenFile.value = null
  if (fileInput.value) fileInput.value.value = ''
  error.value = null
}

function removeRecording(): void {
  recorder.reset()
  error.value = null
}

function chooseFile(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  clearFile()
  if (!file) return
  mic.cancel()
  error.value = null
  chosenFile.value = file
  if (!chosenFileError.value) recorder.reset()
}

async function startRecording(): Promise<void> {
  if (result.value || type.value !== 'RECORDING' || !typeAvailable.value || props.blocked || locked.value) return
  clearFile()
  error.value = null
  await recorder.start()
}

function pausePreview(): void {
  if (previewAudio.value && !previewAudio.value.paused) previewAudio.value.pause()
}

async function togglePreview(): Promise<void> {
  const audio = previewAudio.value
  if (!audio) return
  if (!audio.paused) {
    audio.pause()
    return
  }
  error.value = null
  try {
    await audio.play()
  } catch (reason) {
    if (audio === previewAudio.value && !(reason instanceof DOMException && reason.name === 'AbortError')) {
      error.value = t('aiHelper.previewFailed')
    }
  }
}

async function submit(): Promise<void> {
  const chosen = upload.value
  const kind = type.value
  if (!canCheck.value || !chosen) return
  const sessionVersion = getAuthSessionVersion()
  const stillActive = () => !isUnmounted() && sessionVersion === getAuthSessionVersion()
  mic.cancel()
  pausePreview()
  const fileName = chosenFile.value?.name ?? null
  error.value = null
  checking.value = true
  try {
    const outcome = await aiHelperApi.checkPractice({
      type: kind,
      file: chosen,
      targetText: target.value,
      lessonId: lessonId.value,
      languagePreference: appLocale.value,
    })
    if (!stillActive()) return
    if (outcome.status !== 'SUCCESS' || !outcome.check) {
      error.value = serverMessageOr(outcome.message, t('aiHelper.checkError'))
      return
    }
    clearResult()
    result.value = {
      check: outcome.check,
      photo: kind === 'HANDWRITING' ? photoUrl.value : null,
      fileName,
      audioFile: kind === 'RECORDING' && fileName !== null,
    }
    photoUrl.value = null
    clearFile()
    recorder.reset()
    historyRef.value?.showNewest(outcome.check.id)
    await nextTick()
    if (stillActive()) resultView.value?.reveal()
  } catch (reason) {
    if (stillActive()) error.value = resolveApiError(reason, t('aiHelper.checkError'))
  } finally {
    if (!isUnmounted()) checking.value = false
    if (stillActive()) emit('used')
  }
}

function clearResult(): void {
  if (result.value?.photo) URL.revokeObjectURL(result.value.photo)
  result.value = null
}

function onCheckDeleted(checkId: number): void {
  if (result.value?.check.id === checkId) clearResult()
}

async function tryAgain(): Promise<void> {
  clearResult()
  await nextTick()
  targetInput.value?.focus()
}

function report(check: PracticeCheck): void {
  reportTarget.value = {
    payload: { featureKey: 'PRACTICE_CHECK', practiceCheckId: check.id },
    titleKey: 'aiHelper.reportScore',
    attachedKey: 'aiHelper.reportScoreAttached',
  }
}

function onReported(): void {
  const id = reportTarget.value?.payload.practiceCheckId
  if (id) reportedIds.add(id)
  reportTarget.value = null
  emit('reported')
}

onBeforeUnmount(() => {
  revokePhoto()
  clearResult()
})
onDeactivated(() => {
  recorder.stop()
  pausePreview()
})
</script>
