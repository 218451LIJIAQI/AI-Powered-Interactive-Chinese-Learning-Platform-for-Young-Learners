<template>
  <div class="public-page catalog-page">
    <PageHeaderBlock
      :title="t('catalog.title')"
      :description="t('catalog.intro')"
      :eyebrow="t('catalog.kicker')"
      icon="courses"
    >
      <template v-if="categories.length" #meta>
        <span class="chip">
          <IconGlyph name="learn" :size="14" />
          {{ categories.length }} {{ t('catalog.learningThemes') }}
        </span>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="catalogUsingFallback"
      :error="catalogNoticeError"
      :fallback-title="t('catalog.fallbackTitle')"
      :fallback-message="t('catalog.fallbackMessage')"
      :error-title="t('catalog.errorTitle')"
      :detail="catalogNoticeDetail"
    />

    <div class="catalog-layout">
      <div class="catalog-main">
        <section class="panel catalog-filters">
          <div class="catalog-search-row">
            <div class="field catalog-search-field">
              <label class="field-label" for="catalog-search">{{ t('catalog.searchPlaceholder') }}</label>
              <div class="input-with-icon">
                <IconGlyph class="input-icon" name="search" :size="18" />
                <input
                  id="catalog-search"
                  v-model="filters.keyword"
                  class="input"
                  type="text"
                  maxlength="200"
                  :placeholder="t('catalog.searchPlaceholder')"
                />
              </div>
            </div>
            <div class="catalog-search-actions">
              <button
                class="btn btn-secondary"
                type="button"
                aria-controls="catalog-filter-grid"
                :aria-expanded="filtersOpen"
                @click="toggleFilters"
              >
                <IconGlyph name="filter" :size="16" />
                {{ filterToggleLabel }}
              </button>
              <button class="btn btn-ghost" type="button" @click="clearFilters">{{ t('catalog.clearAll') }}</button>
            </div>
          </div>

          <div v-show="filtersOpen" id="catalog-filter-grid" class="catalog-filter-grid">
            <div class="field">
              <label class="field-label" for="catalog-category">{{ t('catalog.category') }}</label>
              <select id="catalog-category" v-model="filters.categoryId" class="select">
                <option value="">{{ t('catalog.allCategories') }}</option>
                <option v-for="category in categories" :key="category.id" :value="String(category.id)">
                  {{ category.name }}
                </option>
              </select>
            </div>

            <div class="field">
              <label class="field-label" for="catalog-level">{{ t('catalog.level') }}</label>
              <select id="catalog-level" v-model="filters.levelId" class="select">
                <option value="">{{ t('catalog.allLevels') }}</option>
                <option v-for="level in levels" :key="level.id" :value="String(level.id)">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label class="field-label" for="catalog-activity">{{ t('catalog.activityType') }}</label>
              <select id="catalog-activity" v-model="filters.learningType" class="select">
                <option value="">{{ t('catalog.allActivities') }}</option>
                <option value="RECORDED">{{ t('catalog.recorded') }}</option>
                <option value="LIVE">{{ t('catalog.liveSupport') }}</option>
                <option value="HYBRID">{{ t('catalog.hybrid') }}</option>
              </select>
            </div>

            <div class="field">
              <label class="field-label" for="catalog-sort">{{ t('catalog.sortBy') }}</label>
              <select id="catalog-sort" v-model="filters.sort" class="select">
                <option value="newest">{{ t('catalog.recentlyAdded') }}</option>
                <option value="title">{{ t('catalog.sortTitle') }}</option>
              </select>
            </div>
          </div>
        </section>

        <section
          class="catalog-results"
          :aria-labelledby="coursesReady ? 'catalog-results-title' : undefined"
          :aria-busy="coursesInitialLoading ? 'true' : undefined"
        >
          <StatePanel
            v-if="coursesInitialLoading"
            tone="loading"
            icon="progress"
            :title="t('catalog.loadingTitle')"
            :message="t('catalog.loadingMessage')"
            compact
          />

          <StatePanel
            v-else-if="coursesLoadError"
            tone="error"
            icon="alert"
            :title="t('catalog.loadErrorTitle')"
            :message="coursesLoadError"
            compact
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="reloadCatalog">
                {{ t('catalog.retry') }}
              </button>
            </template>
          </StatePanel>

          <h2 v-if="coursesReady" id="catalog-results-title" class="section-title" aria-live="polite">
            {{ matchedCourseCount }} {{ t('catalog.coursesMatched') }}
          </h2>

          <StatePanel
            v-if="coursesReady && !filteredCourses.length"
            tone="empty"
            icon="search"
            :title="t('catalog.noMatchesTitle')"
            :message="t('catalog.noMatchesCopy')"
            :eyebrow="t('catalog.noMatchesEyebrow')"
            compact
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="clearFilters">
                {{ t('catalog.clearFilters') }}
              </button>
            </template>
          </StatePanel>

          <ul v-else-if="coursesReady" class="catalog-course-list">
            <li v-for="course in filteredCourses" :key="course.id">
              <PublicCourseShowcaseCard :course="course" @view="openCourseDetail(course.id)" />
            </li>
          </ul>

          <PaginationBar
            v-if="courseTotalPages > 1"
            :label="t('catalog.resultPages')"
            :info="t('catalog.pageOf', { page: currentPage + 1, total: courseTotalPages })"
            :previous-label="t('catalog.previous')"
            :next-label="t('catalog.next')"
            :previous-disabled="currentPage <= 0 || coursesResource.loading.value"
            :next-disabled="currentPage >= courseTotalPages - 1 || coursesResource.loading.value"
            @previous="goToCoursePage(currentPage - 1)"
            @next="goToCoursePage(currentPage + 1)"
          />
        </section>
      </div>

      <aside class="catalog-side">
        <section class="panel catalog-side-card catalog-access-card">
          <h2 class="card-title">{{ learningAccessTitle }}</h2>
          <p>{{ learningAccessDescription }}</p>
          <button v-if="isStudentViewer" class="btn btn-primary btn-block" type="button" @click="goToStudentLearn">
            {{ t('catalog.continueLearning') }}
          </button>
          <button v-else-if="!auth.isAuthenticated" class="btn btn-primary btn-block" type="button" @click="goToAuth">
            {{ t('catalog.signIn') }}
          </button>
        </section>

        <PublicInfoBanner
          icon="safe"
          :title="t('catalog.safeBannerTitle')"
          :description="t('catalog.safeBannerCopy')"
          tone="green"
        />

        <section class="panel catalog-side-card">
          <h2 class="card-title">{{ t('catalog.helpTitle') }}</h2>
          <p>{{ t('catalog.helpCopy') }}</p>
          <button class="btn btn-secondary btn-block" type="button" @click="goToAiHelperSignIn">
            <IconGlyph name="ai" :size="16" />
            {{ t('catalog.askAi') }}
          </button>
        </section>
      </aside>
    </div>

    <section class="catalog-feature-strip">
      <PublicFeatureCard
        icon="safe"
        :title="t('catalog.safeFeatureTitle')"
        :description="t('catalog.safeFeatureCopy')"
        tone="purple"
        layout="inline"
      />
      <PublicFeatureCard
        icon="teacher"
        :title="t('catalog.teachersFeatureTitle')"
        :description="t('catalog.teachersFeatureCopy')"
        tone="blue"
        layout="inline"
      />
      <PublicFeatureCard
        icon="fun"
        :title="t('catalog.funFeatureTitle')"
        :description="t('catalog.funFeatureCopy')"
        tone="gold"
        layout="inline"
      />
      <PublicFeatureCard
        icon="progress"
        :title="t('catalog.progressFeatureTitle')"
        :description="t('catalog.progressFeatureCopy')"
        tone="green"
        layout="inline"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { publicApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import PublicCourseShowcaseCard from '@/components/public/PublicCourseShowcaseCard.vue'
import PublicFeatureCard from '@/components/public/PublicFeatureCard.vue'
import PublicInfoBanner from '@/components/public/PublicInfoBanner.vue'
import { useResource } from '@/composables/useResource'
import { categories as demoCategories, levels as demoLevels, publicCourses } from '@/demo/data'
import { catalogMessages } from '@/plugins/catalogMessages'
import { useAuthStore } from '@/stores/auth'
import type { LearningCategory, LearningLevel, PublicCourseSummary } from '@/types/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { catalog: catalogMessages.en },
    ms: { catalog: catalogMessages.ms },
    'zh-CN': { catalog: catalogMessages['zh-CN'] },
  },
})

