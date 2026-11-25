<template>
  <div class="page-stack">
    <AppToast :message="toast" />

    <template v-if="page">
      <PageHeaderBlock :title="page.title" :description="meta">
        <template #before>
          <AppBreadcrumb :items="[{ label: t('nav.classes'), to: { name: 'admin-classes' } }, { label: page.title }]" />
        </template>
        <template #badge>
          <StatusPill :label="t(`adminClasses.statuses.${page.status}`)" :tone="contentStatusTone(page.status)" />
        </template>
        <template #actions>
          <template v-if="page.status === 'DRAFT'">
            <button
              type="button"
              class="btn btn-ghost"
              :disabled="saving"
              data-testid="delete-class"
              @click="dialog = 'delete'"
            >
              <IconGlyph name="trash" :size="16" />{{ t('adminClasses.page.delete') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!page.canPublish || saving"
              :aria-describedby="page.canPublish ? undefined : noticeId"
              data-testid="publish-class"
              @click="dialog = 'publish'"
            >
              {{ t('adminClasses.page.publish') }}
            </button>
          </template>
          <button
            v-else-if="page.status === 'PUBLISHED'"
            type="button"
            class="btn btn-ghost"
            :disabled="saving"
            data-testid="archive-class"
            @click="dialog = 'archive'"
          >
            <IconGlyph name="archive" :size="16" />{{ t('adminClasses.page.archive') }}
          </button>
          <button
            v-else
            type="button"
            class="btn btn-secondary"
            :disabled="saving"
            :aria-busy="saving"
            data-testid="restore-class"
            @click="restore"
          >
            <IconGlyph name="archive" :size="16" />{{ t('adminClasses.page.restore') }}
          </button>
        </template>
      </PageHeaderBlock>

      <DataNotice :error="actionError ?? resource.error.value" :on-retry="actionError ? undefined : resource.reload" />
      <p v-if="page.status === 'DRAFT'" :id="noticeId" class="callout" role="note" data-testid="class-notice">
        <IconGlyph name="info" :size="18" />
        <span>
          {{ t('adminClasses.page.draftNotice') }}
          <template v-if="!page.canPublish"> {{ t('adminClasses.page.publishCondition') }}</template>
        </span>
      </p>
      <p v-else-if="page.status === 'ARCHIVED'" class="callout" role="note" data-testid="class-notice">
        <IconGlyph name="archive" :size="18" /><span>{{ t('adminClasses.page.archivedNotice') }}</span>
      </p>

      <RouteTabs :tabs="tabs" :label="t('adminClasses.page.tabsLabel')" />

      <AdminClassUnits
        v-if="tab === 'lessons'"
        :key="page.id"
        :class-id="page.id"
        :units="page.units"
        :editable="editable"
        @updated="showPage"
        @lesson-created="openCreatedLesson"
        @notice="showToast"
      />

      <section v-else class="panel authoring-card" aria-labelledby="admin-class-details-title">
        <h2 id="admin-class-details-title" class="card-title">{{ t('adminClasses.details.title') }}</h2>
        <DataNotice :error="topicsError" @retry="reloadTopics" />
        <form :id="formId" novalidate @submit.prevent="saveDetails">
          <ClassDetailsFields
            v-model="form"
            :themes="themeOptions"
            :learn-units="learnUnits"
            :disabled="!editable || saving"
            :title-error="titleError"
            @title-blur="validateTitle"
          />
        </form>
        <div v-if="editable" class="class-details-actions">
          <button
            type="submit"
            class="btn btn-primary"
            :form="formId"
            :disabled="saving || !detailsDirty || Boolean(titleError)"
            :aria-busy="saving"
            data-testid="save-details"
            @mousedown.prevent
          >
            {{ t('adminClasses.details.save') }}
          </button>
        </div>
      </section>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('nav.classes') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('adminClasses.page.loading')"
        :message="t('nav.classes')"
      />
      <ClassLoadError
        v-else
        :title="t('adminClasses.page.loadErrorTitle')"
        :message="resource.error.value"
        :back-to="{ name: 'admin-classes' }"
        :back-label="t('adminClasses.page.goToClasses')"
        @retry="resource.reload"
      />
    </template>

    <AppOverlay
      :open="dialog === 'publish'"
      :title="t('adminClasses.publish.title', { title: page?.title ?? '' })"
      :busy="saving"
      test-id="publish-class-dialog"
      @close="dialog = null"
    >
      <ul class="row-list authoring-checklist">
        <li v-for="item in publishChecklist" :key="item.label">
          <span class="authoring-tile" :class="{ 'is-filled': item.ready }" aria-hidden="true">
            <IconGlyph :name="item.ready ? 'check' : 'info'" :size="16" />
          </span>
          <strong class="row-list-title">{{ item.label }}</strong>
        </li>
      </ul>
      <p class="overlay-message">{{ t('adminClasses.publish.message') }}</p>
      <p v-if="dialogError" class="field-error" role="alert">{{ dialogError }}</p>
      <template #footer>
        <button type="button" class="btn btn-secondary" :disabled="saving" data-autofocus @click="dialog = null">
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="saving"
          :aria-busy="saving"
          data-testid="publish-class-confirm"
          @click="publish"
        >
          {{ t('adminClasses.page.publish') }}
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="dialog === 'archive'"
      :title="t('adminClasses.archive.title', { title: page?.title ?? '' })"
      :message="t('adminClasses.archive.message')"
      :confirm-label="t('adminClasses.archive.confirm')"
      :cancel-label="t('adminClasses.keepClass')"
      tone="danger"
      :busy="saving"
      :error="dialogError"
      @cancel="dialog = null"
      @confirm="archive"
    />
    <ConfirmDialog
      :open="dialog === 'delete'"
      :title="t('adminClasses.remove.title', { title: page?.title ?? '' })"
      :message="deleteMessage"
      :confirm-label="t('adminClasses.page.delete')"
      :cancel-label="t('adminClasses.keepClass')"
      tone="danger"
      :busy="saving"
      :error="dialogError"
      @cancel="dialog = null"
      @confirm="remove"
    />
    <ConfirmDialog
      :open="leaveTarget !== null"
      :title="t('adminClasses.details.leaveTitle')"
      :message="t('adminClasses.details.leaveMessage')"
      :confirm-label="t('adminClasses.details.leaveConfirm')"
      :cancel-label="t('adminClasses.details.leaveKeep')"
      @cancel="leaveTarget = null"
      @confirm="leaveWithoutSaving"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminClassesApi } from '@/api/adminClasses'
import AdminClassUnits from '@/components/admin/AdminClassUnits.vue'
import { contentStatusTone } from '@/components/admin/adminDisplay'
import { useClassFormOptions } from '@/components/admin/useClassFormOptions'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import ClassDetailsFields from '@/components/classes/ClassDetailsFields.vue'
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
import { useToast } from '@/composables/useToast'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import type { AdminClassPage } from '@/types/adminClasses'
import { resolveApiError } from '@/utils/apiError'
import { classDetailsForm, classDetailsPayload } from '@/utils/classes'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/class-authoring.css'

const { t } = useLocalMessages({ adminClasses: adminClassesMessages })
const route = useRoute()
const router = useRouter()
const { toast, showToast, hideToast } = useToast()
const noticeId = useId()

/** One write at a time (see `run`); its error shows under the header, or in the dialog that started it. */
const saving = ref(false)
const savingDetailsSnapshot = ref<string | null>(null)
const actionError = ref<string | null>(null)
const dialogError = ref<string | null>(null)
const dialog = ref<'publish' | 'archive' | 'delete' | null>(null)

const classId = computed(() => parseRecordId(route.params.classId))
const tab = computed(() => (route.params.tab === 'details' ? 'details' : 'lessons'))

const resource = useRecordById(classId, adminClassesApi.classPage)
const page = resource.data
const { themes, topicsError, reloadTopics, learnUnits, load: loadFormOptions } = useClassFormOptions()
const themeOptions = computed(() => {
  const current = page.value?.category
  if (!current || themes.value.some((topic) => topic.id === current.id)) return themes.value
  return [current, ...themes.value]
})

let pageVersion = 0
onBeforeUnmount(() => pageVersion++)
watch(
  classId,
  () => {
    pageVersion++
    saving.value = false
    savingDetailsSnapshot.value = null
    dialog.value = null
    actionError.value = null
    hideToast()
  },
  { flush: 'sync' },
)
watch(
  tab,
  (current) => {
    if (current === 'details') loadFormOptions()
  },
  { immediate: true },
)
watch(dialog, () => {
  dialogError.value = null
})

/** An archived class is read-only: Restore it first. */
const editable = computed(() => page.value !== null && page.value.status !== 'ARCHIVED')

/**
 * "Study or exchange · 5 lessons"; a class without lessons yet shows its theme alone, and a draft not given a theme yet
 * says so (A3.47).
 */
const meta = computed(() => {
  const current = page.value
  if (!current) return ''
  const theme = current.category?.name ?? (current.status === 'DRAFT' ? t('adminClasses.page.noTheme') : null)
  return [theme, current.lessonCount ? t('adminClasses.page.lessons', current.lessonCount) : null]
    .filter(Boolean)
    .join(' · ')
})

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminClasses.page.tabLessons'),
    to: { name: 'admin-class', params: { classId: classId.value } },
    icon: 'checklist',
  },
  {
    label: t('adminClasses.page.tabDetails'),
    to: { name: 'admin-class', params: { classId: classId.value, tab: 'details' } },
    icon: 'file',
  },
])

