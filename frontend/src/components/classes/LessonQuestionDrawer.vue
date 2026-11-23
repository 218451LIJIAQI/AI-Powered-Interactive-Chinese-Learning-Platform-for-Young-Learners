<template>
  <AppOverlay
    variant="drawer"
    size="lg"
    :title="question ? t('lessonEditor.question.drawerEdit') : t('lessonEditor.question.drawerNew')"
    :busy="busy"
    test-id="question-drawer"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <div class="field">
        <label class="field-label" :for="`${formId}-type`">
          {{ t('lessonEditor.question.type') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <select :id="`${formId}-type`" v-model="type" class="select" :disabled="busy" @change="adjustOptions">
          <option v-for="value in QUIZ_QUESTION_TYPES" :key="value" :value="value">
            {{ t(questionTypeKey(value)) }}
          </option>
        </select>
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
          :disabled="busy"
          data-autofocus
        />
      </div>
      <!-- The server refuses a listen-and-choose question without its recording, and a word-image one without its picture. -->
      <div v-if="type === 'LISTEN_CHOOSE'" class="field">
        <label class="field-label" :for="`${formId}-audio`">
          {{ t('lessonEditor.question.audioUrl') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-audio`"
          v-model="audioUrl"
          :disabled="busy"
          class="input"
          type="url"
          maxlength="500"
          required
          placeholder="https://…"
        />
      </div>
      <div v-if="type === 'MATCH_WORD_IMAGE'" class="field">
        <label class="field-label" :for="`${formId}-image`">
          {{ t('lessonEditor.question.imageUrl') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-image`"
          v-model="imageUrl"
          :disabled="busy"
          class="input"
          type="url"
          maxlength="500"
          required
          placeholder="https://…"
        />
      </div>

      <div v-if="isTypedQuestion(type)" class="field">
        <label class="field-label" :for="`${formId}-answer`">
          {{ t('lessonEditor.question.correctAnswer') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-answer`"
          v-model="correctAnswerText"
          class="input cjk-text"
          maxlength="1000"
          required
          :disabled="busy"
        />
      </div>
      <div v-else class="lesson-rows-set" role="group" :aria-labelledby="`${formId}-options`">
        <div class="lesson-rows-head">
          <span :id="`${formId}-options`" class="field-label">{{ t('lessonEditor.question.options') }}</span>
          <button
            v-if="type !== 'TRUE_FALSE' && options.length < 6"
            :disabled="busy"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="options.push(blankOption())"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.question.addOption') }}
          </button>
        </div>
        <!-- Answer · Right or wrong · Feedback (A3.19): the feedback is what a learner who picks the answer reads. -->
        <div class="lesson-rows-box">
          <div class="lesson-answer-row is-head" aria-hidden="true">
            <span>{{ t('lessonEditor.question.answerColumn') }}</span>
            <span>{{ t('lessonEditor.question.rightOrWrong') }}</span>
            <span>{{ t('lessonEditor.question.feedbackColumn') }}</span>
            <span />
          </div>
          <div v-for="(option, index) in options" :key="index" class="lesson-answer-row" data-testid="answer-row">
            <div class="lesson-phrase-field">
              <span class="lesson-phrase-label" aria-hidden="true">{{ t('lessonEditor.question.answerColumn') }}</span>
              <input
                v-model="option.text"
                :disabled="busy"
                class="input cjk-text"
                maxlength="1000"
                :aria-label="t('lessonEditor.question.option', { number: index + 1 })"
                :placeholder="t('lessonEditor.question.option', { number: index + 1 })"
                :readonly="type === 'TRUE_FALSE'"
                :required="requiresAnswerText(option)"
              />
            </div>
            <label class="choice">
              <input
                type="radio"
                :name="`${formId}-correct`"
                :checked="rightOption === option"
                :disabled="busy"
                required
                @change="rightOption = option"
              />
              <span aria-hidden="true">{{
                option === rightOption ? t('lessonEditor.question.correctAnswer') : t('lessonEditor.question.wrong')
              }}</span>
              <span class="sr-only">{{ t('lessonEditor.question.rightLabel', { number: index + 1 }) }}</span>
            </label>
            <div class="lesson-phrase-field">
              <span class="lesson-phrase-label" aria-hidden="true">{{
                t('lessonEditor.question.feedbackColumn')
              }}</span>
              <input
                v-model="option.feedback"
                :disabled="busy"
                class="input cjk-text"
                maxlength="300"
                :aria-label="t('lessonEditor.question.feedbackLabel', { number: index + 1 })"
              />
            </div>
            <button
              v-if="type !== 'TRUE_FALSE'"
              type="button"
              class="btn btn-ghost btn-icon btn-sm"
              :disabled="busy || options.length <= 2"
              :aria-label="t('lessonEditor.question.removeOption', { number: index + 1 })"
              @click="removeOption(index)"
            >
              <IconGlyph name="trash" :size="16" />
            </button>
            <span v-else aria-hidden="true" />
          </div>
        </div>
      </div>

      <div class="field">
        <label class="field-label" :for="`${formId}-explanation`">{{ t('lessonEditor.question.explanation') }}</label>
        <textarea
          :id="`${formId}-explanation`"
          v-model="explanation"
          :disabled="busy"
          class="textarea"
          rows="2"
          maxlength="2000"
          :aria-describedby="`${formId}-explanation-hint`"
        />
        <p :id="`${formId}-explanation-hint`" class="field-hint">{{ t('lessonEditor.question.explanationHint') }}</p>
      </div>
    </form>
    <template #footer>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
      <button type="button" class="btn btn-secondary" :disabled="busy" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        type="submit"
        class="btn btn-primary"
        :form="formId"
        :disabled="busy || missingRequired"
        :aria-busy="busy"
        data-testid="question-save"
      >
        {{ t('lessonEditor.question.save') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { EditorQuestion, QuestionPayload } from '@/types/adminClasses'
import { QUIZ_QUESTION_TYPES } from '@/types/api'
import { isTypedQuestion, questionTypeKey } from '@/utils/classes'
import { DEFAULT_QUESTION_POINTS } from '@/utils/lessonEditor'

/**
 * Add or edit a quiz question of any type, with its answers (the right one ticked, each with the feedback a learner
 * who picks it reads) or typed answer, and the explanation learners see with their result. The parent mounts it for
 * one opening, so the draft starts from the question it is given.
 */
const props = defineProps<{ question: EditorQuestion | null; busy: boolean; error: string | null }>()
const emit = defineEmits<{ save: [payload: QuestionPayload]; close: [] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const formId = useId()

/** An answer as typed: its text and the feedback a learner who picks it reads. */
interface DraftOption {
  text: string
  feedback: string
}

const blankOption = (text = ''): DraftOption => ({ text, feedback: '' })

const type = ref(props.question?.type ?? 'MULTIPLE_CHOICE')
const text = ref(props.question?.text ?? '')
const options = ref<DraftOption[]>(
  props.question?.options.length
    ? props.question.options.map((option) => ({ text: option.text, feedback: option.feedback ?? '' }))
    : Array.from({ length: 4 }, () => blankOption()),
)
const rightOption = ref<DraftOption | null>(
  props.question?.options.length
    ? (options.value[props.question.options.findIndex((option) => option.correct)] ?? null)
    : options.value[0],
)
const correctAnswerText = ref(props.question?.correctAnswerText ?? '')
const explanation = ref(props.question?.explanation ?? '')
const audioUrl = ref(props.question?.audioUrl ?? '')
const imageUrl = ref(props.question?.imageUrl ?? '')

function requiresAnswerText(option: DraftOption): boolean {
  return option === rightOption.value || Boolean(option.feedback.trim())
}

const missingRequired = computed(() => {
  if (!text.value.trim()) return true
  if (type.value === 'LISTEN_CHOOSE' && !audioUrl.value.trim()) return true
  if (type.value === 'MATCH_WORD_IMAGE' && !imageUrl.value.trim()) return true
  if (isTypedQuestion(type.value)) return !correctAnswerText.value.trim()
  return !rightOption.value || options.value.some((option) => requiresAnswerText(option) && !option.text.trim())
})

function adjustOptions(): void {
  if (type.value === 'TRUE_FALSE') {
    options.value = [blankOption(t('lessonEditor.question.true')), blankOption(t('lessonEditor.question.false'))]
    rightOption.value = options.value[0]
  } else if (options.value.length < 2) {
    while (options.value.length < 2) options.value.push(blankOption())
    if (!rightOption.value) rightOption.value = options.value[0]
  }
}

function removeOption(index: number): void {
  if (options.value[index] === rightOption.value) rightOption.value = null
  options.value.splice(index, 1)
}

function save(): void {
  if (props.busy || missingRequired.value) return
  const typed = isTypedQuestion(type.value)
  emit('save', {
    questionType: type.value,
    questionText: text.value.trim(),
    points: props.question?.points ?? DEFAULT_QUESTION_POINTS,
    correctAnswerText: typed ? correctAnswerText.value.trim() : null,
    explanation: explanation.value.trim() || null,
    // SparkBot writes the hints now; a hint an older question carries is kept as it is.
    hint: props.question?.hint ?? null,
    ...(type.value === 'LISTEN_CHOOSE' ? { audioUrl: audioUrl.value.trim() || null } : {}),
    ...(type.value === 'MATCH_WORD_IMAGE' ? { imageUrl: imageUrl.value.trim() || null } : {}),
    options: typed
      ? []
      : options.value
          .map((option) => ({
            optionText: option.text.trim(),
            correctOption: option === rightOption.value,
            feedback: option.feedback.trim() || null,
          }))
          .filter((option) => option.optionText),
  })
}
</script>
