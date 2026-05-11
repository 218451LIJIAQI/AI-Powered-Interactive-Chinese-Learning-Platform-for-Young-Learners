<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { publicContentService } from '../../services/publicContentService'
import type {
  AccessType,
  LearningCategory,
  LearningLevel,
  LearningType,
  PageResponse,
  PublicCourseSummary,
} from '../../types/contentManagement'
import { formatMoney } from '../../utils/money'

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
  page: number
  size: number
}>({
  keyword: '',
  levelId: undefined,
  categoryId: undefined,
  accessType: '',
  learningType: '',
  page: 1,
  size: 18,
})

const lessonCards = computed(() =>
  (page.value?.items ?? []).filter((item) => item.type === 'LESSON'),
)

function formatLearningType(learningType: LearningType | null) {
  if (learningType === 'LIVE') {
    return t('content.live')
  }

  if (learningType === 'HYBRID') {
    return t('content.hybrid')
  }

  return t('content.recorded')
}

function formatAccessType(accessType: AccessType) {
  return accessType === 'PAID' ? t('content.paid') : t('content.free')
}

async function loadFilters() {
  loadingFilters.value = true

  try {
    const [levelList, categoryList] = await Promise.all([
      publicContentService.getLevels(),
      publicContentService.getCategories(),
    ])
    levels.value = levelList
    categories.value = categoryList
  } finally {
    loadingFilters.value = false
  }
}

async function loadLessons() {
  loading.value = true
  errorMessage.value = ''

  try {
    page.value = await publicContentService.discoverCourses({
      keyword: filters.keyword.trim() || undefined,
      levelId: filters.levelId,
      categoryId: filters.categoryId,
      accessType: filters.accessType || undefined,
      learningType: filters.learningType || undefined,
      page: filters.page - 1,
      size: filters.size,
      sort: 'newest',
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  filters.page = 1
  await loadLessons()
}

async function handlePageChange(nextPage: number) {
  filters.page = nextPage
  await loadLessons()
}

onMounted(async () => {
  await loadFilters()
  await loadLessons()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      kid
      show-mascot
      :eyebrow="t('learning.lessonsEyebrow')"
      :title="t('learning.lessonsTitle')"
      :description="t('learning.lessonsDescription')"
      mascot-holding="课"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.studentLessons') }}</p>
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
        <el-select
          v-model="filters.levelId"
          :placeholder="t('content.level')"
          class="content-filter"
          clearable
          :loading="loadingFilters"
        >
          <el-option v-for="level in levels" :key="level.id" :label="level.name" :value="level.id" />
        </el-select>
        <el-select
          v-model="filters.categoryId"
          :placeholder="t('content.category')"
          class="content-filter"
          clearable
          :loading="loadingFilters"
        >
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <el-select v-model="filters.accessType" :placeholder="t('content.accessType')" class="content-filter" clearable>
          <el-option :label="t('content.free')" value="FREE" />
          <el-option :label="t('content.paid')" value="PAID" />
        </el-select>
        <el-select
          v-model="filters.learningType"
          :placeholder="t('content.learningType')"
          class="content-filter"
          clearable
        >
          <el-option :label="t('content.live')" value="LIVE" />
          <el-option :label="t('content.recorded')" value="RECORDED" />
          <el-option :label="t('content.hybrid')" value="HYBRID" />
        </el-select>
        <el-button type="primary" @click="applyFilters">{{ t('content.applyFilters') }}</el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <section class="student-lessons-grid">
          <el-card v-for="item in lessonCards" :key="item.id" class="course-card" shadow="hover">
            <div class="course-card__media">
              <img
                v-if="item.coverImageUrl"
                :src="item.coverImageUrl"
                :alt="item.title"
                class="course-card__image"
              />
              <div v-else class="course-card__placeholder">
                <span>{{ t('content.lesson') }}</span>
              </div>
            </div>

            <div class="course-card__body">
              <div class="tag-list">
                <el-tag round>{{ t('content.lesson') }}</el-tag>
                <el-tag :type="item.accessType === 'PAID' ? 'warning' : 'success'" round>
                  {{ formatAccessType(item.accessType) }}
                </el-tag>
                <el-tag type="info" round>{{ formatLearningType(item.learningType) }}</el-tag>
                <el-tag v-if="item.locked" type="danger" round>{{ t('content.locked') }}</el-tag>
              </div>

              <h2>{{ item.title }}</h2>
              <p>{{ item.shortDescription || t('learning.lessonsDescription') }}</p>

              <div class="course-card__meta">
                <span>{{ item.level?.name || t('content.level') }}</span>
                <span>{{ item.category?.name || t('content.category') }}</span>
                <span>{{ item.teacher.displayName }}</span>
              </div>
            </div>

            <div class="course-card__footer">
              <div>
                <strong>{{ item.accessType === 'PAID' ? formatMoney(item.price) : t('content.free') }}</strong>
                <p>{{ item.locked ? t('learning.paidLockedMessage') : t('learning.readyToPlay') }}</p>
              </div>
              <div class="table-actions">
                <router-link :to="{ name: 'course-detail', params: { id: item.id } }">
                  <el-button plain type="primary">{{ t('content.reviewDetail') }}</el-button>
                </router-link>
                <router-link
                  v-if="!item.locked"
                  :to="{ name: 'student-lesson-player', params: { lessonId: item.contentId } }"
                >
                  <el-button type="primary">{{ t('common.startLearning') }}</el-button>
                </router-link>
                <el-button v-else type="warning" disabled>{{ t('content.locked') }}</el-button>
              </div>
            </div>
          </el-card>
        </section>

        <el-empty v-if="!lessonCards.length" :description="t('learning.noLessonsAvailable')" />

        <div v-if="(page?.totalItems ?? 0) > filters.size" class="pagination-row">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="filters.page"
            :page-size="filters.size"
            :total="page?.totalItems ?? 0"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </el-skeleton>
  </div>
</template>
