<template>
  <div class="quiz-review">
    <section v-if="result.feedback.length" class="quiz-review-feedback" aria-labelledby="quiz-feedback-title">
      <h2 id="quiz-feedback-title" class="card-title">{{ t('classPlayer.result.feedback') }}</h2>
      <ul class="row-list">
        <li v-for="item in result.feedback" :key="item.id" class="quiz-review-note">
          <PortraitAvatar :size="32" :name="item.teacherName" />
          <span class="row-list-copy">
            <span class="quiz-review-note-message"><CjkText :text="item.message" /></span>
            <span class="row-list-meta">{{ item.teacherName }} · {{ formatDateTime(item.createdAt) }}</span>
          </span>
        </li>
      </ul>
    </section>

    <section class="quiz-review-questions" aria-labelledby="quiz-review-title">
      <div class="class-card-head">
        <div class="row-list-copy">
          <h2 id="quiz-review-title" class="card-title">{{ title }}</h2>
          <span class="row-list-meta">
            {{ t('classPlayer.result.reviewFacts', { wrong: result.wrongCount, correct: result.correctCount }) }}
          </span>
        </div>
        <label class="choice quiz-review-toggle">
          <input v-model="showAll" type="checkbox" />
          {{ t('classPlayer.result.showAll') }}
        </label>
      </div>
      <p v-if="!reviewed.length" class="callout is-success is-compact">
        <IconGlyph name="check" :size="16" /><span>{{ t('classPlayer.result.allCorrect') }}</span>
      </p>
      <ol v-else class="row-list">
        <li
          v-for="question in reviewed"
          :key="question.questionId"
          class="quiz-review-item"
          :class="question.correct ? 'is-correct' : 'is-wrong'"
        >
          <div class="quiz-review-item-head">
            <strong class="row-list-title">{{ question.number }}. <CjkText :text="question.text" /></strong>
            <StatusPill
              :label="question.correct ? t('classPlayer.result.correct') : t('classPlayer.result.incorrect')"
              :tone="question.correct ? 'success' : 'danger'"
              :icon="question.correct ? 'check' : 'close'"
            />
          </div>
          <p class="quiz-review-line">
            {{ t('classPlayer.result.yourAnswer') }}
            <strong><CjkText :text="oneLine(question.yourAnswer) || t('classPlayer.result.noAnswer')" /></strong>
            <template v-if="!question.correct && question.correctAnswer">
              · {{ t('classPlayer.result.correctAnswer') }}
              <strong><CjkText :text="oneLine(question.correctAnswer)" /></strong>
            </template>
          </p>
          <p v-if="question.explanation" class="quiz-review-line is-muted"><CjkText :text="question.explanation" /></p>
          <RouterLink
            v-if="!question.correct"
            class="btn btn-link quiz-review-ask"
            :to="{
              name: 'student-ai',
              params: { tab: 'ask' },
              query: {
                q: t('classPlayer.result.askQuestion', {
                  question: question.text,
                  answer: question.yourAnswer ?? '',
                  correct: question.correctAnswer ?? '',
                }),
              },
            }"
          >
            <IconGlyph name="ai" :size="16" />{{ t('classPlayer.result.askSparkBot') }}
          </RouterLink>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { QuizResult } from '@/types/classes'
import { formatDateTime } from '@/utils/format'

/**
 * The review under a quiz result, in the focus result and on an earlier attempt's page: the teacher's feedback if there
 * is any, then the wrong answers with what was right, the explanation and a question for SparkBot. Show all questions
 * adds the right ones.
 */
const props = defineProps<{ result: QuizResult; title: string }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })
const showAll = ref(false)

const reviewed = computed(() =>
  showAll.value ? props.result.questions : props.result.questions.filter((question) => !question.correct),
)

/** An answer can hold its pinyin on a second line; here it runs on. */
function oneLine(answer: string | null): string {
  return (answer ?? '').replace(/\s*\n\s*/g, ' ')
}
</script>