// Details

const formId = useId()
const form = ref(classDetailsForm())
const formTitle = computed(() => form.value.title)
const {
  error: titleError,
  validate: validateTitle,
  reset: resetTitleError,
} = useFieldCheck(formTitle, (value) => (value.trim() ? null : t('adminClasses.form.titleMissing')))

function fillDetails(current: AdminClassPage): void {
  form.value = classDetailsForm(current)
  resetTitleError()
}

/** Details typed but not saved, compared as the server stores them: trimmed, an empty description is none. */
const detailsPayload = computed(() => classDetailsPayload(form.value))
const detailsSnapshot = computed(() => JSON.stringify(detailsPayload.value))
const savedDetailsSnapshot = computed(() =>
  page.value ? JSON.stringify(classDetailsPayload(classDetailsForm(page.value))) : null,
)
const detailsDirty = computed(() => editable.value && detailsSnapshot.value !== savedDetailsSnapshot.value)

/**
 * A class opened fills the form. A unit added or renamed on the Lessons tab also answers with the whole class: the
 * details typed on the Details tab but not saved yet stay in the form.
 */
watch(page, (current, previous) => {
  if (current && previous?.id !== current.id) fillDetails(current)
})

/** The class a write answered with, on the page; the details typed but not saved stay in the form. */
function showPage(next: AdminClassPage): void {
  if (next.id !== classId.value) return
  resource.replace(next)
}

