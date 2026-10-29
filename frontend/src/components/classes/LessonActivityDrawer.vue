<template>
  <AppOverlay
    variant="drawer"
    :open="open"
    :title="activity ? t('lessonEditor.activities.drawerEdit') : t('lessonEditor.activities.drawerNew')"
    :busy="busy"
    size="lg"
    test-id="activity-drawer"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-type`">{{ t('lessonEditor.activities.type') }}</label>
          <select
            :id="`${formId}-type`"
            v-model="type"
            class="select"
            :disabled="Boolean(activity)"
            data-testid="activity-type"
          >
            <option v-for="value in TYPES" :key="value" :value="value">{{ t(ACTIVITY_TYPE_KEYS[value]) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-stage`">{{ t('lessonEditor.activities.stage') }}</label>
          <select :id="`${formId}-stage`" v-model="stage" class="select">
            <option :value="null">{{ t('lessonEditor.activities.noStage') }}</option>
            <option v-for="value in STAGES" :key="value" :value="value">{{ t(STAGE_KEYS[value]) }}</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-title`">
          {{ t('lessonEditor.activities.activityTitle') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-title`"
          v-model="title"
          class="input"
          required
          maxlength="200"
          data-testid="activity-title"
        />
      </div>

      <p v-if="type === 'FLASHCARDS'" class="alert alert-success">{{ t('lessonEditor.activities.flashcardsNote') }}</p>

      <fieldset v-else-if="type === 'TRACING'" class="form-section">
        <legend class="form-section-title">{{ t('lessonEditor.activities.characters') }}</legend>
        <div v-for="(row, index) in characters" :key="index" class="lesson-editor-row">
          <span class="lesson-editor-row-number" aria-hidden="true">{{ index + 1 }}</span>
          <input
            v-model="row.character"
            class="input cjk-text"
            maxlength="2"
            :aria-label="`${t('lessonEditor.activities.character')} ${index + 1}`"
          />
          <input
            v-model="row.pinyin"
            class="input"
            maxlength="40"
            :aria-label="`${t('lessonEditor.activities.charPinyin')} ${index + 1}`"
            :placeholder="t('lessonEditor.activities.charPinyin')"
          />
          <input
            v-model="row.meaning"
            class="input"
            maxlength="80"
            :aria-label="`${t('lessonEditor.activities.charMeaning')} ${index + 1}`"
            :placeholder="t('lessonEditor.activities.charMeaning')"
          />
          <button
            type="button"
            class="btn btn-ghost btn-icon btn-sm"
            :disabled="characters.length === 1"
            :aria-label="t('lessonEditor.activities.removeCharacter', { number: index + 1 })"
            @click="characters.splice(index, 1)"
          >
            <IconGlyph name="trash" :size="16" />
          </button>
        </div>
        <button
          v-if="characters.length < 8"
          type="button"
          class="btn btn-outline btn-sm"
          @click="characters.push(blankCharacter())"
        >
          <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.activities.addCharacter') }}
        </button>
      </fieldset>

      <fieldset v-else class="form-section">
        <legend class="form-section-title">{{ t('lessonEditor.activities.pairs') }}</legend>
        <div v-for="(row, index) in pairs" :key="index" class="lesson-editor-row">
          <span class="lesson-editor-row-number" aria-hidden="true">{{ index + 1 }}</span>
          <input
            v-model="row.chinese"
            class="input cjk-text"
            maxlength="100"
            :aria-label="`${t('lessonEditor.activities.pairChinese')} ${index + 1}`"
            :placeholder="t('lessonEditor.activities.pairChinese')"
          />
          <input
            v-model="row.pinyin"
            class="input"
            maxlength="200"
            :aria-label="`${t('lessonEditor.activities.pairPinyin')} ${index + 1}`"
            :placeholder="t('lessonEditor.activities.pairPinyin')"
          />
          <input
            v-model="row.meaning"
            class="input"
            maxlength="300"
            :aria-label="`${t('lessonEditor.activities.pairMeaning')} ${index + 1}`"
            :placeholder="t('lessonEditor.activities.pairMeaning')"
          />
          <button
            type="button"
            class="btn btn-ghost btn-icon btn-sm"
            :disabled="pairs.length <= 2"
            :aria-label="t('lessonEditor.activities.removePair', { number: index + 1 })"
            @click="pairs.splice(index, 1)"
          >
            <IconGlyph name="trash" :size="16" />
          </button>
        </div>
        <button v-if="pairs.length < 12" type="button" class="btn btn-outline btn-sm" @click="pairs.push(blankPair())">
          <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.activities.addPair') }}
        </button>
      </fieldset>
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
        :disabled="busy || !title.trim()"
        :aria-busy="busy"
        data-testid="activity-save"
      >
        {{ t('lessonEditor.activities.save') }}
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
import type { LearningStage } from '@/types/api'
import type { ActivityPayload, ClassActivityType, EditorActivity, MatchPair, TraceCharacter } from '@/types/classes'
import { ACTIVITY_TYPE_KEYS, STAGE_KEYS } from '@/utils/classes'
import { STAGES } from '@/utils/learnLabels'

import AppOverlay from '@/components/common/AppOverlay.vue'

/** Add or edit one lesson activity: flashcards (from the key phrases), tracing characters or matching pairs. */
const props = defineProps<{ open: boolean; activity: EditorActivity | null; busy: boolean; error: string | null }>()
const emit = defineEmits<{ save: [payload: ActivityPayload]; close: [] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const formId = useId()
const TYPES: readonly ClassActivityType[] = ['FLASHCARDS', 'TRACING', 'MATCHING']

const type = ref<ClassActivityType>('FLASHCARDS')
const title = ref('')
const stage = ref<LearningStage | null>('GUIDED')
const characters = ref<TraceCharacter[]>([])
const pairs = ref<MatchPair[]>([])

const blankCharacter = (): TraceCharacter => ({ character: '', pinyin: '', meaning: '' })
const blankPair = (): MatchPair => ({ chinese: '', pinyin: '', meaning: '' })

watch(
  () => [props.open, props.activity] as const,
  ([open, activity]) => {
    if (!open) return
    type.value = activity?.type ?? 'FLASHCARDS'
    title.value = activity?.title ?? ''
    stage.value = activity ? activity.stage : 'GUIDED'
    characters.value = activity?.characters.length
      ? activity.characters.map((row) => ({ ...row, meaning: row.meaning ?? '' }))
      : [blankCharacter()]
    pairs.value = activity?.pairs.length ? activity.pairs.map((row) => ({ ...row })) : [blankPair(), blankPair()]
  },
  { immediate: true },
)

function save(): void {
  const payload: ActivityPayload = { type: type.value, title: title.value.trim(), stage: stage.value }
  if (type.value === 'TRACING') {
    payload.characters = characters.value
      .map((row) => ({
        character: row.character.trim(),
        pinyin: row.pinyin.trim(),
        meaning: row.meaning?.trim() || null,
      }))
      .filter((row) => row.character)
  }
  if (type.value === 'MATCHING') {
    payload.pairs = pairs.value
      .map((row) => ({ chinese: row.chinese.trim(), pinyin: row.pinyin.trim(), meaning: row.meaning.trim() }))
      .filter((row) => row.chinese || row.meaning)
  }
  emit('save', payload)
}
</script>
