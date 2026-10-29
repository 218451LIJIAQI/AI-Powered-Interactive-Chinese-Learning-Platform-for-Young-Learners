<template>
  <AppOverlay
    variant="drawer"
    size="lg"
    :open="open"
    :title="existing ? t('adminLearning.editActivity') : t('adminLearning.newActivity')"
    :subtitle="subtitle"
    test-id="set-editor"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid admin-learning-form" novalidate @submit.prevent="save">
      <p v-if="existing" class="type-caption">
        {{
          t(
            'adminLearning.createdByPlayed',
            { author: existing.createdBy ?? t('adminLearning.platformTeam'), count: existing.playedBy },
            existing.playedBy,
          )
        }}
      </p>
      <div v-else class="field">
        <label class="field-label" :for="`${formId}-type`">{{ t('adminLearning.type') }}</label>
        <select :id="`${formId}-type`" v-model="type" class="select" data-testid="editor-type" @change="changeType">
          <option v-for="option in PRACTICE_TYPES" :key="option" :value="option">
            {{ t(practiceTypeKey(option)) }}
          </option>
        </select>
        <p class="field-hint">{{ t('adminLearning.typeFirstHint') }}</p>
      </div>

      <div class="field">
        <label class="field-label" :for="`${formId}-title`">
          {{ t('adminLearning.title') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-title`"
          v-model="form.title"
          class="input"
          maxlength="200"
          required
          :placeholder="t('adminLearning.titlePlaceholder')"
          data-testid="editor-title"
        />
      </div>

      <fieldset class="form-section">
        <legend class="form-section-title">{{ t('adminLearning.stage') }}</legend>
        <SegmentedControl
          v-model="form.stage"
          :label="t('adminLearning.stage')"
          :options="STAGES.map((stage) => ({ value: stage, label: t(stageKey(stage)) }))"
        />
        <p class="field-hint">{{ t('classes.stageHint') }}</p>
      </fieldset>

      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-level`">{{ t('adminLearning.level') }}</label>
          <select :id="`${formId}-level`" v-model.number="form.hskLevel" class="select">
            <option v-for="level in 6" :key="level" :value="level">HSK {{ level }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-xp`">{{ t('adminLearning.xp') }}</label>
          <input :id="`${formId}-xp`" v-model.number="form.xpReward" class="input" type="number" min="0" max="100" />
        </div>
      </div>

      <!-- Tracing: one set of characters -->
      <fieldset v-if="type === 'TRACING'" class="form-section">
        <legend class="form-section-title">{{ t('adminLearning.characters') }}</legend>
        <div class="form-grid two">
          <div class="field">
            <label class="field-label" :for="`${formId}-chars`">
              {{ t('adminLearning.charactersToWrite') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              :id="`${formId}-chars`"
              v-model="form.items[0]!.hanzi"
              class="input cjk-text"
              lang="zh-CN"
              maxlength="10"
            />
          </div>
          <div class="field">
            <label class="field-label" :for="`${formId}-strokes`">
              {{ t('adminLearning.strokes') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              :id="`${formId}-strokes`"
              v-model.number="form.strokeCount"
              class="input"
              type="number"
              min="1"
              max="60"
            />
          </div>
          <div class="field">
            <label class="field-label" :for="`${formId}-tpinyin`">{{ t('adminLearning.pinyin') }}</label>
            <input :id="`${formId}-tpinyin`" v-model="form.items[0]!.pinyin" class="input" maxlength="200" />
          </div>
          <div class="field">
            <label class="field-label" :for="`${formId}-tmeaning`">{{ t('adminLearning.meaning') }}</label>
            <input :id="`${formId}-tmeaning`" v-model="form.items[0]!.meaning" class="input" maxlength="200" />
          </div>
        </div>
      </fieldset>

      <!-- Story lines -->
      <fieldset v-if="type === 'STORY'" class="form-section">
        <legend class="form-section-title">
          {{ t('adminLearning.storyLines', { count: form.storyLines.length }) }}
        </legend>
        <div v-for="(line, index) in form.storyLines" :key="`line-${index}`" class="admin-learning-item">
          <div class="admin-learning-item-head">
            <strong>{{ t('adminLearning.lineNumber', { number: index + 1 }) }}</strong>
            <button
              class="btn btn-ghost btn-icon btn-sm"
              type="button"
              :disabled="form.storyLines.length <= 1"
              :aria-label="t('adminLearning.removeLine', { number: index + 1 })"
              @click="form.storyLines.splice(index, 1)"
            >
              <IconGlyph name="trash" :size="16" />
            </button>
          </div>
          <input
            v-model="line.hanzi"
            class="input cjk-text"
            lang="zh-CN"
            maxlength="300"
            :aria-label="t('adminLearning.chineseFor', { number: index + 1 })"
            :placeholder="t('adminLearning.chinese')"
          />
          <input
            v-model="line.pinyin"
            class="input"
            maxlength="500"
            :aria-label="t('adminLearning.pinyinFor', { number: index + 1 })"
            :placeholder="t('adminLearning.pinyin')"
          />
          <input
            v-model="line.translation"
            class="input"
            maxlength="500"
            :aria-label="t('adminLearning.translationFor', { number: index + 1 })"
            :placeholder="t('adminLearning.translation')"
          />
        </div>
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="form.storyLines.length >= 20"
          @click="form.storyLines.push(emptyLine())"
        >
          <IconGlyph name="plus" :size="14" />{{ t('adminLearning.addLine') }}
        </button>
      </fieldset>

      <!-- Items of every other type -->
      <fieldset v-if="type !== 'TRACING'" class="form-section">
        <legend class="form-section-title">{{ t(itemKeys.count, { count: form.items.length }) }}</legend>
        <div
          v-for="(item, index) in form.items"
          :key="`item-${index}`"
          class="admin-learning-item"
          data-testid="editor-item"
        >
          <div class="admin-learning-item-head">
            <strong>{{ t(itemKeys.number, { number: index + 1 }) }}</strong>
            <button
              class="btn btn-ghost btn-icon btn-sm"
              type="button"
              :disabled="form.items.length <= 1"
              :aria-label="t('adminLearning.removeItem', { number: index + 1 })"
              @click="form.items.splice(index, 1)"
            >
              <IconGlyph name="trash" :size="16" />
            </button>
          </div>

          <template v-if="usesWords">
            <div class="admin-learning-item-row">
              <input
                v-model="item.hanzi"
                class="input cjk-text"
                lang="zh-CN"
                maxlength="100"
                :aria-label="
                  t(type === 'DRAG_DROP' ? 'adminLearning.sentenceTilesFor' : 'adminLearning.chineseFor', {
                    number: index + 1,
                  })
                "
                :placeholder="type === 'DRAG_DROP' ? t('adminLearning.sentenceTiles') : t('adminLearning.chinese')"
              />
              <input
                v-model="item.pinyin"
                class="input"
                maxlength="200"
                :aria-label="t('adminLearning.pinyinFor', { number: index + 1 })"
                :placeholder="t('adminLearning.pinyin')"
              />
              <input
                v-model="item.meaning"
                class="input"
                maxlength="200"
                :aria-label="t('adminLearning.meaningFor', { number: index + 1 })"
                :placeholder="t('adminLearning.meaning')"
              />
            </div>
            <div v-if="type === 'FLASHCARDS'" class="admin-learning-item-row is-two">
              <input
                v-model="item.example"
                class="input cjk-text"
                lang="zh-CN"
                maxlength="200"
                :aria-label="t('adminLearning.exampleFor', { number: index + 1 })"
                :placeholder="t('adminLearning.example')"
              />
              <input
                v-model="item.exampleMeaning"
                class="input"
                maxlength="300"
                :aria-label="t('adminLearning.exampleMeaningFor', { number: index + 1 })"
                :placeholder="t('adminLearning.exampleMeaning')"
              />
            </div>
            <input
              v-if="type === 'DRAG_DROP'"
              v-model="item.distractors"
              class="input cjk-text"
              lang="zh-CN"
              maxlength="255"
              :aria-label="t('adminLearning.extraTilesFor', { number: index + 1 })"
              :placeholder="t('adminLearning.extraTiles')"
            />
          </template>

          <template v-else>
            <input
              v-model="item.prompt"
              class="input"
              maxlength="300"
              :aria-label="t('adminLearning.questionFor', { number: index + 1 })"
              :placeholder="t('adminLearning.question')"
            />
            <div v-if="type === 'QUIZZES'" class="admin-learning-item-row is-two">
              <input
                v-model="item.hanzi"
                class="input cjk-text"
                lang="zh-CN"
                maxlength="100"
                :aria-label="t('adminLearning.chineseFor', { number: index + 1 })"
                :placeholder="t('adminLearning.chineseOptional')"
              />
              <input
                v-model="item.pinyin"
                class="input"
                maxlength="200"
                :aria-label="t('adminLearning.pinyinFor', { number: index + 1 })"
                :placeholder="t('adminLearning.pinyin')"
              />
            </div>
            <input
              v-if="type === 'LISTENING'"
              v-model="item.audioText"
              class="input cjk-text"
              lang="zh-CN"
              maxlength="200"
              :aria-label="t('adminLearning.readAloudFor', { number: index + 1 })"
              :placeholder="t('adminLearning.readAloud')"
            />
            <div
              class="admin-learning-options"
              role="radiogroup"
              :aria-label="t('adminLearning.answersFor', { number: index + 1 })"
            >
              <div v-for="(option, optionIndex) in item.options" :key="optionIndex" class="admin-learning-answer">
                <label class="choice">
                  <input
                    v-model="item.correctIndex"
                    type="radio"
                    :name="`${formId}-right-${index}`"
                    :value="optionIndex"
                  />
                  <span class="sr-only">{{ t('adminLearning.rightAnswerFor', { number: optionIndex + 1 }) }}</span>
                </label>
                <input
                  v-model="option.text"
                  class="input"
                  maxlength="200"
                  :aria-label="t('adminLearning.answerNumber', { number: optionIndex + 1 })"
                  :placeholder="t('adminLearning.answer')"
                />
                <input
                  v-if="type !== 'STORY'"
                  v-model="option.pinyin"
                  class="input"
                  maxlength="200"
                  :aria-label="t('adminLearning.answerPinyin', { number: optionIndex + 1 })"
                  :placeholder="t('adminLearning.pinyin')"
                />
                <button
                  class="btn btn-ghost btn-icon btn-sm"
                  type="button"
                  :disabled="item.options.length <= 2"
                  :aria-label="t('adminLearning.removeAnswer', { number: optionIndex + 1 })"
                  @click="removeOption(item, optionIndex)"
                >
                  <IconGlyph name="close" :size="14" />
                </button>
              </div>
              <button
                class="btn btn-ghost btn-sm"
                type="button"
                :disabled="item.options.length >= 6"
                @click="item.options.push({ text: '', pinyin: null })"
              >
                <IconGlyph name="plus" :size="14" />{{ t('adminLearning.addAnswer') }}
              </button>
            </div>
            <input
              v-model="item.explanation"
              class="input"
              maxlength="500"
              :aria-label="t('adminLearning.explanationFor', { number: index + 1 })"
              :placeholder="t('adminLearning.explanation')"
            />
          </template>
        </div>
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="form.items.length >= 30"
          data-testid="editor-add"
          @click="form.items.push(emptyItem(type))"
        >
          <IconGlyph name="plus" :size="14" />{{ t(itemKeys.add) }}
        </button>
      </fieldset>

      <p class="field-hint">
        <template v-if="form.stage === 'GUIDED'">{{ t('adminLearning.guidedNote') }} </template>
        {{ t('adminLearning.xpRule', { xp: form.xpReward ?? 0 }) }}
      </p>
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
    </form>
    <template #footer>
      <button class="btn btn-ghost" type="button" @click="emit('close')">{{ t('adminLearning.cancel') }}</button>
      <button
        class="btn btn-primary"
        type="submit"
        :form="formId"
        :disabled="saving"
        :aria-busy="saving"
        data-testid="editor-save"
      >
        {{ existing ? t('adminLearning.saveChanges') : t('adminLearning.saveDraft') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { LearningStage } from '@/types/api'
import type { AdminPracticeSetDetail, PracticeItemData, PracticeType } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { PRACTICE_TYPES, practiceTypeKey, STAGES, stageKey } from '@/utils/learnLabels'

/**
 * New or edited Practice set. The type is chosen first (a new set only) and decides the items: cards, questions with
 * answers, characters to trace, sentences, pairs or a story with questions. The server checks every type's rules.
 */
const props = defineProps<{ open: boolean; existing: AdminPracticeSetDetail | null }>()
const emit = defineEmits<{ close: []; saved: [set: AdminPracticeSetDetail] }>()

const DEFAULT_XP: Readonly<Record<PracticeType, number>> = {
  FLASHCARDS: 10,
  QUIZZES: 15,
  LISTENING: 15,
  TRACING: 10,
  DRAG_DROP: 15,
  MATCHING: 15,
  STORY: 20,
}

const { t } = useLocalMessages({ adminLearning: adminLearningMessages, classes: classesMessages })
const formId = `set-${useId()}`

const type = ref<PracticeType>('FLASHCARDS')
const form = reactive({
  title: '',
  stage: 'GUIDED' as LearningStage | null,
  hskLevel: 1 as number | null,
  xpReward: 10 as number | null,
  items: [] as PracticeItemData[],
  storyLines: [] as { hanzi: string; pinyin: string | null; translation: string | null }[],
  strokeCount: null as number | null,
})
const saving = ref(false)
const formError = ref<string | null>(null)

const usesWords = computed(() => ['FLASHCARDS', 'MATCHING', 'DRAG_DROP'].includes(type.value))
const subtitle = computed(() => {
  if (!props.existing) return t('adminLearning.newActivitySubtitle')
  return props.existing.status === 'PUBLISHED'
    ? t('adminLearning.liveEditSubtitle')
    : t('adminLearning.draftEditSubtitle')
})

/** A type's items (list title, item heading, add button) are cards, pairs, sentences or questions. */
const QUESTION_KEYS = {
  count: 'adminLearning.questionsCount',
  number: 'adminLearning.questionNumber',
  add: 'adminLearning.addQuestion',
} as const
const ITEM_KEYS: Readonly<Record<PracticeType, { count: string; number: string; add: string }>> = {
  FLASHCARDS: { count: 'adminLearning.cardsCount', number: 'adminLearning.cardNumber', add: 'adminLearning.addCard' },
  QUIZZES: QUESTION_KEYS,
  LISTENING: QUESTION_KEYS,
  TRACING: QUESTION_KEYS,
  DRAG_DROP: {
    count: 'adminLearning.sentencesCount',
    number: 'adminLearning.sentenceNumber',
    add: 'adminLearning.addSentence',
  },
  MATCHING: { count: 'adminLearning.pairsCount', number: 'adminLearning.pairNumber', add: 'adminLearning.addPair' },
  STORY: QUESTION_KEYS,
}
const itemKeys = computed(() => ITEM_KEYS[type.value])

function emptyItem(forType: PracticeType): PracticeItemData {
  const choice = forType === 'QUIZZES' || forType === 'LISTENING' || forType === 'STORY'
  return {
    hanzi: null,
    pinyin: null,
    meaning: null,
    prompt: null,
    audioText: null,
    example: null,
    exampleMeaning: null,
    options: choice
      ? [
          { text: '', pinyin: null },
          { text: '', pinyin: null },
        ]
      : [],
    correctIndex: choice ? 0 : null,
    explanation: null,
    distractors: null,
  }
}

function emptyLine() {
  return { hanzi: '', pinyin: null, translation: null }
}

function changeType(): void {
  form.items = [emptyItem(type.value)]
  form.storyLines = type.value === 'STORY' ? [emptyLine()] : []
  form.strokeCount = null
  form.xpReward = DEFAULT_XP[type.value]
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    formError.value = null
    const existing = props.existing
    if (!existing) {
      type.value = 'FLASHCARDS'
      Object.assign(form, { title: '', stage: 'GUIDED', hskLevel: 1 })
      changeType()
      return
    }
    type.value = existing.type
    Object.assign(form, {
      title: existing.title,
      stage: existing.stage,
      hskLevel: existing.hskLevel ?? 1,
      xpReward: existing.xpReward,
      items: existing.items.map((item) => ({ ...item, options: item.options.map((option) => ({ ...option })) })),
      storyLines: existing.storyLines.map((line) => ({ ...line })),
      strokeCount: existing.strokeCount,
    })
    if (!form.items.length) form.items = [emptyItem(existing.type)]
  },
)

function removeOption(item: PracticeItemData, optionIndex: number): void {
  item.options.splice(optionIndex, 1)
  if (item.correctIndex !== null && item.correctIndex >= item.options.length)
    item.correctIndex = item.options.length - 1
  else if (item.correctIndex !== null && item.correctIndex > optionIndex) item.correctIndex -= 1
}

function clean(value: string | null): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

async function save(): Promise<void> {
  formError.value = null
  if (!form.title.trim()) {
    formError.value = t('adminLearning.titleRequired')
    return
  }
  saving.value = true
  try {
    const saved = await adminLearningApi.savePracticeSet(type.value, props.existing?.id ?? null, {
      title: form.title.trim(),
      stage: form.stage,
      hskLevel: form.hskLevel,
      xpReward: form.xpReward,
      strokeCount: type.value === 'TRACING' ? form.strokeCount : null,
      storyLines:
        type.value === 'STORY'
          ? form.storyLines.map((line) => ({
              hanzi: line.hanzi.trim(),
              pinyin: clean(line.pinyin),
              translation: clean(line.translation),
            }))
          : [],
      items: form.items.map((item) => ({
        ...item,
        hanzi: clean(item.hanzi),
        pinyin: clean(item.pinyin),
        meaning: clean(item.meaning),
        prompt: clean(item.prompt),
        audioText: clean(item.audioText),
        example: clean(item.example),
        exampleMeaning: clean(item.exampleMeaning),
        explanation: clean(item.explanation),
        distractors: clean(item.distractors),
        options: item.options.map((option) => ({ text: option.text.trim(), pinyin: clean(option.pinyin) })),
      })),
    })
    emit('saved', saved)
  } catch (reason) {
    formError.value = resolveApiError(reason, t('adminLearning.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>
