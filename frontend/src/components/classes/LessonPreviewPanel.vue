<template>
  <div class="page-stack lesson-preview">
    <p v-if="editor.editable" class="panel lesson-preview-note">
      <IconGlyph name="eye" :size="16" />
      <span>{{ t('lessonEditor.preview.note') }}</span>
    </p>

    <article class="panel teacher-card lesson-preview-sheet" :aria-labelledby="headingId">
      <div class="lesson-preview-head">
        <p class="page-eyebrow">
          {{ t('lessonEditor.preview.eyebrow', { classTitle: editor.classTitle, unit: unitLabel }) }}
        </p>
        <h2 :id="headingId" class="lesson-preview-title"><CjkText :text="editor.title" /></h2>
        <p class="type-caption">{{ facts }}</p>
      </div>

      <section class="lesson-preview-section">
        <h3 class="card-title">{{ t('lessonEditor.preview.content') }}</h3>
        <template v-if="editor.video">
          <h4 class="page-eyebrow">{{ t('lessonEditor.preview.video') }}</h4>
          <div class="lesson-preview-video">
            <span class="lesson-preview-video-tile" aria-hidden="true"><IconGlyph name="video" :size="24" /></span>
            <strong class="row-list-title">{{ editor.video.title }}</strong>
          </div>
        </template>
        <template v-if="editor.keyPhrases.length">
          <h4 class="page-eyebrow">{{ t('lessonEditor.preview.phrases') }}</h4>
          <KeyPhraseCards :phrases="editor.keyPhrases" />
        </template>
        <p v-if="!editor.video && !editor.keyPhrases.length" class="type-caption">
          {{ t('lessonEditor.preview.empty') }}
        </p>
      </section>

      <section class="lesson-preview-section">
        <h3 class="card-title">{{ t('lessonEditor.preview.practice') }}</h3>
        <ol v-if="editor.activities.length" class="row-list">
          <li v-for="activity in editor.activities" :key="`${activity.type}-${activity.id}`" class="lesson-preview-row">
            <span class="teacher-tile" aria-hidden="true">{{ activity.number }}</span>
            <span class="row-list-copy">
              <strong class="row-list-title"><CjkText :text="activity.title" /></strong>
              <span class="row-list-meta">
                {{
                  t('lessonEditor.preview.activityFacts', {
                    number: activity.number,
                    type: t(LESSON_ACTIVITY_KEYS[activity.type]),
                  })
                }}
              </span>
            </span>
          </li>
        </ol>
        <p v-else class="type-caption">{{ t('lessonEditor.preview.empty') }}</p>
      </section>

      <section class="lesson-preview-section">
        <h3 class="card-title">{{ t('lessonEditor.preview.quiz') }}</h3>
        <div v-if="quiz" class="row-list-copy">
          <strong class="row-list-title">
            <CjkText :text="t('lessonEditor.preview.quizTitle', { title: editor.title })" />
          </strong>
          <span class="row-list-meta">{{ t('lessonEditor.preview.questions', quiz.questions.length) }}</span>
        </div>
        <p v-else class="type-caption">{{ t('lessonEditor.preview.empty') }}</p>
      </section>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { LessonEditor } from '@/types/classes'
import { LESSON_ACTIVITY_KEYS, lessonQuiz, unitNumber } from '@/utils/teacherLesson'

import KeyPhraseCards from './KeyPhraseCards.vue'

/** The lesson as students will see it once this version is live, with the edits typed so far. */
const props = defineProps<{ editor: LessonEditor }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages })
const headingId = useId()

const quiz = computed(() => lessonQuiz(props.editor))
const unitLabel = computed(() =>
  t('lessonEditor.unitLabel', {
    number: unitNumber(props.editor, props.editor.unitId),
    title: props.editor.unitTitle,
  }),
)
const facts = computed(() =>
  quiz.value
    ? t('lessonEditor.preview.facts', props.editor.activities.length)
    : t('lessonEditor.preview.factsNoQuiz', props.editor.activities.length),
)
</script>
