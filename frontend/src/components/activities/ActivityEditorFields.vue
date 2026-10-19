<template>
  <fieldset class="activity-editor-fields" :disabled="disabled">
    <div class="activity-editor-grid">
      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.activityType') }}</span>
        <select v-model="form.type" class="select" :disabled="typeLocked" @change="emit('type-change', form.type)">
          <option v-for="item in activityTypeOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>

      <label class="field">
        <span class="field-label">{{ titleFieldLabel }}</span>
        <input v-model="form.title" class="input" maxlength="200" :placeholder="titleFieldPlaceholder" />
      </label>

      <label class="field">
        <span class="field-label">{{ t('governance.stageLabel') }}</span>
        <select
          v-model="form.learningStage"
          class="select"
          aria-describedby="activity-editor-stage-hint"
          data-testid="activity-learning-stage"
        >
          <option value="">{{ t('governance.stageNotSet') }}</option>
          <option v-for="stage in LEARNING_STAGES" :key="stage" :value="stage">
            {{ t(`governance.${learningStageMessageKey(stage)}`) }}
          </option>
        </select>
      </label>

      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.hskLevel') }}</span>
        <select v-model="form.hskLevel" class="select">
          <option :value="null">{{ t('adminActivityLibrary.notSet') }}</option>
          <option v-for="level in hskLevels" :key="level" :value="level">HSK {{ level }}</option>
        </select>
      </label>

      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.difficulty') }}</span>
        <select v-model="form.difficulty" class="select">
          <option value="">{{ t('adminActivityLibrary.notSet') }}</option>
          <option v-for="item in difficultyOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>

      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.topic') }}</span>
        <input v-model="form.topic" class="input" :placeholder="t('adminActivityLibrary.topicPlaceholder')" />
      </label>

      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.xpReward') }}</span>
        <input v-model.number="form.xpReward" class="input" type="number" min="0" />
      </label>

      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.estimatedSeconds') }}</span>
        <input v-model.number="form.estimatedSeconds" class="input" type="number" min="0" />
      </label>

      <label class="field activity-editor-grid-wide">
        <span class="field-label">{{ t('adminActivityLibrary.tags') }}</span>
        <input v-model="form.tagsText" class="input" :placeholder="t('adminActivityLibrary.tagsPlaceholder')" />
      </label>
    </div>

    <p id="activity-editor-stage-hint" class="field-hint activity-editor-stage-hint">{{ t('governance.stageHint') }}</p>

    <section v-if="form.type === 'FLASHCARD'" class="activity-editor-section">
      <div class="activity-editor-section-head">
        <strong>{{ t('adminActivityLibrary.flashcardContent') }}</strong>
        <span>{{ t('adminActivityLibrary.flashcardContentHint') }}</span>
      </div>

      <div class="activity-editor-grid">
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.frontText') }}</span>
          <textarea v-model="form.frontText" class="textarea" rows="3" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.backText') }}</span>
          <textarea v-model="form.backText" class="textarea" rows="3" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.pinyin') }}</span>
          <input v-model="form.pinyin" class="input" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.englishMeaning') }}</span>
          <input v-model="form.englishMeaning" class="input" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.malayMeaning') }}</span>
          <input v-model="form.malayMeaning" class="input" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.sortOrder') }}</span>
          <input v-model.number="form.sortOrder" class="input" type="number" min="0" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.imageUrl') }}</span>
          <input v-model="form.imageUrl" class="input" type="url" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.audioUrl') }}</span>
          <input v-model="form.audioUrl" class="input" type="url" />
        </label>
        <label class="field activity-editor-grid-wide">
          <span class="field-label">{{ t('adminActivityLibrary.extraPayload') }}</span>
          <textarea v-model="form.extraPayload" class="textarea" rows="3" />
        </label>
      </div>
    </section>

    <section v-else-if="form.type === 'MATCHING_GAME'" class="activity-editor-section">
      <div class="activity-editor-section-head">
        <strong>{{ t('adminActivityLibrary.matchingPairs') }}</strong>
        <button class="btn btn-secondary btn-sm" type="button" @click="addMatchingItem">
          <IconGlyph name="plus" :size="14" />
          {{ t('adminActivityLibrary.addPair') }}
        </button>
      </div>

      <div class="activity-editor-repeat-list">
        <article
          v-for="(item, index) in form.matchingItems"
          :key="`matching-${index}`"
          class="activity-editor-repeat-card"
        >
          <div class="row-between">
            <strong>{{ t('adminActivityLibrary.pairNumber', { number: index + 1 }) }}</strong>
            <button
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="form.matchingItems.length <= 1"
              @click="removeMatchingItem(index)"
            >
              <IconGlyph name="trash" :size="14" />
              {{ t('adminActivityLibrary.remove') }}
            </button>
          </div>
          <div class="activity-editor-grid">
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.leftText') }}</span>
              <input v-model="item.leftText" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.rightText') }}</span>
              <input v-model="item.rightText" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.leftImageUrl') }}</span>
              <input v-model="item.leftImageUrl" class="input" type="url" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.rightImageUrl') }}</span>
              <input v-model="item.rightImageUrl" class="input" type="url" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.pinyin') }}</span>
              <input v-model="item.pinyin" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.sortOrder') }}</span>
              <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
            </label>
          </div>
        </article>
      </div>
    </section>

    <section v-else-if="form.type === 'LISTENING_EXERCISE'" class="activity-editor-section">
      <div class="activity-editor-section-head">
        <strong>{{ t('adminActivityLibrary.listeningContent') }}</strong>
        <span>{{ t('adminActivityLibrary.listeningContentHint') }}</span>
      </div>

      <div class="activity-editor-grid">
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.audioUrl') }}</span>
          <input v-model="form.audioUrl" class="input" type="url" />
        </label>
        <label class="field activity-editor-grid-wide">
          <span class="field-label">{{ t('adminActivityLibrary.questionPayload') }}</span>
          <textarea
            v-model="form.questionPayload"
            class="textarea"
            rows="5"
            :placeholder="LISTENING_PAYLOAD_EXAMPLE"
            aria-describedby="activity-listening-help"
            data-testid="activity-listening-payload"
          />
        </label>
      </div>

      <div id="activity-listening-help" class="activity-editor-help" data-testid="activity-listening-help">
        <span>{{ t('adminActivityLibrary.listeningPayloadHelp') }}</span>
        <code class="activity-editor-code" lang="zh-CN">{{ LISTENING_PAYLOAD_EXAMPLE }}</code>
      </div>
      <ul v-if="listeningWarnings.length" class="activity-editor-warnings" role="status">
        <li
          v-for="(warning, index) in listeningWarnings"
          :key="`${warning.key}-${index}`"
          data-testid="activity-listening-warning"
        >
          {{ listeningWarningText(warning) }}
        </li>
      </ul>
    </section>

    <section v-else-if="form.type === 'TRACING_EXERCISE'" class="activity-editor-section">
      <div class="activity-editor-section-head">
        <strong>{{ t('adminActivityLibrary.tracingContent') }}</strong>
        <span>{{ t('adminActivityLibrary.tracingContentHint') }}</span>
      </div>

      <div class="activity-editor-grid">
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.worksheetUrl') }}</span>
          <input v-model="form.worksheetUrl" class="input" type="url" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.targetCharacter') }}</span>
          <input v-model="form.targetCharacter" class="input" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.pinyin') }}</span>
          <input v-model="form.pinyin" class="input" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.meaning') }}</span>
          <input v-model="form.meaning" class="input" />
        </label>
        <label class="field activity-editor-grid-wide">
          <span class="field-label">{{ t('adminActivityLibrary.instructions') }}</span>
          <textarea v-model="form.instructions" class="textarea" rows="4" />
        </label>
      </div>
    </section>

    <section v-else-if="form.type === 'DRAG_DROP_ACTIVITY'" class="activity-editor-section">
      <div class="activity-editor-section-head">
        <strong>{{ t('adminActivityLibrary.dragDropActivity') }}</strong>
        <button class="btn btn-secondary btn-sm" type="button" @click="addDragItem">
          <IconGlyph name="plus" :size="14" />
          {{ t('adminActivityLibrary.addDraggableItem') }}
        </button>
      </div>

      <label class="field">
        <span class="field-label">{{ t('adminActivityLibrary.instructions') }}</span>
        <textarea v-model="form.instructions" class="textarea" rows="4" />
      </label>

      <div class="activity-editor-repeat-list">
        <article v-for="(item, index) in form.dragItems" :key="`drag-${index}`" class="activity-editor-repeat-card">
          <div class="row-between">
            <strong>{{ t('adminActivityLibrary.draggableItemNumber', { number: index + 1 }) }}</strong>
            <button
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="form.dragItems.length <= 1"
              @click="removeDragItem(index)"
            >
              <IconGlyph name="trash" :size="14" />
              {{ t('adminActivityLibrary.remove') }}
            </button>
          </div>
          <div class="activity-editor-grid">
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.dragText') }}</span>
              <input v-model="item.dragText" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.targetZone') }}</span>
              <input v-model="item.targetZone" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.pinyin') }}</span>
              <input v-model="item.pinyin" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.imageUrl') }}</span>
              <input v-model="item.imageUrl" class="input" type="url" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.sortOrder') }}</span>
              <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
            </label>
          </div>
        </article>
      </div>
    </section>

    <section v-else class="activity-editor-section">
      <div class="activity-editor-section-head">
        <strong>{{ t('adminActivityLibrary.storyActivity') }}</strong>
        <button class="btn btn-secondary btn-sm" type="button" @click="addStoryQuestion">
          <IconGlyph name="plus" :size="14" />
          {{ t('adminActivityLibrary.addQuestion') }}
        </button>
      </div>

      <slot name="story-tools" />

      <div class="activity-editor-grid">
        <label class="field activity-editor-grid-wide">
          <span class="field-label">{{ t('adminActivityLibrary.instructions') }}</span>
          <textarea v-model="form.instructions" class="textarea" rows="3" />
        </label>
        <label class="field activity-editor-grid-wide">
          <span class="field-label">{{ t('adminActivityLibrary.storyText') }}</span>
          <textarea v-model="form.storyText" class="textarea" rows="8" data-testid="activity-story-text" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.pinyin') }}</span>
          <textarea v-model="form.storyPinyin" class="textarea" rows="4" />
        </label>
        <label class="field">
          <span class="field-label">{{ t('adminActivityLibrary.translation') }}</span>
          <textarea v-model="form.storyTranslation" class="textarea" rows="4" />
        </label>
        <label class="field activity-editor-grid-wide">
          <span class="field-label">{{ t('adminActivityLibrary.imageUrl') }}</span>
          <input v-model="form.imageUrl" class="input" type="url" />
        </label>
      </div>

      <div class="activity-editor-repeat-list">
        <article
          v-for="(item, index) in form.storyQuestions"
          :key="`story-${index}`"
          class="activity-editor-repeat-card"
        >
          <div class="row-between">
            <strong>{{ t('adminActivityLibrary.questionNumber', { number: index + 1 }) }}</strong>
            <button
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="form.storyQuestions.length <= 1"
              @click="removeStoryQuestion(index)"
            >
              <IconGlyph name="trash" :size="14" />
              {{ t('adminActivityLibrary.remove') }}
            </button>
          </div>
          <div class="activity-editor-grid">
            <label class="field activity-editor-grid-wide">
              <span class="field-label">{{ t('adminActivityLibrary.questionText') }}</span>
              <input v-model="item.questionText" class="input" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.optionsPerLine') }}</span>
              <textarea v-model="item.optionsText" class="textarea" rows="5" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.correctOption') }}</span>
              <select
                class="select"
                :value="correctOptionValue(item)"
                :aria-describedby="`activity-story-correct-hint-${index}`"
                data-testid="activity-story-correct-option"
                @change="setCorrectOption(item, $event)"
              >
                <option value="">{{ t('adminActivityLibrary.correctOptionNotSet') }}</option>
                <option
                  v-for="(option, optionIndex) in storyOptionList(item.optionsText)"
                  :key="`story-${index}-option-${optionIndex}`"
                  :value="String(optionIndex)"
                >
                  {{ optionIndex + 1 }}. {{ option }}
                </option>
              </select>
              <small
                :id="`activity-story-correct-hint-${index}`"
                :class="correctOptionStale(item) ? 'activity-editor-warning-text' : 'activity-editor-hint'"
              >
                {{
                  correctOptionStale(item)
                    ? t('adminActivityLibrary.correctOptionMissing')
                    : t('adminActivityLibrary.correctOptionHint')
                }}
              </small>
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.explanation') }}</span>
              <textarea v-model="item.explanation" class="textarea" rows="5" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('adminActivityLibrary.sortOrder') }}</span>
              <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
            </label>
          </div>
        </article>
      </div>
    </section>
  </fieldset>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { adminActivityLibraryMessages } from '@/plugins/adminActivityLibraryMessages'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import type { ActivityDifficulty, AdminActivityType } from '@/types/api'
