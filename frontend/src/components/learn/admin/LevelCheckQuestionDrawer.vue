<template>
  <AppOverlay
    variant="drawer"
    size="lg"
    :title="questionId === null ? t('adminLearning.newQuestion') : t('adminLearning.editQuestion')"
    :subtitle="subtitle"
    :busy="saving"
    test-id="question-drawer"
    @close="emit('close')"
  >
    <StatePanel
      v-if="existing.loading.value"
      tone="loading"
      :title="t('adminLearning.questionLoading')"
      :message="t('adminLearning.questionLoadingHint')"
    />
    <!-- Without the question there is nothing to edit: no empty form, only Try again. -->
    <StatePanel
      v-else-if="existing.error.value"
      tone="error"
      icon="alert"
      :title="t('adminLearning.questionLoadFailed')"
      :message="existing.error.value"
      @retry="existing.reload"
    />
    <form
      v-else
      :id="formId"
      ref="formElement"
      class="form-grid admin-learning-form"
      novalidate
      @input="formError = null"
      @change="formError = null"
      @submit.prevent="save"
    >
      <div class="admin-tags">
        <span class="tag is-outline"
          ><IconGlyph name="checklist" :size="14" />{{ t('adminLearning.multipleChoice') }}</span
        >
        <span class="admin-meta">{{ statsLine }}</span>
      </div>

      <div class="field">
        <label class="field-label" :for="`${formId}-prompt`">
          {{ t('adminLearning.question') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-prompt`"
          v-model="form.prompt"
          class="input"
          maxlength="300"
          required
          v-bind="errorAttributes('prompt')"
          :disabled="saving"
          :placeholder="t('adminLearning.questionPlaceholder')"
          data-testid="question-prompt"
          data-autofocus
        />
      </div>
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-hanzi`">{{ t('adminLearning.chinese') }}</label>
          <input
            :id="`${formId}-hanzi`"
            v-model="form.hanzi"
            class="input cjk-text"
            maxlength="100"
            lang="zh-CN"
            :disabled="saving"
          />
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-pinyin`">{{ t('adminLearning.pinyin') }}</label>
          <input :id="`${formId}-pinyin`" v-model="form.pinyin" class="input" maxlength="200" :disabled="saving" />
        </div>
      </div>
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-skill`">
            {{ t('adminLearning.skill') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select :id="`${formId}-skill`" v-model="form.skill" class="select" :disabled="saving">
            <option v-for="(labelKey, value) in SKILL_KEYS" :key="value" :value="value">{{ t(labelKey) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-level`">
            {{ t('adminLearning.level') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select :id="`${formId}-level`" v-model="form.level" class="select" :disabled="saving">
            <option v-for="(hsk, value) in LEVEL_HSK" :key="value" :value="value">
              {{ t('hsk.number', { level: hsk }) }}
            </option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-difficulty`">
            {{ t('adminLearning.difficulty') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select :id="`${formId}-difficulty`" v-model="form.difficulty" class="select" :disabled="saving">
            <option v-for="(labelKey, value) in DIFFICULTY_KEYS" :key="value" :value="value">{{ t(labelKey) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-status`">{{ t('adminLearning.status') }}</label>
          <select :id="`${formId}-status`" v-model="form.status" class="select" :disabled="saving">
            <option value="DRAFT">{{ t('adminLearning.draft') }}</option>
            <option value="PUBLISHED">{{ t('adminLearning.published') }}</option>
          </select>
        </div>
      </div>
      <div v-if="form.skill === 'LISTENING'" class="field">
        <label class="field-label" :for="`${formId}-audio`">{{ t('adminLearning.readAloud') }}</label>
        <input
          :id="`${formId}-audio`"
          v-model="form.audioText"
          class="input cjk-text"
          maxlength="200"
          lang="zh-CN"
          :disabled="saving"
          :aria-describedby="`${formId}-audio-hint`"
          v-bind="errorAttributes('audio')"
        />
        <p :id="`${formId}-audio-hint`" class="field-hint">{{ t('adminLearning.readAloudHint') }}</p>
      </div>

      <fieldset class="field-set" :disabled="saving">
        <legend>{{ t('adminLearning.answersTickRight') }}</legend>
        <div class="admin-learning-answer type-caption" aria-hidden="true">
          <span>{{ t('adminLearning.answer') }}</span>
          <span />
          <span>{{ t('adminLearning.explanation') }}</span>
        </div>
        <div v-for="(option, index) in form.options" :key="index" class="admin-learning-answer">
          <input
            v-model="option.text"
            class="input"
            maxlength="200"
            v-bind="errorAttributes(`answer-${index}`)"
            :aria-label="t('adminLearning.answerNumber', { number: index + 1 })"
            :placeholder="t('adminLearning.answer')"
            :data-testid="`answer-${index}`"
          />
          <label class="admin-learning-right" :class="{ 'is-right': rightOption === option }">
            <input
              type="radio"
              v-bind="errorAttributes(`correct-${index}`)"
              :name="`${formId}-right`"
              :checked="rightOption === option"
              @change="rightOption = option"
            />
            <span aria-hidden="true">{{
              rightOption === option ? t('adminLearning.rightAnswer') : t('adminLearning.wrongAnswer')
            }}</span>
            <span class="sr-only">{{ t('adminLearning.rightAnswerFor', { number: index + 1 }) }}</span>
          </label>
          <input
            v-model="option.explanation"
            class="input"
            maxlength="300"
            :aria-label="t('adminLearning.explanationFor', { number: index + 1 })"
            :placeholder="option === rightOption ? t('adminLearning.whyRight') : t('adminLearning.wrongOptionHint')"
          />
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :disabled="form.options.length <= 2"
            :aria-label="t('adminLearning.removeAnswer', { number: index + 1 })"
            @click="removeOption(index)"
          >
            <IconGlyph name="trash" :size="16" />
          </button>
        </div>
        <button class="btn btn-secondary btn-sm" type="button" :disabled="form.options.length >= 6" @click="addOption">
          <IconGlyph name="plus" :size="14" />
          <span>{{ t('adminLearning.addAnswer') }}</span>
        </button>
      </fieldset>
    </form>
    <template #footer>
      <p v-if="formError" :id="`${formId}-error`" class="field-error" role="alert">{{ formError.message }}</p>
      <button class="btn btn-secondary" type="button" :disabled="saving" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-primary"
        type="submit"
        :form="formId"
        :disabled="saving || existing.loading.value || existing.error.value !== null"
        :aria-busy="saving"
        data-testid="question-save"
      >
        {{ questionId === null ? t('adminLearning.saveQuestion') : t('adminLearning.saveChanges') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, useId, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminLearningPathMessages } from '@/plugins/adminLearningPathMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import type {
  Difficulty,
  LevelCheckLevel,
  LevelCheckQuestionDetail,
  LevelCheckSkill,
  PublishStatus,
} from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { DIFFICULTY_KEYS, LEVEL_HSK, SKILL_KEYS } from '@/utils/learnLabels'
import { normalizeAppLanguage } from '@/utils/locale'
import { localizeServerMessage } from '@/utils/serverMessages'

/**
 * New or edited level check question: prompt, what it shows or reads aloud, skill, level, and answers with the right
 * one. The page mounts it for one opening, so an existing question is read once.
 */
const props = defineProps<{ questionId: number | null }>()
const emit = defineEmits<{ close: []; saved: [question: LevelCheckQuestionDetail] }>()

const { t, locale } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminLearningPathMessages] })
const serverRule = (message: string) => localizeServerMessage(message, normalizeAppLanguage(locale.value) ?? 'en')
const formId = `question-${useId()}`

interface FormOption {
  text: string
  explanation: string
}

const form = reactive({
  prompt: '',
  hanzi: '',
  pinyin: '',
  audioText: '',
  skill: 'VOCABULARY' as LevelCheckSkill,
  difficulty: 'EASY' as Difficulty,
  level: 'BEGINNER_1' as LevelCheckLevel,
  status: 'DRAFT' as PublishStatus,
  options: [
    { text: '', explanation: '' },
    { text: '', explanation: '' },
    { text: '', explanation: '' },
  ] as FormOption[],
})
const rightOption = ref<FormOption | null>(form.options[0])
const saving = ref(false)
const formElement = ref<HTMLFormElement | null>(null)
const formError = ref<{ field?: string; message: string } | null>(null)

function errorAttributes(field: string) {
  return formError.value?.field === field ? { 'aria-invalid': true, 'aria-errormessage': `${formId}-error` } : {}
}

/** The edited question as it is stored; a new question has none to read. */
const existing = useResource(() => adminLearningApi.question(props.questionId!), props.questionId !== null)

watch(existing.data, (question) => {
  if (!question) return
  Object.assign(form, {
    prompt: question.prompt,
    hanzi: question.hanzi ?? '',
    pinyin: question.pinyin ?? '',
    audioText: question.audioText ?? '',
    skill: question.skill,
    difficulty: question.difficulty,
    level: question.level,
    status: question.status,
    options: question.options.map((option) => ({ text: option.text, explanation: option.explanation ?? '' })),
  })
  rightOption.value = form.options[question.options.findIndex((option) => option.correct)] ?? null
})

const subtitle = computed(() => {
  if (props.questionId === null) return t('adminLearning.newQuestionSubtitle')
  // While the question loads, or when it could not be read, its status is not known: no "Draft" from nothing.
  const status = existing.data.value?.status
  if (!status) return ''
  return status === 'PUBLISHED'
    ? t('adminLearning.publishedQuestionSubtitle')
    : t('adminLearning.draftQuestionSubtitle')
})

const statsLine = computed(() => {
  const question = existing.data.value
  if (!question || question.correctPercent === null) return t('adminLearning.notAnsweredYet')
  return t(
    'adminLearning.answerStats',
    { count: question.answeredCount, percent: question.correctPercent },
    question.answeredCount,
  )
})

function addOption(): void {
  formError.value = null
  form.options.push({ text: '', explanation: '' })
}

function removeOption(index: number): void {
  formError.value = null
  if (form.options[index] === rightOption.value) rightOption.value = null
  form.options.splice(index, 1)
}

/** The first thing to fix before the question can be sent, or null. */
function formProblem(): { field: string; message: string } | null {
  if (!form.prompt.trim()) return { field: 'prompt', message: t('adminLearning.questionRequired') }
  const answers = form.options.map((option) => option.text.trim())
  const empty = answers.indexOf('')
  if (empty !== -1) return { field: `answer-${empty}`, message: serverRule('Every answer needs text.') }
  const repeated = answers.findIndex((answer, index) => answers.indexOf(answer) < index)
  if (repeated !== -1) {
    return {
      field: `answer-${repeated}`,
      message: serverRule('The answers of a question must all be different.'),
    }
  }
  if (!rightOption.value) return { field: 'correct-0', message: serverRule('Tick exactly one right answer.') }
  if (form.status === 'PUBLISHED' && form.skill === 'LISTENING' && !form.audioText.trim()) {
    return { field: 'audio', message: serverRule('Every listening question needs the Chinese that is read aloud.') }
  }
  return null
}

async function save(): Promise<void> {
  // The form only shows once an edited question is read, so only a second submit can arrive too early.
  if (saving.value) return
  formError.value = formProblem()
  if (formError.value) {
    await nextTick()
    formElement.value?.querySelector<HTMLInputElement>('[aria-invalid="true"]')?.focus()
    return
  }
  saving.value = true
  try {
    const saved = await adminLearningApi.saveQuestion(props.questionId, {
      prompt: form.prompt.trim(),
      hanzi: form.hanzi.trim() || null,
      pinyin: form.pinyin.trim() || null,
      // Shown for listening only: text left from before a change of skill must not make it a listening question.
      audioText: form.skill === 'LISTENING' ? form.audioText.trim() || null : null,
      skill: form.skill,
      difficulty: form.difficulty,
      level: form.level,
      status: form.status,
      options: form.options.map((option) => ({
        text: option.text.trim(),
        correct: option === rightOption.value,
        explanation: option.explanation.trim() || null,
      })),
    })
    emit('saved', saved)
  } catch (reason) {
    formError.value = { message: resolveApiError(reason, t('adminLearning.saveFailed')) }
  } finally {
    saving.value = false
  }
}
</script>
