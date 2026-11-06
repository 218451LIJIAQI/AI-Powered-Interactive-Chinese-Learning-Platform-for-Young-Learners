<template>
  <section class="panel teacher-card" aria-labelledby="lesson-activities-title">
    <div class="teacher-card-head">
      <h2 id="lesson-activities-title" class="card-title">{{ t('lessonEditor.activities.title') }}</h2>
      <p class="type-caption">{{ t('lessonEditor.activities.subtitle') }}</p>
    </div>
    <ol class="row-list">
      <li v-for="(row, index) in rows" :key="row.type">
        <div class="lesson-activity-lead">
          <span class="teacher-tile" aria-hidden="true">{{ index + 1 }}</span>
          <span class="teacher-tile" aria-hidden="true"><IconGlyph :name="ACTIVITY_ICONS[row.type]" :size="16" /></span>
          <div class="row-list-copy">
            <strong class="row-list-title"
              ><CjkText :text="row.activity?.title ?? t(LESSON_ACTIVITY_KEYS[row.type])"
            /></strong>
            <span class="row-list-meta">
              {{ row.activity ? t(LESSON_ACTIVITY_KEYS[row.type]) : t('lessonEditor.activities.notSet') }}
            </span>
          </div>
        </div>
        <div v-if="editor.editable" class="row-list-actions">
          <button
            type="button"
            class="btn btn-secondary btn-sm"
            :aria-label="
              t('lessonEditor.activities.editLabel', {
                title: row.activity?.title ?? t(LESSON_ACTIVITY_KEYS[row.type]),
              })
            "
            @click="openDrawer(row.type, index + 1)"
          >
            <IconGlyph name="edit" :size="16" />{{ t('lessonEditor.activities.edit') }}
          </button>
        </div>
      </li>
    </ol>

    <LessonActivityDrawer
      :open="drawerOpen"
      :type="editingType"
      :position="editingPosition"
      :activity="editing"
      :lesson-title="editor.title"
      :busy="busy"
      :error="drawerError"
      @close="drawerOpen = false"
      @save="save"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { lessonEditorApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { ActivityPayload, ClassActivityType, EditorActivity, LessonEditor } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { ACTIVITY_ICONS } from '@/utils/classes'
import { LESSON_ACTIVITY_KEYS, LESSON_ACTIVITY_TYPES } from '@/utils/teacherLesson'

import LessonActivityDrawer from './LessonActivityDrawer.vue'

/**
 * The Activities tab of the lesson editor: every lesson has exactly these three activities, in this order. A row whose
 * activity does not exist yet (a new lesson) is created by its Edit; every change is saved at once to the working copy.
 */
const props = defineProps<{ editor: LessonEditor }>()
const emit = defineEmits<{ updated: [editor: LessonEditor]; notice: [message: string] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages })

const rows = computed(() =>
  LESSON_ACTIVITY_TYPES.map((type) => ({
    type,
    activity: props.editor.activities.find((activity) => activity.type === type) ?? null,
  })),
)

const busy = ref(false)
const drawerOpen = ref(false)
const editingType = ref<ClassActivityType>('FLASHCARDS')
const editingPosition = ref(1)
const editing = ref<EditorActivity | null>(null)
const drawerError = ref<string | null>(null)

function openDrawer(type: ClassActivityType, position: number): void {
  editingType.value = type
  editingPosition.value = position
  editing.value = props.editor.activities.find((activity) => activity.type === type) ?? null
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
</script>
