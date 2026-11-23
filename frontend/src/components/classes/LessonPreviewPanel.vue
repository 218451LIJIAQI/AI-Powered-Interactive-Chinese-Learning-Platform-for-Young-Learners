<template>
  <div class="page-stack">
    <section v-if="!editor.video && !editor.keyPhrases.length" class="panel" aria-labelledby="lesson-preview-empty">
      <h2 id="lesson-preview-empty" class="sr-only">{{ t('lessonEditor.tabs.preview') }}</h2>
      <StatePanel
        tone="empty"
        icon="eye"
        :title="t('lessonEditor.preview.emptyTitle')"
        :message="t('lessonEditor.preview.emptyMessage')"
      >
        <template #actions>
          <RouterLink class="btn btn-secondary" :to="contentRoute">{{
            t('lessonEditor.preview.goToContent')
          }}</RouterLink>
        </template>
      </StatePanel>
    </section>

    <template v-else>
      <p class="panel lesson-preview-note">
        <IconGlyph name="eye" :size="16" />
        <span>{{ t('lessonEditor.preview.note') }}</span>
      </p>

      <article class="panel authoring-card lesson-preview-sheet" :aria-labelledby="headingId">
        <div class="lesson-preview-head">
          <p class="page-eyebrow">
            {{ t('lessonEditor.preview.eyebrow', { classTitle: editor.classTitle, unit: editor.unitTitle }) }}
          </p>
          <h2 :id="headingId" class="lesson-preview-title"><CjkText :text="editor.title" /></h2>
          <p class="type-caption">{{ facts }}</p>
        </div>

        <section class="lesson-preview-section">
          <h3 class="card-title">{{ t('lessonEditor.tabs.content') }}</h3>
          <template v-if="editor.video">
            <h4 class="page-eyebrow">{{ t('lessonEditor.video.title') }}</h4>
            <div class="lesson-preview-video">
              <span class="lesson-preview-video-tile" aria-hidden="true"><IconGlyph name="video" :size="24" /></span>
              <strong class="row-list-title">{{ editor.video.title }}</strong>
            </div>
          </template>
          <template v-if="editor.keyPhrases.length">
            <h4 class="page-eyebrow">{{ t('lessonEditor.phrases.title') }}</h4>
            <KeyPhraseCards :phrases="editor.keyPhrases" />
          </template>
        </section>

        <section class="lesson-preview-section">
          <h3 class="card-title">{{ t('lessonEditor.preview.practice') }}</h3>
          <ol v-if="editor.activities.length" class="row-list">
            <li
              v-for="activity in editor.activities"
              :key="`${activity.type}-${activity.id}`"
              class="lesson-preview-row"
            >
              <span class="authoring-tile" aria-hidden="true">{{ activity.number }}</span>
              <div class="row-list-copy">
                <strong class="row-list-title"><CjkText :text="activity.title" /></strong>
                <span class="row-list-meta">
                  {{
                    t('lessonEditor.activities.heading', {
                      number: activity.number,
                      type: t(`practiceType.${activity.type}`),
                    })
                  }}
                </span>
                <!-- Flashcards: each card's word, then what its back shows. Without cards, the key phrases are used. -->
                <template v-if="activity.type === 'FLASHCARDS'">
                  <ul
                    v-if="activity.cards.length"
                    class="lesson-preview-cards"
                    :aria-label="t('lessonEditor.preview.cards')"
                    data-testid="preview-cards"
                  >
                    <li v-for="(card, index) in activity.cards" :key="index" class="tag is-outline">
                      <strong class="hanzi" lang="zh-CN">{{ card.chinese }}</strong>
                      <span>{{ [card.pinyin, card.meaning].filter(Boolean).join(' · ') }}</span>
                    </li>
                  </ul>
                  <span v-else class="row-list-meta">{{ t('lessonEditor.preview.cardsFromPhrases') }}</span>
                </template>
              </div>
            </li>
          </ol>
          <p v-else class="type-caption">{{ t('lessonEditor.preview.empty') }}</p>
        </section>

        <section class="lesson-preview-section">
          <h3 class="card-title">{{ t('classes.lessonQuiz') }}</h3>
          <div v-if="quiz" class="lesson-preview-quiz">
            <span class="row-list-copy">
              <strong class="row-list-title">
                <CjkText :text="t('lessonEditor.preview.quizTitle', { title: editor.title })" />
              </strong>
              <span class="row-list-meta">{{ t('lessonEditor.preview.questions', quiz.questions.length) }}</span>
            </span>
            <!-- Shown as learners see it; a preview starts nothing. -->
            <button type="button" class="btn btn-primary btn-sm" disabled>
              {{ t('lessonEditor.preview.startQuiz') }}
            </button>
          </div>
          <p v-else class="type-caption">{{ t('lessonEditor.preview.empty') }}</p>
        </section>
      </article>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { LessonEditor } from '@/types/adminClasses'
import { lessonQuiz } from '@/utils/lessonEditor'

import KeyPhraseCards from './KeyPhraseCards.vue'

/**
 * The lesson as learners will see it once this version is published, with the edits typed so far (A3.31). Without a
 * video or key phrases there is nothing to preview yet, and the Content tab is one step away.
 */
const props = defineProps<{ editor: LessonEditor; contentRoute: RouteLocationRaw }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const headingId = useId()

const quiz = computed(() => lessonQuiz(props.editor))
const facts = computed(() =>
  t(quiz.value ? 'lessonEditor.preview.facts' : 'lessonEditor.preview.factsNoQuiz', props.editor.activities.length),
)
</script>
