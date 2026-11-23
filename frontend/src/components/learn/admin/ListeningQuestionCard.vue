<template>
  <div class="admin-learning-item-head">
    <strong>{{ t('adminLearning.questionNumber', { number: index + 1 }) }}</strong>
    <button
      class="btn btn-ghost btn-icon btn-sm"
      type="button"
      :disabled="!canRemove"
      :aria-label="t('adminLearning.removeItem', { number: index + 1 })"
      @click="emit('remove')"
    >
      <IconGlyph name="trash" :size="16" />
    </button>
  </div>
  <input
    :value="item.prompt ?? ''"
    class="input"
    maxlength="300"
    :aria-label="t('adminLearning.questionNumber', { number: index + 1 })"
    :placeholder="t('adminLearning.question')"
    @input="patch({ prompt: ($event.target as HTMLInputElement).value })"
  />
  <input
    :value="item.audioText ?? ''"
    class="input cjk-text"
    lang="zh-CN"
    required
    maxlength="200"
    :aria-label="t('adminLearning.readAloudFor', { number: index + 1 })"
    :placeholder="t('adminLearning.readAloud')"
    @input="patch({ audioText: ($event.target as HTMLInputElement).value })"
  />
  <div class="admin-learning-options" role="group" :aria-label="t('adminLearning.answersFor', { number: index + 1 })">
    <div v-for="(option, optionIndex) in item.options" :key="optionIndex" class="admin-learning-option">
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
        @input="updateOption(optionIndex, { text: ($event.target as HTMLInputElement).value })"
      />
      <input
        :value="option.pinyin ?? ''"
        class="input"
        maxlength="200"
        :aria-label="t('adminLearning.answerPinyin', { number: optionIndex + 1 })"
        :placeholder="t('adminLearning.pinyin')"
        @input="updateOption(optionIndex, { pinyin: ($event.target as HTMLInputElement).value })"
      />
      <button
        class="btn btn-ghost btn-icon btn-sm"
        type="button"
        :disabled="item.options.length <= 2"
        :aria-label="t('adminLearning.removeAnswer', { number: optionIndex + 1 })"
        @click="removeOption(optionIndex)"
      >
        <IconGlyph name="close" :size="14" />
      </button>
    </div>
    <button class="btn btn-secondary btn-sm" type="button" :disabled="item.options.length >= 6" @click="addOption">
      <IconGlyph name="plus" :size="14" />
      <span>{{ t('adminLearning.addAnswer') }}</span>
    </button>
  </div>
  <input
    :value="item.explanation ?? ''"
    class="input"
    maxlength="500"
    :aria-label="t('adminLearning.questionExplanationFor', { number: index + 1 })"
    :placeholder="t('adminLearning.explanation')"
    @input="patch({ explanation: ($event.target as HTMLInputElement).value })"
  />
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'
import type { PracticeItemData } from '@/types/learn'

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
  patch({ options: [...props.item.options, { text: '', pinyin: null }] })
}

/** Deleting an option preserves the selected answer, or clears it so the admin picks an explicit replacement. */
function removeOption(optionIndex: number): void {
  const options = props.item.options.filter((_, current) => current !== optionIndex)
  let correctIndex = props.item.correctIndex
  if (correctIndex === optionIndex) correctIndex = null
  else if (correctIndex !== null && correctIndex > optionIndex) correctIndex -= 1
  patch({ options, correctIndex })
}
</script>
