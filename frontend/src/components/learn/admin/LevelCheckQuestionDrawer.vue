<template>
  <AppOverlay
    variant="drawer"
    size="lg"
    :open="open"
    :title="questionId === null ? t('adminLearning.newQuestion') : t('adminLearning.editQuestion')"
    :subtitle="subtitle"
    test-id="question-drawer"
    @close="emit('close')"
  >
    <StatePanel
      v-if="loading"
      tone="loading"
      :title="t('adminLearning.questionLoading')"
      :message="t('adminLearning.questionLoadingHint')"
    />
    <form v-else :id="formId" class="form-grid admin-learning-form" novalidate @submit.prevent="save">
      <div class="admin-learning-chips">
        <span class="chip"><IconGlyph name="checklist" :size="14" />{{ t('adminLearning.multipleChoice') }}</span>
        <span class="type-caption">{{ statsLine }}</span>
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
          :placeholder="t('adminLearning.questionPlaceholder')"
          data-testid="question-prompt"
        />
      </div>
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-hanzi`">{{ t('adminLearning.chinese') }}</label>
          <input :id="`${formId}-hanzi`" v-model="form.hanzi" class="input cjk-text" maxlength="100" lang="zh-CN" />
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-pinyin`">{{ t('adminLearning.pinyin') }}</label>
          <input :id="`${formId}-pinyin`" v-model="form.pinyin" class="input" maxlength="200" />
        </div>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-audio`">{{ t('adminLearning.readAloud') }}</label>
        <input
          :id="`${formId}-audio`"
          v-model="form.audioText"
          class="input cjk-text"
          maxlength="200"
          lang="zh-CN"
          :aria-describedby="`${formId}-audio-hint`"
        />
        <p :id="`${formId}-audio-hint`" class="field-hint">{{ t('adminLearning.readAloudHint') }}</p>
      </div>
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-skill`">
            {{ t('adminLearning.skill') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select :id="`${formId}-skill`" v-model="form.skill" class="select">
            <option v-for="skill in SKILLS" :key="skill" :value="skill">{{ t(skillKey(skill)) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-difficulty`">
            {{ t('adminLearning.difficulty') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select :id="`${formId}-difficulty`" v-model="form.difficulty" class="select">
            <option value="EASY">{{ t('adminLearning.easy') }}</option>
            <option value="MEDIUM">{{ t('adminLearning.medium') }}</option>
            <option value="HARD">{{ t('adminLearning.hard') }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-level`">
            {{ t('adminLearning.level') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select :id="`${formId}-level`" v-model="form.level" class="select">
            <option value="BEGINNER_1">{{ t('adminLearning.beginner1') }}</option>
            <option value="BEGINNER_2">{{ t('adminLearning.beginner2') }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-status`">{{ t('adminLearning.status') }}</label>
          <select :id="`${formId}-status`" v-model="form.status" class="select">
            <option value="DRAFT">{{ t('adminLearning.draft') }}</option>
            <option value="PUBLISHED">{{ t('adminLearning.published') }}</option>
          </select>
        </div>
      </div>

      <fieldset class="form-section admin-learning-answers">
        <legend class="form-section-title">{{ t('adminLearning.answersTickRight') }}</legend>
        <div v-for="(option, index) in form.options" :key="index" class="admin-learning-answer">
          <label class="choice">
            <input v-model="rightIndex" type="radio" :name="`${formId}-right`" :value="index" />
            <span class="sr-only">{{ t('adminLearning.rightAnswerFor', { number: index + 1 }) }}</span>
          </label>
          <input
            v-model="option.text"
            class="input"
            maxlength="200"
            :aria-label="t('adminLearning.answerNumber', { number: index + 1 })"
            :placeholder="t('adminLearning.answer')"
            :data-testid="`answer-${index}`"
          />
          <input
            v-model="option.explanation"
            class="input"
            maxlength="300"
            :aria-label="t('adminLearning.explanationFor', { number: index + 1 })"
            :placeholder="index === rightIndex ? t('adminLearning.whyRight') : t('adminLearning.whatItMeans')"
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
          <IconGlyph name="plus" :size="14" />{{ t('adminLearning.addAnswer') }}
        </button>
        <p class="field-hint"><IconGlyph name="info" :size="14" /> {{ t('adminLearning.dontKnowNote') }}</p>
        <p class="field-hint">{{ t('adminLearning.placementOnly') }}</p>
      </fieldset>
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
    </form>
    <template #footer>
      <button class="btn btn-ghost" type="button" @click="emit('close')">{{ t('adminLearning.cancel') }}</button>
      <button
        class="btn btn-primary"
        type="submit"
        :form="formId"
        :disabled="saving || loading"
        :aria-busy="saving"
        data-testid="question-save"
      >
        {{ questionId === null ? t('adminLearning.saveQuestion') : t('adminLearning.saveChanges') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type {
  Difficulty,
  LevelCheckLevel,
  LevelCheckQuestionDetail,
  LevelCheckSkill,
  PublishStatus,
} from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { SKILLS, skillKey } from '@/utils/learnLabels'

/** New or edited level check question: prompt, what it shows or reads aloud, skill, level, and answers with the right one. */
const props = defineProps<{ open: boolean; questionId: number | null }>()
const emit = defineEmits<{ close: []; saved: [question: LevelCheckQuestionDetail] }>()

const { t } = useLocalMessages({ adminLearning: adminLearningMessages })
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
  options: [] as FormOption[],
})
const rightIndex = ref(0)
const loaded = ref<LevelCheckQuestionDetail | null>(null)
const loading = ref(false)
const saving = ref(false)
const formError = ref<string | null>(null)

function reset(): void {
  Object.assign(form, {
    prompt: '',
    hanzi: '',
    pinyin: '',
    audioText: '',
    skill: 'VOCABULARY',
    difficulty: 'EASY',
    level: 'BEGINNER_1',
    status: 'DRAFT',
    options: [
      { text: '', explanation: '' },
      { text: '', explanation: '' },
    ],
  })
  rightIndex.value = 0
  loaded.value = null
  formError.value = null
}

watch(
  () => [props.open, props.questionId] as const,
  async ([open, id]) => {
    if (!open) return
    reset()
    if (id === null) return
    loading.value = true
    try {
      const question = await adminLearningApi.question(id)
      loaded.value = question
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
      rightIndex.value = Math.max(
        0,
        question.options.findIndex((option) => option.correct),
      )
    } catch (reason) {
      formError.value = resolveApiError(reason, t('adminLearning.questionLoadFailed'))
    } finally {
      loading.value = false
    }
  },
)

const subtitle = computed(() => {
  if (props.questionId === null) return t('adminLearning.newQuestionSubtitle')
  return loaded.value?.status === 'PUBLISHED'
    ? t('adminLearning.publishedQuestionSubtitle')
    : t('adminLearning.draftQuestionSubtitle')
})

const statsLine = computed(() => {
  const question = loaded.value
  if (!question || question.answeredCount === 0) return t('adminLearning.notAnsweredYet')
  return t('adminLearning.answeredBy', { count: question.answeredCount, percent: question.correctPercent ?? 0 })
})

function addOption(): void {
  form.options.push({ text: '', explanation: '' })
}

function removeOption(index: number): void {
  form.options.splice(index, 1)
  if (rightIndex.value >= form.options.length) rightIndex.value = form.options.length - 1
  else if (rightIndex.value > index) rightIndex.value -= 1
}

async function save(): Promise<void> {
  formError.value = null
  if (!form.prompt.trim()) {
    formError.value = t('adminLearning.questionRequired')
    return
  }
  if (form.options.some((option) => !option.text.trim())) {
    formError.value = t('adminLearning.answerTextRequired')
    return
  }
  saving.value = true
  try {
    const saved = await adminLearningApi.saveQuestion(props.questionId, {
      prompt: form.prompt.trim(),
      hanzi: form.hanzi.trim() || null,
      pinyin: form.pinyin.trim() || null,
      audioText: form.audioText.trim() || null,
      skill: form.skill,
      difficulty: form.difficulty,
      level: form.level,
      status: form.status,
      options: form.options.map((option, index) => ({
        text: option.text.trim(),
        correct: index === rightIndex.value,
        explanation: option.explanation.trim() || null,
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
