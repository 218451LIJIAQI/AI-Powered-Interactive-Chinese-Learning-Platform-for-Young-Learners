<template>
  <div class="lesson-with-rail">
    <div class="page-stack">
      <section class="panel authoring-card" aria-labelledby="lesson-details-title">
        <h2 id="lesson-details-title" class="card-title">{{ t('lessonEditor.details.title') }}</h2>
        <div class="form-grid two">
          <div class="field">
            <label class="field-label" for="lesson-title">
              {{ t('lessonEditor.fields.title') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="lesson-title"
              v-model="title"
              class="input"
              maxlength="200"
              required
              :disabled="!editor.editable"
              :aria-invalid="Boolean(titleError)"
              :aria-describedby="titleError ? 'lesson-title-error' : undefined"
              @blur="emit('blurTitle')"
            />
            <p v-if="titleError" id="lesson-title-error" class="field-error" role="alert">{{ titleError }}</p>
          </div>
          <div class="field">
            <label class="field-label" for="lesson-unit">
              {{ t('lessonEditor.details.unit') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <select
              id="lesson-unit"
              v-model="unitId"
              class="select"
              :disabled="!editor.editable"
              :aria-describedby="editor.status === 'PUBLISHED' && editor.editable ? 'lesson-unit-hint' : undefined"
            >
              <option v-for="unit in editor.units" :key="unit.id" :value="unit.id">
                {{ unitNames.get(unit.id) }}
              </option>
            </select>
            <p v-if="editor.status === 'PUBLISHED' && editor.editable" id="lesson-unit-hint" class="field-hint">
              {{ t('lessonEditor.details.unitMoveLive') }}
            </p>
          </div>
        </div>
      </section>

      <section class="panel authoring-card" aria-labelledby="lesson-phrases-title">
        <div class="section-header">
          <h2 id="lesson-phrases-title" class="card-title">{{ t('lessonEditor.phrases.title') }}</h2>
          <button
            v-if="editor.editable && keyPhrases.length < 30"
            ref="addPhraseButton"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="addPhrase($event)"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.phrases.add') }}
          </button>
        </div>
        <p v-if="!keyPhrases.length" class="type-caption">{{ t('lessonEditor.phrases.empty') }}</p>
        <div
          v-else
          ref="phrasesGroup"
          class="lesson-phrases"
          role="group"
          :aria-label="t('lessonEditor.phrases.label')"
        >
          <div class="lesson-phrase-row is-head" aria-hidden="true">
            <span />
            <span v-for="column in phraseColumns" :key="column.key">{{ column.label }}</span>
            <span />
          </div>
          <div v-for="(phrase, index) in keyPhrases" :key="index" class="lesson-phrase-row" data-row-item>
            <span class="authoring-tile" aria-hidden="true">{{ index + 1 }}</span>
            <label v-for="column in phraseColumns" :key="column.key" class="lesson-phrase-field">
              <span class="lesson-phrase-label">{{ column.label }}</span>
              <input
                v-model="phrase[column.key]"
                class="input"
                :class="{ 'cjk-text': column.key === 'chinese' }"
                :maxlength="column.maxlength"
                :readonly="!editor.editable"
                :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${column.label}`"
              />
            </label>
            <button
              v-if="editor.editable"
              type="button"
              class="btn btn-ghost btn-icon btn-sm"
              data-row-remove
              :aria-label="t('lessonEditor.phrases.remove', { number: index + 1 })"
              @click="removePhrase(index, $event)"
            >
              <IconGlyph name="trash" :size="16" />
            </button>
          </div>
        </div>
      </section>
    </div>

    <section class="panel authoring-card" aria-labelledby="lesson-video-title">
      <div class="authoring-card-head">
        <h2 id="lesson-video-title" class="card-title">{{ t('lessonEditor.video.title') }}</h2>
        <p v-if="!editor.video" class="type-caption">{{ t('lessonEditor.video.none') }}</p>
      </div>
      <div v-if="editor.video" class="lesson-video-file">
        <span class="row-list-copy">
          <strong class="row-list-title">{{ editor.video.title }}</strong>
          <span v-if="editor.video.durationSeconds" class="row-list-meta">
            {{ formatClock(editor.video.durationSeconds) }}
          </span>
        </span>
        <button
          v-if="editor.editable"
          ref="removeVideoButton"
          type="button"
          class="btn btn-ghost btn-sm"
          :disabled="videoBusy"
          :aria-busy="removingVideo || undefined"
          :aria-label="t('lessonEditor.video.removeLabel', { title: editor.video.title })"
          :aria-describedby="videoError ? 'lesson-video-error' : undefined"
          @click="removeVideo"
        >
          {{ t('lessonEditor.video.remove') }}
        </button>
      </div>
      <div v-if="editor.editable" class="field">
        <label class="field-label" for="lesson-video-file">
          {{ editor.video ? t('lessonEditor.video.replace') : t('lessonEditor.video.add') }}
        </label>
        <input
          id="lesson-video-file"
          ref="videoFileInput"
          class="input"
          type="file"
          :accept="uploadAccept('video')"
          :disabled="videoBusy"
          :aria-busy="uploadingVideo"
          :aria-describedby="videoError ? 'lesson-video-error lesson-video-file-hint' : 'lesson-video-file-hint'"
          @change="pickVideo"
        />
        <p id="lesson-video-file-hint" class="field-hint">
          {{ uploadingVideo ? t('lessonEditor.video.uploading') : videoFileHint }}
        </p>
        <p v-if="videoError" id="lesson-video-error" class="field-error" role="alert">{{ videoError }}</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

import { lessonEditorApi } from '@/api/adminClasses'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { LessonEditor } from '@/types/adminClasses'
import type { KeyPhrase } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { focusAfterRowAdd, focusAfterRowRemove } from '@/utils/focusRowList'
import { formatClock, formatFileSize } from '@/utils/format'
import { truncateText } from '@/utils/truncateText'
import { uploadAccept, uploadFileIssue } from '@/utils/uploadFormat'
import { probeVideoDuration } from '@/utils/videoMetadata'

import { useLessonPanelGuard } from './useLessonPanelGuard'

const VIDEO_TITLE_MAX_LENGTH = 200

const title = defineModel<string>('title', { required: true })
const unitId = defineModel<number | null>('unitId', { required: true })
const keyPhrases = defineModel<KeyPhrase[]>('keyPhrases', { required: true })

const props = defineProps<{
  editor: LessonEditor
  unitNames: ReadonlyMap<number, string>
  titleError: string | null
  writePending: boolean
}>()
const emit = defineEmits<{
  blurTitle: []
  updated: [editor: LessonEditor]
  notice: [message: string]
  /** Emitted synchronously when a video upload or removal starts/ends so the editor's tab guard waits for it. */
  saving: [saving: boolean]
}>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages })

const phraseColumns = computed(
  () =>
    [
      { key: 'chinese', label: t('lessonEditor.fields.chinese'), maxlength: 100 },
      { key: 'pinyin', label: t('lessonEditor.fields.pinyin'), maxlength: 200 },
      { key: 'meaning', label: t('lessonEditor.fields.meaning'), maxlength: 300 },
    ] as const,
)

let uploadController: AbortController | null = null
const addPhraseButton = ref<HTMLButtonElement | null>(null)
const phrasesGroup = ref<HTMLDivElement | null>(null)
const removeVideoButton = ref<HTMLButtonElement | null>(null)
const videoFileInput = ref<HTMLInputElement | null>(null)
const videoOperation = ref<'upload' | 'remove' | null>(null)
const videoBusy = computed(() => props.writePending || videoOperation.value !== null)
const uploadingVideo = computed(() => videoOperation.value === 'upload')
const removingVideo = computed(() => videoOperation.value === 'remove')
const videoError = ref<string | null>(null)
const videoLimit = computed(() => props.editor.videoUploadMaxBytes)
const videoFileHint = computed(() =>
  videoLimit.value === undefined
    ? t('lessonEditor.video.formats')
    : t('lessonEditor.video.fileHint', { max: formatFileSize(videoLimit.value) }),
)

function abortUpload(): void {
  uploadController?.abort()
  uploadController = null
}

const guard = useLessonPanelGuard({
  lessonId: () => props.editor.lessonId,
  saving: () => videoOperation.value !== null,
  onSaving: (saving) => emit('saving', saving),
  onCleanup: abortUpload,
  onReset: () => {
    videoOperation.value = null
    videoError.value = null
  },
})

function addPhrase(event: MouseEvent): void {
  const wasFocused = event.currentTarget === document.activeElement
  keyPhrases.value.push({ chinese: '', pinyin: '', meaning: '' })
  focusAfterRowAdd(
    () => phrasesGroup.value,
    () => addPhraseButton.value,
    wasFocused,
  )
}

function removePhrase(index: number, event: MouseEvent): void {
  const moveFocus = event.currentTarget === document.activeElement
  keyPhrases.value.splice(index, 1)
  focusAfterRowRemove(
    () => phrasesGroup.value,
    () => addPhraseButton.value,
    index,
    moveFocus,
  )
}

async function writeVideo(
  operation: 'upload' | 'remove',
  done: string,
  write: (id: number, isCurrent: () => boolean) => Promise<LessonEditor | null>,
): Promise<void> {
  const hadControlFocus =
    document.activeElement === removeVideoButton.value || document.activeElement === videoFileInput.value
  videoOperation.value = operation
  videoError.value = null
  await guard.run(
    async (isCurrent) => {
      const next = await write(props.editor.lessonId, isCurrent)
      if (!next || !isCurrent()) return
      emit('updated', next)
      emit('notice', done)
    },
    {
      onError: (reason) => {
        videoError.value = resolveApiError(
          reason,
          t(operation === 'remove' ? 'lessonEditor.video.removeError' : 'lessonEditor.video.error'),
        )
      },
      onFinally: () => {
        videoOperation.value = null
        if (!hadControlFocus) return
        void nextTick(() => {
          if (document.activeElement && document.activeElement !== document.body) return
          if (operation === 'remove' && props.editor.video) removeVideoButton.value?.focus()
          else videoFileInput.value?.focus()
        })
      },
    },
  )
}

async function pickVideo(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || videoBusy.value || !props.editor.editable) return
  const issue = uploadFileIssue(file, 'video', videoLimit.value)
  if (issue) {
    videoError.value =
      issue === 'fileType'
        ? t('lessonEditor.video.wrongType')
        : issue === 'fileEmpty'
          ? t('lessonEditor.video.emptyFile')
          : t('lessonEditor.video.tooLarge', { max: formatFileSize(videoLimit.value!) })
    return
  }
  await writeVideo('upload', t('lessonEditor.video.saved'), async (id, isCurrent) => {
    const seconds = await probeVideoDuration(file)
    if (!isCurrent()) return null
    const videoTitle = truncateText(file.name.replace(/\.[^.]+$/, '') || file.name, VIDEO_TITLE_MAX_LENGTH)
    abortUpload()
    const controller = new AbortController()
    uploadController = controller
    try {
      return await lessonEditorApi.saveVideo(
        id,
        { title: videoTitle, file, durationSeconds: seconds },
        controller.signal,
      )
    } finally {
      if (uploadController === controller) uploadController = null
    }
  })
}

async function removeVideo(): Promise<void> {
  if (videoBusy.value || !props.editor.editable) return
  await writeVideo('remove', t('lessonEditor.video.removed'), (id) => lessonEditorApi.removeVideo(id))
}
</script>
