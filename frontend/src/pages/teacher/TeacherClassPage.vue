<template>
  <div class="teacher-class-page page-stack">
    <template v-if="page">
      <PageHeaderBlock :title="page.title" :description="facts">
        <template #before>
          <AppBreadcrumb
            :items="[{ label: t('teacherClasses.list.title'), to: { name: 'teacher-classes' } }, { label: page.title }]"
          />
        </template>
        <template #meta>
          <ClassStatusPill :status="page.status" :revision-status="page.revision?.status ?? null" />
          <span class="type-caption">{{ t('teacherClasses.list.students', page.studentCount) }}</span>
        </template>
        <template #actions>
          <button v-if="page.canDelete" type="button" class="btn btn-danger-soft" @click="dialog = 'delete'">
            <IconGlyph name="trash" :size="16" />{{ t('teacherClasses.page.delete') }}
          </button>
          <button v-if="page.status === 'ARCHIVED'" type="button" class="btn btn-secondary" @click="dialog = 'restore'">
            <IconGlyph name="refresh" :size="16" />{{ t('teacherClasses.page.restore') }}
          </button>
          <button
            v-else-if="page.status !== 'SUBMITTED'"
            type="button"
            class="btn btn-ghost"
            data-testid="archive-class"
            @click="dialog = 'archive'"
          >
            <IconGlyph name="archive" :size="16" />{{ t('teacherClasses.page.archive') }}
          </button>
        </template>
      </PageHeaderBlock>

      <DataNotice :error="actionError" :success="actionSuccess" />
      <p
        v-if="page.status === 'REJECTED' && page.rejectionReason"
        class="callout is-compact is-warning teacher-class-state"
        role="note"
      >
        <IconGlyph name="alert" :size="16" />
        <span>{{ t('teacherClasses.page.changesRequestedNoName', { reason: page.rejectionReason }) }}</span>
      </p>
      <p v-else-if="statusNotice" class="callout is-compact teacher-class-state" role="note">
        <IconGlyph :name="statusIcon" :size="16" />
        <span>{{ statusNotice }}</span>
      </p>

      <RouteTabs :tabs="tabs" :label="t('teacherClasses.page.tabsLabel')" />

      <TeacherClassLessons
        v-if="tab === 'lessons'"
        :class-id="page.id"
        :units="activeUnits"
        :editable="structureEditable"
        @updated="resource.data.value = $event"
        @notice="showSuccess"
        @error="showError"
      />

      <div v-else-if="tab === 'details'" class="teacher-class-details">
        <section class="panel teacher-class-form" aria-labelledby="teacher-class-details-title">
          <div>
            <h2 id="teacher-class-details-title" class="card-title">{{ t('teacherClasses.details.title') }}</h2>
            <p class="card-subtitle">
              {{
                page.status === 'PUBLISHED'
                  ? t('teacherClasses.details.publishedHint')
                  : t('teacherClasses.details.draftHint')
              }}
            </p>
          </div>
          <p v-if="page.revision?.status === 'DRAFT'" class="callout is-compact is-warning">
            <IconGlyph name="edit" :size="16" /><span>{{ t('teacherClasses.details.revisionDraft') }}</span>
          </p>
          <p v-else-if="page.revision?.status === 'SUBMITTED'" class="callout is-compact">
            <IconGlyph name="history" :size="16" /><span>{{ t('teacherClasses.details.revisionSubmitted') }}</span>
          </p>
          <p v-else-if="page.revision?.status === 'REJECTED'" class="callout is-compact is-danger">
            <IconGlyph name="alert" :size="16" />
            <span>{{
              t('teacherClasses.details.revisionRejected', { reason: page.revision.rejectionReason ?? '' })
            }}</span>
          </p>
          <p v-if="!detailsEditable" class="callout is-compact">
            <IconGlyph name="lock" :size="16" /><span>{{ t('teacherClasses.details.readOnly') }}</span>
          </p>
          <form :id="formId" @submit.prevent="saveDetails">
            <ClassDetailsFields
              v-model="form"
              :levels="levels.data.value ?? []"
              :topics="topics.data.value ?? []"
              :disabled="!detailsEditable"
            />
          </form>
          <div v-if="detailsEditable" class="card-footer">
            <button
              v-if="page.revision && page.revision.status !== 'SUBMITTED'"
              type="button"
              class="btn btn-ghost"
              :disabled="saving"
              @click="discardDetails"
            >
              {{ t('teacherClasses.details.discard') }}
            </button>
            <button type="submit" class="btn btn-secondary" :form="formId" :disabled="saving || !form.title.trim()">
              {{
                page.status === 'PUBLISHED' ? t('teacherClasses.details.saveDraft') : t('teacherClasses.details.save')
              }}
            </button>
            <button
              v-if="page.status === 'PUBLISHED'"
              type="button"
              class="btn btn-primary"
              :disabled="saving || !form.title.trim()"
              :aria-busy="saving"
              data-testid="submit-details"
              @click="submitDetails"
            >
              <IconGlyph name="send" :size="16" />{{ t('teacherClasses.details.submit') }}
            </button>
          </div>
        </section>

        <section class="teacher-class-note" aria-labelledby="teacher-class-note-title">
          <div class="teacher-class-note-head">
            <h2 id="teacher-class-note-title" class="card-title">{{ t('teacherClasses.note.title') }}</h2>
            <p class="card-subtitle">{{ t('teacherClasses.note.hint') }}</p>
          </div>
          <div class="field">
            <label class="field-label" :for="`${formId}-note`">{{ t('teacherClasses.note.label') }}</label>
            <textarea
              :id="`${formId}-note`"
              v-model="note"
              class="textarea"
              rows="4"
              maxlength="500"
              :placeholder="t('teacherClasses.note.placeholder')"
            />
            <p class="field-hint field-counter">{{ note.length }} / 500</p>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-secondary" :disabled="saving" @click="saveNote">
              {{ t('teacherClasses.note.save') }}
            </button>
          </div>
        </section>
      </div>

      <section v-else class="teacher-class-history" aria-labelledby="teacher-class-history-title">
        <h2 id="teacher-class-history-title" class="sr-only">{{ t('teacherClasses.page.tabHistory') }}</h2>
        <StatePanel
          v-if="history.loading.value"
          tone="loading"
          :title="t('classes.loading')"
          :message="t('teacherClasses.page.tabHistory')"
        />
        <StatePanel
          v-else-if="history.error.value"
          tone="error"
          icon="alert"
          :title="t('classes.loadError')"
          :message="history.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="history.reload">{{ t('classes.retry') }}</button>
          </template>
        </StatePanel>
        <div v-else class="panel"><ContentHistory :events="history.data.value ?? []" /></div>
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
            t('teacherClasses.page.backToClasses')
          }}</RouterLink>
        </template>
      </StatePanel>
    </template>

    <ConfirmDialog
      :open="dialog === 'archive'"
      :title="t('teacherClasses.archive.title')"
      :message="t('teacherClasses.archive.message', { title: page?.title ?? '' })"
      :confirm-label="t('teacherClasses.archive.confirm')"
      tone="danger"
      :busy="saving"
      test-id="archive-class-dialog"
      @cancel="dialog = null"
      @confirm="archive"
    />
    <ConfirmDialog
      :open="dialog === 'restore'"
      :title="t('teacherClasses.archive.restoreTitle')"
      :message="t('teacherClasses.archive.restoreMessage', { title: page?.title ?? '' })"
      :confirm-label="t('teacherClasses.archive.restoreConfirm')"
      :busy="saving"
      @cancel="dialog = null"
      @confirm="restore"
    />
    <ConfirmDialog
      :open="dialog === 'delete'"
      :title="t('teacherClasses.archive.deleteTitle')"
      :message="t('teacherClasses.archive.deleteMessage', { title: page?.title ?? '' })"
      :confirm-label="t('teacherClasses.archive.deleteConfirm')"
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
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import ClassDetailsFields, { type ClassDetailsForm } from '@/components/classes/ClassDetailsFields.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ClassStatusPill from '@/components/classes/ClassStatusPill.vue'
import ContentHistory from '@/components/classes/ContentHistory.vue'
import TeacherClassLessons from '@/components/classes/TeacherClassLessons.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { TeacherClassPage } from '@/types/classes'
import { LEARNING_TYPE_KEYS } from '@/utils/classes'
import '@/styles/classes.css'

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()
const { actionError, actionSuccess, runAction, showError, showSuccess } = useActionFeedback()

