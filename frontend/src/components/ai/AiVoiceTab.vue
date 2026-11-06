<template>
  <div class="ai-voice">
    <section
      v-if="result"
      ref="resultEl"
      class="panel ai-check-result"
      :aria-labelledby="`${baseId}-result`"
      data-testid="ai-check-result"
    >
      <header class="section-header">
        <h2 :id="`${baseId}-result`" class="card-title">
          {{ t('aiHelper.yourScore') }} · {{ kindLabel(result.type) }} ·
          <span lang="zh-CN">{{ result.targetText }}</span>
        </h2>
        <AiResultLabel short />
      </header>
      <AiCheckScore :check="result" :meta="checkMeta(result, t('aiHelper.checkedJustNow'))" />
      <p class="callout">{{ t('aiHelper.practiceScoreNote') }}</p>
      <div class="form-actions">
        <button v-if="!reportedIds.has(result.id)" class="btn btn-secondary" type="button" @click="report(result)">
          {{ t('aiHelper.reportScore') }}
        </button>
        <span v-else class="ai-reported"><IconGlyph name="status" :size="14" />{{ t('aiHelper.reported') }}</span>
        <button class="btn btn-primary" type="button" @click="tryAgain">{{ t('aiHelper.tryAgain') }}</button>
      </div>
    </section>

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
              :disabled="checking"
              @update:model-value="setType"
            />
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-lesson`">{{ t('aiHelper.lessonOptional') }}</label>
            <select :id="`${baseId}-lesson`" v-model="lessonId" class="select" :disabled="checking">
              <option :value="null">{{ t('aiHelper.noLesson') }}</option>
              <option v-for="lesson in lessons" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
            </select>
            <p v-if="!lessons.length" class="field-hint">{{ t('aiHelper.noLessonsHint') }}</p>
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
            :disabled="checking"
            :placeholder="type === 'RECORDING' ? '谢谢' : '好'"
            :aria-describedby="`${baseId}-target-hint`"
          />
          <p :id="`${baseId}-target-hint`" class="field-hint">
            {{ type === 'RECORDING' ? t('aiHelper.targetHintRecording') : t('aiHelper.targetHintHandwriting') }}
          </p>
        </div>

        <div v-if="type === 'RECORDING'" class="field">
          <span class="field-label">{{ t('aiHelper.recordInBrowser') }}</span>
          <div v-if="recorder.supported" class="ai-recorder" data-testid="ai-recorder">
            <button
              v-if="!recorder.isBusy.value"
              class="btn btn-secondary"
              type="button"
              :disabled="!typeAvailable || checking"
              @click="startRecording"
            >
              <IconGlyph name="mic" :size="16" />
              {{ recorder.recording.value ? t('aiHelper.recordAgain') : t('aiHelper.startRecording') }}
            </button>
            <button v-else class="btn btn-danger-soft" type="button" @click="recorder.stop()">
              <IconGlyph name="stop" :size="16" />
              {{ t('aiHelper.stopRecording', { time: formatClock(recorder.elapsedSeconds.value) }) }}
            </button>
            <p class="field-hint" role="status">{{ recorderStatus }}</p>
          </div>
          <p v-else class="callout is-compact">
            <IconGlyph name="info" :size="16" /> {{ t('aiHelper.recorderUnsupported') }}
          </p>
          <div v-if="recorder.previewUrl.value && !chosenFile" class="ai-recording">
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
              <button class="btn btn-ghost btn-sm" type="button" :disabled="checking" @click="recorder.reset()">
                <IconGlyph name="trash" :size="16" />
                {{ t('aiHelper.remove') }}
              </button>
            </div>
          </div>
        </div>

        <div class="field">
          <label class="field-label" :for="`${baseId}-file`">
            {{ type === 'RECORDING' ? t('aiHelper.chooseAudio') : t('aiHelper.choosePhoto') }}
          </label>
          <div class="ai-file-row">
            <label class="ai-file" :class="{ 'is-invalid': fileError }">
              <input
                :id="`${baseId}-file`"
                ref="fileInput"
                class="sr-only"
                type="file"
                :accept="type === 'RECORDING' ? UPLOAD_ACCEPT.audio : UPLOAD_ACCEPT.image"
                :disabled="checking"
                :aria-invalid="Boolean(fileError) || undefined"
                :aria-describedby="fileError ? `${baseId}-file-error` : `${baseId}-file-hint`"
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
          <p v-if="fileError" :id="`${baseId}-file-error`" class="field-error" role="alert">
            <IconGlyph name="alert" :size="16" />
            {{ fileError }}
          </p>
          <p :id="`${baseId}-file-hint`" class="field-hint">
            {{ type === 'RECORDING' ? t('aiHelper.audioFileHint') : t('aiHelper.photoFileHint') }}
          </p>
          <img v-if="photoUrl" class="ai-photo-preview" :src="photoUrl" :alt="t('aiHelper.photoPreview')" />
        </div>

        <p v-if="!blocked && !typeAvailable" class="callout is-compact is-warning">
          {{ type === 'RECORDING' ? t('aiHelper.recordingUnavailable') : t('aiHelper.handwritingUnavailable') }}
        </p>
        <p v-if="error" class="callout is-compact is-danger" role="alert">{{ error }}</p>

        <div class="card-footer ai-check-submit">
          <p v-if="checking" class="field-hint ai-check-status" role="status">
            {{ type === 'RECORDING' ? t('aiHelper.checkingRecording') : t('aiHelper.checkingPhoto') }}
          </p>
          <button class="btn btn-primary" type="submit" :disabled="!canCheck" :aria-busy="checking">
            <IconGlyph v-if="!checking" name="send" :size="16" />
            {{ checking ? t('aiHelper.checking') : t('aiHelper.checkWithSparkbot') }}
          </button>
        </div>
      </form>
    </section>

    <div class="ai-voice-rail">
      <section class="panel ai-checks" :aria-labelledby="`${baseId}-recent`">
        <h2 :id="`${baseId}-recent`" class="card-title">{{ t('aiHelper.recentChecks') }}</h2>
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
        <ul v-else-if="checks.length" class="row-list">
          <li v-for="check in checks" :key="check.id" class="ai-check-item">
            <div class="row-list-copy">
              <span class="row-list-title">
                {{ kindLabel(check.type) }} · <span lang="zh-CN">{{ check.targetText }}</span>
              </span>
              <span class="row-list-meta">{{ whenLabel(check) }}</span>
            </div>
            <div class="row-list-actions">
              <span class="tag is-outline">{{ t('aiHelper.outOf100', { score: check.score }) }}</span>
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                :aria-label="t('aiHelper.openCheck', { kind: kindLabel(check.type), target: check.targetText })"
                @click="openCheck = check"
              >
                {{ t('aiHelper.open') }}
              </button>
            </div>
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
      </section>
      <AiNotice v-if="!result" :text="aiNotice" guidance />
    </div>
  </div>

  <AppOverlay
    v-if="openCheck && !confirmDelete && !reportTarget"
    variant="drawer"
    :title="t('aiHelper.checkDetailTitle', { kind: kindLabel(openCheck.type), target: openCheck.targetText })"
    :subtitle="
      [openCheck.lessonTitle, t('aiHelper.checkedOn', { time: formatDateTime(openCheck.checkedAt) })]
        .filter(Boolean)
        .join(' · ')
    "
    @close="openCheck = null"
  >
    <dl class="kv-list">
      <dt>{{ t('aiHelper.lesson') }}</dt>
      <dd>{{ openCheck.lessonTitle || t('aiHelper.noLesson') }}</dd>
      <dt>{{ t('aiHelper.checkType') }}</dt>
      <dd>{{ kindLabel(openCheck.type) }}</dd>
      <dt>{{ t('aiHelper.target') }}</dt>
      <dd lang="zh-CN">{{ openCheck.targetText }}</dd>
      <dt>{{ t('aiHelper.checked') }}</dt>
      <dd>{{ formatDateTime(openCheck.checkedAt) }}</dd>
      <dt>{{ t('aiHelper.score') }}</dt>
      <dd>
        <StatusPill :label="t('aiHelper.scorePill', { score: openCheck.score })" tone="outline" />
      </dd>
    </dl>
    <section class="ai-check-detail" :aria-labelledby="`${baseId}-detail`">
      <header class="ai-check-detail-head">
        <h3 :id="`${baseId}-detail`" class="card-title">{{ t('aiHelper.sparkbotScore') }}</h3>
        <span class="tag">
          <IconGlyph name="ai" :size="14" />
          {{ t('aiHelper.practiceOnly') }}
        </span>
      </header>
      <AiCheckScore :check="openCheck" />
      <p class="field-hint">{{ t('aiHelper.practiceScoreShort') }}</p>
      <button
        v-if="!reportedIds.has(openCheck.id)"
        class="btn btn-ghost btn-sm ai-check-report"
        type="button"
        @click="report(openCheck)"
      >
        <IconGlyph name="flag" :size="16" />
        {{ t('aiHelper.reportScore') }}
      </button>
      <span v-else class="ai-reported"><IconGlyph name="status" :size="14" />{{ t('aiHelper.reported') }}</span>
      <p v-if="detailError" class="callout is-compact is-danger" role="alert">{{ detailError }}</p>
    </section>
    <template #footer>
      <button class="btn btn-danger-soft is-far-left" type="button" @click="confirmDelete = true">
        <IconGlyph name="trash" :size="16" />
        {{ t('aiHelper.delete') }}
      </button>
      <button class="btn btn-secondary" type="button" @click="openCheck = null">{{ t('aiHelper.close') }}</button>
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
import AppOverlay from '@/components/common/AppOverlay.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import SegmentedControl, { type SegmentedOption } from '@/components/common/SegmentedControl.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
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
import AiNotice from './AiNotice.vue'
import AiReportDialog from './AiReportDialog.vue'
import AiResultLabel from './AiResultLabel.vue'
import { revealResult, type AiBlockedReason, type AiReportTarget } from './aiHelper'

/**
 * Pronunciation & handwriting: what to check (type, lesson, target text, a recording made here or a chosen file or
 * photo) with Recent checks beside it; the score takes the form's place until the student tries again. SparkBot scores
 * the file for the student only; it is sent once and not kept, and Recent checks keep only the result. Teachers and
 * admins never see these checks.
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
  { value: 'RECORDING', label: t('aiHelper.recording'), icon: 'mic' },
  { value: 'HANDWRITING', label: t('aiHelper.handwriting'), icon: 'pen' },
])
const lessonId = ref<number | null>(null)
const target = ref('')
const targetInput = ref<HTMLInputElement | null>(null)
const chosenFile = ref<File | null>(null)
const chosenDuration = ref<number | null>(null)
/** The refused file's name and size, shown with the reason. */
const rejectedLabel = ref<string | null>(null)
const photoUrl = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const fileError = ref<string | null>(null)
const previewAudio = ref<HTMLAudioElement | null>(null)
const previewPlaying = ref(false)
const error = ref<string | null>(null)
const checking = ref(false)
const result = ref<PracticeCheck | null>(null)
/** The check that was just made: Recent checks says "just now" for it. */
const newestId = ref<number | null>(null)
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
const fileLabel = computed(() => (chosenFile.value ? fileSummary(chosenFile.value) : rejectedLabel.value))
const recorderStatus = computed(() => {
  if (recorder.isRecording.value) return t('aiHelper.recordingNow')
  if (recorder.status.value === 'denied') return t('aiHelper.micDenied')
  if (recorder.status.value === 'error') return t('aiHelper.recorderFailed')
  return t('aiHelper.recordingStaysHere')
})

function kindLabel(kind: PracticeCheckType): string {
  return kind === 'RECORDING' ? t('aiHelper.recording') : t('aiHelper.handwriting')
}

function checkMeta(check: PracticeCheck, when: string): string {
  return [check.lessonTitle, when].filter(Boolean).join(' · ')
}

function whenLabel(check: PracticeCheck): string {
  return check.id === newestId.value ? t('aiHelper.justNow') : formatDateTime(check.checkedAt)
}

function fileSummary(file: File): string {
  return `${file.name} · ${formatFileSize(file.size)}`
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
  rejectedLabel.value = null
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
    rejectedLabel.value = fileSummary(file)
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

function togglePreview(): void {
  const audio = previewAudio.value
  if (!audio) return
  if (audio.paused) void audio.play()
  else audio.pause()
}

async function submit(): Promise<void> {
  const chosen = upload.value
  if (!canCheck.value || !chosen) return
  error.value = null
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
    newestId.value = outcome.check.id
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
async function tryAgain(): Promise<void> {
  result.value = null
  await nextTick()
  targetInput.value?.focus()
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
  emit('reported')
}

async function deleteCheck(check: PracticeCheck): Promise<void> {
  deleting.value = true
  try {
    await aiHelperApi.deletePracticeCheck(check.id)
    if (result.value?.id === check.id) result.value = null
    openCheck.value = null
    confirmDelete.value = false
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
