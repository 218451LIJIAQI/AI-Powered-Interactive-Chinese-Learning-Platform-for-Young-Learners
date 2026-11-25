<template>
  <div class="admin-learning-item-head">
    <strong>{{ t('adminLearning.questionNumber', { number: index + 1 }) }}</strong>
    <button
      class="btn btn-ghost btn-icon btn-sm"
      type="button"
      data-row-remove
      :disabled="!canRemove"
      :aria-label="t('adminLearning.removeItem', { number: index + 1 })"
      @click="emit('remove')"
    >
      <IconGlyph name="trash" :size="16" />
    </button>
  </div>
  <label class="field">
    <span class="field-label">{{ t('adminLearning.question') }}</span>
    <input
      :value="item.prompt ?? ''"
      class="input"
      maxlength="300"
      :aria-label="t('adminLearning.questionNumber', { number: index + 1 })"
      :placeholder="t('adminLearning.question')"
      @input="onTextInput($event, (prompt) => patch({ prompt }))"
      @compositionend="onTextInput($event, (prompt) => patch({ prompt }))"
    />
  </label>
  <label class="field">
    <span class="field-label">
      {{ t('adminLearning.readAloud') }}<span class="required-mark" aria-hidden="true">*</span>
    </span>
    <input
      :value="item.audioText ?? ''"
      class="input cjk-text"
      lang="zh-CN"
      required
      maxlength="200"
      :aria-label="t('adminLearning.readAloudFor', { number: index + 1 })"
      :placeholder="t('adminLearning.readAloud')"
      @input="onTextInput($event, (audioText) => patch({ audioText }))"
      @compositionend="onTextInput($event, (audioText) => patch({ audioText }))"
    />
  </label>
  <div
    ref="optionsContainer"
    class="admin-learning-options"
    role="group"
    :aria-label="t('adminLearning.answersFor', { number: index + 1 })"
  >
    <div class="admin-learning-option type-caption" aria-hidden="true">
      <span />
      <span>{{ t('adminLearning.answer') }}</span>
      <span>{{ t('adminLearning.pinyin') }}</span>
      <span />
    </div>
    <div v-for="(option, optionIndex) in item.options" :key="optionIndex" class="admin-learning-option" data-row-item>
      <label class="choice">
        <input
          type="radio"
          :name="`${formId}-right-${index}`"
          :value="optionIndex"
          :checked="item.correctIndex === optionIndex"
          @change="patch({ correctIndex: optionIndex })"
        />
        <span class="sr-only">{{ t('adminLearning.rightAnswerFor', { number: optionIndex + 1 }) }}</span>
      </label>
      <input
        :value="option.text"
        class="input"
        maxlength="200"
        :aria-label="t('adminLearning.answerNumber', { number: optionIndex + 1 })"
        :placeholder="t('adminLearning.answer')"
        @input="onTextInput($event, (text) => updateOption(optionIndex, { text }))"
        @compositionend="onTextInput($event, (text) => updateOption(optionIndex, { text }))"
      />
      <input
        :value="option.pinyin ?? ''"
        class="input"
        maxlength="200"
        :aria-label="t('adminLearning.answerPinyin', { number: optionIndex + 1 })"
        :placeholder="t('adminLearning.pinyin')"
        @input="onTextInput($event, (pinyin) => updateOption(optionIndex, { pinyin }))"
        @compositionend="onTextInput($event, (pinyin) => updateOption(optionIndex, { pinyin }))"
      />
      <button
        class="btn btn-ghost btn-icon btn-sm"
        type="button"
        data-row-remove
        :disabled="item.options.length <= 2"
        :aria-label="t('adminLearning.removeAnswer', { number: optionIndex + 1 })"
        @click="removeOption(optionIndex)"
      >
        <IconGlyph name="close" :size="14" />
      </button>
    </div>
    <button
      ref="addOptionButton"
      class="btn btn-secondary btn-sm"
      type="button"
      :disabled="item.options.length >= 6"
      @click="addOption"
    >
      <IconGlyph name="plus" :size="14" />
      <span>{{ t('adminLearning.addAnswer') }}</span>
    </button>
  </div>
  <label class="field">
    <span class="field-label">{{ t('adminLearning.explanation') }}</span>
    <input
      :value="item.explanation ?? ''"
      class="input"
      maxlength="500"
      :aria-label="t('adminLearning.questionExplanationFor', { number: index + 1 })"
      :placeholder="t('adminLearning.explanation')"
      @input="onTextInput($event, (explanation) => patch({ explanation }))"
      @compositionend="onTextInput($event, (explanation) => patch({ explanation }))"
    />
  </label>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'
import type { PracticeItemData } from '@/types/learn'
import { focusAfterRowAdd, focusAfterRowRemove } from '@/utils/focusRowList'

const props = defineProps<{
  item: PracticeItemData
  index: number
  formId: string
  canRemove: boolean
}>()
const emit = defineEmits<{
  'update:item': [item: PracticeItemData]
  remove: []
}>()

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminPracticeLibraryMessages] })
const optionsContainer = ref<HTMLElement | null>(null)
const addOptionButton = ref<HTMLButtonElement | null>(null)

function onTextInput(event: Event, apply: (value: string) => void): void {
  if ('isComposing' in event && event.isComposing) return
  apply((event.target as HTMLInputElement).value)
}

function patch(changes: Partial<PracticeItemData>): void {
  emit('update:item', { ...props.item, ...changes })
}

function updateOption(optionIndex: number, changes: Partial<PracticeItemData['options'][number]>): void {
  const options = props.item.options.map((option, current) =>
    current === optionIndex ? { ...option, ...changes } : option,
  )
  patch({ options })
}

function addOption(): void {
  const wasFocused = document.activeElement === addOptionButton.value
  patch({ options: [...props.item.options, { text: '', pinyin: null }] })
  focusAfterRowAdd(
    () => optionsContainer.value,
    () => addOptionButton.value,
    wasFocused,
  )
}

/** Deleting an option preserves the selected answer, or clears it so the admin picks an explicit replacement. */
function removeOption(optionIndex: number): void {
  const wasFocused = Boolean(optionsContainer.value?.contains(document.activeElement))
  const options = props.item.options.filter((_, current) => current !== optionIndex)
  let correctIndex = props.item.correctIndex
  if (correctIndex === optionIndex) correctIndex = null
  else if (correctIndex !== null && correctIndex > optionIndex) correctIndex -= 1
  patch({ options, correctIndex })
  focusAfterRowRemove(
    () => optionsContainer.value,
    () => addOptionButton.value,
    optionIndex,
    wasFocused,
  )
}
</script>
