<template>
  <section class="panel authoring-card" aria-labelledby="lesson-activities-title">
    <div class="authoring-card-head">
      <h2 id="lesson-activities-title" class="card-title">{{ t('lessonEditor.activities.title') }}</h2>
      <p class="type-caption">{{ t('lessonEditor.activities.subtitle') }}</p>
    </div>
    <ol class="row-list">
      <li v-for="slot in slots" :key="slot.type" data-testid="activity-slot">
        <div class="lesson-activity-lead">
          <span class="authoring-tile" aria-hidden="true">{{ slot.number }}</span>
          <span class="authoring-tile" aria-hidden="true"
            ><IconGlyph :name="ACTIVITY_ICONS[slot.type]" :size="16"
          /></span>
          <div v-if="slot.activity" class="row-list-copy">
            <strong class="row-list-title"><CjkText :text="slot.activity.title" /></strong>
            <span class="row-list-meta">{{ t(`practiceType.${slot.type}`) }}</span>
          </div>
          <div v-else class="row-list-copy">
            <strong class="row-list-title">{{ t(`practiceType.${slot.type}`) }}</strong>
            <span class="row-list-meta">{{ t('lessonEditor.activities.notAdded') }}</span>
          </div>
        </div>
        <div v-if="editor.editable" class="row-list-actions">
          <button
            type="button"
            class="btn btn-secondary btn-sm"
            :aria-label="
              slot.activity
                ? t('lessonEditor.activities.editLabel', { title: slot.activity.title })
                : t('lessonEditor.activities.addLabel', {
                    number: slot.number,
                    type: t(`practiceType.${slot.type}`),
                  })
            "
            :data-testid="slot.activity ? undefined : `add-activity-${slot.number}`"
            @click="open(slot.type, slot.activity)"
          >
            <IconGlyph :name="slot.activity ? 'edit' : 'plus'" :size="16" />{{
              slot.activity ? t('common.edit') : t('lessonEditor.activities.add')
            }}
          </button>
        </div>
      </li>
    </ol>

    <LessonActivityDrawer
      v-if="drawer"
      :type="drawer.type"
      :activity="drawer.activity"
      :busy="busy"
      :error="drawerError"
      @close="drawer = null"
      @save="save"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { lessonEditorApi } from '@/api/adminClasses'
import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { ActivityPayload, EditorActivity, LessonEditor } from '@/types/adminClasses'
import { CLASS_ACTIVITY_TYPES, type ClassActivityType } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { ACTIVITY_ICONS } from '@/utils/classes'

import LessonActivityDrawer from './LessonActivityDrawer.vue'
import { useLessonPanelGuard } from './useLessonPanelGuard'

/**
 * The Activities tab of the lesson editor: the 3 fixed activities every lesson has, in the order learners do them
 * (1 Flashcards, 2 Tracing, 3 Matching). Each slot shows, also before it is added; its Add or Edit opens the drawer for
 * that slot's type. Every change is saved at once to the lesson's working copy.
 */
const props = defineProps<{ editor: LessonEditor }>()
const emit = defineEmits<{
  updated: [editor: LessonEditor]
  notice: [message: string]
  /** A save starts (true) or ends (false); the editor's tabs wait for it. */
  saving: [saving: boolean]
}>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages })

const slots = computed(() =>
  CLASS_ACTIVITY_TYPES.map((type, index) => ({
    type,
    number: index + 1,
    activity: props.editor.activities.find((activity) => activity.type === type) ?? null,
  })),
)

const busy = ref(false)
/** The open drawer: its slot's type, and the activity it edits (null to add it). */
const drawer = ref<{ type: ClassActivityType; activity: EditorActivity | null } | null>(null)
const drawerError = ref<string | null>(null)

const guard = useLessonPanelGuard({
  lessonId: () => props.editor.lessonId,
  saving: busy,
  onSaving: (saving) => emit('saving', saving),
  onReset: () => {
    drawer.value = null
    drawerError.value = null
    busy.value = false
  },
})

function open(type: ClassActivityType, activity: EditorActivity | null): void {
  if (!props.editor.editable || busy.value) return
  drawer.value = { type, activity }
  drawerError.value = null
}

async function save(payload: ActivityPayload): Promise<void> {
  if (!props.editor.editable || !drawer.value || busy.value) return
  const { activity } = drawer.value
  const lessonId = props.editor.lessonId
  busy.value = true
  drawerError.value = null
  await guard.run(
    async (isCurrent) => {
      const next = activity
        ? await lessonEditorApi.updateActivity(lessonId, activity.type, activity.id, payload)
        : await lessonEditorApi.addActivity(lessonId, payload)
      if (!isCurrent()) return
      emit('updated', next)
      emit('notice', t('lessonEditor.activities.saved'))
      drawer.value = null
    },
    {
      onError: (reason) => {
        drawerError.value = resolveApiError(reason, t('lessonEditor.activities.error'))
      },
      onFinally: () => {
        busy.value = false
      },
    },
  )
}
</script>
