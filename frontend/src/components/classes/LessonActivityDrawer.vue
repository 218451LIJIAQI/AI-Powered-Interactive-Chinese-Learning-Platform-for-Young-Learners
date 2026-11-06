<template>
  <AppOverlay
    variant="drawer"
    :open="open"
    :title="t('lessonEditor.activities.drawerEdit')"
    :subtitle="t('lessonEditor.activities.drawerSubtitle', { lesson: lessonTitle })"
    :busy="busy"
    test-id="activity-drawer"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <div class="lesson-activity-kind">
        <span class="tag is-outline"
          ><IconGlyph :name="ACTIVITY_ICONS[type]" :size="14" />{{ t(LESSON_ACTIVITY_KEYS[type]) }}</span
        >
        <span class="type-caption">{{ t('lessonEditor.activities.position', { number: position }) }}</span>
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

      <p v-if="type === 'FLASHCARDS'" class="callout">
        <IconGlyph name="info" :size="18" /><span>{{ t('lessonEditor.activities.flashcardsNote') }}</span>
      </p>

      <div v-else-if="type === 'TRACING'" class="lesson-rows-set" role="group" :aria-labelledby="`${formId}-rows`">
        <div class="lesson-rows-head">
          <span :id="`${formId}-rows`" class="field-label">{{ t('lessonEditor.activities.characters') }}</span>
          <button
            v-if="characters.length < 8"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="characters.push(blankCharacter())"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.activities.addCharacter') }}
          </button>
        </div>
        <div class="lesson-rows-box">
          <div class="lesson-rows-grid is-head" aria-hidden="true">
            <span>{{ t('lessonEditor.activities.character') }}</span>
            <span>{{ t('lessonEditor.activities.charPinyin') }}</span>
            <span>{{ t('lessonEditor.activities.charMeaning') }}</span>
            <span />
          </div>
          <div v-for="(row, index) in characters" :key="index" class="lesson-rows-grid">
            <input
              v-model="row.character"
              class="input cjk-text"
              maxlength="2"
              :aria-label="rowLabel(t('lessonEditor.activities.character'), index)"
            />
            <input
              v-model="row.pinyin"
              class="input"
              maxlength="40"
              :aria-label="rowLabel(t('lessonEditor.activities.charPinyin'), index)"
            />
            <input
              v-model="row.meaning"
              class="input"
              maxlength="80"
              :aria-label="rowLabel(t('lessonEditor.activities.charMeaning'), index)"
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
        </div>
      </div>

      <div v-else class="lesson-rows-set" role="group" :aria-labelledby="`${formId}-rows`">
        <div class="lesson-rows-head">
          <span :id="`${formId}-rows`" class="field-label">{{ t('lessonEditor.activities.pairs') }}</span>
          <button
            v-if="pairs.length < 12"
            type="button"
            class="btn btn-secondary btn-sm"
            @click="pairs.push(blankPair())"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.activities.addPair') }}
          </button>
        </div>
        <div class="lesson-rows-box">
          <div class="lesson-rows-grid is-head" aria-hidden="true">
            <span>{{ t('lessonEditor.activities.pairChinese') }}</span>
            <span>{{ t('lessonEditor.activities.pairPinyin') }}</span>
            <span>{{ t('lessonEditor.activities.pairMeaning') }}</span>
            <span />
          </div>
          <div v-for="(row, index) in pairs" :key="index" class="lesson-rows-grid">
            <input
              v-model="row.chinese"
              class="input cjk-text"
              maxlength="100"
              :aria-label="rowLabel(t('lessonEditor.activities.pairChinese'), index)"
            />
            <input
              v-model="row.pinyin"
              class="input"
              maxlength="200"
              :aria-label="rowLabel(t('lessonEditor.activities.pairPinyin'), index)"
            />
            <input
              v-model="row.meaning"
              class="input"
              maxlength="300"
              :aria-label="rowLabel(t('lessonEditor.activities.pairMeaning'), index)"
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
        </div>
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn btn-secondary" :disabled="busy" @click="emit('close')">
        {{ t('common.cancel') }}
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

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { LearningStage } from '@/types/api'
import type { ActivityPayload, ClassActivityType, EditorActivity, MatchPair, TraceCharacter } from '@/types/classes'
import { ACTIVITY_ICONS } from '@/utils/classes'
import { LESSON_ACTIVITY_KEYS } from '@/utils/teacherLesson'

/** Edit one of a lesson's three activities: flashcards (from the key phrases), tracing characters or matching pairs. */
const props = defineProps<{
  open: boolean
  type: ClassActivityType
  /** Its place in the lesson, 1 to 3. */
  position: number
  activity: EditorActivity | null
  lessonTitle: string
  busy: boolean
  error: string | null
}>()
const emit = defineEmits<{ save: [payload: ActivityPayload]; close: [] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, common: commonMessages })
const formId = useId()

/** The server still stores a stage; teachers no longer choose one, so a new activity gets the lesson's usual one. */
const DEFAULT_STAGES: Record<ClassActivityType, LearningStage> = {
  FLASHCARDS: 'GUIDED',
  TRACING: 'GUIDED',
  MATCHING: 'SCAFFOLDED',
}

const title = ref('')
const characters = ref<TraceCharacter[]>([])
const pairs = ref<MatchPair[]>([])

const blankCharacter = (): TraceCharacter => ({ character: '', pinyin: '', meaning: '' })
const blankPair = (): MatchPair => ({ chinese: '', pinyin: '', meaning: '' })

watch(
  () => [props.open, props.activity] as const,
  ([open, activity]) => {
    if (!open) return
    title.value = activity?.title ?? ''
    characters.value = activity?.characters.length
      ? activity.characters.map((row) => ({ ...row, meaning: row.meaning ?? '' }))
      : [blankCharacter()]
    pairs.value = activity?.pairs.length ? activity.pairs.map((row) => ({ ...row })) : [blankPair(), blankPair()]
  },
  { immediate: true },
)

function rowLabel(field: string, index: number): string {
  return t('lessonEditor.activities.rowField', { field, number: index + 1 })
}

function save(): void {
  const payload: ActivityPayload = {
    type: props.type,
    title: title.value.trim(),
    stage: props.activity?.stage ?? DEFAULT_STAGES[props.type],
  }
  if (props.type === 'TRACING') {
    payload.characters = characters.value
      .map((row) => ({
        character: row.character.trim(),
        pinyin: row.pinyin.trim(),
        meaning: row.meaning?.trim() || null,
      }))
      .filter((row) => row.character)
  }
  if (props.type === 'MATCHING') {
    payload.pairs = pairs.value
      .map((row) => ({ chinese: row.chinese.trim(), pinyin: row.pinyin.trim(), meaning: row.meaning.trim() }))
      .filter((row) => row.chinese || row.meaning)
  }
  emit('save', payload)
}
</script>
