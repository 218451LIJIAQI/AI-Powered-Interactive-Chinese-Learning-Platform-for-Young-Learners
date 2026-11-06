<template>
  <div class="page-stack teacher-class-page">
    <AppToast :message="toast" />

    <template v-if="page">
      <TeacherPageHeader :title="page.title" :description="facts">
        <template #before>
          <AppBreadcrumb
            :items="[{ label: t('teacherClasses.list.title'), to: { name: 'teacher-classes' } }, { label: page.title }]"
          />
        </template>
        <template #badge>
          <TeacherStatusPill :status="page.status" :revision-status="page.revision?.status ?? null" />
        </template>
        <template #actions>
          <button v-if="page.canDelete" type="button" class="btn btn-ghost" @click="dialog = 'delete'">
            <IconGlyph name="trash" :size="16" />{{ t('teacherClasses.page.delete') }}
          </button>
          <button v-if="page.status === 'ARCHIVED'" type="button" class="btn btn-secondary" @click="dialog = 'restore'">
            <IconGlyph name="archive" :size="16" />{{ t('teacherClasses.page.restore') }}
          </button>
          <button
            v-else-if="page.status !== 'SUBMITTED' && !page.canDelete"
            type="button"
            class="btn btn-ghost"
            data-testid="archive-class"
            @click="dialog = 'archive'"
          >
            <IconGlyph name="archive" :size="16" />{{ t('teacherClasses.page.archive') }}
          </button>
        </template>
      </TeacherPageHeader>

      <DataNotice :error="actionError" />
      <p v-if="page.status === 'REJECTED' && page.rejectionReason" class="callout" role="note">
        <IconGlyph name="alert" :size="18" />
        <span>{{ t('teacherClasses.page.changesRequested', { reason: page.rejectionReason }) }}</span>
      </p>
      <p v-else-if="statusNotice" class="callout" role="note">
        <IconGlyph :name="page.status === 'ARCHIVED' ? 'archive' : 'info'" :size="18" />
        <span>{{ statusNotice }}</span>
      </p>

      <RouteTabs :tabs="tabs" :label="t('teacherClasses.page.tabsLabel')" />

      <TeacherClassLessons
        v-if="tab === 'lessons'"
        :class-id="page.id"
        :units="activeUnits"
        :editable="structureEditable"
        @updated="resource.data.value = $event"
        @notice="showToast"
      />

      <section v-else class="panel teacher-card" aria-labelledby="teacher-class-details-title">
        <div class="teacher-card-head">
          <h2 id="teacher-class-details-title" class="card-title">{{ t('teacherClasses.details.title') }}</h2>
          <p class="type-caption">
            {{
              page.status === 'PUBLISHED' ? t('teacherClasses.details.publishedHint') : t('teacherClasses.details.hint')
            }}
          </p>
        </div>
        <p v-if="page.revision?.status === 'SUBMITTED'" class="callout" role="note">
          <IconGlyph name="history" :size="18" /><span>{{ t('teacherClasses.details.revisionSubmitted') }}</span>
        </p>
        <p v-else-if="page.revision?.status === 'REJECTED'" class="callout" role="note">
          <IconGlyph name="alert" :size="18" />
          <span>{{
            t('teacherClasses.details.revisionRejected', { reason: page.revision.rejectionReason ?? '' })
          }}</span>
        </p>
        <form :id="formId" @submit.prevent="saveDetails">
          <ClassDetailsFields v-model="form" :themes="themes.data.value ?? []" :disabled="!detailsEditable" />
        </form>
        <div v-if="detailsEditable" class="card-footer">
          <button
            v-if="page.revision"
            type="button"
            class="btn btn-secondary"
            :disabled="saving"
            @click="discardDetails"
          >
            {{ t('teacherClasses.details.discard') }}
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :form="formId"
            :disabled="saving || !form.title.trim()"
            :aria-busy="saving"
            :data-testid="page.status === 'PUBLISHED' ? 'submit-details' : 'save-details'"
          >
            {{ t('teacherClasses.details.save') }}
          </button>
        </div>
      </section>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('teacherClasses.list.title') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('teacherClasses.list.title')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('teacherClasses.page.loadErrorTitle')"
        :message="resource.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'teacher-classes' }">{{
            t('teacherClasses.page.goToClasses')
          }}</RouterLink>
        </template>
      </StatePanel>
    </template>

    <ConfirmDialog
      :open="dialog === 'archive'"
      :title="t('teacherClasses.archive.title', { title: page?.title ?? '' })"
      :message="t('teacherClasses.archive.message')"
      :confirm-label="t('teacherClasses.archive.confirm')"
      :cancel-label="t('teacherClasses.archive.keep')"
      tone="danger"
      :busy="saving"
      test-id="archive-class-dialog"
      @cancel="dialog = null"
      @confirm="archive"
    />
    <ConfirmDialog
      :open="dialog === 'restore'"
      :title="t('teacherClasses.archive.restoreTitle', { title: page?.title ?? '' })"
      :message="t('teacherClasses.archive.restoreMessage')"
      :confirm-label="t('teacherClasses.archive.restoreConfirm')"
      :busy="saving"
      @cancel="dialog = null"
      @confirm="restore"
    />
    <ConfirmDialog
      :open="dialog === 'delete'"
      :title="t('teacherClasses.archive.deleteTitle', { title: page?.title ?? '' })"
      :message="t('teacherClasses.archive.deleteMessage')"
      :confirm-label="t('teacherClasses.archive.deleteConfirm')"
      :cancel-label="t('teacherClasses.archive.keep')"
      tone="danger"
      :busy="saving"
      @cancel="dialog = null"
      @confirm="remove"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { publicClassesApi, teacherClassesApi } from '@/api/classes'
