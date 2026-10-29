<template>
  <section class="teacher-outline" aria-labelledby="teacher-outline-title">
    <div class="section-header">
      <div>
        <h2 id="teacher-outline-title" class="section-title">{{ t('teacherClasses.units.title') }}</h2>
        <p class="section-description">{{ t('teacherClasses.units.hint') }}</p>
      </div>
      <div v-if="editable" class="section-header-actions">
        <button type="button" class="btn btn-secondary btn-sm" data-testid="add-unit" @click="openUnitDialog(null)">
          <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.units.addUnit') }}
        </button>
      </div>
    </div>

    <p v-if="!units.length" class="panel type-caption">{{ t('teacherClasses.units.noUnits') }}</p>

    <ol v-else class="panel teacher-outline-units">
      <li v-for="(unit, unitIndex) in units" :key="unit.id" class="teacher-unit" :data-testid="`unit-${unit.id}`">
        <div class="teacher-unit-head">
          <span class="teacher-unit-number" aria-hidden="true">{{ String(unitIndex + 1).padStart(2, '0') }}</span>
          <div class="teacher-unit-copy">
            <h3 class="teacher-unit-title">
              <span class="sr-only">{{ t('teacherClasses.units.unitNumber', { number: unitIndex + 1 }) }}</span>
              {{ unit.title }}
            </h3>
            <span class="type-caption">{{ t('teacherClasses.units.lessons', unit.lessons.length) }}</span>
          </div>
          <div v-if="editable" class="teacher-unit-tools">
            <button
              type="button"
              class="btn btn-ghost btn-sm"
              :aria-label="t('teacherClasses.units.renameLabel', { title: unit.title })"
              @click="openUnitDialog(unit)"
            >
              <IconGlyph name="edit" :size="16" />{{ t('teacherClasses.units.rename') }}
            </button>
            <button
              type="button"
              class="btn btn-ghost btn-sm"
              :disabled="unit.lessons.length > 0"
              :title="unit.lessons.length > 0 ? t('teacherClasses.units.archiveUnitHint') : undefined"
              :aria-label="t('teacherClasses.units.archiveUnitLabel', { title: unit.title })"
              @click="archiveTarget = unit"
            >
              <IconGlyph name="archive" :size="16" />{{ t('teacherClasses.units.archiveUnit') }}
            </button>
            <span class="teacher-move">
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :disabled="busy || unitIndex === 0"
                :aria-label="t('teacherClasses.units.moveUp', { title: unit.title })"
                @click="moveUnit(unitIndex, -1)"
              >
                <IconGlyph name="arrowUp" :size="16" />
              </button>
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :disabled="busy || unitIndex === units.length - 1"
                :aria-label="t('teacherClasses.units.moveDown', { title: unit.title })"
                @click="moveUnit(unitIndex, 1)"
              >
                <IconGlyph name="arrowDown" :size="16" />
              </button>
            </span>
          </div>
        </div>

        <p v-if="!unit.lessons.length" class="type-caption teacher-unit-empty">
          {{ t('teacherClasses.units.noLessons') }}
        </p>
        <ol v-else class="teacher-lessons">
          <li
            v-for="(lesson, lessonIndex) in unit.lessons"
            :key="lesson.id"
            class="teacher-lesson"
            :data-testid="`lesson-row-${lesson.id}`"
          >
            <span class="teacher-lesson-number" aria-hidden="true">{{ unitIndex + 1 }}.{{ lessonIndex + 1 }}</span>
            <div class="teacher-lesson-copy">
              <strong>{{ lesson.title }}</strong>
              <span class="type-caption">
                {{
                  t('teacherClasses.units.lessonFacts', {
                    activities: t('classes.activities', lesson.activityCount),
                    questions: t('classes.questions', lesson.questionCount),
                    date: formatShortDate(lesson.updatedAt),
                  })
                }}
              </span>
              <span v-if="lesson.reviewNote" class="teacher-lesson-note">
                <IconGlyph name="alert" :size="14" />
                <span>{{ lesson.reviewNoteBy ? `${lesson.reviewNoteBy}: ` : '' }}{{ lesson.reviewNote }}</span>
              </span>
            </div>
            <span class="teacher-lesson-status">
              <ClassStatusPill :status="lesson.status" :revision-status="lesson.revisionStatus" />
            </span>
            <span class="teacher-lesson-tools">
              <RouterLink
                class="btn btn-secondary btn-sm teacher-lesson-open"
                :to="{ name: 'teacher-lesson', params: { classId, lessonId: lesson.id } }"
                :aria-label="
                  isLocked(lesson)
                    ? t('teacherClasses.units.openLabel', { title: lesson.title })
                    : t('teacherClasses.units.editLabel', { title: lesson.title })
                "
              >
                <IconGlyph :name="isLocked(lesson) ? 'eye' : 'edit'" :size="16" />
                {{ isLocked(lesson) ? t('teacherClasses.list.open') : t('teacherClasses.units.edit') }}
              </RouterLink>
              <span v-if="editable" class="teacher-move">
                <button
                  type="button"
                  class="btn btn-ghost btn-icon btn-sm"
                  :disabled="busy || (unitIndex === 0 && lessonIndex === 0)"
                  :aria-label="t('teacherClasses.units.moveUp', { title: lesson.title })"
                  @click="moveLesson(unitIndex, lessonIndex, -1)"
                >
                  <IconGlyph name="arrowUp" :size="16" />
                </button>
                <button
                  type="button"
                  class="btn btn-ghost btn-icon btn-sm"
                  :disabled="busy || (unitIndex === units.length - 1 && lessonIndex === unit.lessons.length - 1)"
                  :aria-label="t('teacherClasses.units.moveDown', { title: lesson.title })"
                  @click="moveLesson(unitIndex, lessonIndex, 1)"
                >
                  <IconGlyph name="arrowDown" :size="16" />
                </button>
              </span>
            </span>
          </li>
        </ol>
        <button
          v-if="editable"
          type="button"
          class="btn btn-link teacher-unit-add"
          :data-testid="`add-lesson-${unit.id}`"
          @click="openLessonDialog(unit)"
        >
          <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.units.addLesson') }}
        </button>
      </li>
    </ol>

    <ConfirmDialog
      :open="textDialog !== null"
      :title="textDialogTitle"
      :confirm-label="textDialog?.kind === 'rename' ? t('teacherClasses.units.save') : t('teacherClasses.units.add')"
      :busy="busy"
      :confirm-disabled="!textValue.trim()"
      :error="dialogError"
      test-id="units-text-dialog"
      @cancel="textDialog = null"
      @confirm="confirmText"
    >
      <div class="field">
        <label class="field-label" :for="`${baseId}-text`">
          {{
            textDialog?.kind === 'lesson' ? t('teacherClasses.units.lessonTitle') : t('teacherClasses.units.unitTitle')
          }}
        </label>
        <input
          :id="`${baseId}-text`"
          v-model="textValue"
          class="input"
          maxlength="200"
          :placeholder="
            textDialog?.kind === 'lesson'
              ? t('teacherClasses.units.lessonPlaceholder')
              : t('teacherClasses.units.unitPlaceholder')
          "
          data-testid="units-text-input"
          @keydown.enter.prevent="confirmText"
        />
      </div>
    </ConfirmDialog>

    <ConfirmDialog
      :open="archiveTarget !== null"
      :title="t('teacherClasses.units.archiveUnitTitle')"
      :message="archiveTarget ? t('teacherClasses.units.archiveUnitMessage', { title: archiveTarget.title }) : ''"
      :confirm-label="t('teacherClasses.units.archiveUnit')"
      tone="danger"
      :busy="busy"
      @cancel="archiveTarget = null"
      @confirm="archiveUnit"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { teacherClassesApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { TeacherClassPage, TeacherLessonRow, TeacherUnit } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'

import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ClassStatusPill from './ClassStatusPill.vue'

/**
 * The Lessons tab of a class: units with their lessons in order, statuses with the admin's notes, and adding,
 * renaming, archiving and reordering (live at once, no review).
 */
const props = defineProps<{ classId: number; units: readonly TeacherUnit[]; editable: boolean }>()
const emit = defineEmits<{ updated: [page: TeacherClassPage]; notice: [message: string]; error: [message: string] }>()

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, classes: classesMessages })
const router = useRouter()
const baseId = useId()

