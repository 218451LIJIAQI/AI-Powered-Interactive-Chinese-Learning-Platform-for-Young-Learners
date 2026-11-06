<template>
  <div class="page-stack">
    <template v-if="result">
      <PageHeaderBlock :title="t('classPlayer.quiz.title', { title: result.quiz.lessonTitle })">
        <template #before>
          <AppBreadcrumb
            :items="[
              { label: t('classes.classes'), to: { name: 'student-classes' } },
              {
                label: result.quiz.classTitle,
                to: { name: 'student-class', params: { classId: result.quiz.classId } },
              },
              { label: result.quiz.lessonTitle, to: lessonRoute },
              { label: t('classPlayer.quiz.topLabelUntimed') },
            ]"
          />
        </template>
      </PageHeaderBlock>

      <section class="panel quiz-attempt-summary" data-testid="quiz-result">
        <p class="type-caption">
          {{ t('classPlayer.result.submitted', { when: formatShortDate(result.submittedAt) }) }}
        </p>
        <dl class="quiz-attempt-figures">
          <div>
            <dt>{{ t('classPlayer.result.score') }}</dt>
            <dd>
              {{ t('classPlayer.result.scoreLine', { correct: result.correctCount, total: result.questions.length }) }}
            </dd>
          </div>
          <div>
            <dt>{{ t('classPlayer.result.best') }}</dt>
            <dd>{{ result.bestPercent ?? result.percent }}%</dd>
          </div>
        </dl>
        <p class="quiz-attempt-status">
          <IconGlyph name="target" :size="16" />
          {{ t('classPlayer.result.lessonStatus', { lesson: result.quiz.lessonTitle, status: lessonStatus }) }}
        </p>
      </section>

      <div class="panel">
        <QuizReview :result="result" :title="t('classPlayer.result.review')" />
      </div>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('classPlayer.quiz.topLabelUntimed') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classPlayer.quiz.topLabelUntimed')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('classPlayer.quiz.loadErrorTitle')"
        :message="resource.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">{{ t('classes.classes') }}</RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import QuizReview from '@/components/classes/QuizReview.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import { formatShortDate } from '@/utils/format'
import '@/styles/classes.css'

/** An attempt submitted earlier, read in the normal shell ("See latest result" on the lesson, Open on feedback). */
const { t } = useLocalMessages({
  classPlayer: classPlayerMessages,
  classes: classesMessages,
  studentClasses: studentClassesMessages,
})
const route = useRoute()

const attemptId = computed(() => Number(route.params.attemptId))
const resource = useResource(() => studentClassesApi.quizResult(attemptId.value))
const result = computed(() => resource.data.value)
watch(attemptId, () => void resource.reload())

const lessonRoute = computed(() => ({
  name: 'student-class-lesson',
  params: { classId: result.value?.quiz.classId ?? 0, lessonId: result.value?.quiz.lessonId ?? 0 },
}))

const lessonStatus = computed(() => {
  const status = result.value?.lessonStatus
  if (status === 'IN_PROGRESS') return t('studentClasses.lessonState.IN_PROGRESS')
  if (status === 'NOT_STARTED') return t('studentClasses.lessonState.NOT_STARTED')
  return t('studentClasses.lessonState.COMPLETED')
})
</script>