import ClassDetailsFields, { type ClassDetailsForm } from '@/components/classes/ClassDetailsFields.vue'
import TeacherClassLessons from '@/components/classes/TeacherClassLessons.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
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
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { TeacherClassPage } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import '@/styles/classes-teacher.css'

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()

const classId = computed(() => Number(route.params.classId))
const tab = computed(() => (route.params.tab === 'details' ? 'details' : 'lessons'))

const resource = useResource(() => teacherClassesApi.classPage(classId.value))
const page = computed(() => resource.data.value)
const themes = useResource(() => publicClassesApi.topics(), false)

watch(classId, () => void resource.reload())
watch(
  tab,
  (current) => {
    if (current === 'details' && !themes.data.value) void themes.reload()
  },
  { immediate: true },
)

const activeUnits = computed(() => page.value?.units.filter((unit) => !unit.archived) ?? [])
const lessonCount = computed(() => activeUnits.value.reduce((sum, unit) => sum + unit.lessons.length, 0))
const structureEditable = computed(() => page.value?.status !== 'ARCHIVED')
const detailsEditable = computed(
  () =>
    page.value !== null &&
    page.value.status !== 'SUBMITTED' &&
    page.value.status !== 'ARCHIVED' &&
    page.value.revision?.status !== 'SUBMITTED',
)

/** "Study or exchange · best after Unit 3 · 2 units · 5 lessons" */
const facts = computed(() => {
  const current = page.value
  if (!current) return ''
  return [
    current.topicName,
    current.bestAfterUnit ? t('teacherClasses.page.bestAfter', { unit: current.bestAfterUnit }) : null,
    t('teacherClasses.page.units', activeUnits.value.length),
    t('teacherClasses.page.lessons', lessonCount.value),
  ]
    .filter(Boolean)
    .join(' · ')
})

const statusNotice = computed(() => {
  switch (page.value?.status) {
    case 'SUBMITTED':
      return t('teacherClasses.page.inReview')
    case 'ARCHIVED':
      return t('teacherClasses.page.archivedNotice')
    case 'DRAFT':
      return t('teacherClasses.page.draftNotice')
    default:
      return null
  }
})

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('teacherClasses.page.tabLessons'),
    to: { name: 'teacher-class', params: { classId: classId.value } },
    icon: 'checklist',
  },
  {
    label: t('teacherClasses.page.tabDetails'),
    to: { name: 'teacher-class', params: { classId: classId.value, tab: 'details' } },
    icon: 'file',
  },
])

// ----- details -----

const formId = useId()
const form = ref<ClassDetailsForm>({ title: '', description: '', topicId: null, bestAfterUnit: null })
const saving = ref(false)

/** The form shows the pending details of a published class when there are some. */
function fillForm(current: TeacherClassPage): void {
  const source = current.revision ?? current
  form.value = {
    title: source.title,
    description: source.description ?? '',
    topicId: source.topicId,
    bestAfterUnit: source.bestAfterUnit ?? null,
  }
}

watch(page, (current) => current && fillForm(current), { immediate: true })
watch(page, (current, previous) => {
  // A class just created from New class says so once.
  if (current && !previous && route.query.created) showToast(t('teacherClasses.form.created', { title: current.title }))
})

/** Level and learning type are left out: the class keeps the values it has. */
function detailsPayload() {
  return {
    title: form.value.title.trim(),
    description: form.value.description.trim() || null,
    topicId: form.value.topicId,
    bestAfterUnit: form.value.bestAfterUnit,
  }
}

/** Runs one write; a success shows as a toast, a failure as the notice above the tabs. */
async function run(fallback: string, action: () => Promise<string>): Promise<boolean> {
  saving.value = true
  clearFeedback()
  try {
    const message = await action()
    if (message) showToast(message)
    return true
  } catch (reason) {
    showError(resolveApiError(reason, fallback))
    return false
  } finally {
    saving.value = false
  }
}

/** A draft saves directly; a published class saves its changes and sends them for review in one step. */
async function saveDetails(): Promise<void> {
  const published = page.value?.status === 'PUBLISHED'
  await run(t('teacherClasses.details.error'), async () => {
    const saved = await teacherClassesApi.saveDetails(classId.value, detailsPayload())
    resource.data.value = published ? await teacherClassesApi.submitDetails(classId.value) : saved
    return published ? t('teacherClasses.details.submitted') : t('teacherClasses.details.saved')
  })
}

async function discardDetails(): Promise<void> {
  await run(t('teacherClasses.details.error'), async () => {
    resource.data.value = await teacherClassesApi.discardDetails(classId.value)
    return t('teacherClasses.details.discarded')
  })
}

// ----- archive, restore, delete -----

const dialog = ref<'archive' | 'restore' | 'delete' | null>(null)

async function archive(): Promise<void> {
  await run(t('teacherClasses.archive.error'), async () => {
    resource.data.value = await teacherClassesApi.archive(classId.value)
    return t('teacherClasses.archive.done')
  })
  dialog.value = null
}

async function restore(): Promise<void> {
  await run(t('teacherClasses.archive.restoreError'), async () => {
    resource.data.value = await teacherClassesApi.restore(classId.value)
    return t('teacherClasses.archive.restored')
  })
  dialog.value = null
}

async function remove(): Promise<void> {
  const title = page.value?.title ?? ''
  const done = await run(t('teacherClasses.archive.deleteError'), async () => {
    await teacherClassesApi.remove(classId.value)
    return ''
  })
  dialog.value = null
  if (done) await router.push({ name: 'teacher-classes', query: { deleted: title } })
}
</script>
