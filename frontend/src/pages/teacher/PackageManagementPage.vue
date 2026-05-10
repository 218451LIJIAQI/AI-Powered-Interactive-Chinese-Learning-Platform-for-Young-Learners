<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { useEnumLabel } from '../../composables/useEnumLabel'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { CoursePackageItem } from '../../types/contentManagement'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const translateEnum = useEnumLabel()

const loading = ref(true)
const packages = ref<CoursePackageItem[]>([])
const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')

async function loadPackages() {
  loading.value = true
  try {
    packages.value = await teacherContentService.getPackages()
  } finally {
    loading.value = false
  }
}

async function submitPackage(id: number) {
  try {
    await teacherContentService.submitPackage(id)
    ElMessage.success(t('content.messages.submitted'))
    await loadPackages()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function archivePackage(id: number) {
  try {
    await teacherContentService.archivePackage(id)
    ElMessage.success(t('content.messages.archived'))
    await loadPackages()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

onMounted(() => {
  loadPackages()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('content.packageManagementEyebrow') }}</p>
          <h1>{{ t('content.packageManagementTitle') }}</h1>
          <p>{{ t('content.packageManagementDescription') }}</p>
        </div>
        <div class="teacher-profile-actions">
          <el-button type="primary" :disabled="teacherLocked" @click="router.push({ name: 'teacher-package-new' })">
            {{ t('content.createPackage') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert v-if="teacherLocked" :title="t('content.teacherApprovalRequired')" type="warning" show-icon :closable="false" />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="packages" class="phase-table">
        <el-table-column prop="title" :label="t('content.fields.title')" min-width="220" />
        <el-table-column :label="t('content.level')" min-width="140">
          <template #default="{ row }">{{ row.level?.name || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('content.learningType')" min-width="140">
          <template #default="{ row }">{{ translateEnum('learningType', row.learningType) }}</template>
        </el-table-column>
        <el-table-column :label="t('content.accessType')" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.accessType === 'PAID' ? 'warning' : 'success'" round>{{ translateEnum('accessType', row.accessType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round>{{ translateEnum('contentStatus', row.contentStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.fields.itemCount')" min-width="120">
          <template #default="{ row }">{{ row.packageItems.length }}</template>
        </el-table-column>
        <el-table-column :label="t('content.rejectionReason')" min-width="220">
          <template #default="{ row }">{{ row.rejectionReason || '-' }}</template>
        </el-table-column>
        <el-table-column width="280">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="router.push({ name: 'teacher-package-edit', params: { id: row.id } })">
                {{ t('content.edit') }}
              </el-button>
              <el-button plain type="primary" size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="submitPackage(row.id)">
                {{ t('content.submitForReview') }}
              </el-button>
              <el-button plain type="danger" size="small" :disabled="teacherLocked || !['DRAFT', 'REJECTED'].includes(row.contentStatus)" @click="archivePackage(row.id)">
                {{ t('content.archive') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !packages.length" :description="t('content.noPackages')" />
    </el-card>
  </div>
</template>
