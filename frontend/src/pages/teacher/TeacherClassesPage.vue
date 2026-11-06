<template>
  <div class="page-stack teacher-classes">
    <PageHeaderBlock :title="t('teacherClasses.list.title')" hide-title>
      <template v-if="classes.length" #actions>
        <button type="button" class="btn btn-primary" data-testid="new-class" @click="openNew">
          <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.list.newClass') }}
        </button>
      </template>
    </PageHeaderBlock>
    <AppToast :message="toast" />

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="resource.reload">
      <!-- Nothing yet: the page is one empty card with the first step. -->
      <section v-if="!classes.length" class="panel teacher-empty-page">
        <StatePanel
          tone="empty"
          icon="classes"
          spacious
          :title="t('teacherClasses.list.emptyTitle')"
          :message="t('teacherClasses.list.emptyMessage')"
        >
          <template #actions>
            <button type="button" class="btn btn-primary" data-testid="new-class" @click="openNew">
              <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.list.newClass') }}
            </button>
          </template>
        </StatePanel>
      </section>

      <section v-else class="panel teacher-card" aria-labelledby="teacher-classes-title">
        <div class="title-with-count">
          <h2 id="teacher-classes-title" class="card-title">{{ t('teacherClasses.list.myClasses') }}</h2>
          <span class="count-badge">{{ classes.length }}</span>
        </div>
        <ul class="row-list teacher-class-rows">
          <li v-for="item in classes" :key="item.id" :data-testid="`teacher-class-${item.id}`">
            <div class="row-list-copy teacher-class-copy">
              <h3 class="row-list-title">{{ item.title }}</h3>
              <div v-if="item.topicName || item.bestAfterUnit" class="teacher-class-tags">
                <span v-if="item.topicName" class="tag">{{ item.topicName }}</span>
                <span v-if="item.bestAfterUnit" class="row-list-meta">
                  {{ t('teacherClasses.list.bestAfter', { unit: item.bestAfterUnit }) }}
                </span>
              </div>
              <p class="row-list-meta">{{ metaOf(item) }}</p>
            </div>
            <div class="row-list-actions">
              <TeacherStatusPill :status="item.status" :revision-status="item.revisionStatus" />
              <RouterLink
                class="btn btn-secondary btn-sm"
                :to="{ name: 'teacher-class', params: { classId: item.id } }"
                :aria-label="t('teacherClasses.list.openLabel', { title: item.title })"
              >
                {{ t('teacherClasses.list.open') }}<IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </div>
          </li>
        </ul>
      </section>
    </ResourceSection>

    <AppOverlay
      :open="newOpen"
      :title="t('teacherClasses.list.newClass')"
      :subtitle="t('teacherClasses.form.subtitle')"
      :busy="creating"
      test-id="new-class-drawer"
      @close="newOpen = false"
    >
      <form :id="formId" class="form-grid" @submit.prevent="create">
        <ClassDetailsFields v-model="form" :themes="themes.data.value ?? []" />
        <p v-if="createError" class="field-error" role="alert">{{ createError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn btn-secondary" :disabled="creating" @click="newOpen = false">
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :form="formId"
          :disabled="creating || !form.title.trim()"
          :aria-busy="creating"
          data-testid="create-class"
        >
          <IconGlyph name="plus" :size="16" />
          {{ creating ? t('teacherClasses.form.creating') : t('teacherClasses.form.create') }}
        </button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, useId } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { publicClassesApi, teacherClassesApi } from '@/api/classes'
import ClassDetailsFields, { type ClassDetailsForm } from '@/components/classes/ClassDetailsFields.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherStatusPill from '@/components/teacher/TeacherStatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { commonMessages } from '@/plugins/commonMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { ClassListItem } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import '@/styles/classes-teacher.css'

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, common: commonMessages })
const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()

const resource = useResource(() => teacherClassesApi.classes())
const classes = computed(() => resource.data.value ?? [])
const themes = useResource(() => publicClassesApi.topics(), false)

/** "5 lessons · 1 in review · 1 with changes requested"; a class with every lesson live says so with its units. */
function metaOf(item: ClassListItem): string {
  if (!item.lessonCount) return t('teacherClasses.list.noLessons')
  const review = item.lessonsInReview + item.revisionsInReview
  const changes = item.lessonsWithChangesRequested + item.revisionsWithChangesRequested
  const parts = [t('teacherClasses.list.lessons', item.lessonCount)]
  if (review) parts.push(t('teacherClasses.list.inReview', { count: review }))
  if (changes) parts.push(t('teacherClasses.list.changes', { count: changes }))
  if (item.draftLessons) parts.push(t('teacherClasses.list.draft', { count: item.draftLessons }))
  if (parts.length === 1) {
    parts.push(t('teacherClasses.list.units', item.unitCount), t('teacherClasses.list.allPublished'))
  }
  return parts.join(' · ')
}

/** After a draft class is deleted, its class page sends the teacher back here with its title. */
onMounted(() => {
  if (typeof route.query.deleted !== 'string') return
  showToast(t('teacherClasses.list.deleted', { title: route.query.deleted }))
  void router.replace({ query: {} })
})

const formId = useId()
const newOpen = ref(false)
const creating = ref(false)
const createError = ref<string | null>(null)
const form = ref<ClassDetailsForm>(emptyForm())

function emptyForm(): ClassDetailsForm {
  return { title: '', description: '', topicId: null, bestAfterUnit: null }
}

function openNew(): void {
  form.value = emptyForm()
  createError.value = null
  newOpen.value = true
  if (!themes.data.value) void themes.reload()
}

async function create(): Promise<void> {
  if (!form.value.title.trim()) return
  creating.value = true
  createError.value = null
  try {
    // Level and learning type are left out: the server gives a new class its defaults.
    const created = await teacherClassesApi.create({
      title: form.value.title.trim(),
      description: form.value.description.trim() || null,
      topicId: form.value.topicId,
      bestAfterUnit: form.value.bestAfterUnit,
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
