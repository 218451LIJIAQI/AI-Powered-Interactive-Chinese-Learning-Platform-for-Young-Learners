<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { aiService } from '../../services/aiService'
import type { AdminAiUsageLogPage } from '../../types/ai'

const { t } = useI18n()

const loading = ref(true)
const logs = ref<AdminAiUsageLogPage | null>(null)

const filters = reactive({
  featureType: '',
  status: '',
  userRole: '',
  page: 1,
  size: 20,
})

async function loadLogs() {
  loading.value = true
  try {
    logs.value = await aiService.getAdminUsageLogs({
      featureType: filters.featureType || undefined,
      status: filters.status || undefined,
      userRole: filters.userRole || undefined,
      page: filters.page - 1,
      size: filters.size,
    })
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  filters.page = 1
  await loadLogs()
}

async function handlePageChange(nextPage: number) {
  filters.page = nextPage
  await loadLogs()
}

onMounted(() => {
  loadLogs()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('nav.aiUsageLogs') }}</p>
          <h1>{{ t('ai.adminLogsTitle') }}</h1>
          <p>{{ t('ai.adminLogsDescription') }}</p>
        </div>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-select v-model="filters.featureType" clearable class="content-filter" :placeholder="t('ai.featureType')">
          <el-option label="CHAT_TUTOR" value="CHAT_TUTOR" />
          <el-option label="VOCABULARY_HELPER" value="VOCABULARY_HELPER" />
          <el-option label="SENTENCE_PRACTICE" value="SENTENCE_PRACTICE" />
          <el-option label="QUIZ_GENERATOR" value="QUIZ_GENERATOR" />
          <el-option label="STORY_GENERATOR" value="STORY_GENERATOR" />
          <el-option label="LEARNING_RECOMMENDATION" value="LEARNING_RECOMMENDATION" />
          <el-option label="PROGRESS_INSIGHT" value="PROGRESS_INSIGHT" />
          <el-option label="TEACHER_ASSISTANT" value="TEACHER_ASSISTANT" />
          <el-option label="PRONUNCIATION_FEEDBACK" value="PRONUNCIATION_FEEDBACK" />
          <el-option label="WRITING_FEEDBACK" value="WRITING_FEEDBACK" />
        </el-select>
        <el-select v-model="filters.status" clearable class="content-filter" :placeholder="t('content.status')">
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAILED" value="FAILED" />
          <el-option label="UNAVAILABLE" value="UNAVAILABLE" />
        </el-select>
        <el-select v-model="filters.userRole" clearable class="content-filter" :placeholder="t('dashboard.roles')">
          <el-option label="STUDENT" value="STUDENT" />
          <el-option label="TEACHER" value="TEACHER" />
          <el-option label="ADMIN" value="ADMIN" />
        </el-select>
        <el-button type="primary" @click="applyFilters">{{ t('content.applyFilters') }}</el-button>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="logs?.items || []" class="phase-table">
        <el-table-column prop="createdAt" :label="t('teacherVerification.createdAt')" min-width="180" />
        <el-table-column prop="featureKey" :label="t('ai.featureType')" min-width="180" />
        <el-table-column prop="userName" :label="t('auth.fields.fullName')" min-width="180" />
        <el-table-column prop="userRole" :label="t('dashboard.roles')" min-width="120" />
        <el-table-column prop="providerName" :label="t('ai.providerLabel')" min-width="120" />
        <el-table-column prop="modelName" :label="t('ai.modelLabel')" min-width="140" />
        <el-table-column prop="status" :label="t('content.status')" min-width="120" />
        <el-table-column prop="errorSummary" :label="t('ai.errorSummary')" min-width="260" />
      </el-table>

      <div v-if="(logs?.totalItems ?? 0) > filters.size" class="pagination-row">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="filters.page"
          :page-size="filters.size"
          :total="logs?.totalItems ?? 0"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>
