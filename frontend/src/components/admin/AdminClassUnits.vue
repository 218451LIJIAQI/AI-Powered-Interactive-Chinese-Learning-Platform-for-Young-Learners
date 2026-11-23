<template>
  <section class="panel authoring-card" aria-labelledby="admin-class-units-title">
    <h2 id="admin-class-units-title" class="sr-only">{{ t('adminClasses.units.label') }}</h2>
    <div v-if="editable && units.length" class="class-units-toolbar">
      <!-- A class has its 2 units: then Add unit waits, and the note beside it says why. -->
      <div class="btn-group is-sm">
        <span v-if="unitsFull" :id="`${baseId}-full`" class="type-caption" data-testid="units-full">
          {{ t('adminClasses.units.unitsFull') }}
        </span>
        <button
          type="button"
          class="btn btn-secondary btn-sm"
          :disabled="unitsFull"
          :aria-describedby="unitsFull ? `${baseId}-full` : undefined"
          data-testid="add-unit"
          @click="openDialog({ kind: 'unit' })"
        >
          <IconGlyph name="plus" :size="16" />{{ t('adminClasses.units.addUnit') }}
        </button>
      </div>
    </div>

    <!-- Nothing yet: adding the first unit is the main task here, so it is the primary button. -->
    <StatePanel
      v-if="!units.length"
      tone="empty"
      icon="layers"
      :title="t('adminClasses.units.firstUnit')"
      :message="t('adminClasses.units.firstUnitHint')"
    >
      <template v-if="editable" #actions>
        <button
          type="button"
          class="btn btn-primary"
          data-testid="add-first-unit"
          @click="openDialog({ kind: 'unit' })"
        >
          <IconGlyph name="plus" :size="16" />{{ t('adminClasses.units.addUnit') }}
        </button>
      </template>
    </StatePanel>

    <ol v-else class="class-units">
      <li v-for="(unit, unitIndex) in units" :key="unit.id" class="class-unit" data-testid="class-unit">
        <div class="class-unit-head">
          <span class="authoring-tile" aria-hidden="true">{{ unitIndex + 1 }}</span>
          <div class="row-list-copy">
            <h3 class="row-list-title">{{ unit.title }}</h3>
            <span v-if="!unit.lessons.length" class="row-list-meta">{{ t('adminClasses.units.noLessons') }}</span>
          </div>
          <button
            v-if="editable"
            type="button"
            class="btn btn-ghost btn-sm"
            :aria-label="t('adminClasses.units.renameLabel', { title: unit.title })"
            @click="openDialog({ kind: 'rename', unit })"
          >
            <IconGlyph name="edit" :size="16" />{{ t('adminClasses.units.rename') }}
          </button>
        </div>

        <div class="class-unit-body">
          <ul v-if="unit.lessons.length" class="row-list">
            <li v-for="lesson in unit.lessons" :key="lesson.id" data-testid="class-lesson">
              <div class="row-list-copy">
                <span class="row-list-title class-lesson-title" :title="lesson.title">{{ lesson.title }}</span>
                <span v-if="lesson.hasUnpublishedChanges" class="row-list-meta">
                  {{ t('adminClasses.units.unpublishedChanges') }}
                </span>
              </div>
              <div class="row-list-actions">
                <StatusPill
                  :label="t(`adminClasses.statuses.${lesson.status}`)"
                  :tone="contentStatusTone(lesson.status)"
                />
                <RouterLink
                  v-if="editable"
                  class="btn btn-secondary btn-sm"
                  :to="{ name: 'admin-lesson', params: { classId, lessonId: lesson.id } }"
                  :aria-label="t('adminClasses.units.editLabel', { title: lesson.title })"
                >
                  <IconGlyph name="edit" :size="16" />{{ t('common.edit') }}
                </RouterLink>
              </div>
            </li>
          </ul>
          <div v-if="editable" class="class-unit-add">
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :aria-label="t('adminClasses.units.addLessonLabel', { title: unit.title })"
              @click="openDialog({ kind: 'lesson', unit })"
            >
              <IconGlyph name="plus" :size="16" />{{ t('adminClasses.units.addLesson') }}
            </button>
          </div>
        </div>
      </li>
    </ol>

    <AppOverlay
      :open="textDialog !== null"
      :title="t(dialogKeys.title)"
      :busy="busy"
      test-id="unit-dialog"
      @close="textDialog = null"
    >
      <form :id="baseId" class="form-grid" novalidate @submit.prevent="confirmText">
        <div class="field">
          <label class="field-label" :for="`${baseId}-text`">
            {{ t(dialogKeys.label) }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${baseId}-text`"
            ref="textField"
            v-model="textValue"
            class="input"
            required
            maxlength="200"
            :disabled="busy"
            :placeholder="t(dialogKeys.placeholder)"
            :aria-invalid="Boolean(textError)"
            :aria-describedby="textError ? `${baseId}-error` : undefined"
            data-autofocus
            @blur="checkTextOnBlur"
          />
          <p v-if="textError" :id="`${baseId}-error`" class="field-error" role="alert">{{ textError }}</p>
        </div>
        <p v-if="dialogError" class="field-error" role="alert">{{ dialogError }}</p>
      </form>
      <template #footer>
        <!-- A press on a footer button keeps the focus in the field: its check on blur would move the footer mid-click. -->
        <button type="button" class="btn btn-secondary" :disabled="busy" @mousedown.prevent @click="textDialog = null">
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :form="baseId"
          :disabled="busy || Boolean(textError)"
          :aria-busy="busy"
          data-testid="unit-dialog-confirm"
          @mousedown.prevent
        >
          {{ t(dialogKeys.confirm) }}
        </button>
      </template>
    </AppOverlay>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { adminClassesApi } from '@/api/adminClasses'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import type { AdminClassPage, AdminUnit } from '@/types/adminClasses'
import { resolveApiError } from '@/utils/apiError'

import { contentStatusTone } from './adminDisplay'

/**
 * The Lessons tab of a class page: numbered units with their lessons and statuses; adding and renaming units and
 * adding lessons (X2.08, X2.11–X2.13). A class has 2 units, so Add unit stops there. An archived class shows them
 * read-only.
 */
const props = defineProps<{ classId: number; units: readonly AdminUnit[]; editable: boolean }>()
const emit = defineEmits<{
  updated: [page: AdminClassPage]
  lessonCreated: [lessonId: number]
  notice: [message: string]
}>()

/** The units of a class (Library · UI rules, Classes); the server refuses a third. */
const MAX_UNITS = 2

const { t } = useLocalMessages({ adminClasses: adminClassesMessages })
const baseId = useId()

const unitsFull = computed(() => props.units.length >= MAX_UNITS)

type TextDialog = { kind: 'unit' } | { kind: 'rename' | 'lesson'; unit: AdminUnit }

/** Adding and renaming a unit ask for the same field: the unit's title. */
const UNIT_FIELD_KEYS = {
  label: 'adminClasses.units.unitTitle',
  placeholder: 'adminClasses.units.unitPlaceholder',
  missing: 'adminClasses.units.unitMissing',
  error: 'adminClasses.units.unitError',
}
/** The words of each dialog: its title and button, and its field's label, example, missing text and failure. */
const DIALOG_KEYS = {
  unit: { title: 'adminClasses.units.addUnitTitle', confirm: 'adminClasses.units.addUnit', ...UNIT_FIELD_KEYS },
  rename: { title: 'adminClasses.units.renameUnitTitle', confirm: 'adminClasses.units.save', ...UNIT_FIELD_KEYS },
  lesson: {
    title: 'adminClasses.units.addLessonTitle',
    confirm: 'adminClasses.units.addLesson',
    label: 'adminClasses.units.lessonTitle',
    placeholder: 'adminClasses.units.lessonPlaceholder',
    missing: 'adminClasses.units.lessonMissing',
    error: 'adminClasses.units.lessonError',
  },
}

const busy = ref(false)
const textDialog = ref<TextDialog | null>(null)
const textValue = ref('')
const textField = ref<HTMLInputElement | null>(null)
const dialogError = ref<string | null>(null)
const isUnmounted = useIsUnmounted()

const dialogKeys = computed(() => DIALOG_KEYS[textDialog.value?.kind ?? 'unit'])

const {
  error: textError,
  validate: validateText,
  validateOnBlur: checkTextOnBlur,
  reset: resetTextError,
} = useFieldCheck(textValue, (value) => (value.trim() ? null : t(dialogKeys.value.missing)))

watch(textValue, () => {
  dialogError.value = null
})

function openDialog(dialog: TextDialog): void {
  textDialog.value = dialog
  textValue.value = dialog.kind === 'rename' ? dialog.unit.title : ''
  resetTextError()
  dialogError.value = null
}

async function confirmText(): Promise<void> {
  const dialog = textDialog.value
  if (!dialog || busy.value) return
  if (!validateText()) {
    textField.value?.focus()
    return
  }
  const title = textValue.value.trim()
  busy.value = true
  dialogError.value = null
  try {
    if (dialog.kind === 'lesson') {
      const editor = await adminClassesApi.addLesson(dialog.unit.id, title)
      if (isUnmounted()) return
      textDialog.value = null
      emit('lessonCreated', editor.lessonId)
      return
    }
    const page =
      dialog.kind === 'rename'
        ? await adminClassesApi.renameUnit(dialog.unit.id, title)
        : await adminClassesApi.addUnit(props.classId, title)
    if (isUnmounted()) return
    emit('updated', page)
    emit('notice', dialog.kind === 'rename' ? t('adminClasses.units.unitRenamed') : t('adminClasses.units.unitAdded'))
    textDialog.value = null
  } catch (reason) {
    if (isUnmounted()) return
    dialogError.value = resolveApiError(reason, t(DIALOG_KEYS[dialog.kind].error))
  } finally {
    if (!isUnmounted()) busy.value = false
  }
}
</script>
