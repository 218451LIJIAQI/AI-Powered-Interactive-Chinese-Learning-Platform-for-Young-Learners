<template>
  <div class="page-stack student-practice-submissions-page" data-testid="practice-submissions-page">
    <PageHeaderBlock
      :title="t('practiceSubmissions.title')"
      :description="t('practiceSubmissions.description')"
      icon="mic"
    >
      <template #actions>
        <RouterLink class="btn btn-secondary" to="/student/ai-helper">{{
          t('practiceSubmissions.openAiHelper')
        }}</RouterLink>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="previewMode"
      :success="actionSuccess"
      :fallback-title="t('practiceSubmissions.previewTitle')"
      :fallback-message="t('practiceSubmissions.previewMessage')"
      :success-title="t('practiceSubmissions.successTitle')"
    />

    <AiDisclaimer class="practice-submissions-disclaimer" />

    <section class="practice-submissions-forms">
      <PronunciationSubmissionForm
        :lessons="lessonOptions"
        :language-preference="languagePreference"
        :disabled="previewMode"
        :disabled-message="t('practiceSubmissions.previewUploadDisabled')"
        :initial-lesson-id="initialLessonId"
        @submitted="onSubmitted"
      />
      <HandwritingSubmissionForm
        :lessons="lessonOptions"
        :language-preference="languagePreference"
        :disabled="previewMode"
        :disabled-message="t('practiceSubmissions.previewUploadDisabled')"
        :initial-lesson-id="initialLessonId"
        @submitted="onSubmitted"
      />
    </section>

    <div ref="resultAnchor">
      <PracticeFeedbackResult v-if="lastResult" :result="lastResult" />
    </div>

    <section class="practice-submissions-list-section" :aria-labelledby="listTitleId">
      <div class="section-header">
        <div>
          <h2 :id="listTitleId" class="section-title">{{ t('practiceSubmissions.listTitle') }}</h2>
        </div>
        <div class="section-header-actions">
          <span class="tag" aria-live="polite">{{ t('practiceSubmissions.totalCount', totalItems) }}</span>
          <button class="btn btn-secondary btn-sm" type="button" :disabled="listResource.loading.value" @click="reload">
            <IconGlyph name="refresh" :size="16" />
            {{ t('practiceSubmissions.refresh') }}
          </button>
        </div>
      </div>

      <div class="tabs" role="group" :aria-label="t('practiceSubmissions.filterLabel')">
        <button
          v-for="option in filterOptions"
          :key="option.key"
          class="tab-button"
          :class="{ 'is-active': typeFilter === option.value }"
          type="button"
          :aria-pressed="typeFilter === option.value"
          :data-testid="`submission-filter-${option.key}`"
          @click="selectFilter(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <ResourceSection :loading="listResource.loading.value" :error="listResource.error.value" @retry="reload">
        <ul v-if="items.length" class="practice-submissions-list" data-testid="submission-list">
          <PracticeSubmissionItem v-for="item in items" :key="item.id" :item="item" :preview-mode="previewMode" />
        </ul>
        <StatePanel
          v-else
          :icon="typeFilter ? 'filter' : 'mic'"
          :title="typeFilter ? t('practiceSubmissions.emptyFilteredTitle') : t('practiceSubmissions.emptyTitle')"
          :message="typeFilter ? t('practiceSubmissions.emptyFilteredMessage') : t('practiceSubmissions.emptyMessage')"
          compact
        >
          <template v-if="typeFilter" #actions>
            <button class="btn btn-secondary btn-sm" type="button" @click="selectFilter(null)">
              {{ t('practiceSubmissions.filterAll') }}
            </button>
          </template>
        </StatePanel>

        <PaginationBar
          v-if="totalPages > 1"
          :label="t('practiceSubmissions.pagination')"
          :info="t('practiceSubmissions.pageOf', { page: page + 1, total: totalPages })"
          :previous-label="t('practiceSubmissions.previous')"
          :next-label="t('practiceSubmissions.next')"
          :previous-disabled="page === 0"
          :next-disabled="page >= totalPages - 1"
          @previous="goToPage(page - 1)"
          @next="goToPage(page + 1)"
        />
      </ResourceSection>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { studentApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import HandwritingSubmissionForm from '@/components/submissions/HandwritingSubmissionForm.vue'
import PracticeFeedbackResult from '@/components/submissions/PracticeFeedbackResult.vue'
import PracticeSubmissionItem from '@/components/submissions/PracticeSubmissionItem.vue'
import { usePracticeSubmissionText } from '@/components/submissions/practiceSubmissionText'
import PronunciationSubmissionForm from '@/components/submissions/PronunciationSubmissionForm.vue'
import { appEnv } from '@/config/env'
import { useResource } from '@/composables/useResource'
import { studentPracticeSubmissions, studentProgress } from '@/demo/data'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { PageResponse, PracticeSubmissionResponse, PracticeSubmissionType } from '@/types/api'
import {
  pagePracticeSubmissions,
  parseLessonId,
  type PracticeLessonOption,
  type PracticeUploadResult,
} from '@/utils/practiceUpload'

const PAGE_SIZE = 10

const { t } = usePracticeSubmissionText()
const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const listTitleId = `${useId()}-list`

/** D18: AI feedback uses the learner's current interface language. */
const languagePreference = computed(() => ui.locale)

const typeFilter = ref<PracticeSubmissionType | null>(null)
const page = ref(0)
const lastResult = ref<PracticeUploadResult | null>(null)
const actionSuccess = ref<string | null>(null)
const resultAnchor = ref<HTMLElement | null>(null)

const listResource = useResource<PageResponse<PracticeSubmissionResponse>>(
  () => studentApi.practiceSubmissions({ type: typeFilter.value ?? undefined, page: page.value, size: PAGE_SIZE }),
  () => pagePracticeSubmissions(studentPracticeSubmissions, typeFilter.value, page.value, PAGE_SIZE),
)
const progressResource = useResource(() => studentApi.progress(), studentProgress)

/** Preview: fixtures only, uploads disabled and protected files are not loaded (nothing is saved). */
const previewMode = computed(() => listResource.usingFallback.value || (appEnv.useDemoData && auth.isPreviewSession))

const items = computed(() => listResource.data.value?.items ?? [])
const totalItems = computed(() => listResource.data.value?.totalItems ?? items.value.length)
const totalPages = computed(() => Math.max(1, listResource.data.value?.totalPages ?? 1))

const lessonOptions = computed<PracticeLessonOption[]>(() => {
  const summary = progressResource.data.value
  const source = summary?.lessons?.length ? summary.lessons : (summary?.recentLessons ?? [])
  const seen = new Set<number>()
  const options: PracticeLessonOption[] = []
  for (const lesson of source) {
    if (!lesson || lesson.lessonId <= 0 || seen.has(lesson.lessonId)) continue
    seen.add(lesson.lessonId)
    options.push({ lessonId: lesson.lessonId, lessonTitle: lesson.lessonTitle })
  }
  return options
})

const initialLessonId = computed(() => {
  const raw = Array.isArray(route.query.lessonId) ? route.query.lessonId[0] : route.query.lessonId
  const id = parseLessonId(raw ?? null)
  return id ? String(id) : ''
})

const filterOptions = computed(() => [
  { key: 'all', value: null, label: t('practiceSubmissions.filterAll') },
  { key: 'audio', value: 'PRONUNCIATION_AUDIO' as PracticeSubmissionType, label: t('practiceSubmissions.filterAudio') },
  { key: 'image', value: 'HANDWRITING_IMAGE' as PracticeSubmissionType, label: t('practiceSubmissions.filterImage') },
])

function reload(): Promise<void> {
  return listResource.reload()
}

function selectFilter(value: PracticeSubmissionType | null): void {
  if (typeFilter.value === value) return
  typeFilter.value = value
  page.value = 0
  void reload()
}

function goToPage(next: number): void {
  page.value = Math.min(Math.max(0, next), totalPages.value - 1)
  void reload()
}

async function onSubmitted(result: PracticeUploadResult): Promise<void> {
  lastResult.value = result
  actionSuccess.value =
    result.kind === 'audio' ? t('practiceSubmissions.successAudio') : t('practiceSubmissions.successImage')
  page.value = 0
  await nextTick()
  resultAnchor.value?.querySelector<HTMLElement>('[data-testid="submission-result"]')?.focus()
  await reload()
}
</script>

<style scoped>
.practice-submissions-disclaimer {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.practice-submissions-forms {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.practice-submissions-list-section {
  display: grid;
  gap: var(--section-gap);
  min-width: 0;
}

.practice-submissions-list {
  display: grid;
  gap: var(--grid-gap);
  margin: 0;
  padding: 0;
  list-style: none;
}

@media (max-width: 1200px) {
  .practice-submissions-forms {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
