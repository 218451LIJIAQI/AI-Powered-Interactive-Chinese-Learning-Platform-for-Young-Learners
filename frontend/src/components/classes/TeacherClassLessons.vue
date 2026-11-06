<template>
  <section class="panel teacher-card" aria-labelledby="teacher-outline-title">
    <div class="section-header">
      <h2 id="teacher-outline-title" class="card-title">{{ t('teacherClasses.units.title') }}</h2>
      <button
        v-if="editable && units.length"
        type="button"
        class="btn btn-secondary btn-sm"
        data-testid="add-unit"
        @click="openUnitDialog(null)"
      >
        <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.units.addUnit') }}
      </button>
    </div>

    <StatePanel v-if="!units.length" tone="empty" icon="layers" :title="t('teacherClasses.units.firstUnit')" message="">
      <template v-if="editable" #actions>
        <button type="button" class="btn btn-secondary btn-sm" data-testid="add-unit" @click="openUnitDialog(null)">
          <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.units.addUnit') }}
        </button>
      </template>
    </StatePanel>

    <ol v-else class="teacher-units">
      <li v-for="(unit, unitIndex) in units" :key="unit.id" class="teacher-unit" :data-testid="`unit-${unit.id}`">
        <div class="teacher-unit-head">
          <span class="teacher-tile" aria-hidden="true">{{ unitIndex + 1 }}</span>
          <div class="row-list-copy">
            <h3 class="row-list-title">
              <span class="sr-only">{{ t('teacherClasses.units.unitNumber', { number: unitIndex + 1 }) }}</span>
              {{ unit.title }}
            </h3>
            <span class="row-list-meta">{{ t('teacherClasses.units.lessons', unit.lessons.length) }}</span>
          </div>
          <button
            v-if="editable"
            type="button"
            class="btn btn-ghost btn-sm"
            :aria-label="t('teacherClasses.units.renameLabel', { title: unit.title })"
            @click="openUnitDialog(unit)"
          >
            <IconGlyph name="edit" :size="16" />{{ t('teacherClasses.units.rename') }}
          </button>
        </div>

        <div class="teacher-unit-body">
          <ul v-if="unit.lessons.length" class="row-list">
            <li v-for="lesson in unit.lessons" :key="lesson.id" :data-testid="`lesson-row-${lesson.id}`">
              <div class="row-list-copy">
                <span class="row-list-title">{{ lesson.title }}</span>
                <span class="row-list-meta">
                  {{ t('teacherClasses.units.activities', lesson.activityCount) }} ·
                  {{ t('teacherClasses.units.questions', lesson.questionCount) }}
                </span>
                <span v-if="lesson.reviewNote" class="row-list-meta">
                  {{
                    lesson.reviewNoteBy
                      ? t('teacherClasses.units.note', { name: lesson.reviewNoteBy, note: lesson.reviewNote })
                      : lesson.reviewNote
                  }}
                </span>
              </div>
              <div class="row-list-actions">
                <TeacherStatusPill :status="lesson.status" :revision-status="lesson.revisionStatus" />
                <RouterLink
                  v-if="isReadOnly(lesson)"
                  class="btn btn-secondary btn-sm"
                  :to="{ name: 'teacher-lesson', params: { classId, lessonId: lesson.id } }"
                  :aria-label="t('teacherClasses.units.openLabel', { title: lesson.title })"
                >
                  {{ t('teacherClasses.list.open') }}<IconGlyph name="chevronRight" :size="16" />
                </RouterLink>
                <RouterLink
                  v-else
                  class="btn btn-secondary btn-sm"
                  :to="{ name: 'teacher-lesson', params: { classId, lessonId: lesson.id } }"
                  :aria-label="t('teacherClasses.units.editLabel', { title: lesson.title })"
                >
                  <IconGlyph name="edit" :size="16" />{{ t('teacherClasses.units.edit') }}
                </RouterLink>
              </div>
            </li>
          </ul>
          <div v-if="editable" class="teacher-unit-add">
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :aria-label="t('teacherClasses.units.addLessonLabel', { title: unit.title })"
              :data-testid="`add-lesson-${unit.id}`"
              @click="openLessonDialog(unit)"
            >
              <IconGlyph name="plus" :size="16" />{{ t('teacherClasses.units.addLesson') }}
            </button>
          </div>
        </div>
      </li>
    </ol>

    <AppOverlay
      :open="textDialog !== null"
      :title="textDialogTitle"
      :busy="busy"
      test-id="units-text-dialog"
      @close="textDialog = null"
    >
      <form :id="baseId" class="form-grid" @submit.prevent="confirmText">
        <div class="field">
          <label class="field-label" :for="`${baseId}-text`">
            {{
              textDialog?.kind === 'lesson'
                ? t('teacherClasses.units.lessonTitle')
                : t('teacherClasses.units.unitTitle')
            }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${baseId}-text`"
            v-model="textValue"
            class="input"
            required
            maxlength="200"
            :placeholder="
              textDialog?.kind === 'lesson'
                ? t('teacherClasses.units.lessonPlaceholder')
                : t('teacherClasses.units.unitPlaceholder')
            "
            data-autofocus
            data-testid="units-text-input"
          />
        </div>
        <p v-if="dialogError" class="field-error" role="alert">{{ dialogError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn btn-secondary" :disabled="busy" @click="textDialog = null">
          {{ t('common.cancel') }}
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :form="baseId"
          :disabled="busy || !textValue.trim()"
          :aria-busy="busy"
          data-testid="units-text-dialog-confirm"
        >
          <template v-if="textDialog?.kind === 'rename'">{{ t('teacherClasses.units.save') }}</template>
          <template v-else>
            <IconGlyph name="plus" :size="16" />
            {{
              textDialog?.kind === 'lesson' ? t('teacherClasses.units.addLesson') : t('teacherClasses.units.addUnit')
            }}
          </template>
        </button>
      </template>
    </AppOverlay>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { teacherClassesApi } from '@/api/classes'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import TeacherStatusPill from '@/components/teacher/TeacherStatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { TeacherClassPage, TeacherLessonRow, TeacherUnit } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'

/** The Lessons tab of a class: numbered units with their lessons, statuses with the admin's notes, adding and renaming. */
const props = defineProps<{ classId: number; units: readonly TeacherUnit[]; editable: boolean }>()
const emit = defineEmits<{ updated: [page: TeacherClassPage]; notice: [message: string] }>()

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, common: commonMessages })
const router = useRouter()
const baseId = useId()

const busy = ref(false)
const textDialog = ref<{ kind: 'unit' | 'rename' | 'lesson'; unit: TeacherUnit | null } | null>(null)
const textValue = ref('')
const dialogError = ref<string | null>(null)

const textDialogTitle = computed(() => {
  switch (textDialog.value?.kind) {
    case 'lesson':
      return t('teacherClasses.units.addLessonTitle')
    case 'rename':
      return t('teacherClasses.units.renameUnitTitle')
    default:
      return t('teacherClasses.units.addUnitTitle')
  }
})

/** A lesson (or its update) in review cannot be edited, and an archived class's lessons only open. */
function isReadOnly(lesson: TeacherLessonRow): boolean {
  return !props.editable || lesson.status === 'SUBMITTED' || lesson.revisionStatus === 'SUBMITTED'
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
      await router.push({
        name: 'teacher-lesson',
        params: { classId: props.classId, lessonId: editor.lessonId },
        query: { created: '1' },
      })
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
</script>
