<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { adminTeacherService } from '../../services/adminTeacherService'
import type { AdminTeacherDetail } from '../../types/adminManagement'
import { downloadProtectedFile } from '../../utils/fileDownload'

const route = useRoute()
const { t } = useI18n()
const loading = ref(true)
const working = ref(false)
const detail = ref<AdminTeacherDetail | null>(null)

const teacherId = computed(() => Number(route.params.id))

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await adminTeacherService.getTeacherDetail(teacherId.value)
  } finally {
    loading.value = false
  }
}

async function approveTeacher() {
  working.value = true
  try {
    detail.value = await adminTeacherService.approveTeacher(teacherId.value)
    ElMessage.success(t('teacherVerification.messages.teacherApproved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.teacherActionFailed'))
  } finally {
    working.value = false
  }
}

async function rejectTeacher() {
  try {
    const reason = await ElMessageBox.prompt(
      t('teacherVerification.rejectPrompt'),
      t('teacherVerification.rejectTitle'),
      {
        confirmButtonText: t('teacherVerification.rejectButton'),
        cancelButtonText: t('common.cancel'),
        inputValidator: (value) => (value.trim() ? true : t('teacherVerification.validation.rejectionReasonRequired')),
      },
    )

    working.value = true
    detail.value = await adminTeacherService.rejectTeacher(teacherId.value, reason.value)
    ElMessage.success(t('teacherVerification.messages.teacherRejected'))
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }

    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.teacherActionFailed'))
  } finally {
    working.value = false
  }
}

async function openDocument(documentId: number, originalFileName: string) {
  try {
    await downloadProtectedFile(adminTeacherService.getTeacherDocumentUrl(documentId), originalFileName)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.documentOpenFailed'))
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('teacherVerification.detailEyebrow') }}</p>
              <h1>{{ detail?.fullName || t('teacherVerification.detailTitle') }}</h1>
              <p>{{ t('teacherVerification.detailDescription') }}</p>
            </div>

            <div class="teacher-profile-actions">
              <el-button type="success" :loading="working" @click="approveTeacher">
                {{ t('teacherVerification.approveButton') }}
              </el-button>
              <el-button type="danger" :loading="working" @click="rejectTeacher">
                {{ t('teacherVerification.rejectButton') }}
              </el-button>
            </div>
          </div>
        </el-card>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.teacherProfilePanel') }}</p>
                <h2>{{ t('teacherVerification.teacherProfileTitle') }}</h2>
              </div>
            </div>
            <div class="profile-summary">
              <p><strong>{{ t('auth.fields.fullName') }}:</strong> {{ detail?.fullName }}</p>
              <p><strong>{{ t('auth.fields.email') }}:</strong> {{ detail?.email }}</p>
              <p><strong>{{ t('dashboard.teacherStatus') }}:</strong> {{ detail?.teacherStatus }}</p>
              <p><strong>{{ t('auth.fields.bio') }}:</strong> {{ detail?.bio || t('dashboard.noBio') }}</p>
              <p v-if="detail?.shortIntroduction"><strong>{{ t('teacherVerification.shortIntroduction') }}:</strong> {{ detail.shortIntroduction }}</p>
              <p v-if="detail?.teachingCategories.length"><strong>{{ t('teacherVerification.teachingCategories') }}:</strong> {{ detail.teachingCategories.join(', ') }}</p>
              <p v-if="detail?.rejectionReason"><strong>{{ t('dashboard.rejectionReason') }}:</strong> {{ detail.rejectionReason }}</p>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.documentsPanel') }}</p>
                <h2>{{ t('teacherVerification.documentsTitle') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!detail?.verificationDocuments.length"
              :description="t('teacherVerification.noDocuments')"
            />

            <div v-else class="stacked-summary-list">
              <article
                v-for="document in detail?.verificationDocuments"
                :key="document.id"
                class="summary-row"
              >
                <div>
                  <strong>{{ document.documentType }}</strong>
                  <p>{{ document.originalFileName }}</p>
                  <p>{{ document.description || t('teacherVerification.noDescription') }}</p>
                </div>
                <el-button type="primary" plain @click="openDocument(document.id, document.originalFileName)">
                  {{ t('teacherVerification.viewDocument') }}
                </el-button>
              </article>
            </div>
          </el-card>
        </section>

        <section class="dashboard-grid">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.reviewHistoryLabel') }}</p>
                <h2>{{ t('teacherVerification.reviewHistoryTitle') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!detail?.verificationReviewHistory.length"
              :description="t('teacherVerification.noReviewHistory')"
            />

            <div v-else class="stacked-summary-list">
              <article
                v-for="review in detail?.verificationReviewHistory"
                :key="review.id"
                class="summary-row"
              >
                <div>
                  <strong>{{ review.decisionStatus }}</strong>
                  <p>{{ review.note || t('teacherVerification.noReviewNote') }}</p>
                </div>
                <el-tag :type="review.decisionStatus === 'APPROVED' ? 'success' : review.decisionStatus === 'REJECTED' ? 'danger' : 'warning'" round>
                  {{ review.decisionStatus }}
                </el-tag>
              </article>
            </div>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.auditLabel') }}</p>
                <h2>{{ t('teacherVerification.auditTitle') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!detail?.recentAuditLogs.length"
              :description="t('teacherVerification.noAuditLogs')"
            />

            <div v-else class="stacked-summary-list">
              <article
                v-for="log in detail?.recentAuditLogs"
                :key="log.auditLogId"
                class="summary-row"
              >
                <div>
                  <strong>{{ log.actionType }}</strong>
                  <p>{{ log.description || t('dashboard.activityRecorded') }}</p>
                </div>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