import {
  ACTIVITY_TYPES,
  activityTypeLabelKey,
  createEmptyDragItem,
  createEmptyMatchingItem,
  createEmptyStoryQuestion,
  LISTENING_PAYLOAD_EXAMPLE,
  listeningPayloadWarnings,
  storyCorrectOptionIndex,
  storyOptionList,
  type ActivityEditorForm,
  type ListeningPayloadWarning,
  type StoryQuestionDraft,
} from '@/utils/activityForm'
import { LEARNING_STAGES, learningStageMessageKey } from '@/utils/learningStage'

/**
 * Per-type practice-activity editor shared by the admin activity library and the teacher's own
 * activities (D5). The parent owns the (reactive) form state, passed as `:form`, and the save / status
 * actions; the fields edit that object in place.
 */
withDefaults(defineProps<{ typeLocked?: boolean; disabled?: boolean }>(), { typeLocked: false, disabled: false })
const form = defineModel<ActivityEditorForm>('form', { required: true })
const emit = defineEmits<{ 'type-change': [type: AdminActivityType] }>()

const { t } = useI18n({
  messages: {
    en: { adminActivityLibrary: adminActivityLibraryMessages.en, governance: contentGovernanceMessages.en },
    ms: { adminActivityLibrary: adminActivityLibraryMessages.ms, governance: contentGovernanceMessages.ms },
    'zh-CN': {
      adminActivityLibrary: adminActivityLibraryMessages['zh-CN'],
      governance: contentGovernanceMessages['zh-CN'],
    },
  },
})

