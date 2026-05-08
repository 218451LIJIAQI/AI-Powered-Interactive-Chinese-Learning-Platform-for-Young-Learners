<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminUserService } from '../../services/adminUserService'
import type { AdminUserListItem, PageResponse } from '../../types/adminManagement'

const { t } = useI18n()
const loading = ref(true)
const users = ref<PageResponse<AdminUserListItem> | null>(null)

async function loadUsers() {
  loading.value = true
  try {
    users.value = await adminUserService.getUsers({ page: 0, size: 20 })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('teacherVerification.userManagementEyebrow') }}</p>
          <h1>{{ t('teacherVerification.userManagementTitle') }}</h1>
          <p>{{ t('teacherVerification.userManagementDescription') }}</p>
        </div>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="users?.items || []" class="phase-table">
        <el-table-column prop="fullName" :label="t('auth.fields.fullName')" min-width="180" />
        <el-table-column prop="email" :label="t('auth.fields.email')" min-width="220" />
        <el-table-column :label="t('dashboard.roles')" min-width="180">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="role in row.roles" :key="role" round>{{ role }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('dashboard.teacherStatus')" min-width="140">
          <template #default="{ row }">
            <span>{{ row.teacherStatus || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('teacherVerification.createdAt')" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>