const classId = computed(() => Number(route.params.classId))
const tab = computed(() =>
  route.params.tab === 'details' || route.params.tab === 'history' ? route.params.tab : 'lessons',
)

const resource = useResource(() => teacherClassesApi.classPage(classId.value))
const page = computed(() => resource.data.value)
const history = useResource(() => teacherClassesApi.history(classId.value), false)
const levels = useResource(() => publicClassesApi.levels(), false)
const topics = useResource(() => publicClassesApi.topics(), false)

watch(classId, () => void resource.reload())
watch(
  tab,
  (current) => {
    if (current === 'history') void history.reload()
    if (current === 'details') {
      if (!levels.data.value) void levels.reload()
      if (!topics.data.value) void topics.reload()
    }
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

const facts = computed(() => {
  const current = page.value
  if (!current) return ''
  return [
    current.levelName,
    current.topicName,
    current.learningType ? t(LEARNING_TYPE_KEYS[current.learningType]) : null,
    t('classes.units', activeUnits.value.length),
    t('classes.lessons', lessonCount.value),
  ]
    .filter(Boolean)
    .join(' · ')
})

const statusIcon = computed(() =>
  page.value?.status === 'ARCHIVED' ? 'archive' : page.value?.status === 'SUBMITTED' ? 'history' : 'info',
)

const statusNotice = computed(() => {
  if (route.query.created) return null
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
    count: lessonCount.value,
  },
  {
    label: t('teacherClasses.page.tabDetails'),
    to: { name: 'teacher-class', params: { classId: classId.value, tab: 'details' } },
    icon: 'file',
  },
  {
    label: t('teacherClasses.page.tabHistory'),
    to: { name: 'teacher-class', params: { classId: classId.value, tab: 'history' } },
    icon: 'history',
  },
])

// ----- details -----

const formId = useId()
const form = ref<ClassDetailsForm>({ title: '', description: '', levelId: null, topicId: null, learningType: 'HYBRID' })
const note = ref('')
const saving = ref(false)

/** The form shows the pending details of a published class when there are some. */
function fillForm(current: TeacherClassPage): void {
  const source = current.revision ?? current
  form.value = {
    title: source.title,
    description: source.description ?? '',
    levelId: source.levelId,
    topicId: source.topicId,
    learningType: source.learningType ?? 'HYBRID',
  }
  note.value = current.teacherNote ?? ''
}

watch(page, (current) => current && fillForm(current), { immediate: true })
watch(page, (current, previous) => {
  // A class just created from New class says so once, with what to do next.
  if (current && !previous && route.query.created)
    showSuccess(t('teacherClasses.form.created', { title: current.title }))
})

function detailsPayload() {
  return {
    title: form.value.title.trim(),
    description: form.value.description.trim() || null,
    levelId: form.value.levelId,
    topicId: form.value.topicId,
    learningType: form.value.learningType,
  }
}

async function run(fallback: string, action: () => Promise<string>): Promise<boolean> {
  saving.value = true
  const done = await runAction(fallback, action)
  saving.value = false
  return done
}

async function saveDetails(): Promise<void> {
  const published = page.value?.status === 'PUBLISHED'
  await run(t('teacherClasses.details.error'), async () => {
    resource.data.value = await teacherClassesApi.saveDetails(classId.value, detailsPayload())
    return published ? t('teacherClasses.details.savedDraft') : t('teacherClasses.details.saved')
  })
}

async function submitDetails(): Promise<void> {
  await run(t('teacherClasses.details.error'), async () => {
    await teacherClassesApi.saveDetails(classId.value, detailsPayload())
    resource.data.value = await teacherClassesApi.submitDetails(classId.value)
    return t('teacherClasses.details.submitted')
  })
}

async function discardDetails(): Promise<void> {
  await run(t('teacherClasses.details.error'), async () => {
    resource.data.value = await teacherClassesApi.discardDetails(classId.value)
    return t('teacherClasses.details.discarded')
  })
}

async function saveNote(): Promise<void> {
  await run(t('teacherClasses.note.error'), async () => {
    resource.data.value = await teacherClassesApi.saveNote(classId.value, note.value.trim())
    return t('teacherClasses.note.saved')
  })
}

// ----- archive, restore, delete -----

const dialog = ref<'archive' | 'restore' | 'delete' | null>(null)

async function archive(): Promise<void> {
  const title = page.value?.title ?? ''
  await run(t('teacherClasses.archive.error'), async () => {
    resource.data.value = await teacherClassesApi.archive(classId.value)
    return t('teacherClasses.archive.done', { title })
  })
  dialog.value = null
}

async function restore(): Promise<void> {
  const title = page.value?.title ?? ''
  await run(t('teacherClasses.archive.restoreError'), async () => {
    resource.data.value = await teacherClassesApi.restore(classId.value)
    return t('teacherClasses.archive.restored', { title })
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
