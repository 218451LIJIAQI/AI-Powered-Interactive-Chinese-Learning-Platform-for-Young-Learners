<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { feedbackService } from '../../services/feedbackService'
import type { TeacherFeedbackItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const loading = ref(true)
const feedbackItems = ref<TeacherFeedbackItem[]>([])

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('en-MY')
}

async function loadFeedback() {
  loading.value = true
  try {
    feedbackItems.value = await feedbackService.getStudentFeedback()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadFeedback()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.feedbackEyebrow')"
      :title="t('phase11.studentFeedbackTitle')"
      :description="t('phase11.studentFeedbackDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-timeline v-loading="loading">
        <el-timeline-item
          v-for="item in feedbackItems"
          :key="item.id"
          :timestamp="formatDateTime(item.createdAt)"
          placement="top"
        >
          <el-card shadow="hover">
            <div class="support-thread__header">
              <strong>{{ item.teacherName || '-' }}</strong>
              <el-tag round>{{ item.feedbackType || 'GENERAL' }}</el-tag>
            </div>
            <p>{{ item.message }}</p>
            <p class="muted-line">
              {{ item.lessonTitle || item.quizTitle || t('phase11.generalFeedback') }}
            </p>
          </el-card>
        </el-timeline-item>
      </el-timeline>

      <el-empty v-if="!loading && !feedbackItems.length" :description="t('phase11.emptyFeedback')" />
    </el-card>
  </div>
</template>
