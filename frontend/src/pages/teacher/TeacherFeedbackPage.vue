<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { feedbackService } from '../../services/feedbackService'
import type { FeedbackCandidate, TeacherFeedbackItem, TeacherFeedbackType } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const loading = ref(true)
const saving = ref(false)
const items = ref<TeacherFeedbackItem[]>([])
const candidates = ref<FeedbackCandidate[]>([])
const form = reactive({
  studentId: 0,
  lessonId: null as number | null,
  quizAttemptId: null as number | null,
  feedbackType: 'GENERAL' as TeacherFeedbackType,
  message: '',
})

async function loadData() {
  loading.value = true
  try {
    const [feedbackItems, candidateItems] = await Promise.all([
      feedbackService.getTeacherFeedback(),
      feedbackService.getTeacherFeedbackCandidates(),
    ])
    items.value = feedbackItems
    candidates.value = candidateItems
    if (!form.studentId && candidateItems.length) {
      const first = candidateItems[0]
      form.studentId = first.studentId
      form.lessonId = first.lessonId
      form.quizAttemptId = first.quizAttemptId
    }
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function submit() {
  saving.value = true
  try {
    await feedbackService.createTeacherFeedback(form)
    form.message = ''
    ElMessage.success(t('phase11.messages.feedbackCreated'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
    await loadData()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.feedbackEyebrow')"
      :title="t('phase11.teacherFeedbackTitle')"
      :description="t('phase11.teacherFeedbackDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('phase11.feedbackFormTitle') }}</p>
          <h2>{{ t('phase11.teacherFeedbackTitle') }}</h2>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item :label="t('phase11.studentLabel')">
          <el-select v-model="form.studentId" class="w-full">
            <el-option
              v-for="candidate in candidates"
              :key="`${candidate.studentId}-${candidate.quizAttemptId ?? 0}`"
              :label="`${candidate.studentName} (${candidate.studentEmail || '-'})`"
              :value="candidate.studentId"
            />
          </el-select>
        </el-form-item>
        <div class="content-toolbar">
          <el-form-item :label="t('content.lesson')" class="content-filter content-filter--wide">
            <el-select v-model="form.lessonId" class="w-full" clearable>
              <el-option v-for="candidate in candidates" :key="`lesson-${candidate.lessonId}`" :label="candidate.lessonTitle || '-'" :value="candidate.lessonId" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('learning.quizTitleLabel')" class="content-filter content-filter--wide">
            <el-select v-model="form.quizAttemptId" class="w-full" clearable>
              <el-option v-for="candidate in candidates" :key="`attempt-${candidate.quizAttemptId}`" :label="candidate.quizTitle || '-'" :value="candidate.quizAttemptId" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('phase11.feedbackType')">
          <el-select v-model="form.feedbackType" class="w-full">
            <el-option label="GENERAL" value="GENERAL" />
            <el-option label="QUIZ" value="QUIZ" />
            <el-option label="LESSON" value="LESSON" />
            <el-option label="PROGRESS" value="PROGRESS" />
            <el-option label="WEAK_AREA" value="WEAK_AREA" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('phase11.feedbackMessage')">
          <el-input v-model="form.message" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">{{ t('phase11.submitFeedback') }}</el-button>
      </el-form>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="items" class="phase-table">
        <el-table-column prop="studentName" :label="t('phase11.studentLabel')" min-width="180" />
        <el-table-column prop="feedbackType" :label="t('phase11.feedbackType')" min-width="160" />
        <el-table-column prop="lessonTitle" :label="t('content.lesson')" min-width="220" />
        <el-table-column prop="message" :label="t('phase11.feedbackMessage')" min-width="320" />
      </el-table>
    </el-card>
  </div>
</template>
