<template>
  <section class="lesson-preview" :class="{ 'is-compact': compact }" :aria-labelledby="headingId">
    <div class="lesson-preview-head">
      <h2 :id="headingId" class="lesson-preview-heading">
        <IconGlyph name="eye" :size="14" />
        {{ compact ? t('lessonEditor.preview.live') : t('lessonEditor.preview.title') }}
      </h2>
      <p class="type-caption">
        {{ compact ? t('lessonEditor.preview.liveHint') : t('lessonEditor.preview.subtitle') }}
      </p>
    </div>
    <article class="lesson-preview-stage">
      <p class="type-caption">
        {{ editor.unitTitle
        }}<template v-if="editor.minutes"> · {{ t('classes.minutes', { count: editor.minutes }) }}</template>
      </p>
      <h3 class="lesson-preview-title"><CjkText :text="editor.title" /></h3>
      <p class="lesson-preview-summary" :class="{ 'is-empty': !editor.summary }">
        <CjkText :text="editor.summary || t('lessonEditor.preview.noSummary')" />
      </p>
      <p v-if="editor.video" class="lesson-preview-video">
        <IconGlyph name="video" :size="18" />
        <span>
          {{ editor.video.title }}
          <template v-if="editor.video.durationSeconds"> · {{ formatClock(editor.video.durationSeconds) }}</template>
        </span>
      </p>
      <ul v-if="compact && editor.keyPhrases.length" class="lesson-preview-phrases">
        <li v-for="(phrase, index) in editor.keyPhrases" :key="index">
          <span class="cjk-text" lang="zh-CN">{{ phrase.chinese }}</span>
          <span class="type-caption">{{ [phrase.pinyin, phrase.meaning].filter(Boolean).join(' · ') }}</span>
        </li>
      </ul>
      <KeyPhraseCards v-else-if="editor.keyPhrases.length" :phrases="editor.keyPhrases" />

      <template v-if="!compact">
        <h4 class="lesson-preview-label">{{ t('lessonEditor.preview.activities') }}</h4>
        <ol class="lesson-preview-list">
          <li v-for="activity in editor.activities" :key="`${activity.type}-${activity.id}`">
            <span class="lesson-preview-step" aria-hidden="true">{{ activity.number }}</span>
            <span class="lesson-preview-item">
              <strong><CjkText :text="activity.title" /></strong>
              <span class="type-caption">{{ t(ACTIVITY_TYPE_KEYS[activity.type]) }}</span>
            </span>
            <span v-if="activity.stage" class="tag">{{ t(STAGE_KEYS[activity.stage]) }}</span>
          </li>
        </ol>

        <h4 class="lesson-preview-label">{{ t('lessonEditor.preview.quizzes') }}</h4>
        <ul class="lesson-preview-list">
          <li v-for="quiz in editor.quizzes" :key="quiz.id">
            <span class="lesson-preview-step" aria-hidden="true"><IconGlyph name="quiz" :size="16" /></span>
            <span class="lesson-preview-item">
              <strong>{{ quiz.title }}</strong>
              <span class="type-caption">
                {{
                  t('lessonEditor.preview.quizFacts', {
                    questions: t('classes.questions', quiz.questions.length),
                    marks: t('classes.marks', quiz.totalPoints),
                  })
                }}
              </span>
            </span>
            <span v-if="quiz.stage" class="tag">{{ t(STAGE_KEYS[quiz.stage]) }}</span>
          </li>
        </ul>
      </template>
    </article>
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { LessonEditor } from '@/types/classes'
import { ACTIVITY_TYPE_KEYS, STAGE_KEYS } from '@/utils/classes'
import { formatClock } from '@/utils/format'

import KeyPhraseCards from './KeyPhraseCards.vue'

/**
 * The lesson as students will see it once this version is live, with the edits typed so far. `compact` is the live
 * preview beside the content editor (the lesson's opening only); the Preview tab shows the whole lesson.
 */
withDefaults(defineProps<{ editor: LessonEditor; compact?: boolean }>(), { compact: false })

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const headingId = useId()
</script>
