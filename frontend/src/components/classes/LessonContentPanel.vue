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
            <select id="lesson-unit" v-model="unitId" class="select" :disabled="!editor.editable">
              <option v-for="unit in editor.units" :key="unit.id" :value="unit.id">
                {{ unitNames.get(unit.id) }}
              </option>
            </select>
          </div>
        </div>
      </section>

      <section class="panel authoring-card" aria-labelledby="lesson-phrases-title">
        <div class="section-header">
          <h2 id="lesson-phrases-title" class="card-title">{{ t('lessonEditor.phrases.title') }}</h2>
          <button
            v-if="editor.editable && keyPhrases.length < 30"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="emit('addPhrase')"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.phrases.add') }}
          </button>
        </div>
        <p v-if="!keyPhrases.length" class="type-caption">{{ t('lessonEditor.phrases.empty') }}</p>
        <div v-else class="lesson-phrases" role="group" :aria-label="t('lessonEditor.phrases.label')">
          <div class="lesson-phrase-row is-head" aria-hidden="true">
            <span />
            <span v-for="column in phraseColumns" :key="column.key">{{ column.label }}</span>
            <span />
          </div>
          <div v-for="(phrase, index) in keyPhrases" :key="index" class="lesson-phrase-row">
            <span class="authoring-tile" aria-hidden="true">{{ index + 1 }}</span>
            <label v-for="column in phraseColumns" :key="column.key" class="lesson-phrase-field">
              <span class="lesson-phrase-label">{{ column.label }}</span>
              <input
                :value="phrase[column.key]"
                class="input"
                :class="{ 'cjk-text': column.key === 'chinese' }"
                :maxlength="column.maxlength"
                :readonly="!editor.editable"
                :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${column.label}`"
                @input="emit('updatePhrase', index, column.key, ($event.target as HTMLInputElement).value)"
              />
            </label>
            <button
              v-if="editor.editable"
              type="button"
              class="btn btn-ghost btn-icon btn-sm"
              :aria-label="t('lessonEditor.phrases.remove', { number: index + 1 })"
              @click="emit('removePhrase', index)"
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
          type="button"
          class="btn btn-ghost btn-sm"
          :disabled="videoBusy"
          :aria-busy="removingVideo || undefined"
          :aria-label="t('lessonEditor.video.removeLabel', { title: editor.video.title })"
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { lessonEditorApi } from '@/api/adminClasses'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { LessonEditor } from '@/types/adminClasses'
import type { KeyPhrase } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, formatFileSize } from '@/utils/format'
import { truncateText } from '@/utils/truncateText'
import { uploadAccept, uploadFileIssue } from '@/utils/uploadFormat'
import { probeVideoDuration } from '@/utils/videoMetadata'

const VIDEO_TITLE_MAX_LENGTH = 200

const title = defineModel<string>('title', { required: true })
const unitId = defineModel<number | null>('unitId', { required: true })

const props = defineProps<{
  editor: LessonEditor
  keyPhrases: readonly KeyPhrase[]
  unitNames: ReadonlyMap<number, string>
  titleError: string | null
  writePending: boolean
}>()
const emit = defineEmits<{
  blurTitle: []
  addPhrase: []
  removePhrase: [index: number]
  updatePhrase: [index: number, field: keyof KeyPhrase, value: string]
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

let panelVersion = 0
let uploadController: AbortController | null = null
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

watch(videoOperation, (operation) => emit('saving', operation !== null), { flush: 'sync' })
watch(videoLimit, () => {
  videoError.value = null
})
watch(
  () => props.editor.lessonId,
  () => {
    panelVersion++
    abortUpload()
    videoOperation.value = null
    videoError.value = null
  },
  { flush: 'sync' },
)
onBeforeUnmount(() => {
  panelVersion++
  abortUpload()
  if (videoOperation.value !== null) emit('saving', false)
})

async function writeVideo(
  operation: 'upload' | 'remove',
  done: string,
  write: (id: number, version: number) => Promise<LessonEditor | null>,
): Promise<void> {
  const version = panelVersion
  videoOperation.value = operation
  videoError.value = null
  try {
    const next = await write(props.editor.lessonId, version)
    if (!next || version !== panelVersion) return
    emit('updated', next)
    emit('notice', done)
  } catch (reason) {
    if (version === panelVersion) videoError.value = resolveApiError(reason, t('lessonEditor.video.error'))
  } finally {
    if (version === panelVersion) videoOperation.value = null
  }
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
  await writeVideo('upload', t('lessonEditor.video.saved'), async (id, version) => {
    const seconds = await probeVideoDuration(file)
    if (version !== panelVersion) return null
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
