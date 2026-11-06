<template>
  <div class="page-stack lesson-editor-page">
    <AppToast :message="toast" />

    <template v-if="editor">
      <TeacherPageHeader
        :title="editor.title"
        :description="t('lessonEditor.subtitle', { classTitle: editor.classTitle, unit: unitLabel(editor.unitId) })"
      >
        <template #before>
          <AppBreadcrumb
            :items="[
              { label: t('teacherClasses.list.title'), to: { name: 'teacher-classes' } },
              { label: editor.classTitle, to: classRoute },
              { label: editor.title },
            ]"
          />
        </template>
      </TeacherPageHeader>

      <section class="panel lesson-review" data-testid="lesson-review" :aria-label="t('lessonEditor.reviewLabel')">
        <div class="lesson-review-line">
          <TeacherStatusPill :status="editor.status" :revision-status="editor.revisionStatus" />
          <span class="type-caption">{{ statusText }}</span>
        </div>
        <p v-if="editor.reviewNote" class="callout lesson-review-note" role="note">
          <IconGlyph name="alert" :size="18" />
          <strong>{{
            editor.reviewNoteBy
              ? t('lessonEditor.noteBy', {
                  name: editor.reviewNoteBy,
                  date: formatShortDate(editor.reviewNoteAt),
                  note: editor.reviewNote,
                })
              : editor.reviewNote
          }}</strong>
        </p>
      </section>
      <DataNotice :error="actionError" />

      <RouteTabs :tabs="tabs" :label="t('lessonEditor.tabsLabel')" />

      <div v-if="tab === 'content'" class="lesson-with-rail">
        <div class="page-stack">
          <section class="panel teacher-card" aria-labelledby="lesson-details-title">
            <h2 id="lesson-details-title" class="card-title">{{ t('lessonEditor.details.title') }}</h2>
            <div class="form-grid two">
              <div class="field">
                <label class="field-label" for="lesson-title">
                  {{ t('lessonEditor.details.lessonTitle') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="lesson-title"
                  v-model="content.title"
                  class="input"
                  maxlength="200"
                  required
                  :disabled="!editor.editable"
                />
              </div>
              <div class="field">
                <label class="field-label" for="lesson-unit">
                  {{ t('lessonEditor.details.unit') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <select id="lesson-unit" v-model="content.unitId" class="select" :disabled="!editor.editable">
                  <option v-for="unit in editor.units" :key="unit.id" :value="unit.id">
                    {{ unitLabel(unit.id) }}
                  </option>
                </select>
              </div>
            </div>
          </section>

          <section class="panel teacher-card" aria-labelledby="lesson-phrases-title">
            <div class="section-header">
              <h2 id="lesson-phrases-title" class="card-title">{{ t('lessonEditor.phrases.title') }}</h2>
              <button
                v-if="editor.editable && content.keyPhrases.length < 30"
                type="button"
                class="btn btn-secondary btn-sm"
                data-testid="add-phrase"
                @click="content.keyPhrases.push({ chinese: '', pinyin: '', meaning: '' })"
              >
                <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.phrases.add') }}
              </button>
            </div>
            <p v-if="!content.keyPhrases.length" class="type-caption">{{ t('lessonEditor.phrases.empty') }}</p>
            <div v-else class="lesson-phrases" role="group" :aria-label="t('lessonEditor.phrases.label')">
              <div class="lesson-phrase-row is-head" aria-hidden="true">
                <span />
                <span>{{ t('lessonEditor.phrases.chinese') }}</span>
                <span>{{ t('lessonEditor.phrases.pinyin') }}</span>
                <span>{{ t('lessonEditor.phrases.meaning') }}</span>
                <span />
              </div>
              <div v-for="(phrase, index) in content.keyPhrases" :key="index" class="lesson-phrase-row">
                <span class="teacher-tile" aria-hidden="true">{{ index + 1 }}</span>
                <input
                  v-model="phrase.chinese"
                  class="input cjk-text"
                  maxlength="100"
                  :disabled="!editor.editable"
                  :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${t('lessonEditor.phrases.chinese')}`"
                />
                <input
                  v-model="phrase.pinyin"
                  class="input"
                  maxlength="200"
                  :disabled="!editor.editable"
                  :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${t('lessonEditor.phrases.pinyin')}`"
                />
                <input
                  v-model="phrase.meaning"
                  class="input"
                  maxlength="300"
                  :disabled="!editor.editable"
                  :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${t('lessonEditor.phrases.meaning')}`"
                />
                <button
                  v-if="editor.editable"
                  type="button"
                  class="btn btn-ghost btn-icon btn-sm"
                  :aria-label="t('lessonEditor.phrases.remove', { number: index + 1 })"
                  @click="content.keyPhrases.splice(index, 1)"
                >
                  <IconGlyph name="trash" :size="16" />
                </button>
              </div>
            </div>
          </section>
        </div>

        <section class="panel teacher-card" aria-labelledby="lesson-video-title">
          <div class="teacher-card-head">
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
              :disabled="uploadingVideo"
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
              accept="video/mp4,video/webm"
              :disabled="uploadingVideo"
              :aria-busy="uploadingVideo"
              aria-describedby="lesson-video-file-hint"
              @change="pickVideo"
            />
            <p id="lesson-video-file-hint" class="field-hint">
              {{ uploadingVideo ? t('lessonEditor.video.uploading') : t('lessonEditor.video.fileHint') }}
            </p>
            <p v-if="videoError" class="field-error" role="alert">{{ videoError }}</p>
          </div>
        </section>
      </div>

      <LessonActivitiesPanel
        v-else-if="tab === 'activities'"
        :editor="editor"
        @updated="applyEditor"
        @notice="showToast"
      />
      <LessonQuizPanel
        v-else-if="tab === 'quiz'"
        :editor="editor"
        @updated="applyEditor"
        @notice="showToast"
        @error="showError"
      />
      <LessonPreviewPanel v-else :editor="livePreview!" />

      <section class="panel lesson-footer" :aria-label="t('lessonEditor.footer.label')">
        <template v-if="editor.editable">
          <div class="lesson-footer-note">
            <button
              v-if="editor.revisionStatus === 'DRAFT' || editor.revisionStatus === 'REJECTED'"
              type="button"
              class="btn btn-ghost"
              :disabled="saving"
              @click="dialog = 'discard'"
            >
              {{ t('lessonEditor.discardRevision') }}
            </button>
            <span v-if="submitBlocker" id="lesson-submit-hint" class="lesson-footer-hint">
              <IconGlyph name="edit" :size="16" />{{ submitBlocker }}
            </span>
          </div>
          <div class="btn-group">
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="saving || !dirty || !content.title.trim()"
              :aria-busy="saving"
              data-testid="save-draft"
              @click="saveContent"
            >
              {{ t('lessonEditor.footer.saveDraft') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="saving || Boolean(submitBlocker)"
              :aria-describedby="submitBlocker ? 'lesson-submit-hint' : undefined"
              data-testid="open-submit"
              @click="dialog = 'submit'"
            >
              <IconGlyph name="send" :size="16" />{{ t('lessonEditor.footer.submit') }}
            </button>
          </div>
        </template>
        <template v-else>
          <div class="lesson-footer-note">
            <span class="lesson-footer-hint"
              ><IconGlyph name="lock" :size="16" />{{ t('lessonEditor.footer.readOnly') }}</span
            >
          </div>
          <RouterLink class="btn btn-secondary" :to="classRoute">
            {{ t('lessonEditor.footer.openClass') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </template>
      </section>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('teacherClasses.list.title') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('lessonEditor.tabs.content')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('lessonEditor.loadErrorTitle')"
        :message="resource.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="classRoute">{{ t('lessonEditor.openClass') }}</RouterLink>
        </template>
      </StatePanel>
    </template>

    <AppOverlay
      :open="dialog === 'submit'"
      :title="t('lessonEditor.submit.title', { title: editor?.title ?? '' })"
      :subtitle="
        editor ? t('lessonEditor.subtitle', { classTitle: editor.classTitle, unit: unitLabel(editor.unitId) }) : ''
      "
      :busy="saving"
      test-id="submit-dialog"
      @close="dialog = null"
    >
      <ul class="row-list lesson-checklist">
        <li v-for="item in checklist" :key="item.key">
          <span class="teacher-tile" :class="{ 'is-filled': item.done }" aria-hidden="true">
            <IconGlyph :name="item.done ? 'check' : 'close'" :size="16" />
          </span>
          <span class="row-list-copy">
            <strong class="row-list-title">{{ item.label }}</strong>
            <span class="row-list-meta">{{ item.detail }}</span>
          </span>
        </li>
      </ul>
      <p class="callout">
        <IconGlyph name="info" :size="18" />
        <span>
          {{
            !checklistReady
              ? t('lessonEditor.submit.notReady')
              : editor?.status === 'PUBLISHED'
                ? t('lessonEditor.submit.revisionMessage')
                : t('lessonEditor.submit.message')
          }}
          <template v-if="dirty"> {{ t('lessonEditor.submit.saveFirst') }}</template>
        </span>
      </p>
      <p v-if="dialogError" class="field-error" role="alert">{{ dialogError }}</p>
      <template #footer>
        <button type="button" class="btn btn-secondary" :disabled="saving" @click="dialog = null">
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="saving || !checklistReady"
          :aria-busy="saving"
          data-testid="submit-dialog-confirm"
          @click="submit"
        >
          <IconGlyph name="send" :size="16" />{{ t('lessonEditor.submit.confirm') }}
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="dialog === 'discard'"
      :title="t('lessonEditor.archive.discardTitle')"
      :message="t('lessonEditor.archive.discardMessage')"
      :confirm-label="t('lessonEditor.archive.discardConfirm')"
      :cancel-label="t('lessonEditor.archive.keep')"
      tone="danger"
      :busy="saving"
      @cancel="dialog = null"
      @confirm="discardRevision"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'

import { lessonEditorApi } from '@/api/classes'
import LessonActivitiesPanel from '@/components/classes/LessonActivitiesPanel.vue'
import LessonPreviewPanel from '@/components/classes/LessonPreviewPanel.vue'
import LessonQuizPanel from '@/components/classes/LessonQuizPanel.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import TeacherPageHeader from '@/components/teacher/TeacherPageHeader.vue'
import TeacherStatusPill from '@/components/teacher/TeacherStatusPill.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { classesMessages } from '@/plugins/classesMessages'
import { commonMessages } from '@/plugins/commonMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { KeyPhrase, LessonEditor } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, formatShortDate } from '@/utils/format'
import { hasAllActivities, lessonQuiz, unitNumber } from '@/utils/teacherLesson'
import '@/styles/classes.css'
import '@/styles/classes-teacher.css'

const { t } = useLocalMessages({
  lessonEditor: lessonEditorMessages,
  teacherClasses: teacherClassesMessages,
  classes: classesMessages,
  common: commonMessages,
})
const route = useRoute()
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()

const classId = computed(() => Number(route.params.classId))
const lessonId = computed(() => Number(route.params.lessonId))
const tab = computed(() => {
  const value = route.params.tab
  return value === 'activities' || value === 'quiz' || value === 'preview' ? value : 'content'
})
const classRoute = computed<RouteLocationRaw>(() => ({ name: 'teacher-class', params: { classId: classId.value } }))

const resource = useResource(() => lessonEditorApi.get(lessonId.value))
const editor = computed(() => resource.data.value)
watch(lessonId, () => void resource.reload())

/** "Unit 1: First Conversations": the class's units in order, numbered. */
function unitLabel(unitId: number | null): string {
  const current = editor.value
  if (!current) return ''
  const title = current.units.find((unit) => unit.id === unitId)?.title ?? current.unitTitle
  return t('lessonEditor.unitLabel', { number: unitNumber(current, unitId), title })
}

const tabs = computed<RouteTab[]>(() => {
  const params = { classId: classId.value, lessonId: lessonId.value }
  return [
    { label: t('lessonEditor.tabs.content'), to: { name: 'teacher-lesson', params }, icon: 'file' },
    {
      label: t('lessonEditor.tabs.activities'),
      to: { name: 'teacher-lesson', params: { ...params, tab: 'activities' } },
      icon: 'target',
      count: editor.value?.activities.length ?? null,
    },
    {
      label: t('lessonEditor.tabs.quiz'),
      to: { name: 'teacher-lesson', params: { ...params, tab: 'quiz' } },
      icon: 'quiz',
      count: editor.value?.checklist.questionCount ?? null,
    },
    {
      label: t('lessonEditor.tabs.preview'),
      to: { name: 'teacher-lesson', params: { ...params, tab: 'preview' } },
      icon: 'eye',
    },
  ]
})

const statusText = computed(() => {
  const current = editor.value
  if (!current) return ''
  if (current.classStatus === 'SUBMITTED' && current.status === 'SUBMITTED')
    return t('lessonEditor.status.classInReview')
  if (current.status === 'PUBLISHED' && current.revisionStatus === 'DRAFT')
    return t('lessonEditor.status.revisionDraft')
  if (current.status === 'PUBLISHED' && current.revisionStatus === 'SUBMITTED')
    return t('lessonEditor.status.revisionSubmitted')
  if (current.status === 'PUBLISHED' && current.revisionStatus === 'REJECTED')
    return t('lessonEditor.status.revisionRejected')
  const keys = {
    DRAFT: 'lessonEditor.status.DRAFT',
    SUBMITTED: 'lessonEditor.status.SUBMITTED',
    REJECTED: 'lessonEditor.status.REJECTED',
    PUBLISHED: 'lessonEditor.status.PUBLISHED',
    ARCHIVED: 'lessonEditor.status.ARCHIVED',
  } as const
  return t(keys[current.status])
})

// ----- content (saved with Save draft) -----

interface ContentForm {
  title: string
  unitId: number | null
  keyPhrases: KeyPhrase[]
}

const content = ref<ContentForm>({ title: '', unitId: null, keyPhrases: [] })
const savedSnapshot = ref('')

function contentOf(current: LessonEditor): ContentForm {
  return {
    title: current.title,
    unitId: current.unitId,
    keyPhrases: current.keyPhrases.map((phrase) => ({ ...phrase })),
  }
}

const dirty = computed(() => Boolean(editor.value?.editable) && JSON.stringify(content.value) !== savedSnapshot.value)

/** The lesson as students would see it with the edits typed so far (the Preview tab; set whenever `editor` is). */
const livePreview = computed<LessonEditor | null>(() =>
  editor.value
    ? {
        ...editor.value,
        title: content.value.title.trim() || editor.value.title,
        unitId: content.value.unitId ?? editor.value.unitId,
        unitTitle: editor.value.units.find((unit) => unit.id === content.value.unitId)?.title ?? editor.value.unitTitle,
        keyPhrases: content.value.keyPhrases.filter((phrase) => phrase.chinese.trim() || phrase.meaning.trim()),
      }
    : null,
)

/** Fills the content form from the lesson as saved. */
function fillContent(lesson: LessonEditor): void {
  content.value = contentOf(lesson)
  savedSnapshot.value = JSON.stringify(content.value)
}

/** A fresh editor from the server; the content form is refilled unless `keepEdits` (default: it has unsaved edits). */
function applyEditor(next: LessonEditor, keepEdits = dirty.value): void {
  resource.data.value = next
  if (keepEdits) savedSnapshot.value = JSON.stringify(contentOf(next))
  else fillContent(next)
}

watch(
  editor,
  (current, previous) => {
    if (!current || current.lessonId === previous?.lessonId) return
    fillContent(current)
    // A lesson just added from its class says so once.
    if (route.query.created) showToast(t('lessonEditor.created'))
  },
  { immediate: true },
)

const saving = ref(false)

/** Minutes and summary are no longer edited here; the lesson keeps what it has. */
function contentPayload() {
  return {
    title: content.value.title.trim(),
    unitId: content.value.unitId,
    minutes: editor.value?.minutes ?? null,
    summary: editor.value?.summary ?? null,
    keyPhrases: content.value.keyPhrases
      .map((phrase) => ({
        chinese: phrase.chinese.trim(),
        pinyin: phrase.pinyin.trim(),
        meaning: phrase.meaning.trim(),
      }))
      .filter((phrase) => phrase.chinese || phrase.pinyin || phrase.meaning),
  }
}

async function persistContent(): Promise<void> {
  applyEditor(await lessonEditorApi.saveContent(lessonId.value, contentPayload()), false)
}

/** Runs one write; a success shows as a toast, a failure as the notice above the tabs. */
async function run(fallback: string, action: () => Promise<string>): Promise<void> {
  saving.value = true
  clearFeedback()
  try {
    showToast(await action())
  } catch (reason) {
    showError(resolveApiError(reason, fallback))
  } finally {
    saving.value = false
  }
}

function saveContent(): Promise<void> {
  return run(t('lessonEditor.saveError'), async () => {
    await persistContent()
    return t('lessonEditor.saved')
  })
}

// ----- video: choosing a file uploads it -----

const VIDEO_TYPES = new Set(['video/mp4', 'video/webm'])
/** The server's limit for one video file. */
const MAX_VIDEO_BYTES = 100 * 1024 * 1024

const uploadingVideo = ref(false)
const videoError = ref<string | null>(null)

/** The video's length from the browser's own reading of the file; null when it cannot tell. */
function videoLength(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const probe = document.createElement('video')
    const finish = (seconds: number | null) => {
      URL.revokeObjectURL(url)
      resolve(seconds)
    }
    probe.preload = 'metadata'
    probe.onloadedmetadata = () => finish(Number.isFinite(probe.duration) ? Math.round(probe.duration) : null)
    probe.onerror = () => finish(null)
    probe.src = url
  })
}

async function pickVideo(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  videoError.value = null
  if (!VIDEO_TYPES.has(file.type)) {
    videoError.value = t('lessonEditor.video.wrongType')
    return
  }
  if (file.size > MAX_VIDEO_BYTES) {
    videoError.value = t('lessonEditor.video.tooLarge')
    return
  }
  uploadingVideo.value = true
  try {
    const form = new FormData()
    form.append('title', file.name.replace(/\.[^.]+$/, '') || file.name)
    form.append('sourceType', 'UPLOADED_FILE')
    form.append('file', file)
    const seconds = await videoLength(file)
    if (seconds !== null) form.append('durationSeconds', String(seconds))
    applyEditor(await lessonEditorApi.saveVideo(lessonId.value, form))
    showToast(t('lessonEditor.video.saved'))
  } catch (reason) {
    videoError.value = resolveApiError(reason, t('lessonEditor.video.error'))
  } finally {
    uploadingVideo.value = false
  }
}

async function removeVideo(): Promise<void> {
  uploadingVideo.value = true
  videoError.value = null
  try {
    applyEditor(await lessonEditorApi.removeVideo(lessonId.value))
    showToast(t('lessonEditor.video.removed'))
  } catch (reason) {
    videoError.value = resolveApiError(reason, t('lessonEditor.video.error'))
  } finally {
    uploadingVideo.value = false
  }
}

// ----- submit, discard -----

const dialog = ref<'submit' | 'discard' | null>(null)
const dialogError = ref<string | null>(null)

const activitiesReady = computed(() => Boolean(editor.value) && hasAllActivities(editor.value!))
const quizReady = computed(() => {
  const current = editor.value?.checklist
  return (current?.quizCount ?? 0) > 0 && Boolean(current?.quizzesReady)
})
const pinyinReady = computed(() =>
  dirty.value
    ? content.value.keyPhrases.every((phrase) => !phrase.chinese.trim() || Boolean(phrase.pinyin.trim()))
    : Boolean(editor.value?.checklist.pinyinComplete),
)

const checklist = computed(() => {
  const activityCount = editor.value?.activities.length ?? 0
  const questionCount = editor.value ? (lessonQuiz(editor.value)?.questions.length ?? 0) : 0
  return [
    {
      key: 'activities',
      label: t('lessonEditor.submit.activities'),
      detail: t('lessonEditor.submit.activitiesDetail', activityCount),
      done: activitiesReady.value,
    },
    {
      key: 'quiz',
      label: t('lessonEditor.submit.quiz'),
      detail: t('lessonEditor.submit.quizDetail', questionCount),
      done: quizReady.value,
    },
    {
      key: 'pinyin',
      label: t('lessonEditor.submit.pinyin'),
      detail: pinyinReady.value ? t('lessonEditor.submit.pinyinDetail') : t('lessonEditor.submit.pinyinMissing'),
      done: pinyinReady.value,
    },
  ]
})
const checklistReady = computed(() => checklist.value.every((item) => item.done))

/** Why Submit is not available yet, in one line beside it. */
const submitBlocker = computed(() => {
  if (!activitiesReady.value && !quizReady.value) return t('lessonEditor.footer.needsAll')
  if (!activitiesReady.value) return t('lessonEditor.footer.needsActivities')
  if (!quizReady.value) return t('lessonEditor.footer.needsQuiz')
  if (!pinyinReady.value) return t('lessonEditor.footer.needsPinyin')
  // Published with nothing changed: the server refuses an update without changes.
  const current = editor.value
  return current?.status === 'PUBLISHED' && !current.revisionStatus && !dirty.value
    ? t('lessonEditor.footer.needsChange')
    : null
})

watch(dialog, () => {
  dialogError.value = null
})

async function submit(): Promise<void> {
  saving.value = true
  dialogError.value = null
  try {
    if (dirty.value) await persistContent()
    applyEditor(await lessonEditorApi.submit(lessonId.value))
    dialog.value = null
    showToast(t('lessonEditor.submit.done', { title: editor.value?.title ?? '' }))
  } catch (reason) {
    dialogError.value = resolveApiError(reason, t('lessonEditor.submit.error'))
  } finally {
    saving.value = false
  }
}

async function discardRevision(): Promise<void> {
  await run(t('lessonEditor.saveError'), async () => {
    applyEditor(await lessonEditorApi.discardRevision(lessonId.value), false)
    return t('lessonEditor.archive.discarded')
  })
  dialog.value = null
}

// Closing the tab with unsaved content: the browser's own warning.
function warnOnUnload(event: BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
}

onMounted(() => window.addEventListener('beforeunload', warnOnUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnOnUnload))
</script>
