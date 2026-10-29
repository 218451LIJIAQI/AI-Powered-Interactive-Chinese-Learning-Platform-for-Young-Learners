<template>
  <div class="lesson-editor-page page-stack">
    <template v-if="editor">
      <PageHeaderBlock
        :title="editor.title"
        :description="t('lessonEditor.subtitle', { classTitle: editor.classTitle, unit: editor.unitTitle })"
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
        <template #actions>
          <button
            v-if="editor.status === 'ARCHIVED'"
            type="button"
            class="btn btn-secondary"
            :disabled="saving"
            @click="restoreLesson"
          >
            <IconGlyph name="refresh" :size="16" />{{ t('lessonEditor.restoreLesson') }}
          </button>
          <button
            v-else-if="editor.status !== 'SUBMITTED' && editor.revisionStatus !== 'SUBMITTED'"
            type="button"
            class="btn btn-ghost"
            @click="dialog = 'archive'"
          >
            <IconGlyph name="archive" :size="16" />{{ t('lessonEditor.archiveLesson') }}
          </button>
        </template>
      </PageHeaderBlock>

      <div class="lesson-editor-state">
        <p class="callout is-compact" :class="{ 'is-warning': editor.reviewNote }">
          <IconGlyph :name="editor.editable ? 'info' : 'lock'" :size="16" />
          <span>
            {{ statusText }}
            <template v-if="editor.reviewNote">
              <strong>{{
                editor.reviewNoteBy
                  ? t('lessonEditor.noteBy', {
                      name: editor.reviewNoteBy,
                      date: formatShortDate(editor.reviewNoteAt),
                      note: editor.reviewNote,
                    })
                  : editor.reviewNote
              }}</strong>
            </template>
          </span>
        </p>
        <div class="lesson-editor-state-links">
          <RouterLink
            class="btn btn-link"
            :to="{ name: 'teacher-class', params: { classId: editor.classId, tab: 'history' } }"
          >
            <IconGlyph name="history" :size="16" />{{ t('lessonEditor.history') }}
          </RouterLink>
          <button
            v-if="editor.revisionStatus === 'DRAFT' || editor.revisionStatus === 'REJECTED'"
            type="button"
            class="btn btn-ghost btn-sm"
            @click="dialog = 'discard'"
          >
            {{ t('lessonEditor.discardRevision') }}
          </button>
        </div>
      </div>
      <DataNotice :error="actionError" :success="actionSuccess" />

      <div class="lesson-editor-workspace">
        <nav v-if="outlineUnits.length" class="lesson-editor-rail" :aria-label="t('lessonEditor.rail.label')">
          <p class="page-eyebrow">{{ t('lessonEditor.rail.title') }}</p>
          <ol class="lesson-editor-rail-units">
            <li v-for="(unit, unitIndex) in outlineUnits" :key="unit.id">
              <span class="lesson-editor-rail-unit">{{ unitIndex + 1 }} · {{ unit.title }}</span>
              <ol class="lesson-editor-rail-lessons">
                <li v-for="lesson in unit.lessons" :key="lesson.id">
                  <RouterLink
                    class="lesson-editor-rail-link"
                    :class="{ 'is-current': lesson.id === lessonId }"
                    :to="{ name: 'teacher-lesson', params: { classId, lessonId: lesson.id } }"
                    :aria-current="lesson.id === lessonId ? 'page' : undefined"
                  >
                    <span>{{ lesson.title }}</span>
                    <span class="lesson-editor-rail-status" :class="`is-${statusView(lesson).tone}`">
                      {{ t(statusView(lesson).key) }}
                    </span>
                  </RouterLink>
                </li>
              </ol>
            </li>
          </ol>
        </nav>

        <div class="lesson-editor-main">
          <RouteTabs :tabs="tabs" :label="t('lessonEditor.tabsLabel')" />

          <!-- Content: the editor with a live preview beside it on wide screens -->
          <div v-if="tab === 'content'" class="lesson-editor-content">
            <div class="panel lesson-editor-sheet">
              <section class="lesson-editor-section" aria-labelledby="lesson-details-title">
                <h2 id="lesson-details-title" class="card-title">{{ t('lessonEditor.details.title') }}</h2>
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
                <div class="lesson-editor-details">
                  <div class="field">
                    <label class="field-label" for="lesson-unit">{{ t('lessonEditor.details.unit') }}</label>
                    <select id="lesson-unit" v-model="content.unitId" class="select" :disabled="!editor.editable">
                      <option v-for="unit in editor.units" :key="unit.id" :value="unit.id">{{ unit.title }}</option>
                    </select>
                  </div>
                  <div class="field">
                    <label class="field-label" for="lesson-minutes">{{ t('lessonEditor.details.minutes') }}</label>
                    <input
                      id="lesson-minutes"
                      v-model.number="content.minutes"
                      class="input"
                      type="number"
                      min="1"
                      max="240"
                      :disabled="!editor.editable"
                    />
                  </div>
                </div>
                <div class="field">
                  <label class="field-label" for="lesson-summary">{{ t('lessonEditor.details.summary') }}</label>
                  <textarea
                    id="lesson-summary"
                    v-model="content.summary"
                    class="textarea"
                    rows="3"
                    maxlength="1000"
                    :disabled="!editor.editable"
                    aria-describedby="lesson-summary-hint"
                  />
                  <div class="field-foot">
                    <p id="lesson-summary-hint" class="field-hint">{{ t('lessonEditor.details.summaryHint') }}</p>
                    <p class="field-hint field-counter">{{ content.summary.length }} / 1000</p>
                  </div>
                </div>
              </section>

              <section class="lesson-editor-section" aria-labelledby="lesson-phrases-title">
                <div class="lesson-editor-section-head">
                  <div>
                    <h2 id="lesson-phrases-title" class="card-title">{{ t('lessonEditor.phrases.title') }}</h2>
                    <p class="type-caption">{{ t('lessonEditor.phrases.hint') }}</p>
                  </div>
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
                <div v-else class="lesson-editor-phrases" role="group" :aria-label="t('lessonEditor.phrases.label')">
                  <div class="lesson-editor-row lesson-editor-row-head" aria-hidden="true">
                    <span />
                    <span>{{ t('lessonEditor.phrases.chinese') }}</span>
                    <span>{{ t('lessonEditor.phrases.pinyin') }}</span>
                    <span>{{ t('lessonEditor.phrases.meaning') }}</span>
                    <span />
                  </div>
                  <div v-for="(phrase, index) in content.keyPhrases" :key="index" class="lesson-editor-row">
                    <span class="lesson-editor-row-number" aria-hidden="true">{{ index + 1 }}</span>
                    <input
                      v-model="phrase.chinese"
                      :placeholder="t('lessonEditor.phrases.chinese')"
                      class="input cjk-text"
                      maxlength="100"
                      :disabled="!editor.editable"
                      :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${t('lessonEditor.phrases.chinese')}`"
                    />
                    <input
                      v-model="phrase.pinyin"
                      :placeholder="t('lessonEditor.phrases.pinyin')"
                      class="input"
                      maxlength="200"
                      :disabled="!editor.editable"
                      :aria-label="`${t('lessonEditor.phrases.rowLabel', { number: index + 1 })}: ${t('lessonEditor.phrases.pinyin')}`"
                    />
                    <input
                      v-model="phrase.meaning"
                      :placeholder="t('lessonEditor.phrases.meaning')"
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

              <section class="lesson-editor-section" aria-labelledby="lesson-video-title">
                <h2 id="lesson-video-title" class="card-title">{{ t('lessonEditor.video.title') }}</h2>
                <div v-if="editor.video" class="lesson-editor-video">
                  <span class="lesson-editor-video-icon" aria-hidden="true"><IconGlyph name="video" :size="20" /></span>
                  <span class="lesson-editor-video-copy">
                    <strong>{{ editor.video.title }}</strong>
                    <span class="type-caption">
                      {{
                        t('lessonEditor.video.watched', {
                          length: formatClock(editor.video.durationSeconds),
                          watched: editor.video.watchedCount,
                          students: editor.video.studentCount,
                        })
                      }}
                    </span>
                  </span>
                  <span v-if="editor.editable" class="lesson-editor-video-actions">
                    <button
                      v-if="!videoFormOpen"
                      type="button"
                      class="btn btn-ghost btn-sm"
                      :aria-expanded="videoFormOpen"
                      @click="videoFormOpen = true"
                    >
                      {{ t('lessonEditor.video.replace') }}
                    </button>
                    <button type="button" class="btn btn-ghost btn-sm" :disabled="saving" @click="removeVideo">
                      {{ t('lessonEditor.video.remove') }}
                    </button>
                  </span>
                </div>
                <p v-else class="type-caption">{{ t('lessonEditor.video.none') }}</p>

                <form
                  v-if="editor.editable && (videoFormOpen || !editor.video)"
                  class="lesson-editor-video-form"
                  @submit.prevent="saveVideo"
                >
                  <SegmentedControl
                    v-model="video.sourceType"
                    :label="editor.video ? t('lessonEditor.video.replace') : t('lessonEditor.video.add')"
                    :options="videoSourceOptions"
                  />
                  <div v-if="video.sourceType === 'UPLOADED_FILE'" class="field">
                    <label class="field-label" for="lesson-video-file">{{ t('lessonEditor.video.file') }}</label>
                    <input
                      id="lesson-video-file"
                      class="input"
                      type="file"
                      accept="video/mp4,video/webm"
                      aria-describedby="lesson-video-file-hint"
                      @change="pickFile"
                    />
                    <p id="lesson-video-file-hint" class="field-hint">{{ t('lessonEditor.video.fileHint') }}</p>
                  </div>
                  <div v-else class="field">
                    <label class="field-label" for="lesson-video-url">{{ t('lessonEditor.video.url') }}</label>
                    <input
                      id="lesson-video-url"
                      v-model="video.url"
                      class="input"
                      type="url"
                      maxlength="500"
                      :placeholder="t('lessonEditor.video.urlPlaceholder')"
                    />
                  </div>
                  <div class="lesson-editor-details">
                    <div class="field">
                      <label class="field-label" for="lesson-video-title">{{
                        t('lessonEditor.video.videoTitle')
                      }}</label>
                      <input
                        id="lesson-video-title"
                        v-model="video.title"
                        class="input"
                        maxlength="200"
                        :placeholder="t('lessonEditor.video.videoTitlePlaceholder')"
                      />
                    </div>
                    <div class="field">
                      <label class="field-label" for="lesson-video-length">{{
                        t('lessonEditor.video.duration')
                      }}</label>
                      <input
                        id="lesson-video-length"
                        v-model.number="video.durationSeconds"
                        class="input"
                        type="number"
                        min="0"
                        max="36000"
                      />
                    </div>
                  </div>
                  <div class="lesson-editor-video-submit">
                    <button v-if="editor.video" type="button" class="btn btn-ghost" @click="videoFormOpen = false">
                      {{ t('classes.cancel') }}
                    </button>
                    <button
                      type="submit"
                      class="btn btn-secondary"
                      :disabled="saving || !videoReady"
                      :aria-busy="saving"
                    >
                      {{ t('lessonEditor.video.save') }}
                    </button>
                  </div>
                </form>
              </section>
            </div>

            <LessonPreviewPanel class="lesson-editor-live" :editor="livePreview!" compact />
          </div>

          <LessonActivitiesPanel
            v-else-if="tab === 'activities'"
            :editor="editor"
            @updated="applyEditor"
            @notice="showSuccess"
            @error="showError"
          />
          <LessonQuizPanel
            v-else-if="tab === 'quiz'"
            :editor="editor"
            @updated="applyEditor"
            @notice="showSuccess"
            @error="showError"
          />
          <LessonPreviewPanel v-else :editor="livePreview!" />
        </div>
      </div>

      <div class="lesson-editor-workbar" role="region" :aria-label="t('lessonEditor.footer.label')">
        <div class="lesson-editor-workbar-state">
          <ClassStatusPill :status="editor.status" :revision-status="editor.revisionStatus" />
          <span class="lesson-editor-save-state" :class="{ 'is-dirty': dirty }">
            <IconGlyph :name="!editor.editable ? 'lock' : dirty ? 'edit' : 'check'" :size="14" />
            {{
              !editor.editable
                ? t('lessonEditor.footer.readOnly')
                : dirty
                  ? t('lessonEditor.footer.unsaved')
                  : t('lessonEditor.footer.saved')
            }}
          </span>
        </div>
        <div v-if="editor.editable" class="lesson-editor-workbar-actions">
          <button
            type="button"
            class="btn btn-secondary"
            :disabled="saving || !dirty || !content.title.trim()"
            @click="saveContent"
          >
            <IconGlyph name="check" :size="16" />{{ t('lessonEditor.footer.saveDraft') }}
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="saving"
            data-testid="open-submit"
            @click="dialog = 'submit'"
          >
            <IconGlyph name="send" :size="16" />{{ t('lessonEditor.footer.submit') }}
          </button>
        </div>
      </div>
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
          <RouterLink class="btn btn-primary" :to="classRoute">{{ t('lessonEditor.backToClass') }}</RouterLink>
        </template>
      </StatePanel>
    </template>

    <ConfirmDialog
      :open="dialog === 'submit'"
      :title="t('lessonEditor.submit.title')"
      :message="
        editor?.status === 'PUBLISHED' ? t('lessonEditor.submit.revisionMessage') : t('lessonEditor.submit.message')
      "
      :confirm-label="t('lessonEditor.submit.confirm')"
      :busy="saving"
      :confirm-disabled="!checklistReady"
      :error="dialogError"
      test-id="submit-dialog"
      @cancel="dialog = null"
      @confirm="submit"
    >
      <h3 class="field-label">{{ t('lessonEditor.submit.checklist') }}</h3>
      <ul class="lesson-editor-checklist">
        <li v-for="item in checklist" :key="item.key" :class="{ 'is-done': item.done }">
          <IconGlyph :name="item.done ? 'check' : 'close'" :size="16" />
          <span>{{ item.label }}</span>
          <span class="sr-only">{{ item.done ? '✓' : '✗' }}</span>
        </li>
      </ul>
      <p v-if="dirty" class="type-caption">{{ t('lessonEditor.submit.saveFirst') }}</p>
    </ConfirmDialog>

    <ConfirmDialog
      :open="dialog === 'archive'"
      :title="t('lessonEditor.archive.title')"
      :message="t('lessonEditor.archive.message', { title: editor?.title ?? '' })"
      :confirm-label="t('lessonEditor.archive.confirm')"
      tone="danger"
      :busy="saving"
      @cancel="dialog = null"
      @confirm="archiveLesson"
    />
    <ConfirmDialog
      :open="dialog === 'discard'"
      :title="t('lessonEditor.archive.discardTitle')"
      :message="t('lessonEditor.archive.discardMessage')"
      :confirm-label="t('lessonEditor.archive.discardConfirm')"
      tone="danger"
      :busy="saving"
      @cancel="dialog = null"
      @confirm="discardRevision"
    />
    <ConfirmDialog
      :open="pendingRoute !== null"
      :title="t('lessonEditor.unsaved.title')"
      :message="t('lessonEditor.unsaved.message')"
      tone="leave"
      :confirm-label="t('lessonEditor.unsaved.leave')"
      :cancel-label="t('lessonEditor.unsaved.stay')"
      @confirm="leaveWithoutSaving"
      @cancel="pendingRoute = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  RouterLink,
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
  useRoute,
  useRouter,
  type RouteLocationRaw,
} from 'vue-router'

import { lessonEditorApi, teacherClassesApi } from '@/api/classes'
import SegmentedControl, { type SegmentedOption } from '@/components/common/SegmentedControl.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ClassStatusPill from '@/components/classes/ClassStatusPill.vue'
import LessonActivitiesPanel from '@/components/classes/LessonActivitiesPanel.vue'
import LessonPreviewPanel from '@/components/classes/LessonPreviewPanel.vue'
import LessonQuizPanel from '@/components/classes/LessonQuizPanel.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { KeyPhrase, LessonEditor, TeacherLessonRow } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { contentStatusView } from '@/utils/classes'
import { formatClock, formatShortDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({
  lessonEditor: lessonEditorMessages,
  teacherClasses: teacherClassesMessages,
  classes: classesMessages,
})
const route = useRoute()
const router = useRouter()
const { actionError, actionSuccess, runAction, showError, showSuccess } = useActionFeedback()

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

/** The class's units and lessons for the rail beside the editor (wide screens). */
const outline = useResource(() => teacherClassesApi.classPage(classId.value))
const outlineUnits = computed(() => outline.data.value?.units.filter((unit) => !unit.archived) ?? [])

function statusView(lesson: TeacherLessonRow) {
  return contentStatusView(lesson.status, lesson.revisionStatus)
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
  minutes: number | null
  summary: string
  keyPhrases: KeyPhrase[]
}

const content = ref<ContentForm>({ title: '', unitId: null, minutes: null, summary: '', keyPhrases: [] })
const savedSnapshot = ref('')

function contentOf(current: LessonEditor): ContentForm {
  return {
    title: current.title,
    unitId: current.unitId,
    minutes: current.minutes,
    summary: current.summary ?? '',
    keyPhrases: current.keyPhrases.map((phrase) => ({ ...phrase })),
  }
}

function snapshot(form: ContentForm): string {
  return JSON.stringify({ ...form, minutes: form.minutes || null })
}

const dirty = computed(() => Boolean(editor.value?.editable) && snapshot(content.value) !== savedSnapshot.value)

/** The lesson as students would see it with the edits typed so far (the live preview; set whenever `editor` is). */
const livePreview = computed<LessonEditor | null>(() =>
  editor.value
    ? {
        ...editor.value,
        title: content.value.title.trim() || editor.value.title,
        minutes: content.value.minutes || null,
        summary: content.value.summary.trim() || null,
        keyPhrases: content.value.keyPhrases.filter((phrase) => phrase.chinese.trim() || phrase.meaning.trim()),
      }
    : null,
)

/** Fills the content form from the lesson as saved. */
function fillContent(lesson: LessonEditor): void {
  content.value = contentOf(lesson)
  savedSnapshot.value = snapshot(content.value)
}

/** A fresh editor from the server; the content form is refilled unless `keepEdits` (default: it has unsaved edits). */
function applyEditor(next: LessonEditor, keepEdits = dirty.value): void {
  resource.data.value = next
  if (keepEdits) savedSnapshot.value = snapshot(contentOf(next))
  else fillContent(next)
}

watch(
  editor,
  (current, previous) => {
    // A new lesson (first load, or another lesson opened from the rail) fills the form.
    if (current && current.lessonId !== previous?.lessonId) fillContent(current)
  },
  { immediate: true },
)

const saving = ref(false)

function contentPayload() {
  return {
    title: content.value.title.trim(),
    unitId: content.value.unitId,
    minutes: content.value.minutes || null,
    summary: content.value.summary.trim() || null,
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
  void outline.reload()
}

async function saveContent(): Promise<void> {
  saving.value = true
  await runAction(t('lessonEditor.saveError'), async () => {
    await persistContent()
    return t('lessonEditor.saved')
  })
  saving.value = false
}

// ----- video -----

const videoSourceOptions = computed<SegmentedOption<'UPLOADED_FILE' | 'EXTERNAL_URL'>[]>(() => [
  { value: 'UPLOADED_FILE', label: t('lessonEditor.video.upload'), icon: 'upload' },
  { value: 'EXTERNAL_URL', label: t('lessonEditor.video.link'), icon: 'link' },
])
const video = ref<{
  sourceType: 'UPLOADED_FILE' | 'EXTERNAL_URL'
  url: string
  title: string
  durationSeconds: number | null
  file: File | null
}>({
  sourceType: 'UPLOADED_FILE',
  url: '',
  title: '',
  durationSeconds: null,
  file: null,
})
const videoFormOpen = ref(false)
const videoReady = computed(
  () =>
    Boolean(video.value.title.trim()) &&
    (video.value.sourceType === 'UPLOADED_FILE' ? Boolean(video.value.file) : Boolean(video.value.url.trim())),
)

function pickFile(event: Event): void {
  video.value.file = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function saveVideo(): Promise<void> {
  const form = new FormData()
  form.append('title', video.value.title.trim())
  form.append('sourceType', video.value.sourceType)
  if (video.value.durationSeconds) form.append('durationSeconds', String(video.value.durationSeconds))
  if (video.value.sourceType === 'UPLOADED_FILE' && video.value.file) form.append('file', video.value.file)
  if (video.value.sourceType === 'EXTERNAL_URL') form.append('externalVideoUrl', video.value.url.trim())
  saving.value = true
  const done = await runAction(t('lessonEditor.video.error'), async () => {
    applyEditor(await lessonEditorApi.saveVideo(lessonId.value, form))
    return t('lessonEditor.video.saved')
  })
  saving.value = false
  if (done) {
    video.value = { sourceType: video.value.sourceType, url: '', title: '', durationSeconds: null, file: null }
    videoFormOpen.value = false
  }
}

async function removeVideo(): Promise<void> {
  saving.value = true
  await runAction(t('lessonEditor.video.error'), async () => {
    applyEditor(await lessonEditorApi.removeVideo(lessonId.value))
    return t('lessonEditor.video.removed')
  })
  saving.value = false
}

// ----- submit, archive, discard -----

const dialog = ref<'submit' | 'archive' | 'discard' | null>(null)
const dialogError = ref<string | null>(null)

const checklist = computed(() => {
  const current = editor.value?.checklist
  const pinyinDone = content.value.keyPhrases.every((phrase) => !phrase.chinese.trim() || Boolean(phrase.pinyin.trim()))
  return [
    { key: 'activities', label: t('lessonEditor.submit.activities'), done: (current?.activityCount ?? 0) > 0 },
    {
      key: 'quiz',
      label: t('lessonEditor.submit.quiz'),
      done: (current?.quizCount ?? 0) > 0 && Boolean(current?.quizzesReady),
    },
    {
      key: 'pinyin',
      label: t('lessonEditor.submit.pinyin'),
      done: dirty.value ? pinyinDone : Boolean(current?.pinyinComplete),
    },
  ]
})
const checklistReady = computed(() => checklist.value.every((item) => item.done))

watch(dialog, () => {
  dialogError.value = null
})

async function submit(): Promise<void> {
  saving.value = true
  dialogError.value = null
  try {
    if (dirty.value) await persistContent()
    applyEditor(await lessonEditorApi.submit(lessonId.value))
    void outline.reload()
    dialog.value = null
    showSuccess(t('lessonEditor.submit.done'))
  } catch (reason) {
    dialogError.value = resolveApiError(reason, t('lessonEditor.submit.error'))
  } finally {
    saving.value = false
  }
}

async function lessonAction(action: () => Promise<LessonEditor>, done: string, fallback: string): Promise<void> {
  saving.value = true
  await runAction(fallback, async () => {
    applyEditor(await action(), false)
    void outline.reload()
    return done
  })
  saving.value = false
  dialog.value = null
}

function archiveLesson(): Promise<void> {
  return lessonAction(
    () => lessonEditorApi.archive(lessonId.value),
    t('lessonEditor.archive.done'),
    t('lessonEditor.archive.error'),
  )
}

function restoreLesson(): Promise<void> {
  return lessonAction(
    () => lessonEditorApi.restore(lessonId.value),
    t('lessonEditor.archive.restored'),
    t('lessonEditor.archive.error'),
  )
}

function discardRevision(): Promise<void> {
  return lessonAction(
    () => lessonEditorApi.discardRevision(lessonId.value),
    t('lessonEditor.archive.discarded'),
    t('lessonEditor.saveError'),
  )
}

// ----- leaving with unsaved content -----

const pendingRoute = ref<RouteLocationRaw | null>(null)
let leaving = false

// Switching tabs keeps the same page (and the content form), so only leaving the lesson asks.
onBeforeRouteLeave((to) => {
  if (leaving || !dirty.value) return true
  pendingRoute.value = to.fullPath
  return false
})

// Opening another lesson from the rail updates this page; switching tabs of the same lesson keeps the form.
onBeforeRouteUpdate((to, from) => {
  if (leaving || !dirty.value || to.params.lessonId === from.params.lessonId) return true
  pendingRoute.value = to.fullPath
  return false
})

function leaveWithoutSaving(): void {
  const target = pendingRoute.value
  pendingRoute.value = null
  if (!target) return
  leaving = true
  void router.push(target).finally(() => {
    leaving = false
  })
}

function warnOnUnload(event: BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
}

onMounted(() => window.addEventListener('beforeunload', warnOnUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnOnUnload))
</script>
