<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { categoryService } from '../../services/categoryService'
import { levelService } from '../../services/levelService'
import { publicContentService } from '../../services/publicContentService'
import type {
  AccessType,
  LearningCategory,
  LearningLevel,
  LearningType,
  PageResponse,
  PublicCourseSummary,
} from '../../types/contentManagement'

const { t } = useI18n()

const loading = ref(true)
const loadingFilters = ref(true)
const errorMessage = ref('')
const levels = ref<LearningLevel[]>([])
const categories = ref<LearningCategory[]>([])
const page = ref<PageResponse<PublicCourseSummary> | null>(null)

const filters = reactive<{
  keyword: string
  levelId?: number
  categoryId?: number
  accessType: '' | AccessType
  learningType: '' | LearningType
  sort: 'newest' | 'title' | 'rating' | 'popular'
  page: number
  size: number
}>({
  keyword: '',
  levelId: undefined,
  categoryId: undefined,
  accessType: '',
  learningType: '',
  sort: 'newest',
  page: 1,
  size: 9,
})

const items = computed(() => page.value?.items ?? [])
const totalItems = computed(() => page.value?.totalItems ?? 0)

function formatAccessType(accessType: AccessType) {
  return accessType === 'PAID' ? t('content.paid') : t('content.free')
}

function formatLearningType(learningType: LearningType | null) {
  if (learningType === 'LIVE') {
    return t('content.live')
  }

  if (learningType === 'HYBRID') {
    return t('content.hybrid')
  }

  return t('content.recorded')
}

function formatPrice(item: PublicCourseSummary) {
  if (item.accessType === 'FREE') {
    return t('content.freeToBrowse')
  }

  return `RM ${Number(item.price ?? 0).toFixed(2)}`
}

async function loadFilters() {
  loadingFilters.value = true
  try {
    const [levelList, categoryList] = await Promise.all([
      levelService.getLevels(),
      categoryService.getCategories(),
    ])
    levels.value = levelList
    categories.value = categoryList
  } finally {
    loadingFilters.value = false
  }
}

async function loadCourses() {
  loading.value = true
  errorMessage.value = ''

  try {
    page.value = await publicContentService.discoverCourses({
      keyword: filters.keyword.trim() || undefined,
      levelId: filters.levelId,
      categoryId: filters.categoryId,
      accessType: filters.accessType || undefined,
      learningType: filters.learningType || undefined,
      sort: filters.sort,
      page: filters.page - 1,
      size: filters.size,
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  filters.page = 1
  await loadCourses()
}

async function handlePageChange(nextPage: number) {
  filters.page = nextPage
  await loadCourses()
}

onMounted(async () => {
  await loadFilters()
  await loadCourses()
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      :eyebrow="t('content.courseDiscoveryEyebrow')"
      :title="t('content.courseDiscoveryTitle')"
      :description="t('content.courseDiscoveryDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.courses') }}</p>
          <h2>{{ t('content.applyFilters') }}</h2>
        </div>
      </div>

      <div class="content-toolbar">
        <el-input
          v-model="filters.keyword"
          :placeholder="t('content.searchPlaceholder')"
          class="content-filter content-filter--wide"
          clearable
        />
        <el-select v-model="filters.levelId" :placeholder="t('content.level')" class="content-filter" clearable :loading="loadingFilters">
          <el-option v-for="level in levels" :key="level.id" :label="level.name" :value="level.id" />
        </el-select>
        <el-select
          v-model="filters.categoryId"
          :placeholder="t('content.category')"
          class="content-filter"
          clearable
          :loading="loadingFilters"
        >
          <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-select v-model="filters.accessType" :placeholder="t('content.accessType')" class="content-filter" clearable>
          <el-option :label="t('content.free')" value="FREE" />
          <el-option :label="t('content.paid')" value="PAID" />
        </el-select>
        <el-select v-model="filters.learningType" :placeholder="t('content.learningType')" class="content-filter" clearable>
          <el-option :label="t('content.live')" value="LIVE" />
          <el-option :label="t('content.recorded')" value="RECORDED" />
          <el-option :label="t('content.hybrid')" value="HYBRID" />
        </el-select>
        <el-select v-model="filters.sort" class="content-filter">
          <el-option label="Newest" value="newest" />
          <el-option label="Title" value="title" />
          <el-option label="Rating" value="rating" />
          <el-option label="Popular" value="popular" />
        </el-select>
        <el-button type="primary" @click="applyFilters">{{ t('content.applyFilters') }}</el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <section class="course-discovery-grid">
          <el-card v-for="item in items" :key="item.id" class="course-card" shadow="hover">
            <div class="course-card__media">
              <img
                v-if="item.coverImageUrl"
                :src="item.coverImageUrl"
                :alt="item.title"
                class="course-card__image"
              />
              <div v-else class="course-card__placeholder">
                <span>{{ item.type }}</span>
              </div>
            </div>

            <div class="course-card__body">
              <div class="tag-list">
                <el-tag round>{{ item.type }}</el-tag>
                <el-tag :type="item.accessType === 'PAID' ? 'warning' : 'success'" round>
                  {{ formatAccessType(item.accessType) }}
                </el-tag>
                <el-tag type="info" round>{{ formatLearningType(item.learningType) }}</el-tag>
                <el-tag v-if="item.locked" type="danger" round>{{ t('content.locked') }}</el-tag>
              </div>

              <h2>{{ item.title }}</h2>
              <p>{{ item.shortDescription || t('content.courseDiscoveryDescription') }}</p>

              <div class="course-card__meta">
                <span>{{ item.level?.name || t('content.level') }}</span>
                <span>{{ item.category?.name || t('content.category') }}</span>
                <span>{{ item.teacher.verified ? t('teacherVerification.verifiedBadge') : t('content.teacher') }}</span>
              </div>

              <p class="course-card__teacher">
                <strong>{{ t('content.teacher') }}:</strong>
                <router-link
                  :to="{ name: 'teacher-public-profile', params: { id: item.teacher.teacherId } }"
                  class="course-card__teacher-link"
                >
                  {{ item.teacher.displayName }}
                </router-link>
              </p>
            </div>

            <div class="course-card__footer">
              <div>
                <strong>{{ formatPrice(item) }}</strong>
                <p>{{ item.locked ? t('content.locked') : t('content.freeToBrowse') }}</p>
              </div>
              <div class="table-actions">
                <router-link :to="{ name: 'course-detail', params: { id: item.id } }">
                  <el-button plain type="primary">{{ t('content.reviewDetail') }}</el-button>
                </router-link>
                <router-link :to="{ name: 'login' }">
                  <el-button type="primary">{{ t('nav.login') }}</el-button>
                </router-link>
              </div>
            </div>
          </el-card>
        </section>

        <el-empty v-if="!items.length" :description="t('content.noPublishedCourses')" />

        <div v-if="totalItems > filters.size" class="pagination-row">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="filters.page"
            :page-size="filters.size"
            :total="totalItems"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </el-skeleton>
  </div>
</template>
