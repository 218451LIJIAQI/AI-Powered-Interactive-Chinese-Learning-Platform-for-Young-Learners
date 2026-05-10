<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import { publicContentService } from '../../services/publicContentService'
import { useAuthStore } from '../../stores/authStore'
import type { PublicCourseDetail } from '../../types/contentManagement'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const translateEnum = useEnumLabel()

const loading = ref(true)
const course = ref<PublicCourseDetail | null>(null)

const courseId = computed(() => String(route.params.id))

async function addToCart() {
  if (!course.value) {
    return
  }

  if (!authStore.isAuthenticated || !authStore.hasRole('STUDENT')) {
    await router.push({ name: 'login', query: { redirect: '/student/cart' } })
    return
  }

  await commerceService.addCartItem({ itemType: course.value.type, itemId: course.value.contentId })
  ElMessage.success(t('commerce.messages.cartUpdated'))
}

async function buyNow() {
  if (!course.value) {
    return
  }

  if (!authStore.isAuthenticated || !authStore.hasRole('STUDENT')) {
    await router.push({ name: 'login', query: { redirect: '/student/cart' } })
    return
  }

  const result = await commerceService.buyNow({ itemType: course.value.type, itemId: course.value.contentId })
  if (result.checkoutUrl) {
    window.location.assign(result.checkoutUrl)
    return
  }
  ElMessage.warning(result.message)
}

async function loadCourse() {
  loading.value = true
  try {
    course.value = await publicContentService.getCourseDetail(courseId.value)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCourse()
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      :eyebrow="t('content.courseDetailEyebrow')"
      :title="course?.title || t('content.courseDetailTitle')"
      :description="course?.description || t('content.courseDetailDescription')"
    />

    <el-skeleton :loading="loading" animated :rows="10">
      <template #default>
        <el-card class="simple-page-card" shadow="hover">
            <div class="feature-banner__content">
              <div>
              <div class="tag-list">
                <el-tag round>{{ translateEnum('managedContentType', course?.type) }}</el-tag>
                <el-tag type="success" round>{{ translateEnum('accessType', course?.accessType) }}</el-tag>
                <el-tag v-if="course?.locked" type="warning" round>{{ t('content.locked') }}</el-tag>
              </div>
              <h1>{{ course?.title }}</h1>
              <p>{{ course?.description }}</p>
              <p>
                <strong>{{ t('content.teacher') }}:</strong>
                <router-link
                  v-if="course?.teacher?.teacherId"
                  :to="{ name: 'teacher-public-profile', params: { id: course.teacher.teacherId } }"
                  class="course-card__teacher-link"
                >
                  {{ course.teacher.displayName }}
                </router-link>
                <span v-else>{{ course?.teacher.displayName }}</span>
              </p>
              <div v-if="course?.accessType === 'PAID'" class="table-actions" style="margin-top: 1rem;">
                <el-button plain type="primary" @click="addToCart">{{ t('commerce.addToCart') }}</el-button>
                <el-button type="primary" @click="buyNow">{{ t('commerce.buyNow') }}</el-button>
              </div>
            </div>
            <div class="dashboard-bubble">
              <span class="dashboard-bubble__label">{{ t('content.callToAction') }}</span>
              <strong>{{ course?.callToAction }}</strong>
              <span>{{ course?.lockedMessage || t('content.freeToBrowse') }}</span>
            </div>
          </div>
        </el-card>

        <section class="dashboard-grid">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.contentSummary') }}</p>
                <h2>{{ t('content.contentSummaryTitle') }}</h2>
              </div>
            </div>
            <div class="profile-summary">
              <p><strong>{{ t('content.level') }}:</strong> {{ course?.level?.name || '-' }}</p>
              <p><strong>{{ t('content.category') }}:</strong> {{ course?.category?.name || '-' }}</p>
              <p><strong>{{ t('content.learningType') }}:</strong> {{ course?.learningType || '-' }}</p>
              <p><strong>{{ t('content.price') }}:</strong> RM {{ course?.price ?? 0 }}</p>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.packageItems') }}</p>
                <h2>{{ t('content.packageItemsTitle') }}</h2>
              </div>
            </div>
            <el-empty v-if="!course?.packageItems.length" :description="t('content.noPackageItems')" />
            <div v-else class="stacked-summary-list">
              <article v-for="item in course?.packageItems" :key="`${item.type}-${item.contentId}-${item.displayOrder}`" class="summary-row">
                <div>
                  <strong>{{ item.title || `${item.type} #${item.contentId}` }}</strong>
                  <p>{{ item.type }} - {{ t('content.displayOrder') }} {{ item.displayOrder }}</p>
                </div>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.activityPlaceholders') }}</p>
                <h2>{{ t('content.activityPlaceholderTitle') }}</h2>
              </div>
            </div>
            <el-empty v-if="!course?.activities.length" :description="t('content.noActivities')" />
            <div v-else class="stacked-summary-list">
              <article v-for="activity in course?.activities" :key="activity.id" class="summary-row">
                <div>
                  <strong>{{ activity.title }}</strong>
                  <p>{{ activity.activityType }} - {{ activity.instruction || t('content.noInstruction') }}</p>
                </div>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
