<template>
  <div class="ai-stage">
    <section class="ai-side" :aria-labelledby="`${baseId}-form`">
      <header class="ai-side-head">
        <h2 :id="`${baseId}-form`" class="ai-side-title">{{ t('aiHelper.checkTitle') }}</h2>
      </header>

      <form class="ai-check-fields" @submit.prevent="submit">
        <SegmentedControl
          :model-value="type"
          :options="checkTypeOptions"
          :label="t('aiHelper.checkType')"
          :disabled="checking"
          @update:model-value="setType"
        />

        <div class="field">
          <label class="field-label" :for="`${baseId}-target`">
            {{ t('aiHelper.targetText') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${baseId}-target`"
            v-model="target"
            class="input cjk-text ai-check-target-input"
            lang="zh-CN"
            required
            autocomplete="off"
            :maxlength="MAX_PRACTICE_TARGET_LENGTH"
            :placeholder="type === 'RECORDING' ? '谢谢' : '好'"
            :aria-describedby="`${baseId}-target-hint`"
          />
          <p :id="`${baseId}-target-hint`" class="field-hint">
            {{ type === 'RECORDING' ? t('aiHelper.targetHintRecording') : t('aiHelper.targetHintHandwriting') }}
          </p>
        </div>

        <template v-if="type === 'RECORDING'">
          <div v-if="recorder.supported" class="ai-recorder" data-testid="ai-recorder">
            <button
              v-if="!recorder.isBusy.value"
              class="ai-record"
              type="button"
              :disabled="!typeAvailable || checking"
              @click="startRecording"
            >
              <span class="ai-record-icon" aria-hidden="true"><IconGlyph name="mic" :size="20" /></span>
              {{ recorder.recording.value ? t('aiHelper.recordAgain') : t('aiHelper.startRecording') }}
            </button>
            <button v-else class="ai-record is-recording" type="button" @click="recorder.stop()">
              <span class="ai-record-icon" aria-hidden="true"><IconGlyph name="stop" :size="20" /></span>
              {{ t('aiHelper.stopRecording', { time: formatClock(recorder.elapsedSeconds.value) }) }}
            </button>
            <p class="ai-recorder-status" role="status">{{ recorderStatus }}</p>
          </div>
          <p v-else class="callout is-compact">
            <IconGlyph name="info" :size="16" /> {{ t('aiHelper.recorderUnsupported') }}
          </p>
          <div v-if="recorder.previewUrl.value && !chosenFile" class="ai-recording">
            <p class="ai-recording-name">
              <strong>{{ t('aiHelper.yourRecording') }}</strong>
              <small>{{ formatClock(recorder.elapsedSeconds.value) }}</small>
            </p>
            <audio
              :src="recorder.previewUrl.value"
              controls
              preload="metadata"
              :aria-label="t('aiHelper.yourRecording')"
            />
            <button class="btn btn-ghost btn-sm" type="button" @click="recorder.reset()">
              <IconGlyph name="trash" :size="16" />
              {{ t('aiHelper.remove') }}
            </button>
          </div>
        </template>

        <div class="field">
          <label class="field-label" :for="`${baseId}-file`">
            {{ type === 'RECORDING' ? t('aiHelper.chooseAudio') : t('aiHelper.choosePhoto') }}
          </label>
          <input
            :id="`${baseId}-file`"
            ref="fileInput"
            class="input"
            type="file"
            :accept="type === 'RECORDING' ? UPLOAD_ACCEPT.audio : UPLOAD_ACCEPT.image"
            :aria-invalid="Boolean(fileError) || undefined"
            :aria-describedby="fileError ? `${baseId}-file-error` : `${baseId}-file-hint`"
            @change="chooseFile"
          />
          <p :id="`${baseId}-file-hint`" class="field-hint">
            {{ type === 'RECORDING' ? t('aiHelper.audioFileHint') : t('aiHelper.photoFileHint') }}
          </p>
          <p v-if="fileError" :id="`${baseId}-file-error`" class="field-error" role="alert">{{ fileError }}</p>
        </div>
        <div v-if="chosenFile" class="ai-recording">
          <img v-if="photoUrl" class="ai-photo-preview" :src="photoUrl" :alt="t('aiHelper.photoPreview')" />
          <p class="ai-recording-name">
            <strong>{{ chosenFile.name }}</strong>
            <small>{{ formatFileSize(chosenFile.size) }}</small>
          </p>
          <button class="btn btn-ghost btn-sm" type="button" @click="clearFile">
            <IconGlyph name="trash" :size="16" />
            {{ t('aiHelper.remove') }}
          </button>
        </div>

        <div class="field">
          <label class="field-label" :for="`${baseId}-lesson`">{{ t('aiHelper.lessonOptional') }}</label>
          <select
            :id="`${baseId}-lesson`"
            v-model="lessonId"
            class="select"
            :aria-describedby="`${baseId}-lesson-hint`"
          >
            <option :value="null">{{ t('aiHelper.noLesson') }}</option>
            <option v-for="lesson in lessons" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
          </select>
          <p :id="`${baseId}-lesson-hint`" class="field-hint">
            {{ lessons.length ? t('aiHelper.lessonHint') : t('aiHelper.noLessonsHint') }}
          </p>
        </div>

        <p v-if="!blocked && !typeAvailable" class="callout is-compact is-warning">
          {{ type === 'RECORDING' ? t('aiHelper.recordingUnavailable') : t('aiHelper.handwritingUnavailable') }}
        </p>
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>

        <div class="ai-check-submit">
          <button class="btn btn-primary" type="submit" :disabled="!canCheck" :aria-busy="checking">
            <IconGlyph v-if="!checking" name="send" :size="16" />
            {{ t('aiHelper.checkWithSparkbot') }}
          </button>
          <p class="field-hint">{{ t('aiHelper.checkSubmitHint') }}</p>
        </div>
      </form>
    </section>

    <div class="ai-output">
      <StatePanel
        v-if="checking"
        tone="loading"
        :title="t('aiHelper.checking')"
        :message="type === 'RECORDING' ? t('aiHelper.checkingRecording') : t('aiHelper.checkingPhoto')"
      />
      <section
        v-else-if="result"
        ref="resultEl"
        class="ai-sheet ai-check-result"
        :aria-labelledby="`${baseId}-result`"
        data-testid="ai-check-result"
      >
        <header class="ai-result-head">
          <div class="ai-result-heading">
            <p class="ai-overline">{{ t('aiHelper.yourScore') }}</p>
            <h2 :id="`${baseId}-result`" class="ai-result-title">
              {{ kindLabel(result.type) }} · <span class="cjk-text" lang="zh-CN">{{ result.targetText }}</span>
            </h2>
          </div>
          <div class="ai-result-tools">
            <AiResultLabel />
          </div>
        </header>
        <AiCheckScore :check="result" :meta="checkMeta(result, true)" />
        <AiNotice :text="aiNotice" />
        <p v-if="notice" class="callout is-compact" role="status">{{ notice }}</p>
        <div class="ai-result-actions">
          <button class="btn btn-primary" type="button" @click="tryAgain">
            <IconGlyph name="retry" :size="16" />
            {{ t('aiHelper.tryAgain') }}
          </button>
          <button v-if="!reportedIds.has(result.id)" class="btn btn-ghost" type="button" @click="report(result)">
            <IconGlyph name="flag" :size="16" />
            {{ t('aiHelper.reportScore') }}
          </button>
          <span v-else class="ai-reported"><IconGlyph name="status" :size="16" />{{ t('aiHelper.reported') }}</span>
        </div>
      </section>

      <section class="ai-sheet ai-checks" :aria-labelledby="`${baseId}-recent`">
        <header class="ai-checks-head">
          <h2 :id="`${baseId}-recent`" class="ai-recap-label">{{ t('aiHelper.recentChecks') }}</h2>
          <p class="ai-checks-caption">{{ t('aiHelper.recentChecksSubtitle') }}</p>
        </header>
        <StatePanel
          v-if="checksResource.loading.value"
          tone="loading"
          :title="t('aiHelper.checksLoading')"
          :message="t('aiHelper.checksLoadingHint')"
          :skeleton="false"
        />
        <StatePanel
          v-else-if="checksResource.error.value && !checks.length"
          tone="error"
          icon="alert"
          :title="t('aiHelper.checksError')"
          :message="checksResource.error.value"
        >
          <template #actions>
            <button class="btn btn-secondary btn-sm" type="button" @click="checksResource.reload">
              {{ t('aiHelper.retry') }}
            </button>
          </template>
        </StatePanel>
        <ul v-else-if="checks.length" class="ai-check-list">
          <li v-for="check in checks" :key="check.id" class="ai-check-item">
            <button
              class="ai-check-open"
              type="button"
              :aria-label="t('aiHelper.openCheck', { kind: kindLabel(check.type), target: check.targetText })"
              @click="openCheck = check"
            >
              <span class="ai-check-kind" aria-hidden="true">
                <IconGlyph :name="check.type === 'RECORDING' ? 'mic' : 'pen'" :size="16" />
              </span>
              <span class="ai-check-item-copy">
                <span class="ai-check-item-zh cjk-text" lang="zh-CN">{{ check.targetText }}</span>
                <small>{{ kindLabel(check.type) }} · {{ checkMeta(check, false) }}</small>
              </span>
              <span class="ai-check-item-score">{{ t('aiHelper.outOf100', { score: check.score }) }}</span>
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </li>
        </ul>
        <StatePanel
          v-else
          tone="empty"
          icon="mic"
          :title="t('aiHelper.noChecks')"
          :message="t('aiHelper.noChecksHint')"
        />
        <PaginationBar
          v-if="totalPages > 1"
          :current-page="checksPage + 1"
          :total-pages="totalPages"
          :label="t('aiHelper.recentChecks')"
          @update:current-page="goToPage($event - 1)"
        />

        <details class="ai-how">
          <summary>
            <IconGlyph class="ai-disclosure" name="chevronDown" :size="16" />
            {{ t('aiHelper.howScoring') }}
          </summary>
          <ul class="ai-how-list">
            <li><IconGlyph name="ear" :size="16" />{{ t('aiHelper.howRecording') }}</li>
            <li><IconGlyph name="pen" :size="16" />{{ t('aiHelper.howHandwriting') }}</li>
            <li><IconGlyph name="star" :size="16" />{{ t('aiHelper.howNoXp') }}</li>
            <li><IconGlyph name="lock" :size="16" />{{ t('aiHelper.howPrivate') }}</li>
            <li><IconGlyph name="flag" :size="16" />{{ t('aiHelper.howReport') }}</li>
          </ul>
        </details>
      </section>
    </div>
  </div>

  <AppOverlay
    v-if="openCheck && !confirmDelete && !reportTarget"
    size="lg"
    :title="t('aiHelper.checkDetailTitle', { kind: kindLabel(openCheck.type), target: openCheck.targetText })"
    @close="openCheck = null"
  >
    <div class="ai-check-detail">
      <dl class="kv-list">
        <div>
          <dt>{{ t('aiHelper.checkType') }}</dt>
          <dd>{{ openCheck.type === 'RECORDING' ? t('aiHelper.recording') : t('aiHelper.handwritingPhoto') }}</dd>
        </div>
        <div>
          <dt>{{ t('aiHelper.lesson') }}</dt>
          <dd>{{ openCheck.lessonTitle || t('aiHelper.noLesson') }}</dd>
        </div>
        <div>
          <dt>{{ t('aiHelper.checked') }}</dt>
          <dd>{{ formatDateTime(openCheck.checkedAt) }}</dd>
        </div>
      </dl>
      <AiCheckScore :check="openCheck" :meta="t('aiHelper.practiceScoreShort')" />
      <p v-if="detailError" class="callout is-compact is-danger" role="alert">{{ detailError }}</p>
    </div>
    <template #footer>
      <button class="btn btn-danger-soft" type="button" @click="confirmDelete = true">
        <IconGlyph name="trash" :size="16" />
        {{ t('aiHelper.delete') }}
      </button>
      <button v-if="!reportedIds.has(openCheck.id)" class="btn btn-secondary" type="button" @click="report(openCheck)">
        <IconGlyph name="flag" :size="16" />
        {{ t('aiHelper.reportScore') }}
      </button>
      <button class="btn btn-primary" type="button" @click="openCheck = null">{{ t('aiHelper.close') }}</button>
    </template>
  </AppOverlay>
  <ConfirmDialog
    v-if="openCheck && confirmDelete"
    tone="danger"
    :busy="deleting"
    :title="t('aiHelper.deleteCheckTitle')"
    :message="t('aiHelper.deleteCheckMessage', { kind: kindLabel(openCheck.type), target: openCheck.targetText })"
    :confirm-label="t('aiHelper.deleteCheckConfirm')"
    @confirm="deleteCheck(openCheck)"
    @cancel="confirmDelete = false"
  />
  <AiReportDialog v-if="reportTarget" :target="reportTarget" @close="reportTarget = null" @sent="onReported" />
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import SegmentedControl, { type SegmentedOption } from '@/components/common/SegmentedControl.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useAudioRecorder } from '@/composables/useAudioRecorder'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { useUiStore } from '@/stores/ui'
import type { AiHelperStatus, PracticeCheck, PracticeCheckType } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, formatDateTime, formatFileSize } from '@/utils/format'
import {
  MAX_PRACTICE_TARGET_LENGTH,
  MAX_PRACTICE_UPLOAD_BYTES,
  practiceFileIssue,
  UPLOAD_ACCEPT,
} from '@/utils/practiceUpload'
import { localizedServerMessage } from '@/utils/serverMessages'

import AiCheckScore from './AiCheckScore.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import { revealResult, type AiBlockedReason, type AiReportTarget } from './aiHelper'

/**
 * Pronunciation & handwriting: what to check on the left (type, the target text, a recording made here or a chosen
 * file or photo, an optional lesson), the score and Recent checks on the right. SparkBot scores the file for the
 * student only; it is sent once and not kept, and Recent checks keep only the result. Teachers and admins never see
 * these checks.
 */
const props = defineProps<{
  blocked: AiBlockedReason
  status: AiHelperStatus | null
  aiNotice: string
}>()
const emit = defineEmits<{ used: []; reported: [] }>()

const PAGE_SIZE = 5
const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const ui = useUiStore()
const baseId = useId()
const recorder = useAudioRecorder()

const type = ref<PracticeCheckType>('RECORDING')
const checkTypeOptions = computed<SegmentedOption<PracticeCheckType>[]>(() => [
  { value: 'RECORDING', label: t('aiHelper.recording') },
  { value: 'HANDWRITING', label: t('aiHelper.handwriting') },
])
const lessonId = ref<number | null>(null)
const target = ref('')
const chosenFile = ref<File | null>(null)
const chosenDuration = ref<number | null>(null)
const photoUrl = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const fileError = ref<string | null>(null)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const checking = ref(false)
const result = ref<PracticeCheck | null>(null)
const openCheck = ref<PracticeCheck | null>(null)
const confirmDelete = ref(false)
const deleting = ref(false)
const detailError = ref<string | null>(null)
const reportTarget = ref<AiReportTarget | null>(null)
const reportedIds = ref(new Set<number>())
const checksPage = ref(0)
const resultEl = ref<HTMLElement | null>(null)

const lessonsResource = useResource(() => aiHelperApi.practiceCheckLessons())
const lessons = computed(() => lessonsResource.data.value ?? [])
const checksResource = useResource(() => aiHelperApi.practiceChecks(checksPage.value, PAGE_SIZE))
const { items: checks, totalPages, goToPage } = useServerPage(checksResource, checksPage)

const typeAvailable = computed(() =>
  type.value === 'RECORDING'
    ? Boolean(props.status?.speechCheckAvailable)
    : Boolean(props.status?.handwritingCheckAvailable),
)
const upload = computed<{ file: File; duration: number | null } | null>(() => {
  if (chosenFile.value) return { file: chosenFile.value, duration: chosenDuration.value }
  if (type.value === 'RECORDING' && recorder.recording.value) {
    return { file: recorder.recording.value, duration: recorder.elapsedSeconds.value || null }
  }
  return null
})
const canCheck = computed(
  () =>
    !props.blocked && typeAvailable.value && !checking.value && Boolean(target.value.trim()) && Boolean(upload.value),
)
const recorderStatus = computed(() => {
  if (recorder.isRecording.value) return t('aiHelper.recordingNow')
  if (recorder.status.value === 'denied') return t('aiHelper.micDenied')
  if (recorder.status.value === 'error') return t('aiHelper.recorderFailed')
  return t('aiHelper.recordingStaysHere')
})

function kindLabel(kind: PracticeCheckType): string {
  return kind === 'RECORDING' ? t('aiHelper.recording') : t('aiHelper.handwriting')
}

function checkMeta(check: PracticeCheck, justNow: boolean): string {
  const when = justNow ? t('aiHelper.checkedJustNow') : formatDateTime(check.checkedAt)
  return [check.lessonTitle, when].filter(Boolean).join(' · ')
}

function setType(next: PracticeCheckType): void {
  if (next === type.value) return
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
  chosenDuration.value = null
  fileError.value = null
  revokePhoto()
  if (fileInput.value) fileInput.value.value = ''
}

/** The length of a chosen recording, when the browser can read it (helps SparkBot judge the pace). */
function readDuration(file: File): void {
  if (typeof Audio === 'undefined') return
  const url = URL.createObjectURL(file)
  const audio = new Audio()
  audio.preload = 'metadata'
  audio.onloadedmetadata = () => {
    if (chosenFile.value === file && Number.isFinite(audio.duration)) chosenDuration.value = Math.round(audio.duration)
    URL.revokeObjectURL(url)
  }
  audio.onerror = () => URL.revokeObjectURL(url)
  audio.src = url
}

function chooseFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  clearFile()
  if (!file) return
  const kind = type.value === 'RECORDING' ? 'audio' : 'image'
  const issue = practiceFileIssue(file, kind)
  if (issue) {
    input.value = ''
    fileError.value =
      issue === 'fileTooLarge'
        ? t('aiHelper.fileTooLarge', {
            size: formatFileSize(file.size),
            max: formatFileSize(MAX_PRACTICE_UPLOAD_BYTES),
          })
        : issue === 'fileEmpty'
          ? t('aiHelper.fileEmpty')
          : kind === 'audio'
            ? t('aiHelper.audioWrongType')
            : t('aiHelper.photoWrongType')
    return
  }
  recorder.reset()
  chosenFile.value = file
  if (kind === 'image') photoUrl.value = URL.createObjectURL(file)
  else readDuration(file)
}

async function startRecording(): Promise<void> {
  clearFile()
  error.value = null
  await recorder.start()
}

async function submit(): Promise<void> {
  const chosen = upload.value
  if (!canCheck.value || !chosen) return
  error.value = null
  notice.value = null
  checking.value = true
  try {
    const outcome = await aiHelperApi.checkPractice({
      type: type.value,
      file: chosen.file,
      fileName: chosen.file.name,
      targetText: target.value,
      lessonId: lessonId.value,
      durationSeconds: chosen.duration,
      languagePreference: ui.locale,
    })
    if (outcome.status !== 'SUCCESS' || !outcome.check) {
      error.value = localizedServerMessage(outcome.message, t('aiHelper.checkError'))
      return
    }
    result.value = outcome.check
    clearFile()
    recorder.reset()
    goToPage(0)
    checking.value = false
    await nextTick()
    revealResult(resultEl.value)
  } catch (reason) {
    error.value = resolveApiError(reason, t('aiHelper.checkError'))
  } finally {
    checking.value = false
    emit('used')
  }
}

/** Puts the score away and goes back to the target text for the next try. */
function tryAgain(): void {
  result.value = null
  notice.value = null
  document.getElementById(`${baseId}-target`)?.focus()
}

function report(check: PracticeCheck): void {
  reportTarget.value = {
    payload: { featureKey: 'PRACTICE_CHECK', practiceCheckId: check.id },
    titleKey: 'aiHelper.reportScoreTitle',
    attachedKey: 'aiHelper.reportScoreAttached',
  }
}

function onReported(): void {
  const id = reportTarget.value?.payload.practiceCheckId
  if (id) reportedIds.value = new Set([...reportedIds.value, id])
  reportTarget.value = null
  notice.value = t('aiHelper.reportScoreSent')
  emit('reported')
}

async function deleteCheck(check: PracticeCheck): Promise<void> {
  deleting.value = true
  try {
    await aiHelperApi.deletePracticeCheck(check.id)
    if (result.value?.id === check.id) result.value = null
    openCheck.value = null
    confirmDelete.value = false
    notice.value = null
    void checksResource.reload()
  } catch (reason) {
    confirmDelete.value = false
    detailError.value = resolveApiError(reason, t('aiHelper.deleteError'))
  } finally {
    deleting.value = false
  }
}

watch(openCheck, () => {
  detailError.value = null
  confirmDelete.value = false
})

onBeforeUnmount(revokePhoto)
</script>