async function openCreatedLesson(lessonId: number): Promise<void> {
  const ownerClassId = classId.value
  if (ownerClassId === null) return
  const version = pageVersion
  actionError.value = null
  try {
    const failure = await router.push({
      name: 'admin-lesson',
      params: { classId: ownerClassId, lessonId },
      query: { created: '1' },
    })
    if (!failure) return
  } catch (reason) {
    if (version === pageVersion) actionError.value = resolveApiError(reason)
  }
  // Unsaved class details can keep the admin here after the lesson has already been created.
  if (version === pageVersion) await resource.reload()
}

const { leaveTarget, leaveTo, leaveWithoutSaving } = useEditorLeaveGuard(
  (target) =>
    target.name === 'admin-class' && classId.value !== null && parseRecordId(target.params.classId) === classId.value,
  detailsDirty,
  computed(() => savingDetailsSnapshot.value === detailsSnapshot.value),
)

/**
 * Runs one write transaction of the open class; only the class still open may apply UI effects. A write from a dialog
 * shows its error in the dialog; any other one under the header.
 */
async function run(fallback: string, action: (id: number, isCurrent: () => boolean) => Promise<void>): Promise<void> {
  if (saving.value || !page.value || classId.value === null || page.value.id !== classId.value) return
  const id = classId.value
  const version = pageVersion
  const fromDialog = dialog.value !== null
  saving.value = true
  actionError.value = null
  dialogError.value = null
  try {
    await action(id, () => version === pageVersion)
  } catch (reason) {
    if (version !== pageVersion) return
    const message = resolveApiError(reason, fallback)
    if (fromDialog && dialog.value !== null) dialogError.value = message
    else actionError.value = message
  } finally {
    if (version === pageVersion) saving.value = false
  }
}

/** Saved at once, also on a published class: learners see the new details right away (X1.01). */
async function saveDetails(): Promise<void> {
  if (saving.value || !detailsDirty.value || !validateTitle()) return
  const submittedSnapshot = detailsSnapshot.value
  const payload = detailsPayload.value
  await run(t('adminClasses.details.error'), async (id, isCurrent) => {
    savingDetailsSnapshot.value = submittedSnapshot
    try {
      const saved = await adminClassesApi.saveDetails(id, payload)
      if (!isCurrent()) return
      resource.replace(saved)
      showToast(t('adminClasses.details.saved'))
      // The form shows the details as they were saved (trimmed) unless newer edits were typed while saving.
      if (detailsSnapshot.value === submittedSnapshot) fillDetails(saved)
    } finally {
      if (isCurrent()) savingDetailsSnapshot.value = null
    }
  })
}

