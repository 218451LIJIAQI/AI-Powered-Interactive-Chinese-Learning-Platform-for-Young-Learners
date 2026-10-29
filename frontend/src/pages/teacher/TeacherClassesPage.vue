<template>
  <div class="teacher-classes-page page-stack">
    <PageHeaderBlock :title="t('teacherClasses.list.title')" :description="summary">
      <template #actions>
        <button type="button" class="btn btn-primary" data-testid="new-class" @click="openNew">
          <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.list.newClass') }}
        </button>
      </template>
    </PageHeaderBlock>
    <DataNotice :success="deletedNotice" />

    <ResourceSection
      class="page-stack"
      :loading="resource.loading.value"
      :error="resource.error.value"
      @retry="resource.reload"
    >
      <StatePanel
        v-if="!classes.length"
        tone="empty"
        icon="classes"
        spacious
        :title="t('teacherClasses.list.emptyTitle')"
        :message="t('teacherClasses.list.emptyMessage')"
      >
        <template #actions>
          <button type="button" class="btn btn-primary" @click="openNew">
            <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.list.newClass') }}
          </button>
        </template>
      </StatePanel>
      <ul v-else class="panel teacher-classes-list">
        <li
          v-for="item in classes"
          :key="item.id"
          class="teacher-classes-row"
          :data-testid="`teacher-class-${item.id}`"
        >
          <div class="teacher-classes-name">
            <h2 class="teacher-classes-title">{{ item.title }}</h2>
            <p class="type-caption">{{ metaOf(item) }}</p>
          </div>
          <div class="teacher-classes-status">
            <ClassStatusPill :status="item.status" :revision-status="item.revisionStatus" />
          </div>
          <div class="teacher-classes-lessons">
            <span v-if="item.lessonCount" class="teacher-stack-bar" aria-hidden="true">
              <span
                v-for="part in barOf(item)"
                :key="part.key"
                :class="`is-${part.key}`"
                :style="{ flexGrow: part.count }"
              />
            </span>
            <span class="type-caption teacher-classes-pipeline">
              <template v-for="(part, index) in pipelineOf(item)" :key="part.text">
                <span :class="{ 'is-warning': part.warn }">{{ part.text }}</span>
                <template v-if="index < pipelineOf(item).length - 1"> · </template>
              </template>
            </span>
          </div>
          <div class="teacher-classes-people">
            <span>{{ t('teacherClasses.list.students', item.studentCount) }}</span>
            <span class="type-caption">{{
              t('teacherClasses.list.updated', { date: formatShortDate(item.updatedAt) })
            }}</span>
          </div>
          <RouterLink
            class="btn btn-link teacher-classes-open"
            :to="{ name: 'teacher-class', params: { classId: item.id } }"
            :aria-label="t('teacherClasses.list.openLabel', { title: item.title })"
          >
            {{ t('teacherClasses.list.open') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </li>
      </ul>
    </ResourceSection>

    <AppOverlay
      variant="drawer"
      :open="newOpen"
      :title="t('teacherClasses.list.newClass')"
      :subtitle="t('teacherClasses.form.drawerSubtitle')"
      :busy="creating"
      test-id="new-class-drawer"
      @close="newOpen = false"
    >
      <form :id="formId" @submit.prevent="create">
        <p class="type-caption">{{ t('teacherClasses.form.required') }}</p>
        <ClassDetailsFields v-model="form" :levels="levels.data.value ?? []" :topics="topics.data.value ?? []" />
        <p v-if="createError" class="field-error" role="alert">{{ createError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn btn-ghost" :disabled="creating" @click="newOpen = false">
          {{ t('classes.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :form="formId"
          :disabled="creating || !form.title.trim()"
          :aria-busy="creating"
          data-testid="create-class"
        >
          {{ creating ? t('teacherClasses.form.creating') : t('teacherClasses.form.create') }}
        </button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { publicClassesApi, teacherClassesApi } from '@/api/classes'
import ClassDetailsFields, { type ClassDetailsForm } from '@/components/classes/ClassDetailsFields.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import ClassStatusPill from '@/components/classes/ClassStatusPill.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { ClassListItem } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { LEARNING_TYPE_KEYS } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()

const resource = useResource(() => teacherClassesApi.classes())
const classes = computed(() => resource.data.value ?? [])

/** "4 classes · 2 published · 1 in review · 1 draft": the whole list at a glance. */
const summary = computed(() => {
  const list = resource.data.value
  if (!list?.length) return ''
  const count = (status: ClassListItem['status']) => list.filter((item) => item.status === status).length
  return [
    t('teacherClasses.list.count', list.length),
    t('teacherClasses.list.countPublished', { count: count('PUBLISHED') }),
    t('teacherClasses.list.countReview', { count: count('SUBMITTED') }),
    t('teacherClasses.list.countDraft', { count: count('DRAFT') + count('REJECTED') }),
  ].join(' · ')
})

function metaOf(item: ClassListItem): string {
  return [
    item.levelName,
    item.topicName,
    item.learningType ? t(LEARNING_TYPE_KEYS[item.learningType]) : null,
    t('classes.units', item.unitCount),
  ]
    .filter(Boolean)
    .join(' · ')
}

/** Where the class's lessons are in review, in words; only the stages that have lessons are named. */
function pipelineOf(item: ClassListItem): { text: string; warn: boolean }[] {
  if (!item.lessonCount) return [{ text: t('teacherClasses.list.noLessons'), warn: false }]
  const review = item.lessonsInReview + item.revisionsInReview
  const changes = item.lessonsWithChangesRequested + item.revisionsWithChangesRequested
  const parts = [
    {
      text: t('teacherClasses.list.lessonsOf', { published: item.publishedLessons, total: item.lessonCount }),
      warn: false,
    },
    { text: review ? t('teacherClasses.list.lessonsReview', { count: review }) : '', warn: false },
    { text: changes ? t('teacherClasses.list.lessonsChanges', { count: changes }, changes) : '', warn: true },
    { text: item.draftLessons ? t('teacherClasses.list.lessonsDraft', { count: item.draftLessons }) : '', warn: false },
  ]
  return parts.filter((part) => part.text)
}

/** The lessons by stage for the bar: live, in review, changes requested, draft. */
function barOf(item: ClassListItem): { key: string; count: number }[] {
  return [
    { key: 'published', count: item.publishedLessons },
    { key: 'review', count: item.lessonsInReview },
    { key: 'changes', count: item.lessonsWithChangesRequested },
    { key: 'draft', count: item.draftLessons },
  ].filter((part) => part.count > 0)
}
const levels = useResource(() => publicClassesApi.levels(), false)
const topics = useResource(() => publicClassesApi.topics(), false)

/** After a draft class is deleted, its class page sends the teacher back here with its title. */
const deletedNotice = computed(() =>
  typeof route.query.deleted === 'string' ? t('teacherClasses.list.deleted', { title: route.query.deleted }) : null,
)

const formId = useId()
const newOpen = ref(false)
const creating = ref(false)
const createError = ref<string | null>(null)
const form = ref<ClassDetailsForm>(emptyForm())

function emptyForm(): ClassDetailsForm {
  return { title: '', description: '', levelId: null, topicId: null, learningType: 'HYBRID' }
}

function openNew(): void {
  form.value = emptyForm()
  createError.value = null
  newOpen.value = true
  if (!levels.data.value) void levels.reload()
  if (!topics.data.value) void topics.reload()
}

async function create(): Promise<void> {
  if (!form.value.title.trim()) return
  creating.value = true
  createError.value = null
  try {
    const created = await teacherClassesApi.create({
      title: form.value.title.trim(),
      description: form.value.description.trim() || null,
      levelId: form.value.levelId,
      topicId: form.value.topicId,
      learningType: form.value.learningType,
    })
    newOpen.value = false
    await router.push({ name: 'teacher-class', params: { classId: created.id }, query: { created: '1' } })
  } catch (reason) {
    createError.value = resolveApiError(reason, t('teacherClasses.form.createError'))
  } finally {
    creating.value = false
  }
}
</script>
