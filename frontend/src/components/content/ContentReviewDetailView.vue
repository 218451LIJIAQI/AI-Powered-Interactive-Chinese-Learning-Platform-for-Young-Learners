<template>
  <div class="review-detail" data-testid="content-review-detail">
    <template v-if="submission">
      <dl class="review-detail-grid">
        <div>
          <dt>{{ t('adminContentReview.level') }}</dt>
          <dd>{{ submission.level?.name || t('adminContentReview.noValue') }}</dd>
        </div>
        <div>
          <dt>{{ t('adminContentReview.category') }}</dt>
          <dd>{{ submission.category?.name || t('adminContentReview.noValue') }}</dd>
        </div>
        <div v-if="submission.learningType">
          <dt>{{ t('adminContentReview.learningType') }}</dt>
          <dd>{{ t(`adminContentReview.learningType${submission.learningType}`) }}</dd>
        </div>
        <div v-if="submission.moduleTitle">
          <dt>{{ t('adminContentReview.module') }}</dt>
          <dd>{{ submission.moduleTitle }}</dd>
        </div>
      </dl>
      <section class="review-detail-block">
        <strong>{{ t('adminContentReview.descriptionLabel') }}</strong>
        <p>{{ submission.description || t('adminContentReview.noValue') }}</p>
      </section>
      <section v-if="response.item.type === 'LESSON'" class="review-detail-block">
        <strong>{{ t('adminContentReview.lessonBody') }}</strong>
        <p class="review-detail-body" data-testid="content-review-lesson-body">
          {{ submission.basicContent || t('adminContentReview.noLessonBody') }}
        </p>
      </section>
      <section v-if="response.item.type === 'PACKAGE'" class="review-detail-block">
        <strong>{{ t('adminContentReview.courseItems') }}</strong>
        <ol v-if="submission.packageItems?.length" class="review-detail-list">
          <li v-for="item in submission.packageItems" :key="item.id">
            {{ item.titleSnapshot }} · {{ courseItemTypeLabel(item.itemType) }}
          </li>
        </ol>
        <p v-else>{{ t('adminContentReview.noCourseItems') }}</p>
      </section>
    </template>

    <template v-else-if="quiz">
      <dl class="review-detail-grid">
        <div>
          <dt>{{ t('adminContentReview.lesson') }}</dt>
          <dd>{{ quiz.lessonTitle }}</dd>
        </div>
        <div>
          <dt>{{ t('adminContentReview.timeLimit') }}</dt>
          <dd>
            {{
              quiz.timeLimitMinutes
                ? t('adminContentReview.minutes', { count: quiz.timeLimitMinutes })
                : t('adminContentReview.noTimeLimit')
            }}
          </dd>
        </div>
        <div>
          <dt>{{ t('adminContentReview.totalPoints') }}</dt>
          <dd>{{ quiz.totalPoints }}</dd>
        </div>
      </dl>
      <section v-if="quiz.description" class="review-detail-block">
        <strong>{{ t('adminContentReview.descriptionLabel') }}</strong>
        <p>{{ quiz.description }}</p>
      </section>
      <section class="review-detail-block">
        <strong>{{ t('adminContentReview.questions') }}</strong>
        <p v-if="!quiz.questions.length">{{ t('adminContentReview.noQuestions') }}</p>
        <ol v-else class="review-detail-list review-detail-questions">
          <li v-for="question in sortedQuestions" :key="question.id">
            <div class="row-between u-gap-10">
              <strong>{{ question.questionText }}</strong>
              <small>{{ t('adminContentReview.points', { count: question.points }) }}</small>
            </div>
            <ul v-if="question.options.length" class="review-detail-options">
              <li v-for="option in question.options" :key="option.id" :class="{ 'is-correct': option.correctOption }">
                {{ option.optionText }}
                <span v-if="option.correctOption" class="review-detail-correct">
                  {{ t('adminContentReview.correctOption') }}
                </span>
              </li>
            </ul>
            <p v-if="question.correctAnswerText">
              <b>{{ t('adminContentReview.correctAnswer') }}:</b> {{ question.correctAnswerText }}
            </p>
            <p v-if="question.explanation" class="muted">
              <b>{{ t('adminContentReview.explanation') }}:</b> {{ question.explanation }}
            </p>
          </li>
        </ol>
      </section>
    </template>

    <template v-else-if="activity">
      <dl class="review-detail-grid">
        <div v-for="field in activityFields" :key="field.label">
          <dt>{{ field.label }}</dt>
          <dd class="review-detail-body">{{ field.value || t('adminContentReview.noValue') }}</dd>
        </div>
      </dl>
      <section v-if="activityRows.length" class="review-detail-block">
        <strong>{{ activityRowsTitle }}</strong>
        <ol class="review-detail-list">
          <li v-for="row in activityRows" :key="row.key" class="review-detail-body">{{ row.text }}</li>
        </ol>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminContentReviewMessages } from '@/plugins/adminContentReviewMessages'