const busy = ref(false)
const textDialog = ref<{ kind: 'unit' | 'rename' | 'lesson'; unit: TeacherUnit | null } | null>(null)
const textValue = ref('')
const dialogError = ref<string | null>(null)
const archiveTarget = ref<TeacherUnit | null>(null)

const textDialogTitle = computed(() => {
  if (!textDialog.value) return ''
  if (textDialog.value.kind === 'lesson') {
    return t('teacherClasses.units.addLessonTitle', { unit: textDialog.value.unit?.title ?? '' })
  }
  return textDialog.value.kind === 'rename'
    ? t('teacherClasses.units.renameUnitTitle')
    : t('teacherClasses.units.addUnitTitle')
})

function isLocked(lesson: TeacherLessonRow): boolean {
  return lesson.status === 'SUBMITTED' || lesson.revisionStatus === 'SUBMITTED'
}

function openUnitDialog(unit: TeacherUnit | null): void {
  textDialog.value = { kind: unit ? 'rename' : 'unit', unit }
  textValue.value = unit?.title ?? ''
  dialogError.value = null
}

function openLessonDialog(unit: TeacherUnit): void {
  textDialog.value = { kind: 'lesson', unit }
  textValue.value = ''
  dialogError.value = null
}

async function confirmText(): Promise<void> {
  const dialog = textDialog.value
  const title = textValue.value.trim()
  if (!dialog || !title || busy.value) return
  busy.value = true
  dialogError.value = null
  try {
    if (dialog.kind === 'lesson' && dialog.unit) {
      const editor = await teacherClassesApi.addLesson(dialog.unit.id, title)
      textDialog.value = null
      await router.push({ name: 'teacher-lesson', params: { classId: props.classId, lessonId: editor.lessonId } })
      return
    }
    const page =
      dialog.kind === 'rename' && dialog.unit
        ? await teacherClassesApi.renameUnit(dialog.unit.id, title)
        : await teacherClassesApi.addUnit(props.classId, title)
    emit('updated', page)
    emit(
      'notice',
      dialog.kind === 'rename' ? t('teacherClasses.units.unitRenamed') : t('teacherClasses.units.unitAdded', { title }),
    )
    textDialog.value = null
  } catch (reason) {
    dialogError.value = resolveApiError(
      reason,
      dialog.kind === 'lesson' ? t('teacherClasses.units.lessonError') : t('teacherClasses.units.unitError'),
    )
  } finally {
    busy.value = false
  }
}

