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
          :aria-invalid="characterLimitError ? 'true' : undefined"
          :aria-describedby="characterLimitError ? `${formId}-chars-error` : undefined"
        />
        <p v-if="characterLimitError" :id="`${formId}-chars-error`" class="field-error" role="alert">
          {{ characterLimitError }}
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
          min="1"
          :max="tracingCharacters.length === 1 ? 60 : 600"
          :aria-invalid="totalStrokesError ? 'true' : undefined"
          :aria-describedby="totalStrokesError ? `${formId}-strokes-error` : undefined"
          @blur="emit('blurStrokes')"
        />
        <p v-if="totalStrokesError" :id="`${formId}-strokes-error`" class="field-error" role="alert">
          {{ totalStrokesError }}
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
    <div v-if="tracingCharacters.length > 1 && !characterLimitError" class="field">
      <span :id="`${formId}-each`" class="field-label">{{ t('adminLearning.characterStrokes') }}</span>
      <div
        class="admin-learning-strokes"
        role="group"
        :aria-labelledby="`${formId}-each`"
        :aria-describedby="characterStrokesError ? `${formId}-each-error` : undefined"
      >
        <label v-for="(character, index) in tracingCharacters" :key="index" class="admin-learning-stroke">
          <span class="cjk-text" lang="zh-CN" aria-hidden="true">{{ character }}</span>
          <input
            :value="characterStrokes[index] ?? ''"
            class="input"
            type="number"
            min="1"
            max="60"
            :aria-label="t('adminLearning.strokesOf', { character })"
            :aria-invalid="characterStrokesError ? 'true' : undefined"
            data-testid="editor-character-strokes"
            @input="onStrokeInput(index, ($event.target as HTMLInputElement).value)"
            @blur="emit('blurStrokes')"
          />
        </label>
      </div>
      <p v-if="characterStrokesError" :id="`${formId}-each-error`" class="field-error" role="alert">
        {{ characterStrokesError }}
      </p>
    </div>
  </fieldset>
</template>

<script setup lang="ts">
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'

const hanzi = defineModel<string | null>('hanzi', { required: true })
const pinyin = defineModel<string | null>('pinyin', { required: true })
const meaning = defineModel<string | null>('meaning', { required: true })
const strokeCount = defineModel<number | string | null>('strokeCount', { required: true })

defineProps<{
  formId: string
  disabled: boolean
  tracingCharacters: readonly string[]
  characterStrokes: readonly (number | string | null)[]
  characterLimitError: string | null
  totalStrokesError: string | null
  characterStrokesError: string | null
}>()
const emit = defineEmits<{
  updateStroke: [index: number, value: number | string]
  blurStrokes: []
}>()

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminPracticeLibraryMessages] })

function onStrokeInput(index: number, raw: string): void {
  emit('updateStroke', index, raw === '' ? '' : Number(raw))
}
</script>