type CatalogSort = 'newest' | 'title'
type LearningTypeFilter = '' | 'RECORDED' | 'LIVE' | 'HYBRID'

const learningTypeOptions: readonly Exclude<LearningTypeFilter, ''>[] = ['RECORDED', 'LIVE', 'HYBRID']

const filters = reactive({
  keyword: queryText(route.query.keyword),
  sort: validSort(queryText(route.query.sort)),
  levelId: validPositiveIdQuery(route.query.levelId),
  categoryId: validPositiveIdQuery(route.query.categoryId),
  learningType: validEnumQuery(route.query.learningType, learningTypeOptions),
})
const filtersOpen = ref(true)
const currentPage = ref(validPageQuery(route.query.page))
let courseReloadTimer: number | undefined

const coursesResource = useResource(
  () =>
    publicApi.listCourses({
      keyword: filters.keyword.trim() || undefined,
      sort: filters.sort,
      levelId: optionalPositiveId(filters.levelId),
      categoryId: optionalPositiveId(filters.categoryId),
      learningType: filters.learningType || undefined,
      page: currentPage.value,
      size: 20,
    }),
  {
    items: publicCourses,
    page: 0,
    size: 20,
    totalItems: publicCourses.length,
    totalPages: 1,
  },
)
const levelsResource = useResource(() => publicApi.getLevels(), demoLevels)
const categoriesResource = useResource(() => publicApi.getCategories(), demoCategories)

