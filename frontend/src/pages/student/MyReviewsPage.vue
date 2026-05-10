<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { reviewService } from '../../services/reviewService'
import type { CreateReviewPayload, ReviewItem, UpdateReviewPayload } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const reviews = ref<ReviewItem[]>([])
const form = reactive<CreateReviewPayload & UpdateReviewPayload>({
  targetType: 'LESSON',
  targetId: 0,
  rating: 5,
  comment: '',
})

async function loadReviews() {
  loading.value = true
  try {
    reviews.value = await reviewService.getMyReviews()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.targetType = 'LESSON'
  form.targetId = 0
  form.rating = 5
  form.comment = ''
  dialogVisible.value = true
}

function openEdit(item: ReviewItem) {
  editingId.value = item.id
  form.targetType = item.targetType as 'LESSON' | 'PACKAGE'
  form.targetId = item.targetId
  form.rating = item.rating
  form.comment = item.comment || ''
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      await reviewService.updateReview(editingId.value, { rating: form.rating, comment: form.comment })
      ElMessage.success(t('phase11.messages.reviewUpdated'))
    } else {
      await reviewService.createReview(form)
      ElMessage.success(t('phase11.messages.reviewCreated'))
    }
    dialogVisible.value = false
    await loadReviews()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    saving.value = false
  }
}

async function hideReview(item: ReviewItem) {
  try {
    await reviewService.hideMyReview(item.id)
    ElMessage.success(t('phase11.messages.reviewHidden'))
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
      :title="t('phase11.myReviewsTitle')"
      :description="t('phase11.myReviewsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('phase11.myReviews') }}</p>
          <h2>{{ t('phase11.ratingsAndReviews') }}</h2>
        </div>
        <el-button type="primary" @click="openCreate">{{ t('phase11.createReview') }}</el-button>
      </div>

      <el-table v-loading="loading" :data="reviews" class="phase-table">
        <el-table-column :label="t('phase11.reviewTargetType')" min-width="140">
          <template #default="{ row }">{{ translateEnum('reviewTargetType', row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTitle" :label="t('phase11.reviewTargetTitle')" min-width="220" />
        <el-table-column prop="rating" :label="t('phase11.rating')" min-width="120" />
        <el-table-column :label="t('commerce.status')" min-width="140">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('reviewStatus', row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" :label="t('phase11.reviewComment')" min-width="320" />
        <el-table-column width="220">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain type="primary" @click="openEdit(row)">{{ t('content.edit') }}</el-button>
              <el-button plain type="warning" @click="hideReview(row)">{{ t('phase11.hide') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !reviews.length" :description="t('phase11.emptyReviews')" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('phase11.editReview') : t('phase11.createReview')" width="640px">
      <el-form label-position="top">
        <el-form-item v-if="!editingId" :label="t('phase11.reviewTargetType')">
          <el-select v-model="form.targetType" class="w-full">
            <el-option label="LESSON" value="LESSON" />
            <el-option label="PACKAGE" value="PACKAGE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingId" :label="t('phase11.reviewTargetId')">
          <el-input-number v-model="form.targetId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('phase11.rating')">
          <el-rate v-model="form.rating" />
        </el-form-item>
        <el-form-item :label="t('phase11.reviewComment')">
          <el-input v-model="form.comment" type="textarea" :rows="5" maxlength="2000" show-word-limit />
        </el-form-item>
        <div class="table-actions">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="save">{{ t('content.save') }}</el-button>
        </div>
      </el-form>
    </el-dialog>
  </div>
</template>
