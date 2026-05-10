<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { useEnumLabel } from '../../composables/useEnumLabel'
import { adminContentService } from '../../services/adminContentService'
import type { ContentSubmissionDetail, ManagedContentType } from '../../types/contentManagement'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const translateEnum = useEnumLabel()

const loading = ref(true)
const working = ref(false)
const detail = ref<ContentSubmissionDetail | null>(null)

const contentType = computed(() => String(route.params.type).toUpperCase() as ManagedContentType)
const contentId = computed(() => Number(route.params.id))
const canApprove = computed(() => detail.value?.contentStatus === 'SUBMITTED')
const canReject = computed(() => detail.value?.contentStatus === 'SUBMITTED')
const canPublish = computed(() => detail.value?.contentStatus === 'APPROVED')
const canArchive = computed(() => detail.value?.contentStatus !== 'ARCHIVED')

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await adminContentService.getSubmissionDetail(contentType.value, contentId.value)
  } finally {
    loading.value = false
  }
}

async function approveContent() {
  working.value = true
  try {
    detail.value = await adminContentService.approveContent(contentType.value, contentId.value)
    ElMessage.success(t('content.messages.approved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    working.value = false
  }
}

async function publishContent() {
  working.value = true
  try {
    detail.value = await adminContentService.publishContent(contentType.value, contentId.value)
    ElMessage.success(t('content.messages.published'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    working.value = false
  }
}

async function archiveContent() {
  working.value = true
  try {
    detail.value = await adminContentService.archiveContent(contentType.value, contentId.value)
    ElMessage.success(t('content.messages.archived'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    working.value = false
  }
}

async function rejectContent() {
  try {
    const result = await ElMessageBox.prompt(
      t('content.rejectPrompt'),
      t('content.rejectTitle'),
      {
        confirmButtonText: t('content.reject'),
        cancelButtonText: t('common.cancel'),
        inputValidator: (value) => (value.trim() ? true : t('content.validation.rejectionReasonRequired')),
      },
    )

    working.value = true
    detail.value = await adminContentService.rejectContent(contentType.value, contentId.value, result.value)
    ElMessage.success(t('content.messages.rejected'))
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    working.value = false
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="10">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('content.contentReviewDetailEyebrow') }}</p>
              <h1>{{ detail?.title || t('content.reviewDetail') }}</h1>
              <p>{{ t('content.contentReviewDetailDescription') }}</p>
            </div>
            <div class="teacher-profile-actions">
              <el-button plain @click="router.push({ name: 'admin-content-approvals' })">{{ t('content.backToApprovals') }}</el-button>
              <el-button type="success" :loading="working" :disabled="!canApprove" @click="approveContent">
                {{ t('content.approve') }}
              </el-button>
              <el-button type="warning" :loading="working" :disabled="!canPublish" @click="publishContent">
                {{ t('content.publish') }}
              </el-button>
              <el-button type="danger" :loading="working" :disabled="!canReject" @click="rejectContent">
                {{ t('content.reject') }}
              </el-button>
              <el-button plain :loading="working" :disabled="!canArchive" @click="archiveContent">
                {{ t('content.archive') }}
              </el-button>
            </div>
          </div>
        </el-card>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.contentSummary') }}</p>
                <h2>{{ t('content.contentSummaryTitle') }}</h2>
              </div>
            </div>
            <div class="profile-summary">
              <p><strong>{{ t('content.type') }}:</strong> {{ translateEnum('managedContentType', detail?.contentType) }}</p>
              <p><strong>{{ t('content.status') }}:</strong> {{ translateEnum('contentStatus', detail?.contentStatus) }}</p>
              <p><strong>{{ t('content.teacher') }}:</strong> {{ detail?.teacher?.fullName || '-' }}</p>
              <p><strong>{{ t('content.level') }}:</strong> {{ detail?.level?.name || '-' }}</p>
              <p><strong>{{ t('content.category') }}:</strong> {{ detail?.category?.name || '-' }}</p>
              <p><strong>{{ t('content.learningType') }}:</strong> {{ detail?.learningType ? translateEnum('learningType', detail.learningType) : '-' }}</p>
              <p><strong>{{ t('content.accessType') }}:</strong> {{ translateEnum('accessType', detail?.accessType) }}</p>
              <p><strong>{{ t('content.price') }}:</strong> RM {{ detail?.price ?? 0 }}</p>
              <p v-if="detail?.rejectionReason"><strong>{{ t('content.rejectionReason') }}:</strong> {{ detail.rejectionReason }}</p>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.fields.description') }}</p>
                <h2>{{ t('content.fields.basicContent') }}</h2>
              </div>
            </div>
            <p>{{ detail?.description || '-' }}</p>
            <el-divider />
            <p>{{ detail?.basicContent || t('content.noBasicContent') }}</p>
          </el-card>
        </section>

        <section class="dashboard-grid">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.activityPlaceholders') }}</p>
                <h2>{{ t('content.activityPlaceholderTitle') }}</h2>
              </div>
            </div>
            <el-empty v-if="!detail?.activities.length" :description="t('content.noActivities')" />
            <div v-else class="stacked-summary-list">
              <article v-for="activity in detail?.activities" :key="activity.id" class="summary-row">
                <div>
                  <strong>{{ activity.title }}</strong>
                  <p>{{ activity.activityType }} - {{ activity.instruction || t('content.noInstruction') }}</p>
                </div>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('content.packageItems') }}</p>
                <h2>{{ t('content.packageItemsTitle') }}</h2>
              </div>
            </div>
            <el-empty v-if="!detail?.packageItems.length" :description="t('content.noPackageItems')" />
            <div v-else class="stacked-summary-list">
              <article v-for="item in detail?.packageItems" :key="item.id" class="summary-row">
                <div>
                  <strong>{{ item.titleSnapshot || `${translateEnum('managedContentType', item.itemType)} #${item.itemId}` }}</strong>
                  <p>{{ translateEnum('managedContentType', item.itemType) }} - {{ t('content.displayOrder') }} {{ item.displayOrder }}</p>
                </div>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
