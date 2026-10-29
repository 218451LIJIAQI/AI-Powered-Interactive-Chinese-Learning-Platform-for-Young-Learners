<template>
  <section class="panel lesson-editor-sheet" aria-labelledby="lesson-activities-title">
    <div class="lesson-editor-section">
      <div class="lesson-editor-section-head">
        <div>
          <h2 id="lesson-activities-title" class="card-title">{{ t('lessonEditor.activities.title') }}</h2>
          <p class="type-caption">{{ t('lessonEditor.activities.subtitle') }}</p>
        </div>
        <button
          v-if="editor.editable"
          type="button"
          class="btn btn-secondary btn-sm"
          data-testid="add-activity"
          @click="openDrawer(null)"
        >
          <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.activities.add') }}
        </button>
      </div>
      <p v-if="!editor.activities.length" class="lesson-editor-empty">{{ t('lessonEditor.activities.empty') }}</p>
      <ol v-else class="lesson-editor-list">
        <li
          v-for="(activity, index) in editor.activities"
          :key="`${activity.type}-${activity.id}`"
          class="lesson-editor-item"
        >
          <span class="lesson-editor-step" aria-hidden="true">{{ index + 1 }}</span>
          <div class="lesson-editor-item-copy">
            <strong><CjkText :text="activity.title" /></strong>
            <span class="type-caption lesson-editor-item-type">
              <IconGlyph :name="ACTIVITY_ICONS[activity.type]" :size="14" />
              {{
                t('lessonEditor.activities.facts', { number: index + 1, type: t(ACTIVITY_TYPE_KEYS[activity.type]) })
              }}
            </span>
          </div>
          <span v-if="activity.stage" class="tag">{{ t(STAGE_KEYS[activity.stage]) }}</span>
          <div v-if="editor.editable" class="lesson-editor-item-actions">
            <button
              type="button"
              class="btn btn-ghost btn-sm"
              :aria-label="t('lessonEditor.activities.editLabel', { title: activity.title })"
              @click="openDrawer(activity)"
            >
              <IconGlyph name="edit" :size="16" />{{ t('lessonEditor.activities.edit') }}
            </button>
            <button
              type="button"
              class="btn btn-ghost btn-icon btn-sm"
              :disabled="busy"
              :aria-label="t('lessonEditor.activities.duplicateLabel', { title: activity.title })"
              :title="t('lessonEditor.activities.duplicate')"
              @click="duplicate(activity)"
            >
              <IconGlyph name="copy" :size="16" />
            </button>
            <button
              type="button"
              class="btn btn-ghost btn-icon btn-sm"
              :aria-label="t('lessonEditor.activities.deleteLabel', { title: activity.title })"
              :title="t('lessonEditor.activities.delete')"
              @click="deleteTarget = activity"
            >
              <IconGlyph name="trash" :size="16" />
            </button>
            <span class="teacher-move">
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :disabled="busy || index === 0"
                :aria-label="t('lessonEditor.activities.moveUp', { title: activity.title })"
                @click="move(index, -1)"
              >
                <IconGlyph name="arrowUp" :size="16" />
              </button>
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :disabled="busy || index === editor.activities.length - 1"
                :aria-label="t('lessonEditor.activities.moveDown', { title: activity.title })"
                @click="move(index, 1)"
              >
                <IconGlyph name="arrowDown" :size="16" />
              </button>
            </span>
          </div>
        </li>
      </ol>
      <p class="type-caption lesson-editor-foot-note">
        <IconGlyph name="info" :size="14" /> {{ t('classes.stageHint') }}
      </p>
    </div>

    <LessonActivityDrawer
      :open="drawerOpen"
      :activity="editing"
      :busy="busy"
      :error="drawerError"
      @close="drawerOpen = false"
      @save="save"
    />
    <ConfirmDialog
      :open="deleteTarget !== null"
      :title="t('lessonEditor.activities.deleteTitle')"
      :message="deleteTarget ? t('lessonEditor.activities.deleteMessage', { title: deleteTarget.title }) : ''"
      :confirm-label="t('lessonEditor.activities.delete')"
      tone="danger"
      :busy="busy"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { lessonEditorApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { ActivityPayload, EditorActivity, LessonEditor } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { ACTIVITY_ICONS, ACTIVITY_TYPE_KEYS, STAGE_KEYS } from '@/utils/classes'

import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import LessonActivityDrawer from './LessonActivityDrawer.vue'

/** The Activities tab of the lesson editor; every change is saved at once to the lesson's working copy. */
const props = defineProps<{ editor: LessonEditor }>()
const emit = defineEmits<{ updated: [editor: LessonEditor]; notice: [message: string]; error: [message: string] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })

const busy = ref(false)
const drawerOpen = ref(false)
const editing = ref<EditorActivity | null>(null)
const drawerError = ref<string | null>(null)
const deleteTarget = ref<EditorActivity | null>(null)

function openDrawer(activity: EditorActivity | null): void {
  editing.value = activity
  drawerError.value = null
  drawerOpen.value = true
}

async function save(payload: ActivityPayload): Promise<void> {
  busy.value = true
  drawerError.value = null
  try {
    const next = editing.value
      ? await lessonEditorApi.updateActivity(props.editor.lessonId, editing.value.type, editing.value.id, payload)
      : await lessonEditorApi.addActivity(props.editor.lessonId, payload)
    emit('updated', next)
    emit('notice', t('lessonEditor.activities.saved'))
    drawerOpen.value = false
  } catch (reason) {
    drawerError.value = resolveApiError(reason, t('lessonEditor.activities.error'))
  } finally {
    busy.value = false
  }
}

async function act(action: () => Promise<LessonEditor>, notice: string): Promise<void> {
  busy.value = true
  try {
    emit('updated', await action())
    emit('notice', notice)
  } catch (reason) {
    emit('error', resolveApiError(reason, t('lessonEditor.activities.error')))
  } finally {
    busy.value = false
  }
}

function duplicate(activity: EditorActivity): void {
  void act(
    () => lessonEditorApi.duplicateActivity(props.editor.lessonId, activity.type, activity.id),
    t('lessonEditor.activities.duplicated'),
  )
}

async function remove(): Promise<void> {
  const target = deleteTarget.value
  if (!target) return
  await act(
    () => lessonEditorApi.deleteActivity(props.editor.lessonId, target.type, target.id),
    t('lessonEditor.activities.deleted'),
  )
  deleteTarget.value = null
}

function move(index: number, step: -1 | 1): void {
  const order = props.editor.activities.map((activity) => ({ type: activity.type, id: activity.id }))
  const [moved] = order.splice(index, 1)
  order.splice(index + step, 0, moved!)
  void act(() => lessonEditorApi.reorderActivities(props.editor.lessonId, order), t('lessonEditor.saved'))
}
</script>
