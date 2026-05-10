<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { reviewService } from '../../services/reviewService'
import type { ReviewItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const reviews = ref<ReviewItem[]>([])

async function loadReviews() {
  loading.value = true
  try {
    reviews.value = await reviewService.getAdminReviews()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function apply(action: 'hide' | 'show' | 'flag', id: number) {
  try {
    if (action === 'hide') {
      await reviewService.hideReview(id)
    } else if (action === 'show') {
      await reviewService.showReview(id)
    } else {
      await reviewService.flagReview(id)
    }
    ElMessage.success(t('phase11.messages.reviewUpdated'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
    await loadReviews()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
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
      :title="t('phase11.adminReviewsTitle')"
      :description="t('phase11.adminReviewsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="reviews" class="phase-table">
        <el-table-column :label="t('phase11.reviewTargetType')" min-width="140">
          <template #default="{ row }">{{ translateEnum('reviewTargetType', row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTitle" :label="t('phase11.reviewTargetTitle')" min-width="220" />
        <el-table-column prop="reviewerName" :label="t('phase11.reviewer')" min-width="180" />
        <el-table-column prop="rating" :label="t('phase11.rating')" min-width="120" />
        <el-table-column :label="t('commerce.status')" min-width="160">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('reviewStatus', row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column width="280">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain type="warning" @click="apply('hide', row.id)">{{ t('phase11.hide') }}</el-button>
              <el-button plain type="success" @click="apply('show', row.id)">{{ t('phase11.show') }}</el-button>
              <el-button plain type="danger" @click="apply('flag', row.id)">{{ t('phase11.flag') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