const hskLevels = [1, 2, 3, 4, 5, 6] as const

const activityTypeOptions = computed(() =>
  ACTIVITY_TYPES.map((value) => ({ value, label: t(`adminActivityLibrary.${activityTypeLabelKey(value)}`) })),
)

const difficultyOptions = computed<Array<{ value: ActivityDifficulty; label: string }>>(() => [
  { value: 'EASY', label: t('adminActivityLibrary.easy') },
  { value: 'MEDIUM', label: t('adminActivityLibrary.medium') },
  { value: 'HARD', label: t('adminActivityLibrary.hard') },
])

const titleFieldLabel = computed(() =>
  form.value.type === 'FLASHCARD' ? t('adminActivityLibrary.optionalTitle') : t('adminActivityLibrary.titleLabel'),
)
const titleFieldPlaceholder = computed(() =>
  form.value.type === 'FLASHCARD'
    ? t('adminActivityLibrary.optionalFlashcardTitle')
    : t('adminActivityLibrary.activityTitle'),
)

// Listening payload checks only warn; saving a draft is never blocked (the backend checks on submit / publish).
const listeningWarnings = computed(() =>
  form.value.type === 'LISTENING_EXERCISE' ? listeningPayloadWarnings(form.value.questionPayload) : [],
)

function listeningWarningText(warning: ListeningPayloadWarning): string {
  if (warning.key === 'listeningWarningAnswerNotOption') {
    return t('adminActivityLibrary.listeningWarningAnswerNotOption', { number: warning.number })
  }
  return t(`adminActivityLibrary.${warning.key}`)
}

