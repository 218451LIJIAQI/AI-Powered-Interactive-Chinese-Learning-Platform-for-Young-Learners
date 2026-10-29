<template>
  <AppOverlay
    variant="drawer"
    :open="open"
    :title="
      question
        ? t('lessonEditor.question.drawerEdit', { number: question.number })
        : t('lessonEditor.question.drawerNew')
    "
    :busy="busy"
    size="lg"
    test-id="question-drawer"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-type`">{{ t('lessonEditor.question.type') }}</label>
          <select
            :id="`${formId}-type`"
            v-model="type"
            class="select"
            data-testid="question-type"
            @change="adjustOptions"
          >
            <option v-for="value in QUESTION_TYPES" :key="value" :value="value">{{ t(questionTypeKey(value)) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-points`">{{ t('lessonEditor.question.points') }}</label>
          <input
            :id="`${formId}-points`"
            v-model.number="points"
            class="input"
            type="number"
            min="1"
            max="100"
            required
          />
        </div>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-text`">
          {{ t('lessonEditor.question.text') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          :id="`${formId}-text`"
          v-model="text"
          class="textarea cjk-text"
          rows="2"
          maxlength="2000"
          required
          data-testid="question-text"
        />
      </div>
      <div v-if="type === 'LISTEN_CHOOSE'" class="field">
        <label class="field-label" :for="`${formId}-audio`">{{ t('lessonEditor.question.audioUrl') }}</label>
        <input
          :id="`${formId}-audio`"
          v-model="audioUrl"
          class="input"
          type="url"
          maxlength="500"
          placeholder="https://…"
        />
      </div>
      <div v-if="type === 'MATCH_WORD_IMAGE'" class="field">
        <label class="field-label" :for="`${formId}-image`">{{ t('lessonEditor.question.imageUrl') }}</label>
        <input
          :id="`${formId}-image`"
          v-model="imageUrl"
          class="input"
          type="url"
          maxlength="500"
          placeholder="https://…"
        />
      </div>

      <div v-if="isTypedQuestion(type)" class="field">
        <label class="field-label" :for="`${formId}-answer`">
          {{ t('lessonEditor.question.correctAnswer') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input :id="`${formId}-answer`" v-model="correctAnswerText" class="input cjk-text" maxlength="1000" required />
      </div>
      <fieldset v-else class="form-section">
        <legend class="form-section-title">{{ t('lessonEditor.question.options') }}</legend>
        <div v-for="(option, index) in options" :key="index" class="lesson-editor-row is-option">
          <label class="choice">
            <input v-model="correctIndex" type="radio" :name="`${formId}-correct`" :value="index" />
            <span class="sr-only">{{ t('lessonEditor.question.correct') }} {{ index + 1 }}</span>
          </label>
          <input
            v-model="option.text"
            class="input cjk-text"
            maxlength="500"
            :aria-label="t('lessonEditor.question.option', { number: index + 1 })"
            :placeholder="t('lessonEditor.question.option', { number: index + 1 })"
            :readonly="type === 'TRUE_FALSE'"
          />
          <button
            v-if="type !== 'TRUE_FALSE'"
            type="button"
            class="btn btn-ghost btn-icon btn-sm"
            :disabled="options.length <= 2"
            :aria-label="t('lessonEditor.question.removeOption', { number: index + 1 })"
            @click="removeOption(index)"
          >
            <IconGlyph name="trash" :size="16" />
          </button>
        </div>
        <p class="field-hint">{{ t('lessonEditor.question.correct') }}: {{ options[correctIndex]?.text || '—' }}</p>
        <button
          v-if="type !== 'TRUE_FALSE' && options.length < 6"
          type="button"
          class="btn btn-outline btn-sm"
          @click="options.push({ text: '' })"
        >
          <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.question.addOption') }}
        </button>
      </fieldset>

      <div class="field">
        <label class="field-label" :for="`${formId}-explanation`">{{ t('lessonEditor.question.explanation') }}</label>
        <textarea
          :id="`${formId}-explanation`"
          v-model="explanation"
          class="textarea"
          rows="2"
          maxlength="2000"
          :aria-describedby="`${formId}-explanation-hint`"
        />
        <p :id="`${formId}-explanation-hint`" class="field-hint">{{ t('lessonEditor.question.explanationHint') }}</p>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-hint`">{{ t('lessonEditor.question.hint') }}</label>
        <input
          :id="`${formId}-hint`"
          v-model="hint"
          class="input"
          maxlength="500"
          :aria-describedby="`${formId}-hint-hint`"
        />
        <p :id="`${formId}-hint-hint`" class="field-hint">{{ t('lessonEditor.question.hintHint') }}</p>
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn btn-ghost" :disabled="busy" @click="emit('close')">
        {{ t('classes.cancel') }}
      </button>
      <button
        type="submit"
        class="btn btn-primary"
        :form="formId"
        :disabled="busy || !text.trim()"
        :aria-busy="busy"
        data-testid="question-save"
      >
        {{ t('lessonEditor.question.save') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { EditorQuestion, QuestionPayload } from '@/types/classes'
import { QUESTION_TYPES, isTypedQuestion, questionTypeKey } from '@/utils/classes'

import AppOverlay from '@/components/common/AppOverlay.vue'

/** Add or edit a quiz question of any type, with its options or typed answer, explanation and hint. */
const props = defineProps<{ open: boolean; question: EditorQuestion | null; busy: boolean; error: string | null }>()
const emit = defineEmits<{ save: [payload: QuestionPayload]; close: [] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const formId = useId()

const type = ref('MULTIPLE_CHOICE')
const text = ref('')
const points = ref(2)
const options = ref<{ text: string }[]>([])
const correctIndex = ref(0)
const correctAnswerText = ref('')
const explanation = ref('')
const hint = ref('')
const audioUrl = ref('')
const imageUrl = ref('')

function trueFalseOptions(): { text: string }[] {
  return [{ text: t('lessonEditor.question.true') }, { text: t('lessonEditor.question.false') }]
}

watch(
  () => [props.open, props.question] as const,
  ([open, question]) => {
    if (!open) return
    type.value = question?.type ?? 'MULTIPLE_CHOICE'
    text.value = question?.text ?? ''
    points.value = question?.points ?? 2
    options.value = question?.options.length
      ? question.options.map((option) => ({ text: option.text }))
      : [{ text: '' }, { text: '' }, { text: '' }]
    correctIndex.value = Math.max(0, question?.options.findIndex((option) => option.correct) ?? 0)
    correctAnswerText.value = question?.correctAnswerText ?? ''
    explanation.value = question?.explanation ?? ''
    hint.value = question?.hint ?? ''
    audioUrl.value = question?.audioUrl ?? ''
    imageUrl.value = question?.imageUrl ?? ''
  },
  { immediate: true },
)

function adjustOptions(): void {
  if (type.value === 'TRUE_FALSE') {
    options.value = trueFalseOptions()
    correctIndex.value = 0
  } else if (options.value.length < 2) {
    options.value = [{ text: '' }, { text: '' }]
  }
}

function removeOption(index: number): void {
  options.value.splice(index, 1)
  if (correctIndex.value >= options.value.length) correctIndex.value = 0
}

function save(): void {
  const typed = isTypedQuestion(type.value)
  emit('save', {
    questionType: type.value,
    questionText: text.value.trim(),
    points: Number(points.value) || 1,
    correctAnswerText: typed ? correctAnswerText.value.trim() : null,
    explanation: explanation.value.trim() || null,
    hint: hint.value.trim() || null,
    ...(type.value === 'LISTEN_CHOOSE' ? { audioUrl: audioUrl.value.trim() || null } : {}),
    ...(type.value === 'MATCH_WORD_IMAGE' ? { imageUrl: imageUrl.value.trim() || null } : {}),
    options: typed
      ? []
      : options.value
          .map((option, index) => ({
            optionText: option.text.trim(),
            correctOption: index === correctIndex.value,
          }))
          .filter((option) => option.optionText),
  })
}
</script>
