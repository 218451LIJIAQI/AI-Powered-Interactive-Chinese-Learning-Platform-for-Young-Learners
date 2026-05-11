<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { studentLearningService } from '../../services/studentLearningService'
import type { StudentQuiz } from '../../types/learning'

const router = useRouter()
const { t } = useI18n()

const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const quiz = ref<StudentQuiz | null>(null)
const answers = reactive<Record<number, string>>({})

const quizId = useNumericRouteParam('quizId')

async function loadQuiz() {
  loading.value = true
  try {
    quiz.value = await studentLearningService.getQuiz(quizId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('content.messages.saveFailed')
  } finally {
    loading.value = false
  }
}

async function submitQuiz() {
  if (!quiz.value) {
    return
  }

  const payload = {
    answers: quiz.value.questions.map((question) => ({
      questionId: question.id,
      answerText: answers[question.id] ?? '',
    })),
  }

  if (payload.answers.some((answer) => !answer.answerText.trim())) {
    ElMessage.error(t('learning.validation.answerEveryQuestion'))
    return
  }

  submitting.value = true
  try {
    const result = await studentLearningService.submitQuizAttempt(quizId.value, payload)
    ElMessage.success(t('learning.messages.quizSubmitted', { score: result.score, maxScore: result.maxScore }))
    router.push({ name: 'student-quiz-result', params: { attemptId: result.attemptId } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('learning.messages.quizSubmitFailed'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadQuiz()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      kid
      show-mascot
      :eyebrow="t('learning.quizzesEyebrow')"
      :title="quiz?.title || t('kid.quizTimeFun')"
      :description="quiz?.description || t('learning.quizPageDescription')"
      :mascot-message="t('kid.quizTimeFun')"
      mascot-holding="题"
    />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <div v-if="quiz" class="dashboard-stack">
          <el-card
            v-for="question in quiz.questions"
            :key="question.id"
            class="dashboard-panel"
            shadow="hover"
          >
            <div class="panel-header">
              <div>
                <p class="section-label">{{ question.questionType }}</p>
                <h2>{{ question.questionText }}</h2>
              </div>
              <el-tag round>{{ question.points }} {{ t('learning.points') }}</el-tag>
            </div>

            <div v-if="question.options.length" class="student-option-list">
              <el-radio-group v-model="answers[question.id]" class="student-option-group">
                <el-radio
                  v-for="option in question.options"
                  :key="option.id"
                  :value="option.optionText"
                  border
                  size="large"
                >
                  {{ option.optionText }}
                </el-radio>
              </el-radio-group>
            </div>
            <el-input
              v-else
              v-model="answers[question.id]"
              type="textarea"
              :rows="2"
              :placeholder="t('learning.typeYourAnswer')"
            />
          </el-card>

          <div class="student-practice-card__actions">
            <el-button type="primary" round size="large" :loading="submitting" @click="submitQuiz">🚀 {{ t('learning.submitQuiz') }}</el-button>
          </div>
        </div>
      </template>
    </el-skeleton>
  </div>
</template>
