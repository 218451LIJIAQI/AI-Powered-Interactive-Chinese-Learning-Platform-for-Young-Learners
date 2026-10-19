<template>
  <div class="ai-story" data-testid="ai-story-reader">
    <strong class="ai-story-title"><CjkText :text="story.title" /></strong>
    <p class="ai-story-text" lang="zh-CN" data-testid="ai-story-chinese">{{ story.chineseStory }}</p>

    <template v-if="pinyin">
      <button
        class="btn btn-secondary btn-sm ai-story-toggle"
        type="button"
        :aria-pressed="showPinyin"
        :aria-controls="pinyinId"
        data-testid="ai-story-pinyin-toggle"
        @click="showPinyin = !showPinyin"
      >
        {{ showPinyin ? t('studentAi.hidePinyin') : t('studentAi.showPinyin') }}
      </button>
      <p v-show="showPinyin" :id="pinyinId" class="ai-story-pinyin" data-testid="ai-story-pinyin">{{ pinyin }}</p>
    </template>

    <p
      v-for="translation in translations"
      :key="translation.language"
      class="ai-story-translation"
      :lang="translation.language"
      :data-testid="`ai-story-translation-${translation.language}`"
    >
      <strong>{{
        translation.language === 'ms' ? t('studentAi.malayTranslation') : t('studentAi.englishTranslation')
      }}</strong>
      <span>{{ translation.text }}</span>
    </p>

    <section v-if="vocabulary.length" class="ai-story-section" :aria-labelledby="`${baseId}-vocab`">
      <strong :id="`${baseId}-vocab`" class="ai-story-heading">{{ t('studentAi.storyVocabulary') }}</strong>
      <div class="ai-story-table-wrap">
        <table class="ai-story-table" data-testid="ai-story-vocabulary">
          <thead>
            <tr>
              <th scope="col">{{ t('studentAi.vocabWord') }}</th>
              <th scope="col">{{ t('studentAi.vocabPinyin') }}</th>
              <th scope="col">{{ t('studentAi.vocabMeaning') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in vocabulary" :key="index" data-testid="ai-story-vocabulary-item">
              <td lang="zh-CN">{{ item.chineseWord }}</td>
              <td>{{ item.pinyin }}</td>
              <td>{{ item.meaning }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="questions.length" class="ai-story-section" :aria-labelledby="`${baseId}-questions`">
      <strong :id="`${baseId}-questions`" class="ai-story-heading">{{ t('studentAi.comprehensionQuestions') }}</strong>
      <ol class="ai-story-questions">
        <li v-for="(question, index) in questions" :key="index" data-testid="ai-story-question">
          <span><CjkText :text="question.question" /></span>
          <div
            v-if="question.options.length"
            class="ai-story-options"
            role="group"
            :aria-label="t('studentAi.storyChoices', { number: index + 1 })"
          >
            <button
              v-for="(option, optionIndex) in question.options"
              :key="optionIndex"
              type="button"
              class="ai-story-option"
              :class="{
                'is-selected': picks[index] === optionIndex,
                'is-answer': revealed[index] && question.answerIndexes.includes(optionIndex),
              }"
              :aria-pressed="picks[index] === optionIndex"
              data-testid="ai-story-option"
              @click="picks[index] = optionIndex"
            >
              <CjkText :text="option" />
            </button>
          </div>
          <button
            class="btn btn-secondary btn-sm ai-story-toggle"
            type="button"
            :aria-expanded="Boolean(revealed[index])"
            :aria-controls="`${baseId}-answer-${index}`"
            data-testid="ai-story-reveal"
            @click="revealed[index] = !revealed[index]"
          >
            {{ revealed[index] ? t('studentAi.hideAnswer') : t('studentAi.revealAnswer') }}
          </button>
          <div :id="`${baseId}-answer-${index}`" class="ai-story-answer" aria-live="polite">
            <template v-if="revealed[index]">
              <strong data-testid="ai-story-answer"
                ><CjkText :text="t('studentAi.storyAnswer', { answer: question.answer })"
              /></strong>
              <small v-if="question.explanation"><CjkText :text="question.explanation" /></small>
              <small v-if="picks[index] !== undefined" data-testid="ai-story-pick-result">{{
                question.answerIndexes.includes(picks[index] as number)
                  ? t('studentAi.storyChoiceCorrect')
                  : t('studentAi.storyChoiceIncorrect')
              }}</small>
            </template>
          </div>
        </li>
      </ol>
    </section>

    <small v-if="story.encouragement" class="ai-story-encouragement">{{ story.encouragement }}</small>
    <p class="ai-story-note" role="note">{{ t('studentAi.storyReadingNote') }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import CjkText from '@/components/learning/CjkText.vue'
import { studentAiHelperMessages } from '@/plugins/studentAiHelperMessages'
import type { AiStoryGeneratorResponse, AppLanguage } from '@/types/api'
import { correctOptionIndexes, storyTranslations, storyVocabularyRows } from '@/utils/aiPracticeQuiz'

/** Full AI-generated reading story (FR-AIGEN-08): story, pinyin, translations, vocabulary and comprehension. */
const props = defineProps<{ story: AiStoryGeneratorResponse; language: AppLanguage }>()

const { t } = useI18n({
  messages: {
    en: { studentAi: studentAiHelperMessages.en },
    ms: { studentAi: studentAiHelperMessages.ms },
    'zh-CN': { studentAi: studentAiHelperMessages['zh-CN'] },
  },
})

const baseId = `ai-story-${useId()}`
const pinyinId = `${baseId}-pinyin`
const showPinyin = ref(true)
const picks = reactive<Record<number, number>>({})
const revealed = reactive<Record<number, boolean>>({})

const pinyin = computed(() => props.story.pinyin?.trim() ?? '')
const translations = computed(() => storyTranslations(props.story, props.language))
const vocabulary = computed(() => storyVocabularyRows(props.story, props.language))
const questions = computed(() =>
  (props.story.comprehensionQuestions ?? [])
    .filter((question) => question.question?.trim())
    .map((question) => {
      const options = (question.options ?? []).map((option) => option.trim()).filter(Boolean)
      return {
        question: question.question?.trim() ?? '',
        options,
        answer: question.answer?.trim() ?? '',
        explanation: question.explanation?.trim() ?? '',
        answerIndexes: correctOptionIndexes(options, question.answer),
      }
    }),
)

watch(
  () => props.story,
  () => {
    showPinyin.value = true
    for (const key of Object.keys(picks)) delete picks[Number(key)]
    for (const key of Object.keys(revealed)) delete revealed[Number(key)]
  },
)
</script>

<style scoped>
.ai-story {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.ai-story p {
  margin: 0;
}

/* Host cards may shrink nested spans; keep the reader's own text at its intended size. */
.ai-story :deep(span) {
  color: inherit;
  font-size: inherit;
}

.ai-story-title {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.ai-story-text {
  color: var(--text-primary);
  font-family: var(--font-cjk);
  font-size: var(--font-size-lg);
  line-height: var(--line-height-relaxed);
  overflow-wrap: anywhere;
}

.ai-story-pinyin,
.ai-story-translation {
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.ai-story-pinyin {
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
}

.ai-story-translation {
  display: grid;
  gap: var(--space-0-5);
}

.ai-story-translation strong,
.ai-story-heading {
  color: var(--text-primary);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.ai-story-toggle {
  justify-self: start;
}

.ai-story-section {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-subtle);
}

.ai-story-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.ai-story-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.ai-story-table th,
.ai-story-table td {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
  vertical-align: top;
}

.ai-story-table tbody tr:last-child td {
  border-bottom: 0;
}

.ai-story-table th {
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.ai-story-table td:first-child {
  color: var(--text-primary);
  font-family: var(--font-cjk);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
}

.ai-story-questions {
  display: grid;
  gap: var(--space-4);
  margin: 0;
  padding-left: var(--space-5);
}

.ai-story-questions li {
  display: grid;
  gap: var(--space-2);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.ai-story-options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ai-story-option {
  min-height: var(--control-height-md);
  padding: var(--space-1-5) var(--space-3);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-regular);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ai-story-option:hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.ai-story-option.is-selected {
  border-color: var(--color-primary-500);
  background: var(--surface-selected);
  font-weight: var(--font-weight-bold);
}

.ai-story-option.is-answer {
  border-color: var(--color-success);
  box-shadow: inset 0 0 0 1px var(--color-success);
}

.ai-story-answer {
  display: grid;
  gap: var(--space-0-5);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-regular);
}

.ai-story-answer strong {
  color: var(--color-success-text);
}

.ai-story-encouragement,
.ai-story-note {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

@media (max-width: 640px) {
  .ai-story-option {
    min-height: var(--tap-target-min);
  }
}
</style>