async function archiveUnit(): Promise<void> {
  const unit = archiveTarget.value
  if (!unit) return
  busy.value = true
  try {
    emit('updated', await teacherClassesApi.archiveUnit(unit.id))
    emit('notice', t('teacherClasses.units.unitArchived', { title: unit.title }))
  } catch (reason) {
    emit('error', resolveApiError(reason, t('teacherClasses.units.unitError')))
  } finally {
    busy.value = false
    archiveTarget.value = null
  }
}

function structureOf(units: readonly TeacherUnit[]) {
  return units.map((unit) => ({ unitId: unit.id, lessonIds: unit.lessons.map((lesson) => lesson.id) }))
}

async function saveOrder(structure: { unitId: number; lessonIds: number[] }[]): Promise<void> {
  busy.value = true
  try {
    emit('updated', await teacherClassesApi.reorder(props.classId, structure))
    emit('notice', t('teacherClasses.units.reordered'))
  } catch (reason) {
    emit('error', resolveApiError(reason, t('teacherClasses.units.reorderError')))
  } finally {
    busy.value = false
  }
}

function moveUnit(index: number, step: -1 | 1): void {
  const structure = structureOf(props.units)
  const [moved] = structure.splice(index, 1)
  structure.splice(index + step, 0, moved!)
  void saveOrder(structure)
}

/** Moves a lesson one place; at the edge of its unit it moves into the next or previous unit. */
function moveLesson(unitIndex: number, lessonIndex: number, step: -1 | 1): void {
  const structure = structureOf(props.units)
  const lessons = structure[unitIndex]!.lessonIds
  const [lessonId] = lessons.splice(lessonIndex, 1)
  const target = lessonIndex + step
  if (target >= 0 && target <= lessons.length) {
    lessons.splice(target, 0, lessonId!)
  } else if (step < 0) {
    structure[unitIndex - 1]!.lessonIds.push(lessonId!)
  } else {
    structure[unitIndex + 1]!.lessonIds.unshift(lessonId!)
  }
  void saveOrder(structure)
}
</script>