const signedInWithoutStudentRole = computed(() => auth.isAuthenticated && !auth.hasRole('STUDENT'))
const isStudentViewer = computed(() => auth.hasRole('STUDENT') && (auth.isAuthenticated || auth.isPreviewSession))

// useResource always holds data while it serves preview fixtures, so `?? []` only covers the live empty state.
const levels = computed<LearningLevel[]>(() => levelsResource.data.value ?? [])
const categories = computed<LearningCategory[]>(() => categoriesResource.data.value ?? [])
const courses = computed<PublicCourseSummary[]>(() => coursesResource.data.value?.items ?? [])
const courseTotalPages = computed(() =>
  coursesResource.usingFallback.value ? 1 : Math.max(1, coursesResource.data.value?.totalPages ?? 1),
)

const catalogUsingFallback = computed(
  () =>
    coursesResource.usingFallback.value || levelsResource.usingFallback.value || categoriesResource.usingFallback.value,
)
// Before any course data exists the results area shows its own loading / error state.
const coursesInitialLoading = computed(() => coursesResource.loading.value && !coursesResource.hasData.value)
const coursesLoadError = computed(() => (coursesResource.hasData.value ? null : coursesResource.error.value))
const coursesReady = computed(() => !coursesInitialLoading.value && !coursesLoadError.value)

// When the course list itself failed, its error panel (whose retry reloads everything) replaces the notice.
const catalogNoticeError = computed(() =>
  coursesLoadError.value
    ? null
    : coursesResource.error.value || levelsResource.error.value || categoriesResource.error.value,
)
const catalogNoticeDetail = computed(() => {
  if (catalogNoticeError.value) return t('catalog.browsingRecovery')

  if (catalogUsingFallback.value) {
    return isStudentViewer.value ? t('catalog.previewReadOnly') : t('catalog.liveAfterSignIn')
  }

  return null
})

const filteredCourses = computed(() => {
  if (!coursesResource.usingFallback.value) return courses.value

  const keyword = filters.keyword.trim().toLowerCase()

  return courses.value
    .filter((course) => courseMatchesFallbackFilters(course, keyword))
    .sort((left, right) => {
      if (filters.sort === 'title') return left.title.localeCompare(right.title)
      return right.contentId - left.contentId
    })
})
const matchedCourseCount = computed(() =>
  coursesResource.usingFallback.value ? filteredCourses.value.length : (coursesResource.data.value?.totalItems ?? 0),
)

