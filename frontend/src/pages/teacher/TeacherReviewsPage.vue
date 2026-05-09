<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { reviewService } from '../../services/reviewService'
import type { ReviewItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const loading = ref(true)
const reviews = ref<ReviewItem[]>([])

async function loadReviews() {
  loading.value = true
  try {
    reviews.value = await reviewService.getTeacherReviews()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadReviews()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.reviewsEyebrow')"
      :title="t('phase11.teacherReviewsTitle')"
      :description="t('phase11.teacherReviewsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="reviews" class="phase-table">
        <el-table-column prop="targetType" :label="t('phase11.reviewTargetType')" min-width="140" />
        <el-table-column prop="targetTitle" :label="t('phase11.reviewTargetTitle')" min-width="220" />
        <el-table-column prop="reviewerName" :label="t('phase11.reviewer')" min-width="180" />
        <el-table-column prop="rating" :label="t('phase11.rating')" min-width="120" />
        <el-table-column prop="comment" :label="t('phase11.reviewComment')" min-width="320" />
      </el-table>

      <el-empty v-if="!loading && !reviews.length" :description="t('phase11.emptyReviews')" />
    </el-card>
  </div>
</template>
