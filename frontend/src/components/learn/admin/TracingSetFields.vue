<template>
  <fieldset class="field-set" :disabled="disabled">
    <legend>{{ t('adminLearning.characters') }}</legend>
    <div class="form-grid two">
      <div class="field">
        <label class="field-label" :for="`${formId}-chars`">
          {{ t('adminLearning.charactersToWrite') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-chars`"
          v-model="hanzi"
          class="input cjk-text"
          lang="zh-CN"
          maxlength="100"
          :aria-invalid="tracing.characterLimitError ? 'true' : undefined"
          :aria-describedby="tracing.characterLimitError ? `${formId}-chars-error` : undefined"
        />
        <p v-if="tracing.characterLimitError" :id="`${formId}-chars-error`" class="field-error" role="alert">
          {{ tracing.characterLimitError }}
        </p>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-strokes`">
          {{ t('adminLearning.strokes') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-strokes`"
          v-model.number="strokeCount"
          class="input"
          type="number"
          :min="tracing.minTotalStrokes"
          :max="tracing.maxTotalStrokes"
          :aria-invalid="tracing.totalStrokesError ? 'true' : undefined"
          :aria-describedby="tracing.totalStrokesError ? `${formId}-strokes-error` : undefined"
          @blur="tracing.checkStrokes()"
        />
        <p v-if="tracing.totalStrokesError" :id="`${formId}-strokes-error`" class="field-error" role="alert">
          {{ tracing.totalStrokesError }}
        </p>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-tpinyin`">{{ t('adminLearning.pinyin') }}</label>
        <input :id="`${formId}-tpinyin`" v-model="pinyin" class="input" maxlength="200" />
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-tmeaning`">{{ t('adminLearning.meaning') }}</label>
        <input :id="`${formId}-tmeaning`" v-model="meaning" class="input" maxlength="200" />
      </div>
    </div>
    <!-- A word: the strokes of each of its characters, which add up to the total (optional). -->
    <div v-if="tracing.characters.length > 1 && !tracing.characterLimitError" class="field">
      <span :id="`${formId}-each`" class="field-label">{{ t('adminLearning.characterStrokes') }}</span>
      <div
        class="admin-learning-strokes"
        role="group"
        :aria-labelledby="`${formId}-each`"
        :aria-describedby="tracing.characterStrokesError ? `${formId}-each-error` : undefined"
      >
        <label v-for="(character, index) in tracing.characters" :key="index" class="admin-learning-stroke">
          <span class="cjk-text" lang="zh-CN" aria-hidden="true">{{ character }}</span>
          <input
            :value="tracing.characterStrokes[index] ?? ''"
            class="input"
            type="number"
            min="1"
            :max="MAX_STROKES_PER_CHARACTER"
            :aria-label="t('adminLearning.strokesOf', { character })"
            :aria-invalid="tracing.characterStrokesError ? 'true' : undefined"
            data-testid="editor-character-strokes"
            @input="tracing.setCharacterStroke(index, ($event.target as HTMLInputElement).value)"
            @blur="tracing.checkStrokes()"
          />
        </label>
      </div>
      <p v-if="tracing.characterStrokesError" :id="`${formId}-each-error`" class="field-error" role="alert">
        {{ tracing.characterStrokesError }}
      </p>
    </div>
  </fieldset>
</template>

<script lang="ts">
import { computed, reactive, ref, watch, type ComputedRef } from 'vue'

import { tracingGlyphs } from '@/utils/tracing'

const MAX_CHARACTERS = 10
const MAX_STROKES_PER_CHARACTER = 60
const MAX_TOTAL_STROKES = MAX_CHARACTERS * MAX_STROKES_PER_CHARACTER

function isStrokeNumber(value: number | string | null): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

interface TracingSetFieldsOptions {
  active: ComputedRef<boolean>
  hanzi: ComputedRef<string>
  initialStrokeCount: number | null
  initialCharacterStrokes: readonly number[]
  serverRule: (message: string) => string
}

/**
 * Owns tracing stroke counts, per-character stroke breakdown, and server-aligned stroke validation for PracticeSetEditor.
 * Lives outside AppOverlay so teleport-stubbed tests preserve state across parent re-renders.
 */
export function useTracingSetFields(options: TracingSetFieldsOptions) {
  const strokeCount = ref<number | string | null>(options.initialStrokeCount)
  const submitted = ref(false)
  const characters = computed(() => (options.active.value ? tracingGlyphs(options.hanzi.value) : []))
  const characterLimitError = computed(() => {
    if (!options.active.value) return null
    if (characters.value.length > MAX_CHARACTERS) return options.serverRule('A tracing set has at most 10 characters.')
    if (submitted.value && characters.value.length === 0) return options.serverRule('Add the characters to write.')
    return null
  })
  const characterStrokes = ref<(number | null)[]>([...options.initialCharacterStrokes])
  const strokesChecked = ref(false)

  watch(
    characters,
    (next, previous) => {
      const previousCharacters = previous ?? next
      characterStrokes.value =
        next.length > 1
          ? next.map((char, index) =>
              previousCharacters[index] === char ? (characterStrokes.value[index] ?? null) : null,
            )
          : []
      strokesChecked.value = false
    },
    { immediate: true },
  )

  const strokeValues = computed(() => characterStrokes.value.filter(isStrokeNumber))
  const minTotalStrokes = computed(() =>
    characters.value.length > 1 && characters.value.length <= MAX_CHARACTERS ? characters.value.length : 1,
  )
  const maxTotalStrokes = computed(() =>
    characters.value.length >= 1 && characters.value.length <= MAX_CHARACTERS
      ? characters.value.length * MAX_STROKES_PER_CHARACTER
      : MAX_TOTAL_STROKES,
  )

  const totalStrokesProblem = computed(() => {
    if (!options.active.value) return null
    if (!isStrokeNumber(strokeCount.value)) {
      return submitted.value ? options.serverRule('Enter how many strokes the characters have.') : null
    }
    const count = characters.value.length
    const value = strokeCount.value
    if (count === 1) {
      return !Number.isInteger(value) || value < 1 || value > MAX_STROKES_PER_CHARACTER
        ? options.serverRule('Strokes must be between 1 and 60.')
        : null
    }
    if (!Number.isInteger(value) || value < 1 || value > MAX_TOTAL_STROKES) {
      return options.serverRule('Total strokes must be between 1 and 600.')
    }
    if (count > 1 && count <= MAX_CHARACTERS && (value < count || value > count * MAX_STROKES_PER_CHARACTER)) {
      return options.serverRule('Each character needs 1 to 60 strokes.')
    }
    return null
  })

  const characterStrokesProblem = computed(() => {
    if (!options.active.value || characters.value.length < 2) return null
    const given = strokeValues.value
    if (!given.length) return null
    if (given.some((strokes) => !Number.isInteger(strokes) || strokes < 1 || strokes > MAX_STROKES_PER_CHARACTER)) {
      return options.serverRule('Each character needs 1 to 60 strokes.')
    }
    if (!isStrokeNumber(strokeCount.value)) return null
    const total = given.reduce((sum, strokes) => sum + strokes, 0)
    return given.length === characterStrokes.value.length && total === strokeCount.value
      ? null
      : options.serverRule('Give the strokes of each character, adding up to the total.')
  })

  const totalStrokesError = computed(() => (strokesChecked.value || submitted.value ? totalStrokesProblem.value : null))
  const characterStrokesError = computed(() => (strokesChecked.value ? characterStrokesProblem.value : null))
  const blocked = computed(
    () =>
      Boolean(characterLimitError.value) || totalStrokesError.value !== null || characterStrokesError.value !== null,
  )

  function setStrokeCount(value: number | string | null): void {
    strokeCount.value = value
  }

  function setCharacterStroke(index: number, raw: string): void {
    characterStrokes.value[index] = raw === '' ? null : Number(raw)
  }

  function checkStrokes(): void {
    strokesChecked.value = true
  }

  function reset(): void {
    strokeCount.value = null
    characterStrokes.value = []
    strokesChecked.value = false
    submitted.value = false
  }

  function validate(): { strokeCount: number | null; characterStrokes: number[] } | null {
    if (!options.active.value) return { strokeCount: null, characterStrokes: [] }
    submitted.value = true
    strokesChecked.value = true
    if (characterLimitError.value || totalStrokesProblem.value || characterStrokesProblem.value) return null
    return {
      strokeCount: isStrokeNumber(strokeCount.value) ? strokeCount.value : null,
      characterStrokes: characters.value.length > 1 ? strokeValues.value : [],
    }
  }

  return reactive({
    strokeCount,
    characters,
    characterStrokes,
    minTotalStrokes,
    maxTotalStrokes,
    characterLimitError,
    totalStrokesError,
    characterStrokesError,
    blocked,
    setStrokeCount,
    setCharacterStroke,
    checkStrokes,
    reset,
    validate,
  })
}

type TracingSetFieldsState = ReturnType<typeof useTracingSetFields>
</script>

<script setup lang="ts">
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'

const hanzi = defineModel<string | null>('hanzi', { required: true })
const pinyin = defineModel<string | null>('pinyin', { required: true })
const meaning = defineModel<string | null>('meaning', { required: true })

const props = defineProps<{
  formId: string
  disabled: boolean
  tracing: TracingSetFieldsState
}>()

const strokeCount = computed({
  get: () => props.tracing.strokeCount,
  set: (value) => props.tracing.setStrokeCount(value),
})

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminPracticeLibraryMessages] })
</script>