/** The select shows the correct option 1-based ("1. text"); the form keeps it 0-based (`correctOptionIndex`). */
function correctOptionValue(item: StoryQuestionDraft): string {
  return typeof item.correctOptionIndex === 'number' ? String(item.correctOptionIndex) : ''
}

function setCorrectOption(item: StoryQuestionDraft, event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  item.correctOptionIndex = value === '' ? null : Number(value)
}

function correctOptionStale(item: StoryQuestionDraft): boolean {
  return typeof item.correctOptionIndex === 'number' && storyCorrectOptionIndex(item) === null
}

function addMatchingItem(): void {
  form.value.matchingItems.push(createEmptyMatchingItem(form.value.matchingItems.length))
}

function removeMatchingItem(index: number): void {
  if (form.value.matchingItems.length <= 1) return
  form.value.matchingItems.splice(index, 1)
}

function addDragItem(): void {
  form.value.dragItems.push(createEmptyDragItem(form.value.dragItems.length))
}

function removeDragItem(index: number): void {
  if (form.value.dragItems.length <= 1) return
  form.value.dragItems.splice(index, 1)
}

function addStoryQuestion(): void {
  form.value.storyQuestions.push(createEmptyStoryQuestion(form.value.storyQuestions.length))
}

function removeStoryQuestion(index: number): void {
  if (form.value.storyQuestions.length <= 1) return
  form.value.storyQuestions.splice(index, 1)
}
</script>

