<template>
  <AppOverlay
    variant="drawer"
    size="lg"
    :title="t('lessonEditor.activities.heading', { number, type: typeName })"
    :busy="busy"
    test-id="activity-drawer"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <!-- The slot decides the type, so it shows as text, not as a choice. -->
      <div class="field" role="group" :aria-labelledby="`${formId}-type`">
        <span :id="`${formId}-type`" class="field-label">{{ t('lessonEditor.activities.type') }}</span>
        <span class="lesson-activity-kind" data-testid="activity-type">
          <IconGlyph :name="ACTIVITY_ICONS[type]" :size="16" />{{
            t('lessonEditor.activities.fixedType', { type: typeName, number, total: CLASS_ACTIVITY_TYPES.length })
          }}
        </span>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-title`">
          {{ t('lessonEditor.fields.title') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-title`"
          v-model="title"
          class="input"
          required
          maxlength="200"
          :disabled="busy"
          data-autofocus
          data-testid="activity-title"
        />
      </div>

      <div class="lesson-rows-set" role="group" :aria-labelledby="`${formId}-rows`">
        <div class="lesson-rows-head">
          <span :id="`${formId}-rows`" class="field-label">{{ t(rowSet.label) }}</span>
          <button
            v-if="rows.length < rowSet.max"
            ref="addRowButton"
            type="button"
            class="btn btn-secondary btn-sm"
            :disabled="busy"
            @click="addRow($event)"
          >
            <IconGlyph name="plus" :size="16" />{{ t(rowSet.add) }}
          </button>
        </div>
        <!-- Only flashcards can be left without rows: their learners then review the lesson's key phrases. -->
        <p v-if="type === 'FLASHCARDS' && !rows.length" class="callout" data-testid="no-cards">
          <IconGlyph name="info" :size="18" /><span>{{ t('lessonEditor.activities.noCards') }}</span>
        </p>
        <div v-else ref="rowsBox" class="lesson-rows-box">
          <div class="lesson-rows-grid is-head" aria-hidden="true">
            <span v-for="column in columns" :key="column.key">{{ column.label }}</span>
            <span />
          </div>
          <div v-for="(row, index) in rows" :key="row.id" class="lesson-rows-grid" data-row-item>
            <div v-for="column in columns" :key="column.key" class="lesson-phrase-field">
              <span class="lesson-phrase-label" aria-hidden="true">{{ column.label }}</span>
              <input
                v-model="row[column.key]"
                class="input"
                :class="{ 'cjk-text': column.key === 'chinese' }"
                :maxlength="column.maxlength"
                :disabled="busy"
                :aria-label="t('lessonEditor.activities.rowField', { field: column.label, number: index + 1 })"
              />
            </div>
            <button
              type="button"
              class="btn btn-ghost btn-icon btn-sm"
              data-row-remove
              :disabled="busy || rows.length <= rowSet.min"
              :aria-label="t(rowSet.remove, { number: index + 1 })"
              @click="removeRow(index, $event)"
            >
              <IconGlyph name="trash" :size="16" />
            </button>
          </div>
        </div>
      </div>
    </form>
    <template #footer>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
      <button type="button" class="btn btn-secondary" :disabled="busy" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button type="submit" class="btn btn-primary" :form="formId" :disabled="busy || !title.trim()" :aria-busy="busy">
        {{ t('lessonEditor.activities.save') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import { focusAfterRowAdd, focusAfterRowRemove } from '@/utils/focusRowList'
import type { ActivityPayload, EditorActivity } from '@/types/adminClasses'
import { CLASS_ACTIVITY_TYPES, type ClassActivityType, type KeyPhrase } from '@/types/classes'
import { ACTIVITY_ICONS } from '@/utils/classes'

/**
 * Add or edit one of a lesson's 3 fixed activities with its rows: flashcards' cards, tracing characters or matching
 * pairs. The slot it opens from sets the type (A3.16). The parent mounts it for one opening, so the draft starts from
 * the activity it is given.
 */
const props = defineProps<{
  /** The slot's type: activity 1 is Flashcards, 2 Tracing, 3 Matching. */
  type: ClassActivityType
  /** The slot's activity, or null to add it. */
  activity: EditorActivity | null
  busy: boolean
  error: string | null
}>()
const emit = defineEmits<{ save: [payload: ActivityPayload]; close: [] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages })
const formId = useId()

/**
 * Per-activity row configuration: wording keys, min/max counts, and the server's per-column max lengths. Flashcards
 * may have none: learners then review the lesson's key phrases.
 */
const ROW_SETS = {
  FLASHCARDS: {
    label: 'lessonEditor.activities.cards',
    add: 'lessonEditor.activities.addCard',
    remove: 'lessonEditor.activities.removeCard',
    min: 0,
    max: 20,
    lengths: { chinese: 100, pinyin: 200, meaning: 200 },
  },
  TRACING: {
    label: 'lessonEditor.activities.characters',
    add: 'lessonEditor.activities.addCharacter',
    remove: 'lessonEditor.activities.removeCharacter',
    min: 1,
    max: 8,
    lengths: { chinese: 4, pinyin: 40, meaning: 80 },
  },
  MATCHING: {
    label: 'lessonEditor.activities.pairs',
    add: 'lessonEditor.activities.addPair',
    remove: 'lessonEditor.activities.removePair',
    min: 2,
    max: 12,
    lengths: { chinese: 100, pinyin: 200, meaning: 300 },
  },
} as const

interface DraftRow extends KeyPhrase {
  id: number
}

let nextRowId = 1
const blankRow = (): DraftRow => ({ id: nextRowId++, chinese: '', pinyin: '', meaning: '' })

function savedRows(activity: EditorActivity): DraftRow[] {
  const items = { FLASHCARDS: activity.cards, TRACING: activity.characters, MATCHING: activity.pairs }[activity.type]
  const existing = items.map((item) => ({
    id: nextRowId++,
    chinese: 'character' in item ? item.character : item.chinese,
    pinyin: item.pinyin ?? '',
    meaning: item.meaning ?? '',
  }))
  const min = ROW_SETS[activity.type].min
  while (existing.length < min) existing.push(blankRow())
  return existing
}

const typeName = computed(() => t(`practiceType.${props.type}`))
/** The slot's place: activity 1, 2 or 3. */
const number = computed(() => CLASS_ACTIVITY_TYPES.indexOf(props.type) + 1)
const title = ref(props.activity?.title ?? '')
const rowSet = computed(() => ROW_SETS[props.type])
const columns = computed(() => {
  const { lengths } = rowSet.value
  return [
    {
      key: 'chinese',
      label: t(props.type === 'TRACING' ? 'lessonEditor.fields.character' : 'lessonEditor.fields.chinese'),
      maxlength: lengths.chinese,
    },
    { key: 'pinyin', label: t('lessonEditor.fields.pinyin'), maxlength: lengths.pinyin },
    { key: 'meaning', label: t('lessonEditor.fields.meaning'), maxlength: lengths.meaning },
  ] as const
})
// An activity keeps its rows (an older Flashcards activity has none); a new one starts with the rows to fill.
const rows = ref<DraftRow[]>(
  props.activity ? savedRows(props.activity) : Array.from({ length: Math.max(1, rowSet.value.min) }, blankRow),
)
const addRowButton = ref<HTMLButtonElement | null>(null)
const rowsBox = ref<HTMLDivElement | null>(null)

function addRow(event: MouseEvent): void {
  const wasFocused = event.currentTarget === document.activeElement
  rows.value.push(blankRow())
  focusAfterRowAdd(
    () => rowsBox.value,
    () => addRowButton.value,
    wasFocused,
  )
}

function removeRow(index: number, event: MouseEvent): void {
  const wasFocused = event.currentTarget === document.activeElement
  rows.value.splice(index, 1)
  focusAfterRowRemove(
    () => rowsBox.value,
    () => addRowButton.value,
    index,
    wasFocused,
  )
}

function save(): void {
  if (props.busy || !title.value.trim()) return
  const payload: ActivityPayload = {
    type: props.type,
    title: title.value.trim(),
  }
  // Leave out untouched rows, but submit partial ones so validation can tell the editor what is missing.
  const typed = rows.value
    .map((row) => ({ chinese: row.chinese.trim(), pinyin: row.pinyin.trim(), meaning: row.meaning.trim() }))
    .filter((row) => row.chinese || row.pinyin || row.meaning)
  if (props.type === 'TRACING') {
    payload.characters = typed.map((row) => ({
      character: row.chinese,
      pinyin: row.pinyin,
      meaning: row.meaning || null,
    }))
  }
  if (props.type === 'MATCHING') payload.pairs = typed
  if (props.type === 'FLASHCARDS') payload.cards = typed
  emit('save', payload)
}
</script>
