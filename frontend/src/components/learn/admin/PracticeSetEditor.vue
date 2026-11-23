<template>
  <AppOverlay
    variant="drawer"
    size="lg"
    :title="existing ? t('adminLearning.editActivity') : t('adminLearning.newActivity')"
    :subtitle="subtitle"
    test-id="set-editor"
    @close="emit('close')"
  >
    <form
      :id="formId"
      class="form-grid admin-learning-form"
      novalidate
      @input="formError = null"
      @change="formError = null"
      @submit.prevent="save"
    >
      <div v-if="existing" class="admin-tags">
        <span class="tag is-outline">{{ t(`practiceType.${existing.type}`) }}</span>
        <span class="admin-meta">
          {{
            t(
              'adminLearning.createdByPlayed',
              { author: existing.createdBy ?? t('adminLearning.platformTeam'), count: formatNumber(existing.playedBy) },
              existing.playedBy,
            )
          }}
        </span>
      </div>
      <div v-else class="field">
        <label class="field-label" :for="`${formId}-type`">{{ t('adminLearning.type') }}</label>
        <select
          :id="`${formId}-type`"
          v-model="type"
          class="select"
          :disabled="saving"
          data-testid="editor-type"
          data-autofocus
          @change="changeType"
        >
          <option v-for="option in PRACTICE_TYPES" :key="option" :value="option">
            {{ t(`practiceType.${option}`) }}
          </option>
        </select>
      </div>

      <div class="field">
        <label class="field-label" :for="`${formId}-title`">
          {{ t('adminLearning.title') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-title`"
          ref="titleField"
          v-model="form.title"
          class="input"
          maxlength="200"
          required
          :disabled="saving"
          :aria-invalid="Boolean(titleError)"
          :aria-describedby="titleError ? `${formId}-title-error` : undefined"
          :placeholder="t('adminLearning.titlePlaceholder')"
          data-testid="editor-title"
          data-autofocus
        />
        <p v-if="titleError" :id="`${formId}-title-error`" class="field-error" role="alert">{{ titleError }}</p>
      </div>

      <div class="field">
        <label class="field-label" :for="`${formId}-level`">
          {{ t('adminLearning.level') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <select :id="`${formId}-level`" v-model.number="form.hskLevel" class="select" :disabled="saving">
          <option v-for="level in 6" :key="level" :value="level">{{ t('hsk.number', { level }) }}</option>
        </select>
      </div>

      <TracingSetFields
        v-if="type === 'TRACING'"
        v-model:hanzi="form.items[0]!.hanzi"
        v-model:pinyin="form.items[0]!.pinyin"
        v-model:meaning="form.items[0]!.meaning"
        v-model:stroke-count="form.strokeCount"
        :form-id="formId"
        :disabled="saving"
        :tracing-characters="tracingCharacters"
        :character-strokes="characterStrokes"
        :character-limit-error="characterLimitError"
        :total-strokes-error="totalStrokesError"
        :character-strokes-error="characterStrokesError"
        @update-stroke="(index, value) => (characterStrokes[index] = value)"
        @blur-strokes="strokesChecked = true"
      />

      <!-- Items of every other type -->
      <fieldset v-else-if="itemKeys" class="field-set" :disabled="saving">
        <legend>{{ t(itemKeys.count, { count: form.items.length }) }}</legend>
        <p v-if="type === 'DRAG_DROP' || type === 'TYPING'" class="field-hint">
          {{ t(type === 'DRAG_DROP' ? 'adminLearning.extraTiles' : 'adminLearning.lookAlike') }}
        </p>

        <div
          v-for="(item, index) in form.items"
          :key="`item-${index}`"
          :class="usesWords ? 'admin-learning-words' : 'admin-learning-item'"
          data-testid="editor-item"
        >
          <template v-if="usesWords">
            <div class="admin-learning-word-row" :class="{ 'is-sentence': type === 'DRAG_DROP' }">
              <label class="field">
                <span class="field-label">{{
                  t(type === 'DRAG_DROP' ? 'adminLearning.sentenceTilesFor' : 'adminLearning.chineseFor', {
                    number: index + 1,
                  })
                }}</span>
                <input
                  v-model="item.hanzi"
                  class="input cjk-text"
                  lang="zh-CN"
                  maxlength="100"
                  :placeholder="type === 'DRAG_DROP' ? t('adminLearning.sentenceTiles') : undefined"
                />
              </label>
              <label class="field">
                <span class="field-label">{{ t('adminLearning.pinyin') }}</span>
                <input
                  v-model="item.pinyin"
                  class="input"
                  maxlength="200"
                  :aria-label="t('adminLearning.pinyinFor', { number: index + 1 })"
                />
              </label>
              <label class="field">
                <span class="field-label">{{ t('adminLearning.meaning') }}</span>
                <input
                  v-model="item.meaning"
                  class="input"
                  maxlength="200"
                  :aria-label="t('adminLearning.meaningFor', { number: index + 1 })"
                />
              </label>
              <button
                class="btn btn-ghost btn-icon btn-sm"
                type="button"
                :disabled="form.items.length <= 1"
                :aria-label="t('adminLearning.removeItem', { number: index + 1 })"
                @click="removeItem(index)"
              >
                <IconGlyph name="trash" :size="16" />
              </button>
            </div>
            <!-- A card's example sentence and its meaning, shown on the back of the card. -->
            <div v-if="type === 'FLASHCARDS'" class="admin-learning-item-row">
              <label class="field">
                <span class="field-label">{{ t('adminLearning.example') }}</span>
                <input
                  v-model="item.example"
                  class="input cjk-text"
                  lang="zh-CN"
                  maxlength="200"
                  :aria-label="t('adminLearning.exampleFor', { number: index + 1 })"
                />
              </label>
              <label class="field">
                <span class="field-label">{{ t('adminLearning.exampleMeaning') }}</span>
                <input
                  v-model="item.exampleMeaning"
                  class="input"
                  maxlength="300"
                  :aria-label="t('adminLearning.exampleMeaningFor', { number: index + 1 })"
                />
              </label>
            </div>
            <label v-if="type === 'DRAG_DROP' || type === 'TYPING'" class="field">
              <span class="field-label">{{
                t(type === 'DRAG_DROP' ? 'adminLearning.extraTilesFor' : 'adminLearning.lookAlikeFor', {
                  number: index + 1,
                })
              }}</span>
              <input v-model="item.distractors" class="input cjk-text" lang="zh-CN" maxlength="255" />
            </label>
          </template>

          <ListeningQuestionCard
            v-else
            :item="item"
            :index="index"
            :form-id="formId"
            :can-remove="form.items.length > 1"
            @update:item="(updated) => updateItem(index, updated)"
            @remove="removeItem(index)"
          />
        </div>

        <button
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="form.items.length >= 30"
          data-testid="editor-add"
          @click="addItem"
        >
          <IconGlyph name="plus" :size="14" />
          <span>{{ t(itemKeys.add) }}</span>
        </button>
      </fieldset>

      <p v-if="existing" class="field-hint">
        {{
          t(existing.type === 'FLASHCARDS' ? 'adminLearning.flashcardXpRule' : 'adminLearning.xpRule', {
            xp: existing.xpReward,
          })
        }}
      </p>
    </form>
    <template #footer>
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <button class="btn btn-secondary" type="button" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-primary"
        type="submit"
        :form="formId"
        :disabled="
          saving || Boolean(characterLimitError) || totalStrokesError !== null || characterStrokesError !== null
        "
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

import type { adminLearningApi } from '@/api/learn'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { PRACTICE_TYPES } from '@/config/practiceActivities'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'
import type { AdminPracticeSetDetail, PracticeItemData, PracticeType } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatNumber } from '@/utils/format'
import { normalizeAppLanguage } from '@/utils/locale'
import { localizeServerMessage } from '@/utils/serverMessages'
import { tracingGlyphs } from '@/utils/tracing'

import ListeningQuestionCard from './ListeningQuestionCard.vue'
import TracingSetFields from './TracingSetFields.vue'

const props = defineProps<{
  existing: AdminPracticeSetDetail | null
  saveSet: typeof adminLearningApi.savePracticeSet
}>()
const emit = defineEmits<{ close: []; saved: [set: AdminPracticeSetDetail] }>()

const { t, locale } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminPracticeLibraryMessages] })
const serverRule = (message: string) => localizeServerMessage(message, normalizeAppLanguage(locale.value) ?? 'en')
const formId = `set-${useId()}`
const isUnmounted = useIsUnmounted()

const type = ref<PracticeType>(props.existing?.type ?? 'FLASHCARDS')
const form = reactive<{
  title: string
  hskLevel: number
  items: PracticeItemData[]
  strokeCount: number | string | null
}>({
  title: props.existing?.title ?? '',
  hskLevel: props.existing?.hskLevel ?? 1,
  items: props.existing?.items.length
    ? props.existing.items.map((item) => ({ ...item, options: item.options.map((option) => ({ ...option })) }))
    : [emptyItem(type.value)],
  strokeCount: props.existing?.strokeCount ?? null,
})
const saving = ref(false)
const formError = ref<string | null>(null)
const titleField = ref<HTMLInputElement | null>(null)
const { error: titleError, validate: validateTitle } = useFieldCheck(
  computed(() => form.title),
  (title) => (title.trim() ? null : t('adminLearning.titleRequired')),
)

const tracingCharacters = computed(() => (type.value === 'TRACING' ? tracingGlyphs(form.items[0]?.hanzi ?? '') : []))
const characterLimitError = computed(() =>
  tracingCharacters.value.length > 10 ? serverRule('A tracing set has at most 10 characters.') : null,
)
const characterStrokes = ref<(number | string | null)[]>([...(props.existing?.characterStrokes ?? [])])
const strokesChecked = ref(false)

watch(
  tracingCharacters,
  (characters, previous) => {
    const previousCharacters = previous ?? characters
    characterStrokes.value =
      characters.length > 1
        ? characters.map((char, index) =>
            previousCharacters[index] === char ? (characterStrokes.value[index] ?? null) : null,
          )
        : []
    strokesChecked.value = false
  },
  { immediate: true },
)

function isStrokeNumber(value: number | string | null): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

const strokeValues = computed(() => characterStrokes.value.filter(isStrokeNumber))

const totalStrokesProblem = computed(() => {
  if (type.value !== 'TRACING' || !isStrokeNumber(form.strokeCount)) return null
  const single = tracingCharacters.value.length === 1
  const maxTotal = single ? 60 : 600
  if (!Number.isInteger(form.strokeCount) || form.strokeCount < 1 || form.strokeCount > maxTotal) {
    return serverRule(single ? 'Strokes must be between 1 and 60.' : 'Total strokes must be between 1 and 600.')
  }
  return null
})
const characterStrokesProblem = computed(() => {
  if (type.value !== 'TRACING' || tracingCharacters.value.length < 2) return null
  const given = strokeValues.value
  if (!given.length) return null
  if (given.some((strokes) => !Number.isInteger(strokes) || strokes < 1 || strokes > 60)) {
    return serverRule('Each character needs 1 to 60 strokes.')
  }
  const total = given.reduce((sum, strokes) => sum + strokes, 0)
  return given.length === characterStrokes.value.length && total === form.strokeCount
    ? null
    : serverRule('Give the strokes of each character, adding up to the total.')
})
const totalStrokesError = computed(() => (strokesChecked.value ? totalStrokesProblem.value : null))
const characterStrokesError = computed(() => (strokesChecked.value ? characterStrokesProblem.value : null))

const usesWords = computed(() => type.value !== 'LISTENING')

const STATUS_SUBTITLE_KEYS: Readonly<Record<AdminPracticeSetDetail['status'], string>> = {
  PUBLISHED: 'adminLearning.liveEditSubtitle',
  DRAFT: 'adminLearning.draftEditSubtitle',
  ARCHIVED: 'adminLearning.archivedEditSubtitle',
}
const subtitle = computed(() =>
  t(props.existing ? STATUS_SUBTITLE_KEYS[props.existing.status] : 'adminLearning.newActivitySubtitle'),
)

const ITEM_KEYS: Readonly<Record<Exclude<PracticeType, 'TRACING'>, { count: string; add: string }>> = {
  FLASHCARDS: { count: 'adminLearning.cardsCount', add: 'adminLearning.addCard' },
  LISTENING: { count: 'adminLearning.questionsCount', add: 'adminLearning.addQuestion' },
  DRAG_DROP: { count: 'adminLearning.sentencesCount', add: 'adminLearning.addSentence' },
  MATCHING: { count: 'adminLearning.pairsCount', add: 'adminLearning.addPair' },
  TYPING: { count: 'adminLearning.wordsCount', add: 'adminLearning.addWord' },
}
const itemKeys = computed(() => (type.value === 'TRACING' ? null : ITEM_KEYS[type.value]))

function emptyItem(forType: PracticeType): PracticeItemData {
  const choice = forType === 'LISTENING'
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

function addItem(): void {
  formError.value = null
  form.items.push(emptyItem(type.value))
}

function updateItem(index: number, updated: PracticeItemData): void {
  formError.value = null
  form.items[index] = updated
}

function removeItem(index: number): void {
  formError.value = null
  form.items.splice(index, 1)
}

function changeType(): void {
  form.items = [emptyItem(type.value)]
  form.strokeCount = null
  characterStrokes.value = []
  strokesChecked.value = false
  formError.value = null
}

async function save(): Promise<void> {
  if (saving.value) return
  formError.value = null
  if (!validateTitle()) {
    titleField.value?.focus()
    return
  }
  if (type.value === 'LISTENING' && form.items.some((item) => item.correctIndex === null)) {
    formError.value = serverRule('Tick exactly one right answer.')
  }
  strokesChecked.value = true
  if (formError.value || characterLimitError.value || totalStrokesProblem.value || characterStrokesProblem.value) return
  const tracing = type.value === 'TRACING'
  saving.value = true
  try {
    const saved = await props.saveSet(type.value, props.existing?.id ?? null, {
      title: form.title.trim(),
      stage: props.existing ? props.existing.stage : 'GUIDED',
      hskLevel: form.hskLevel,
      strokeCount: tracing && isStrokeNumber(form.strokeCount) ? form.strokeCount : null,
      characterStrokes: tracing && tracingCharacters.value.length > 1 ? strokeValues.value : [],
      items: form.items,
    })
    if (!isUnmounted()) emit('saved', saved)
  } catch (reason) {
    if (!isUnmounted()) formError.value = resolveApiError(reason, t('adminLearning.saveFailed'))
  } finally {
    if (!isUnmounted()) saving.value = false
  }
}
</script>
