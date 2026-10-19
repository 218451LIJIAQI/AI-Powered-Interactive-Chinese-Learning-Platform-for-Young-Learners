<template>
  <div class="practice-player story-player">
    <img v-if="safeImageUrl" :src="safeImageUrl" alt="" class="story-image" />

    <section class="story-reading" :aria-labelledby="`${baseId}-story`">
      <div class="story-reading-head">
        <h3 :id="`${baseId}-story`" class="practice-section-title">{{ t('studentPractice.storyText') }}</h3>
        <div class="story-toggles">
          <button
            v-if="story.pinyin"
            type="button"
            class="btn btn-ghost btn-sm"
            :aria-pressed="showPinyin"
            :aria-controls="`${baseId}-pinyin`"
            @click="showPinyin = !showPinyin"
          >
            {{ t('studentPractice.showPinyin') }}
          </button>
          <button
            v-if="story.translation"
            type="button"
            class="btn btn-ghost btn-sm"
            :aria-pressed="showTranslation"
            :aria-controls="`${baseId}-translation`"
            @click="showTranslation = !showTranslation"
          >
            {{ t('studentPractice.showTranslation') }}
          </button>
        </div>
      </div>
      <p lang="zh-CN" class="cjk-text story-text">{{ story.storyText }}</p>
      <div v-if="story.pinyin" v-show="showPinyin" :id="`${baseId}-pinyin`" class="story-support">
        <small>{{ t('studentPractice.storyPinyin') }}</small>
        <p>{{ story.pinyin }}</p>
      </div>
      <div v-if="story.translation" v-show="showTranslation" :id="`${baseId}-translation`" class="story-support">
        <small>{{ t('studentPractice.storyTranslation') }}</small>
        <p>{{ story.translation }}</p>
      </div>
    </section>

    <section :aria-labelledby="`${baseId}-questions`" class="story-questions-section">
      <h3 :id="`${baseId}-questions`" class="practice-section-title">{{ t('studentPractice.storyQuestions') }}</h3>
      <p v-if="!story.questions.length" class="practice-note">{{ t('studentPractice.storyNoQuestions') }}</p>
      <ol v-else class="story-questions">
        <li v-for="(question, questionIndex) in story.questions" :key="question.id" class="story-question">
          <p :id="questionId(question.id)" class="story-question-text">
            <span class="practice-section-title">{{
              t('studentPractice.questionNumber', { number: questionIndex + 1 })
            }}</span>
            <span :lang="cjkLang(question.questionText)">{{ question.questionText }}</span>
          </p>
          <div class="story-options" role="group" :aria-labelledby="questionId(question.id)">
            <button
              v-for="option in question.options"
              :key="option.index"
              type="button"
              class="practice-choice"
              data-testid="story-option"
              :aria-pressed="answers[question.id] === option.index"
              :disabled="locked"
              @click="choose(question.id, option.index)"
            >
              <span :class="{ 'cjk-text': cjkLang(option.text) }" :lang="cjkLang(option.text)">{{ option.text }}</span>
            </button>
          </div>
        </li>
      </ol>
    </section>

    <div class="practice-submit-row">
      <span class="practice-progress">{{
        t('studentPractice.progressAnswered', { done: answeredCount, total: story.questions.length })
      }}</span>
      <button
        type="button"
        class="btn btn-primary"
        data-testid="activity-submit"
        :disabled="!ready || locked || submitting"
        :aria-describedby="ready ? undefined : `${baseId}-hint`"
        @click="submit"
      >
        {{ submitting ? t('studentPractice.submitting') : t('studentPractice.submitAnswers') }}
      </button>
    </div>
    <p v-if="!ready && !locked" :id="`${baseId}-hint`" class="practice-progress">
      {{ t('studentPractice.submitHint') }}
    </p>

    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import type { StudentStoryActivityResponse } from '@/types/api'
import {
  buildStoryCompletion,
  cjkLang,
  isStoryReady,
  type ActivitySubmission,
  type StoryAnswers,
} from '@/utils/activityCompletion'
import { safeHttpUrl } from '@/utils/url'

import { usePracticeI18n } from './usePracticeI18n'

const props = defineProps<{
  story: StudentStoryActivityResponse
  locked?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ActivitySubmission] }>()

const { t } = usePracticeI18n()
const baseId = `story-${useId()}`
const showPinyin = ref(false)
const showTranslation = ref(false)
const answers = ref<StoryAnswers>({})
const announcement = ref('')

const safeImageUrl = computed(() => safeHttpUrl(props.story.imageUrl, { allowRelative: true }))
const ready = computed(() => isStoryReady(props.story.questions, answers.value))
const answeredCount = computed(() => props.story.questions.filter((q) => answers.value[q.id] !== undefined).length)

function questionId(id: number): string {
  return `${baseId}-q-${id}`
}

function choose(id: number, optionIndex: number): void {
  answers.value = { ...answers.value, [id]: optionIndex }
  announcement.value = t('studentPractice.progressAnswered', {
    done: answeredCount.value,
    total: props.story.questions.length,
  })
}

function submit(): void {
  if (!ready.value || props.locked) return
  emit('submit', { type: 'STORY_ACTIVITY', body: buildStoryCompletion(props.story.questions, answers.value) })
}
</script>

<style scoped src="./practicePlayer.css"></style>
<style scoped>
.story-image {
  width: 100%;
  max-height: 220px;
  border-radius: var(--radius-lg);
  object-fit: cover;
}

.story-reading {
  display: grid;
  gap: var(--space-2-5);
  padding: var(--space-3-5) var(--space-4);
  border: 1px solid var(--color-accent-200);
  border-radius: var(--radius-lg);
  background: var(--color-accent-50);
}

.story-reading-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.story-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.story-toggles .btn[aria-pressed='true'] {
  border-color: var(--color-primary-500);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.story-text {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: 1.9;
  overflow-wrap: anywhere;
}

.story-support small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.story-support p {
  margin: var(--space-0-5) 0 0;
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
}

.story-questions-section {
  display: grid;
  gap: var(--space-2-5);
}

.story-questions {
  display: grid;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.story-question {
  display: grid;
  gap: var(--space-2);
}

.story-question-text {
  display: grid;
  gap: var(--space-0-5);
  margin: 0;
  font-weight: var(--font-weight-bold);
}

.story-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