<style scoped>
.activity-editor-fields {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

/* Intrinsic grid: as many 200px+ columns as the parent panel allows (admin and teacher panels differ in width). */
.activity-editor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
  align-items: start;
  gap: var(--space-3) var(--space-4);
  min-width: 0;
}

.activity-editor-grid-wide {
  grid-column: 1 / -1;
}

.activity-editor-stage-hint {
  margin: calc(var(--space-2) * -1) 0 0;
}

.activity-editor-hint,
.activity-editor-warning-text {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.activity-editor-hint,
.activity-editor-section-head > span,
.activity-editor-help {
  color: var(--text-secondary);
}

.activity-editor-section {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.activity-editor-section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
}

.activity-editor-section-head > strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.activity-editor-section-head > span {
  flex-basis: 100%;
  font-size: var(--font-size-md);
}

.activity-editor-help {
  display: grid;
  gap: var(--space-1-5);
  font-size: var(--font-size-md);
}

.activity-editor-code {
  display: block;
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.activity-editor-warnings {
  display: grid;
  gap: var(--space-1);
  margin: 0;
  padding: var(--space-2-5) var(--space-3) var(--space-2-5) var(--space-8);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
}

.activity-editor-warnings,
.activity-editor-warning-text {
  color: var(--color-warning-text);
  font-weight: var(--font-weight-semibold);
}

.activity-editor-repeat-list {
  display: grid;
  gap: var(--space-3);
}

.activity-editor-repeat-card {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

@media (max-width: 640px) {
  .activity-editor-section,
  .activity-editor-repeat-card {
    padding: var(--space-3);
  }
}
</style>