watch(
  filters,
  () => {
    if (currentPage.value !== 0) {
      currentPage.value = 0
      return
    }
    syncCatalogQuery()
    scheduleCourseReload()
  },
  { deep: true },
)

watch(currentPage, () => {
  syncCatalogQuery()
  void coursesResource.reload()
})

onBeforeUnmount(() => {
  if (courseReloadTimer != null) window.clearTimeout(courseReloadTimer)
})

const filterToggleLabel = computed(() => (filtersOpen.value ? t('catalog.hideFilters') : t('catalog.showFilters')))

const learningAccessTitle = computed(() => {
  if (isStudentViewer.value) return t('catalog.studentReadyTitle')
  if (signedInWithoutStudentRole.value) return t('catalog.studentOnlyTitle')
  return t('catalog.signInTitle')
})
const learningAccessDescription = computed(() => {
  if (isStudentViewer.value) return t('catalog.studentReadyCopy')
  if (signedInWithoutStudentRole.value) return t('catalog.nonStudentCopy')
  return t('catalog.signInCopy')
})

function clearFilters(): void {
  filters.keyword = ''
  filters.sort = 'newest'
  filters.levelId = ''
  filters.categoryId = ''
  filters.learningType = ''
}

function reloadCatalog(): void {
  void Promise.all([coursesResource.reload(), levelsResource.reload(), categoriesResource.reload()])
}

function goToCoursePage(page: number): void {
  const lastPage = Math.max(0, courseTotalPages.value - 1)
  currentPage.value = Math.min(Math.max(page, 0), lastPage)
}

function scheduleCourseReload(): void {
  if (courseReloadTimer != null) window.clearTimeout(courseReloadTimer)
  courseReloadTimer = window.setTimeout(() => {
    void coursesResource.reload()
  }, 250)
}

function syncCatalogQuery(): void {
  const query: Record<string, string> = {}
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.sort !== 'newest') query.sort = filters.sort
  if (filters.levelId) query.levelId = filters.levelId
  if (filters.categoryId) query.categoryId = filters.categoryId
  if (filters.learningType) query.learningType = filters.learningType
  if (currentPage.value > 0) query.page = String(currentPage.value)
  void router.replace({ query })
}

function queryText(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function validSort(value: string): CatalogSort {
  return value === 'title' ? 'title' : 'newest'
}

function validPositiveIdQuery(value: unknown): string {
  const text = queryText(value)
  return optionalPositiveId(text) == null ? '' : text
}

function optionalPositiveId(value: string): number | undefined {
  const numeric = Number(value)
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : undefined
}

function validEnumQuery<T extends string>(value: unknown, validValues: readonly T[]): T | '' {
  const text = queryText(value)
  return validValues.includes(text as T) ? (text as T) : ''
}

function validPageQuery(value: unknown): number {
  const numeric = Number(queryText(value))
  return Number.isSafeInteger(numeric) && numeric >= 0 ? numeric : 0
}

function toggleFilters(): void {
  filtersOpen.value = !filtersOpen.value
}

function openCourseDetail(courseId: string): void {
  void router.push(`/courses/${courseId}`)
}

function goToAuth(): void {
  void router.push({ name: 'auth', query: { redirect: '/courses' } })
}

function goToStudentLearn(): void {
  void router.push({ name: 'student-learn' })
}

function goToAiHelperSignIn(): void {
  // A signed-in (or preview) student goes straight there; the auth route would redirect them to their home page.
  if (isStudentViewer.value) {
    void router.push('/student/ai-helper')
    return
  }
  void router.push({ name: 'auth', query: { redirect: '/student/ai-helper' } })
}

function courseMatchesFallbackFilters(course: PublicCourseSummary, keyword: string): boolean {
  if (keyword && !`${course.title} ${course.shortDescription ?? ''}`.toLowerCase().includes(keyword)) return false
  if (filters.levelId && String(course.level.id) !== filters.levelId) return false
  if (filters.categoryId && String(course.category.id) !== filters.categoryId) return false
  if (filters.learningType && course.learningType !== filters.learningType) return false
  return true
}
</script>
