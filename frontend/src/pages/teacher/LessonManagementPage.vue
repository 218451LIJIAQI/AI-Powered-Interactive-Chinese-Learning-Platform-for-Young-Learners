<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LessonItem } from '../../types/contentManagement'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const lessons = ref<LessonItem[]>([])
const statusFilter = ref<'ALL' | 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'ARCHIVED'>('ALL')
const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')

const filteredLessons = computed(() =>
  statusFilter.value === 'ALL'
    ? lessons.value
    : lessons.value.filter((lesson) => lesson.contentStatus === statusFilter.value),
)

async function loadLessons() {
  loading.value = true
  try {
    lessons.value = await teacherContentService.getLessons()
  } finally {
    loading.value = false
  }
}

async function submitLesson(id: number) {
  try {
    await teacherContentService.submitLesson(id)
    ElMessage.success(t('content.messages.submitted'))
    await loadLessons()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function archiveLesson(id: number) {
  try {
    await teacherContentService.archiveLesson(id)
    ElMessage.success(t('content.messages.archived'))
    await loadLessons()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

onMounted(() => {
  loadLessons()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('content.lessonManagementEyebrow') }}</p>
          <h1>{{ t('content.lessonManagementTitle') }}</h1>
          <p>{{ t('content.lessonManagementDescription') }}</p>
        </div>
        <div class="teacher-profile-actions">
          <el-button type="primary" :disabled="teacherLocked" @click="router.push({ name: 'teacher-lesson-new' })">
            {{ t('content.createLesson') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert v-if="teacherLocked" :title="t('content.teacherApprovalRequired')" type="warning" show-icon :closable="false" />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-select v-model="statusFilter" class="content-filter">
          <el-option label="ALL" value="ALL" />
          <el-option label="DRAFT" value="DRAFT" />
          <el-option label="SUBMITTED" value="SUBMITTED" />
          <el-option label="APPROVED" value="APPROVED" />
          <el-option label="REJECTED" value="REJECTED" />
          <el-option label="PUBLISHED" value="PUBLISHED" />
          <el-option label="ARCHIVED" value="ARCHIVED" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="filteredLessons" class="phase-table">
        <el-table-column prop="title" :label="t('content.fields.title')" min-width="220" />
        <el-table-column :label="t('content.module')" min-width="180">
          <template #default="{ row }">{{ row.moduleTitle || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('content.learningType')" min-width="140">
          <template #default="{ row }">{{ row.learningType }}</template>
        </el-table-column>
        <el-table-column :label="t('content.accessType')" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.accessType === 'PAID' ? 'warning' : 'success'" round>{{ row.accessType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round>{{ row.contentStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.rejectionReason')" min-width="220">
          <template #default="{ row }">{{ row.rejectionReason || '-' }}</template>
        </el-table-column>
        <el-table-column width="280">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="router.push({ name: 'teacher-lesson-edit', params: { id: row.id } })">
                {{ t('content.edit') }}
              </el-button>
              <el-button plain type="primary" size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="submitLesson(row.id)">
                {{ t('content.submitForReview') }}
              </el-button>
              <el-button plain type="danger" size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="archiveLesson(row.id)">
                {{ t('content.archive') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !filteredLessons.length" :description="t('content.noLessons')" />
    </el-card>
  </div>
</template>
