<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { useEnumLabel } from '../../composables/useEnumLabel'
import { adminContentService } from '../../services/adminContentService'
import type { ContentStatus, ContentSubmissionItem, ManagedContentType, PageResponse } from '../../types/contentManagement'

const router = useRouter()
const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const page = ref<PageResponse<ContentSubmissionItem> | null>(null)

const filters = reactive<{
  contentType: '' | ManagedContentType
  status: '' | ContentStatus
  keyword: string
}>({
  contentType: '',
  status: 'SUBMITTED',
  keyword: '',
})

const items = computed(() => page.value?.items ?? [])

async function loadSubmissions() {
  loading.value = true
  try {
    page.value = await adminContentService.getSubmissions({
      contentType: filters.contentType || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      page: 0,
      size: 20,
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSubmissions()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('content.adminApprovalEyebrow') }}</p>
          <h1>{{ t('content.adminApprovalTitle') }}</h1>
          <p>{{ t('content.adminApprovalDescription') }}</p>
        </div>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-input v-model="filters.keyword" :placeholder="t('content.searchPlaceholder')" class="content-filter--wide" />
        <el-select v-model="filters.contentType" clearable class="content-filter">
          <el-option label="MODULE" value="MODULE" />
          <el-option label="LESSON" value="LESSON" />
          <el-option label="PACKAGE" value="PACKAGE" />
        </el-select>
        <el-select v-model="filters.status" clearable class="content-filter">
          <el-option label="SUBMITTED" value="SUBMITTED" />
          <el-option label="APPROVED" value="APPROVED" />
          <el-option label="REJECTED" value="REJECTED" />
          <el-option label="PUBLISHED" value="PUBLISHED" />
          <el-option label="ARCHIVED" value="ARCHIVED" />
        </el-select>
        <el-button type="primary" @click="loadSubmissions">{{ t('content.applyFilters') }}</el-button>
      </div>

      <el-table v-loading="loading" :data="items" class="phase-table">
        <el-table-column :label="t('content.type')" min-width="120">
          <template #default="{ row }">
            <el-tag round size="small" type="info">{{ translateEnum('managedContentType', row.contentType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('content.fields.title')" min-width="220" />
        <el-table-column :label="t('content.teacher')" min-width="180">
          <template #default="{ row }">{{ row.teacher?.fullName || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('content.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round>{{ translateEnum('contentStatus', row.contentStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('content.accessType')" min-width="120">
          <template #default="{ row }">{{ translateEnum('accessType', row.accessType) }}</template>
        </el-table-column>
        <el-table-column width="160">
          <template #default="{ row }">
            <el-button plain type="primary" @click="router.push({ name: 'admin-content-approval-detail', params: { type: row.contentType, id: row.id } })">
              {{ t('content.reviewDetail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !items.length" :description="t('content.noSubmissions')" />
    </el-card>
  </div>
</template>
