<template>
  <ol class="row-list">
    <li v-for="row in rows" :key="row.id" :data-testid="`feedback-${row.id}`">
      <div class="row-list-copy">
        <strong class="row-list-title"><CjkText :text="aboutText(row)" /></strong>
        <span v-if="showStudent" class="row-list-meta">
          {{ t('teacherStudents.history.sentTo', { name: row.studentName, classTitle: row.classTitle ?? '' }) }}
        </span>
        <span class="row-list-meta feedback-list-message"><CjkText :text="row.message" /></span>
        <time class="row-list-meta" :datetime="row.createdAt">
          {{ formatDateTime(row.createdAt) }}
          <template v-if="row.editedAt">
            · {{ t('teacherStudents.history.edited', { date: formatDateTime(row.editedAt) }) }}
          </template>
        </time>
      </div>
      <div v-if="editable" class="row-list-actions">
        <button
          type="button"
          class="btn btn-secondary btn-sm"
          :aria-label="t('teacherStudents.history.editLabel', { name: row.studentName })"
          @click="emit('edit', row)"
        >
          <IconGlyph name="edit" :size="16" />{{ t('teacherStudents.history.edit') }}
        </button>
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { FeedbackAbout, FeedbackRow } from '@/types/classes'
import { formatDateTime } from '@/utils/format'

/** Feedback a teacher sent, newest first, as divider rows: what it is about, the message and when. */
withDefaults(defineProps<{ rows: readonly FeedbackRow[]; editable?: boolean; showStudent?: boolean }>(), {
  editable: false,
  showStudent: false,
})
const emit = defineEmits<{ edit: [row: FeedbackRow] }>()

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages })

const ABOUT_KEYS: Record<FeedbackAbout, string> = {
  QUIZ_ATTEMPT: 'teacherStudents.history.about.QUIZ_ATTEMPT',
  LESSON: 'teacherStudents.history.about.LESSON',
  GENERAL: 'teacherStudents.history.about.GENERAL',
}

function aboutText(row: FeedbackRow): string {
  return t(ABOUT_KEYS[row.about], { lesson: row.lessonTitle ?? '' })
}
</script>
