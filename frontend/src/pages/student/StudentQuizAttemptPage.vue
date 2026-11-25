<template>
  <div class="page-stack">
    <template v-if="result">
      <PageHeaderBlock :title="t('classes.lessonQuiz')">
        <template #before>
          <AppBreadcrumb
            :items="[
              { label: t('nav.classes'), to: { name: 'student-classes' } },
              {
                label: result.quiz.classTitle,
                to: { name: 'student-class', params: { classId: result.quiz.classId } },
              },
              { label: result.quiz.lessonTitle, to: classLessonRoute(result.quiz.classId, result.quiz.lessonId) },
              { label: t('classes.lessonQuiz') },
            ]"
          />
        </template>
      </PageHeaderBlock>

      <section class="panel quiz-attempt-summary" data-testid="quiz-result">
        <p class="type-caption">
          {{ t('classPlayer.result.submitted', { when: formatShortDate(result.submittedAt) }) }}
        </p>
        <!-- "See best result" opens the best attempt, which says so (S11.16); another attempt shows its plain score. -->
        <dl class="quiz-attempt-figures">
          <div>
            <dt>
              {{ result.percent >= result.bestPercent ? t('classes.bestScore') : t('classPlayer.result.score') }}
            </dt>
            <dd>
              {{
                t('classPlayer.result.scoreLine', {
                  correct: result.correctCount,
                  total: result.questions.length,
                  percent: result.percent,
                })
              }}
            </dd>
          </div>
        </dl>
      </section>

      <div class="panel">
        <QuizReview :result="result" :title="t('classPlayer.result.review')" />
      </div>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('classes.lessonQuiz') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classes.lessonQuiz')"
      />
      <ClassLoadError
        v-else
        :title="t('classPlayer.quiz.loadErrorTitle')"
        :message="resource.error.value"
        :back-to="{ name: 'student-classes' }"
        :back-label="t('nav.classes')"
        :on-retry="attemptId !== null ? resource.reload : undefined"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import QuizReview from '@/components/classes/QuizReview.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/layout/PageHeaderBlock.vue'
import { usePageRedirect } from '@/composables/usePageNavigation'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useRecordById } from '@/composables/useResource'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { classLessonRoute } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/classes.css'

/** A submitted attempt read in the normal shell: "See best result" on the lesson opens the best one. */
const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
const route = useRoute()

const attemptId = computed(() => parseRecordId(route.params.attemptId))
const resource = useRecordById(attemptId, studentClassesApi.quizResult)
const result = resource.data
usePageRedirect(() => {
  const loaded = result.value
  if (!loaded || loaded.attemptId !== attemptId.value || loaded.quiz.id === parseRecordId(route.params.quizId)) {
    return null
  }
  return {
    name: 'student-quiz-attempt',
    params: { quizId: loaded.quiz.id, attemptId: loaded.attemptId },
    query: route.query,
    hash: route.hash,
  }
})
</script>