import type {
  AdminDragDropActivityResponse,
  AdminFlashcardResponse,
  AdminListeningExerciseResponse,
  AdminMatchingGameResponse,
  AdminStoryActivityResponse,
  AdminTracingExerciseResponse,
  ContentReviewDetailResponse,
  ContentSubmissionDetailResponse,
  PackageItemType,
  TeacherQuizResponse,
} from '@/types/api'
import { optionsPayloadToText } from '@/utils/activityForm'

/** Type-specific reviewer view: lesson body, quiz questions with correct answers, or activity fields. */
const props = defineProps<{ response: ContentReviewDetailResponse }>()

const { t } = useI18n({
  messages: {
    en: { adminContentReview: adminContentReviewMessages.en },
    ms: { adminContentReview: adminContentReviewMessages.ms },
    'zh-CN': { adminContentReview: adminContentReviewMessages['zh-CN'] },
  },
})

const type = computed(() => props.response.item.type)
const submission = computed(() =>
  type.value === 'PACKAGE' || type.value === 'MODULE' || type.value === 'LESSON'
    ? (props.response.detail as ContentSubmissionDetailResponse)
    : null,
)
const quiz = computed(() => (type.value === 'QUIZ' ? (props.response.detail as TeacherQuizResponse) : null))
const activity = computed(() => (submission.value || quiz.value ? null : props.response.detail))
const sortedQuestions = computed(() =>
  [...(quiz.value?.questions ?? [])].sort((a, b) => a.displayOrder - b.displayOrder),
)

type Field = { label: string; value: string | null | undefined }

const activityFields = computed<Field[]>(() => {
  const detail = activity.value
  if (!detail) return []
  switch (type.value) {
    case 'FLASHCARD': {
      const item = detail as AdminFlashcardResponse
      return [
        { label: t('adminContentReview.frontText'), value: item.frontText },
        { label: t('adminContentReview.backText'), value: item.backText },
        { label: t('adminContentReview.pinyin'), value: item.pinyin },
        { label: t('adminContentReview.englishMeaning'), value: item.englishMeaning },
        { label: t('adminContentReview.malayMeaning'), value: item.malayMeaning },
        { label: t('adminContentReview.media'), value: [item.imageUrl, item.audioUrl].filter(Boolean).join('\n') },
      ]
    }
    case 'LISTENING_EXERCISE': {
      const item = detail as AdminListeningExerciseResponse
      return [
        { label: t('adminContentReview.audioUrl'), value: item.audioUrl },
        { label: t('adminContentReview.questionPayload'), value: item.questionPayload },
      ]
    }
    case 'TRACING_EXERCISE': {
      const item = detail as AdminTracingExerciseResponse
      return [
        { label: t('adminContentReview.targetCharacter'), value: item.targetCharacter },
        { label: t('adminContentReview.pinyin'), value: item.pinyin },
        { label: t('adminContentReview.meaning'), value: item.meaning },
        { label: t('adminContentReview.worksheetUrl'), value: item.worksheetUrl },
        { label: t('adminContentReview.instructions'), value: item.instructions },
      ]
    }
    case 'DRAG_DROP_ACTIVITY': {
      const item = detail as AdminDragDropActivityResponse
      return [{ label: t('adminContentReview.instructions'), value: item.instructions }]
    }
    case 'STORY_ACTIVITY': {
      const item = detail as AdminStoryActivityResponse
      return [
        { label: t('adminContentReview.instructions'), value: item.instructions },
        { label: t('adminContentReview.storyText'), value: item.storyText },
        { label: t('adminContentReview.pinyin'), value: item.pinyin },
        { label: t('adminContentReview.translation'), value: item.translation },
      ]
    }
    default:
      return []
  }
})

