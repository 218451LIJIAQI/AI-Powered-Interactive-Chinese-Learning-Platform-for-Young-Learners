<template>
  <div class="page-stack">
    <AppToast :message="toast" :action="toastAction" />

    <template v-if="editor">
      <PageHeaderBlock :title="editor.title" :description="headerDescription">
        <template #before>
          <AppBreadcrumb
            :items="[
              { label: t('nav.classes'), to: { name: 'admin-classes' } },
              { label: editor.classTitle, to: classRoute },
              { label: editor.title },
            ]"
          />
        </template>
        <template v-if="editor.classStatus !== 'ARCHIVED'" #actions>
          <button
            v-if="editor.status === 'ARCHIVED'"
            ref="restoreLessonButton"
            type="button"
            class="btn btn-secondary"
            :disabled="writePending"
            :aria-busy="pendingAction === 'restoreLesson'"
            data-testid="restore-lesson"
            @click="restoreLesson"
          >
            <IconGlyph name="archive" :size="16" />{{ t('adminClasses.page.restore') }}
          </button>
          <button
            v-else
            ref="archiveLessonButton"
            type="button"
            class="btn btn-ghost"
            :disabled="writePending"
            data-testid="archive-lesson"
            @click="dialog = 'archive'"
          >
            <IconGlyph name="archive" :size="16" />{{ t('adminClasses.page.archive') }}
          </button>
        </template>
      </PageHeaderBlock>

      <section
        v-if="statusText"
        ref="statusRef"
        class="panel lesson-status"
        tabindex="-1"
        data-testid="lesson-status"
        :aria-label="t('lessonEditor.status.label')"
      >
        <span class="lesson-status-line">
          <StatusPill :label="t(`adminClasses.statuses.${editor.status}`)" :tone="contentStatusTone(editor.status)" />
          <span class="type-caption">{{ statusText }}</span>
        </span>
        <!-- Just published: the class page, where its other lessons are, is the next step (A3.34). -->
        <RouterLink
          v-if="justPublished && editor.status === 'PUBLISHED'"
          class="btn btn-secondary btn-sm"
          :to="classRoute"
          data-testid="open-class"
        >
          {{ t('classes.openClass') }}<IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
      </section>

      <DataNotice :error="failure?.message" :error-title="failure?.title">
        <!-- A failed save can be repeated from here; a load error has its own retry in the page's error state. -->
        <template v-if="failure?.retry" #actions>
          <button
            type="button"
            class="btn btn-secondary btn-sm"
            :disabled="writePending"
            data-testid="retry-save"
            @click="failure?.retry?.()"
          >
            {{ t('common.retry') }}
          </button>
        </template>
      </DataNotice>

      <RouteTabs :tabs="tabs" :label="t('lessonEditor.tabsLabel')" :disabled="panelSaving" />

      <LessonContentPanel
        v-if="tab === 'content'"
        v-model:title="content.title"
        v-model:unit-id="content.unitId"
        v-model:key-phrases="content.keyPhrases"
        :editor="editor"
        :unit-names="unitNames"
        :title-error="titleError"
        :write-pending="writePending"
        @blur-title="validateTitle"
        @updated="applyEditor"
        @notice="showToast"
        @saving="onPanelSaving"
      />
      <LessonActivitiesPanel
        v-else-if="tab === 'activities'"
        :editor="editor"
        :inert="saving"
        @updated="applyEditor"
        @notice="showToast"
        @saving="onPanelSaving"
      />
      <LessonQuizPanel
        v-else-if="tab === 'quiz'"
        :editor="editor"
        :inert="saving"
        @updated="applyEditor"
        @notice="showToast"
        @error="onPanelError"
        @deleted="offerQuestionUndo"
        @saving="onPanelSaving"
      />
      <LessonPreviewPanel v-else :editor="livePreview!" :content-route="contentRoute" />

      <section class="panel lesson-footer" :aria-label="t('lessonEditor.footer.label')">
        <template v-if="editor.editable">
          <div class="lesson-footer-note">
            <span v-if="publishBlocker" id="lesson-publish-hint" class="lesson-footer-hint" data-testid="publish-hint">
              <IconGlyph name="edit" :size="16" />{{ publishBlocker }}
            </span>
            <!-- Saved edits of a published lesson can be dropped: learners keep the published version anyway. -->
            <button
              v-if="editor.status === 'PUBLISHED' && editor.hasUnpublishedChanges"
              type="button"
              class="btn btn-ghost btn-sm"
              :disabled="writePending"
              data-testid="discard-changes"
              @click="dialog = 'discard'"
            >
              {{ t('lessonEditor.discard.action') }}
            </button>
          </div>
          <div class="btn-group">
            <button
              ref="saveDraftButton"
              type="button"
              class="btn btn-secondary"
              :disabled="writePending || !dirty || !content.title.trim()"
              :aria-busy="pendingAction === 'save'"
              data-testid="save-draft"
              @click="saveContent"
            >
              {{ t('lessonEditor.footer.saveDraft') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="writePending || Boolean(publishBlocker)"
              :aria-describedby="publishBlocker ? 'lesson-publish-hint' : undefined"
              data-testid="open-publish"
              @click="dialog = 'publish'"
            >
              <IconGlyph name="send" :size="16" />{{ t('lessonEditor.publish.action') }}
            </button>
          </div>
        </template>
        <template v-else>
          <div class="lesson-footer-note">
            <span class="lesson-footer-hint" data-testid="read-only-reason"
              ><IconGlyph name="lock" :size="16" />{{ readOnlyReason }}</span
            >
          </div>
          <RouterLink class="btn btn-secondary" :to="classRoute">
            {{ t('classes.openClass') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </template>
      </section>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('nav.classes') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('lessonEditor.loading')"
        :message="t('common.loadingMessage')"
      />
      <ClassLoadError
        v-else
        :title="t('classes.lessonLoadError')"
        :message="resource.error.value"
        :back-to="classRoute"
        :back-label="t('classes.openClass')"
        @retry="resource.reload"
      />
    </template>

    <AppOverlay
      :open="dialog === 'publish'"
      :title="t('lessonEditor.publish.title', { title: livePreview?.title ?? '' })"
      :busy="saving"
      test-id="publish-dialog"
      @close="dialog = null"
    >
      <ul class="row-list authoring-checklist">
        <li v-for="item in publishChecklist" :key="item">
          <span class="authoring-tile is-filled" aria-hidden="true"><IconGlyph name="check" :size="16" /></span>
          <strong class="row-list-title">{{ item }}</strong>
        </li>
      </ul>
      <p v-if="dirty" class="overlay-message">{{ t('lessonEditor.publish.saveFirst') }}</p>
      <p v-if="dialogError" class="field-error" role="alert">{{ dialogError }}</p>
      <template #footer>
        <button type="button" class="btn btn-secondary" :disabled="saving" data-autofocus @click="dialog = null">
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="saving"
          :aria-busy="pendingAction === 'publish'"
          data-testid="publish-confirm"
          @click="publish"
        >
          {{ t('lessonEditor.publish.action') }}
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="dialog === 'discard'"
      :title="t('lessonEditor.discard.title')"
      :message="t('lessonEditor.discard.message')"
      :confirm-label="t('lessonEditor.discard.action')"
      :cancel-label="t('lessonEditor.discard.keep')"
      tone="danger"
      :busy="saving"
      :error="dialogError"
      @cancel="dialog = null"
      @confirm="discardChanges"
    />

    <ConfirmDialog
      :open="dialog === 'archive'"
      :title="t('lessonEditor.archive.title', { title: editor?.title ?? '' })"
      :message="
        dirty
          ? `${t('lessonEditor.archive.message')} ${t('lessonEditor.archive.unsavedNote')}`
          : t('lessonEditor.archive.message')
      "
      :confirm-label="t('lessonEditor.archive.confirm')"
      :cancel-label="t('lessonEditor.archive.keep')"
      tone="danger"
      :busy="saving"
      :error="dialogError"
      @cancel="dialog = null"
      @confirm="archiveLesson"
    />

    <ConfirmDialog
      :open="leaveTarget !== null"
      :title="t('lessonEditor.leave.title')"
      :message="t('lessonEditor.leave.message')"
      :confirm-label="t('lessonEditor.leave.confirm')"
      :cancel-label="t('lessonEditor.leave.keep')"
      @cancel="leaveTarget = null"
      @confirm="leaveWithoutSaving"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { lessonEditorApi } from '@/api/adminClasses'
import { contentStatusTone } from '@/components/admin/adminDisplay'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import LessonActivitiesPanel from '@/components/classes/LessonActivitiesPanel.vue'
import LessonContentPanel from '@/components/classes/LessonContentPanel.vue'
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
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/layout/PageHeaderBlock.vue'
import { useEditorLeaveGuard } from '@/composables/useEditorLeaveGuard'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useRecordById } from '@/composables/useResource'
import { usePageRedirect } from '@/composables/usePageNavigation'
import { useToast } from '@/composables/useToast'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { EditorQuestion, LessonEditor } from '@/types/adminClasses'
import { CLASS_ACTIVITY_TYPES } from '@/types/classes'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { formatList } from '@/utils/format'
import {
  QUIZ_SIZE,
  isLessonPinyinReady,
  lessonDraftFields,
  lessonQuiz,
  questionPayload,
  savedLessonContent,
  uncheckedAiDraftQuestionNumbers,
  type LessonContentForm,
} from '@/utils/lessonEditor'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/class-authoring.css'

const { t, locale } = useLocalMessages({
  lessonEditor: lessonEditorMessages,
  adminClasses: adminClassesMessages,
  classes: classesMessages,
})
const route = useRoute()
const router = useRouter()
const { toast, toastAction, showToast, hideToast } = useToast()

function normalizedTab(value: unknown): 'content' | 'activities' | 'quiz' | 'preview' {
  return value === 'activities' || value === 'quiz' || value === 'preview' ? value : 'content'
}

const classId = computed(() => parseRecordId(route.params.classId))
const lessonId = computed(() => parseRecordId(route.params.lessonId))
const tab = computed(() => normalizedTab(route.params.tab))

const resource = useRecordById(lessonId, lessonEditorApi.get)
const editor = resource.data
let pageVersion = 0

const resolvedClassId = computed(() => editor.value?.classId ?? classId.value)
const classRoute = computed<RouteLocationRaw>(() =>
  resolvedClassId.value !== null
    ? { name: 'admin-class', params: { classId: resolvedClassId.value } }
    : { name: 'admin-classes' },
)
const contentRoute = computed<RouteLocationRaw>(() =>
  resolvedClassId.value !== null && lessonId.value !== null
    ? { name: 'admin-lesson', params: { classId: resolvedClassId.value, lessonId: lessonId.value } }
    : classRoute.value,
)

const quiz = computed(() => (editor.value ? lessonQuiz(editor.value) : null))
const lessonQuizCount = computed(() => quiz.value?.questions.length ?? 0)

const tabs = computed<RouteTab[]>(() => {
  const currentClassId = resolvedClassId.value
  const currentLessonId = editor.value?.lessonId ?? lessonId.value
  if (currentClassId === null || currentLessonId === null) return []
  const params = { classId: currentClassId, lessonId: currentLessonId }
  return [
    { label: t('lessonEditor.tabs.content'), to: { name: 'admin-lesson', params }, icon: 'file' },
    {
      label: t('lessonEditor.activities.title'),
      to: { name: 'admin-lesson', params: { ...params, tab: 'activities' } },
      icon: 'target',
      count: editor.value?.activities.length ?? null,
    },
    {
      label: t('lessonEditor.tabs.quiz'),
      to: { name: 'admin-lesson', params: { ...params, tab: 'quiz' } },
      icon: 'quiz',
      count: lessonQuizCount.value,
    },
    {
      label: t('lessonEditor.tabs.preview'),
      to: { name: 'admin-lesson', params: { ...params, tab: 'preview' } },
      icon: 'eye',
    },
  ]
})

// Status: what the lesson is for learners right now (A3.14, A3.34).

const justPublished = ref(false)
const statusRef = ref<HTMLElement | null>(null)

const statusText = computed(() => {
  const current = editor.value
  if (!current) return ''
  if (current.classStatus === 'ARCHIVED') return t('lessonEditor.status.classArchived')
  if (current.status === 'ARCHIVED') return t('lessonEditor.status.archived')
  if (current.status === 'DRAFT') return t('lessonEditor.status.draft')
  return justPublished.value ? t('lessonEditor.status.justPublished') : ''
})

const unitNames = computed(
  () =>
    new Map(
      (editor.value?.units ?? []).map((unit, index) => [
        unit.id,
        t('classes.unitLabel', { number: index + 1, title: unit.title }),
      ]),
    ),
)

const headerDescription = computed(() => {
  const current = editor.value
  if (!current) return ''
  const unit = unitNames.value.get(current.unitId) ?? current.unitTitle
  const publishedAndEditable = current.status === 'PUBLISHED' && current.editable && !justPublished.value
  return publishedAndEditable ? t('lessonEditor.status.publishedMeta', { unit }) : unit
})

const readOnlyReason = computed(() =>
  editor.value?.classStatus === 'ARCHIVED'
    ? t('lessonEditor.footer.readOnlyClassArchived')
    : t('lessonEditor.footer.readOnlyArchived'),
)

// Content form (saved with Save draft)

const content = ref<LessonContentForm>({ title: '', unitId: null, keyPhrases: [] })
const draft = computed(() => lessonDraftFields(content.value))
const draftJson = computed(() => JSON.stringify(draft.value))
const savedDraft = computed(() => (editor.value ? JSON.stringify(lessonDraftFields(editor.value)) : null))
const formSnapshot = computed(() => `${draftJson.value}|${content.value.unitId ?? ''}`)
const publishableContentDirty = computed(() => Boolean(editor.value?.editable) && draftJson.value !== savedDraft.value)
const contentTitle = computed(() => content.value.title)
const {
  error: titleError,
  validate: validateTitle,
  reset: resetTitleError,
} = useFieldCheck(contentTitle, (value) => (value.trim() ? null : t('lessonEditor.details.titleMissing')))

const dirty = computed(
  () =>
    publishableContentDirty.value || Boolean(editor.value?.editable && content.value.unitId !== editor.value.unitId),
)

const livePreview = computed<LessonEditor | null>(() =>
  editor.value
    ? {
        ...editor.value,
        title: draft.value.title || editor.value.title,
        unitId: content.value.unitId ?? editor.value.unitId,
        unitTitle: unitNames.value.get(content.value.unitId ?? editor.value.unitId) ?? editor.value.unitTitle,
        keyPhrases: draft.value.keyPhrases,
      }
    : null,
)

function fillContent(lesson: LessonEditor): void {
  content.value = savedLessonContent(lesson)
  resetTitleError()
}

function applyEditor(next: LessonEditor, keepEdits = dirty.value): void {
  if (next.lessonId !== lessonId.value) return
  resource.replace(next)
  failure.value = null
  if (next.hasUnpublishedChanges || next.status !== 'PUBLISHED') justPublished.value = false
  if (!keepEdits) fillContent(next)
}

watch(editor, (current, previous) => {
  if (!current || current.lessonId !== lessonId.value) return
  if (current.lessonId !== previous?.lessonId) fillContent(current)
  if (route.query.created) showToast(t('lessonEditor.created'))
})
usePageRedirect(() => {
  const current = editor.value
  if (!current || current.lessonId !== lessonId.value || (current.classId === classId.value && !route.query.created)) {
    return null
  }
  return {
    name: 'admin-lesson',
    params: { ...route.params, classId: current.classId },
    query: { ...route.query, created: undefined },
    hash: route.hash,
  }
})

const pendingAction = ref<'save' | 'publish' | 'discard' | 'archive' | 'restoreLesson' | 'restore' | null>(null)
const savingSnapshot = ref<string | null>(null)
const saving = computed(() => pendingAction.value !== null)
const saveDraftButton = ref<HTMLButtonElement | null>(null)
const archiveLessonButton = ref<HTMLButtonElement | null>(null)
const restoreLessonButton = ref<HTMLButtonElement | null>(null)
const failure = shallowRef<{ title: string; message: string; retry: (() => void) | null } | null>(null)
/** A write of the open tab's panel is out (video upload/remove, hint switch, question, AI draft, activity). */
const panelSaving = ref(false)

function onPanelSaving(busy: boolean): void {
  panelSaving.value = busy
}

function onPanelError(message: string): void {
  failure.value = { message, title: '', retry: null }
}

// Block switching to another tab of this lesson while a panel write or video upload is in flight, rather than queueing
// an invisible deferred jump that would discard inline errors on failure or yank the admin off the tab when a long
// upload finishes.
const removeTabGuard = router.beforeEach((to) => {
  if (
    !panelSaving.value ||
    to.name !== 'admin-lesson' ||
    lessonId.value === null ||
    parseRecordId(to.params.lessonId) !== lessonId.value ||
    normalizedTab(to.params.tab) === tab.value
  ) {
    return
  }
  return false
})
const hasPendingLeaveRisk = computed(() => dirty.value || panelSaving.value || pendingAction.value === 'publish')
const savingCurrent = computed(
  () => pendingAction.value === 'save' && savingSnapshot.value === formSnapshot.value && !panelSaving.value,
)
const { leaveTarget, leaveWithoutSaving } = useEditorLeaveGuard(
  (target) =>
    target.name === 'admin-lesson' &&
    lessonId.value !== null &&
    parseRecordId(target.params.lessonId) === lessonId.value,
  hasPendingLeaveRisk,
  savingCurrent,
)

async function runPageAction(
  action: NonNullable<typeof pendingAction.value>,
  work: (isCurrent: () => boolean) => Promise<void>,
  onError: (reason: unknown) => void,
): Promise<void> {
  const version = pageVersion
  const isCurrent = () => version === pageVersion
  pendingAction.value = action
  failure.value = null
  try {
    await work(isCurrent)
  } catch (reason) {
    if (isCurrent()) onError(reason)
  } finally {
    if (isCurrent()) pendingAction.value = null
  }
}

async function persistContent(id: number, isCurrent: () => boolean): Promise<boolean> {
  const submittedSnapshot = formSnapshot.value
  savingSnapshot.value = submittedSnapshot
  try {
    const next = await lessonEditorApi.saveContent(id, {
      ...draft.value,
      unitId: content.value.unitId,
    })
    if (!isCurrent()) return false
    applyEditor(next, formSnapshot.value !== submittedSnapshot)
    return true
  } finally {
    if (isCurrent() && savingSnapshot.value === submittedSnapshot) savingSnapshot.value = null
  }
}

function saveFailureMessage(reason: unknown): string {
  return apiErrorStatus(reason) === null
    ? t('lessonEditor.saveFailed')
    : resolveApiError(reason, t('lessonEditor.saveFailed'))
}

async function saveContent(): Promise<void> {
  const id = lessonId.value
  if (id === null || writePending.value || !editor.value?.editable || !validateTitle()) return
  const movedPublishedUnit = editor.value.status === 'PUBLISHED' && content.value.unitId !== editor.value.unitId
  const hadDraftContentChange = publishableContentDirty.value
  await runPageAction(
    'save',
    async (isCurrent) => {
      if (await persistContent(id, isCurrent)) {
        const key = movedPublishedUnit
          ? hadDraftContentChange
            ? 'lessonEditor.savedWithUnitMove'
            : 'lessonEditor.savedUnitMoved'
          : 'lessonEditor.saved'
        showToast(t(key))
      }
    },
    (reason) => {
      failure.value = {
        title: t('lessonEditor.saveFailedTitle'),
        message: saveFailureMessage(reason),
        retry: () => {
          saveDraftButton.value?.focus()
          void saveContent()
        },
      }
    },
  )
}

// Publish, archive, restore & discard

const dialog = ref<'publish' | 'discard' | 'archive' | null>(null)
const dialogError = ref<string | null>(null)

const pinyinReady = computed(() =>
  editor.value ? isLessonPinyinReady(editor.value, draft.value.keyPhrases, dirty.value) : false,
)
const uncheckedAiDraftNumbers = computed(() => uncheckedAiDraftQuestionNumbers(quiz.value))
const writePending = computed(() => saving.value || panelSaving.value)
watch(
  writePending,
  (pending) => {
    if (pending && toastAction.value) hideToast()
  },
  { flush: 'sync' },
)

const publishBlocker = computed<string | null>(() => {
  const current = editor.value
  if (!current) return null
  if (!content.value.title.trim()) return t('lessonEditor.details.titleMissing')
  const checklist = current.checklist
  const keyPhrasesReady = dirty.value
    ? draft.value.keyPhrases.length > 0 &&
      draft.value.keyPhrases.every((phrase) => Boolean(phrase.chinese && phrase.meaning))
    : checklist.keyPhrasesAdded
  const missing = [
    checklist.videoAdded ? null : t('lessonEditor.footer.needVideo'),
    keyPhrasesReady ? null : t('lessonEditor.footer.needPhrases'),
    checklist.activitiesComplete ? null : t('lessonEditor.footer.needActivities'),
  ].filter((item) => item !== null)
  if (missing.length) {
    if (!checklist.quizzesReady) missing.push(t('lessonEditor.footer.needQuiz'))
    return t('lessonEditor.footer.needs', { items: formatList(missing, locale.value) })
  }
  if (!checklist.quizzesReady) {
    const count = lessonQuizCount.value
    if (count > 0 && count < QUIZ_SIZE) {
      return t('lessonEditor.footer.questionsShort', { count: QUIZ_SIZE - count, total: QUIZ_SIZE }, QUIZ_SIZE - count)
    }
    if (count > QUIZ_SIZE) {
      return t('lessonEditor.footer.questionsOver', { count: count - QUIZ_SIZE, total: QUIZ_SIZE }, count - QUIZ_SIZE)
    }
    return t('lessonEditor.footer.needs', { items: t('lessonEditor.footer.needQuiz') })
  }
  const drafts = uncheckedAiDraftNumbers.value
  if (drafts.length) {
    return t(
      'lessonEditor.footer.checkAiDrafts',
      { numbers: formatList(drafts.map(String), locale.value) },
      drafts.length,
    )
  }
  if (!pinyinReady.value) return t('lessonEditor.footer.needsPinyin')
  if (current.status === 'PUBLISHED' && !current.hasUnpublishedChanges && !publishableContentDirty.value) {
    return t('lessonEditor.footer.nothingNew')
  }
  return null
})

const publishChecklist = computed(() => [
  t('lessonEditor.publish.content'),
  t('lessonEditor.publish.activities', {
    types: formatList(
      CLASS_ACTIVITY_TYPES.map((type) => t(`practiceType.${type}`)),
      locale.value,
    ),
  }),
  t('lessonEditor.publish.quiz', { count: lessonQuizCount.value }),
  t('lessonEditor.publish.pinyin'),
])

watch(dialog, () => {
  dialogError.value = null
})

async function publish(): Promise<void> {
  const id = lessonId.value
  if (id === null || writePending.value || !editor.value?.editable) return
  dialogError.value = null
  await runPageAction(
    'publish',
    async (isCurrent) => {
      if (dirty.value && !(await persistContent(id, isCurrent))) return
      if (editor.value?.status === 'PUBLISHED' && !editor.value.hasUnpublishedChanges) {
        dialog.value = null
        showToast(t('lessonEditor.saved'))
        return
      }
      const next = await lessonEditorApi.publish(id)
      if (!isCurrent()) return
      applyEditor(next)
      justPublished.value = true
      dialog.value = null
      await nextTick()
      statusRef.value?.focus()
    },
    (reason) => {
      dialogError.value = resolveApiError(reason, t('lessonEditor.publish.error'))
    },
  )
}

async function discardChanges(): Promise<void> {
  const id = lessonId.value
  if (id === null || writePending.value) return
  dialogError.value = null
  await runPageAction(
    'discard',
    async (isCurrent) => {
      const next = await lessonEditorApi.discardChanges(id)
      if (!isCurrent()) return
      applyEditor(next, false)
      dialog.value = null
      showToast(t('lessonEditor.discard.done'))
    },
    (reason) => {
      dialogError.value = resolveApiError(reason, t('lessonEditor.discard.error'))
    },
  )
}

async function archiveLesson(): Promise<void> {
  const id = lessonId.value
  if (id === null || writePending.value || !editor.value || editor.value.status === 'ARCHIVED') return
  dialogError.value = null
  await runPageAction(
    'archive',
    async (isCurrent) => {
      const next = await lessonEditorApi.archive(id)
      if (!isCurrent()) return
      applyEditor(next, false)
      dialog.value = null
      showToast(t('lessonEditor.archive.done'))
      await nextTick()
      ;(restoreLessonButton.value ?? statusRef.value)?.focus()
    },
    (reason) => {
      dialogError.value = resolveApiError(reason, t('lessonEditor.archive.error'))
    },
  )
}

async function restoreLesson(): Promise<void> {
  const id = lessonId.value
  if (id === null || writePending.value || editor.value?.status !== 'ARCHIVED') return
  const hadFocus = document.activeElement === restoreLessonButton.value
  await runPageAction(
    'restoreLesson',
    async (isCurrent) => {
      const next = await lessonEditorApi.restore(id)
      if (!isCurrent()) return
      applyEditor(next, false)
      const asDraft = next.status === 'DRAFT'
      showToast(
        t(asDraft ? 'lessonEditor.archive.restoredDraft' : 'lessonEditor.archive.restored', { title: next.title }),
      )
      await nextTick()
      if (hadFocus || !document.activeElement || document.activeElement === document.body) {
        ;(archiveLessonButton.value ?? statusRef.value)?.focus()
      }
    },
    (reason) => {
      failure.value = {
        title: '',
        message: resolveApiError(reason, t('lessonEditor.archive.restoreError')),
        retry: () => {
          restoreLessonButton.value?.focus()
          void restoreLesson()
        },
      }
    },
  )
}

// Undo of a deleted question (A3.36)

function offerQuestionUndo(question: EditorQuestion, quizId: number): void {
  const id = lessonId.value
  if (id === null) return
  showToast(t('lessonEditor.question.deleted', { number: question.number }), {
    label: t('common.undo'),
    run: () => void restoreQuestion(id, quizId, question),
  })
}

async function restoreQuestion(id: number, quizId: number, question: EditorQuestion): Promise<void> {
  if (id !== lessonId.value || writePending.value) return
  await runPageAction(
    'restore',
    async (isCurrent) => {
      const currentQuizId = quiz.value?.id ?? quizId
      const next = await lessonEditorApi.addQuestion(id, currentQuizId, {
        ...questionPayload(question),
        position: question.number,
      })
      if (!isCurrent()) return
      applyEditor(next)
    },
    (reason) => {
      failure.value = { title: '', message: resolveApiError(reason, t('lessonEditor.question.error')), retry: null }
    },
  )
}

watch(
  lessonId,
  () => {
    pageVersion++
    pendingAction.value = null
    savingSnapshot.value = null
    dialog.value = null
    hideToast()
    failure.value = null
    justPublished.value = false
    panelSaving.value = false
  },
  { flush: 'sync' },
)

onBeforeUnmount(() => {
  pageVersion++
  removeTabGuard()
  panelSaving.value = false
})
</script>