// Publish, archive, restore, delete

/** What the publish dialog confirms (the format of a lesson's publish checklist, A3.33). */
const publishChecklist = computed(() => {
  const units = page.value?.units ?? []
  const lessons = units.flatMap((unit) => unit.lessons)
  const published = lessons.filter((lesson) => lesson.status === 'PUBLISHED').length
  const drafts = lessons.filter((lesson) => lesson.status === 'DRAFT').length
  return [
    { label: t('adminClasses.publish.units', units.length), ready: true },
    { label: t('adminClasses.publish.lessons', published), ready: true },
    ...(drafts ? [{ label: t('adminClasses.publish.draftLessons', drafts), ready: false }] : []),
  ]
})

async function saveDirtyDetails(id: number, isCurrent: () => boolean): Promise<boolean> {
  if (!detailsDirty.value) return true
  if (!validateTitle()) {
    dialogError.value = titleError.value
    return false
  }
  try {
    const saved = await adminClassesApi.saveDetails(id, detailsPayload.value)
    if (!isCurrent()) return false
    resource.replace(saved)
    fillDetails(saved)
    return true
  } catch (reason) {
    if (isCurrent()) dialogError.value = resolveApiError(reason, t('adminClasses.details.error'))
    return false
  }
}

async function publish(): Promise<void> {
  await run(t('adminClasses.publish.error'), async (id, isCurrent) => {
    if (!(await saveDirtyDetails(id, isCurrent))) return
    const published = await adminClassesApi.publish(id)
    if (!isCurrent()) return
    resource.replace(published)
    dialog.value = null
    showToast(t('adminClasses.publish.done', { title: published.title }))
  })
}

async function archive(): Promise<void> {
  await run(t('adminClasses.archive.error'), async (id, isCurrent) => {
    if (!(await saveDirtyDetails(id, isCurrent))) return
    const archived = await adminClassesApi.archive(id)
    if (!isCurrent()) return
    resource.replace(archived)
    fillDetails(archived)
    dialog.value = null
    showToast(t('adminClasses.archive.done'))
  })
}

/**
 * Restoring is undone by archiving again, so it runs at once (X1.14). The class comes back published; one archived
 * as a draft before classes were authored here comes back a draft, and the toast says which.
 */
async function restore(): Promise<void> {
  await run(t('adminClasses.page.restoreError'), async (id, isCurrent) => {
    const restored = await adminClassesApi.restore(id)
    if (!isCurrent()) return
    resource.replace(restored)
    const asDraft = restored.status === 'DRAFT'
    showToast(t(asDraft ? 'adminClasses.page.restoredDraft' : 'adminClasses.page.restored', { title: restored.title }))
  })
}

/**
 * What deleting the draft takes with it, counted (A3.09): "The draft class, its unit and 2 draft lessons are removed."
 * Its lessons are called drafts while none of them is published.
 */
const deleteMessage = computed(() => {
  const units = page.value?.units ?? []
  const lessons = units.flatMap((unit) => unit.lessons)
  if (!units.length) return t('adminClasses.remove.message')
  const unitsText = t('adminClasses.remove.units', units.length)
  if (!lessons.length) return t('adminClasses.remove.messageUnits', { units: unitsText })
  const allDrafts = lessons.every((lesson) => lesson.status === 'DRAFT')
  return t('adminClasses.remove.messageUnitsLessons', {
    units: unitsText,
    lessons: t(allDrafts ? 'adminClasses.remove.draftLessons' : 'adminClasses.page.lessons', lessons.length),
  })
})

async function remove(): Promise<void> {
  await run(t('adminClasses.remove.error'), async (id, isCurrent) => {
    const title = page.value?.title ?? ''
    await adminClassesApi.remove(id)
    if (!isCurrent()) return
    try {
      await leaveTo({ name: 'admin-classes', query: { deleted: title } })
    } catch (reason) {
      if (isCurrent()) {
        dialog.value = null
        actionError.value = resolveApiError(reason)
      }
    }
  })
}
</script>