const activityRowsTitle = computed(() => {
  if (type.value === 'MATCHING_GAME') return t('adminContentReview.pairs')
  if (type.value === 'STORY_ACTIVITY') return t('adminContentReview.questions')
  return t('adminContentReview.items')
})

const activityRows = computed<Array<{ key: string; text: string }>>(() => {
  const detail = activity.value
  if (!detail) return []
  switch (type.value) {
    case 'MATCHING_GAME':
      return (detail as AdminMatchingGameResponse).items.map((item) => ({
        key: `pair-${item.id}`,
        text: `${item.leftText}${item.pinyin ? ` (${item.pinyin})` : ''} = ${item.rightText}`,
      }))
    case 'DRAG_DROP_ACTIVITY':
      return (detail as AdminDragDropActivityResponse).items.map((item) => ({
        key: `drag-${item.id}`,
        text: `${item.dragText}${item.pinyin ? ` (${item.pinyin})` : ''} → ${item.targetZone}`,
      }))
    case 'STORY_ACTIVITY':
      return (detail as AdminStoryActivityResponse).questions.map((question) => ({
        key: `story-${question.id}`,
        text: [
          question.questionText,
          `${t('adminContentReview.options')}: ${optionsPayloadToText(question.optionsPayload).split('\n').join(' / ')}`,
          question.explanation ? `${t('adminContentReview.explanation')}: ${question.explanation}` : '',
        ]
          .filter(Boolean)
          .join('\n'),
      }))
    default:
      return []
  }
})

function courseItemTypeLabel(itemType: PackageItemType): string {
  return itemType === 'MODULE' ? t('adminContentReview.typeModule') : t('adminContentReview.typeLesson')
}
</script>

<style scoped>
.review-detail {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.review-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.review-detail-grid > div,
.review-detail-block {
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.review-detail-grid dt,
.review-detail-block > strong {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.review-detail-grid dd {
  margin: var(--space-1) 0 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  overflow-wrap: anywhere;
}

.review-detail-block p {
  margin: var(--space-1-5) 0 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.review-detail-body {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: var(--line-height-relaxed);
}

.review-detail-list {
  display: grid;
  gap: var(--space-2-5);
  margin: var(--space-2) 0 0;
  padding-left: var(--space-5);
  font-size: var(--font-size-md);
}

.review-detail-questions strong {
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.review-detail-questions small {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.review-detail-options {
  display: grid;
  gap: var(--space-1);
  margin: var(--space-1-5) 0;
  padding-left: var(--space-5);
}

.review-detail-options li.is-correct {
  color: var(--color-success-text);
  font-weight: var(--font-weight-bold);
}

.review-detail-correct {
  display: inline-flex;
  align-items: center;
  margin-left: var(--space-1-5);
  padding: 0 var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--color-success-soft);
  color: var(--color-success-text);
  font-size: var(--font-size-xs);
}

@media (max-width: 640px) {
  .review-detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
