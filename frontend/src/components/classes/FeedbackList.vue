<template>
  <ol class="teacher-timeline">
    <li v-for="row in rows" :key="row.id" class="teacher-timeline-item" :data-testid="`feedback-${row.id}`">
      <time class="teacher-timeline-date" :datetime="row.createdAt">
        <strong>{{ formatShortDate(row.createdAt) }}</strong>
        <span>{{ formatTime(row.createdAt) }}</span>
      </time>
      <span class="teacher-timeline-dot" :class="`is-${row.type.toLowerCase()}`" aria-hidden="true" />
      <div class="teacher-timeline-body">
        <div class="teacher-timeline-head">
          <strong>{{ aboutText(row) }}</strong>
          <span class="tag">{{ t(FEEDBACK_TYPE_KEYS[row.type]) }}</span>
        </div>
        <span v-if="showStudent" class="type-caption">
          {{ t('teacherStudents.history.sentTo', { name: row.studentName, classTitle: row.classTitle ?? '' }) }}
        </span>
        <p class="teacher-timeline-message">{{ row.message }}</p>
        <span v-if="row.editedAt" class="type-caption">
          {{ t('teacherStudents.history.edited', { date: formatDateTime(row.editedAt) }) }}
        </span>
      </div>
      <button
        v-if="editable"
        type="button"
        class="btn btn-ghost btn-sm"
        :aria-label="t('teacherStudents.history.editLabel', { name: row.studentName })"
        @click="emit('edit', row)"
      >
        <IconGlyph name="edit" :size="16" />{{ t('teacherStudents.history.edit') }}
      </button>
    </li>
  </ol>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { FeedbackAbout, FeedbackRow } from '@/types/classes'
import { FEEDBACK_TYPE_KEYS } from '@/utils/classes'
import { formatDateTime, formatShortDate, formatTime } from '@/utils/format'

/** Feedback a teacher sent, newest first, as a timeline (student detail and Feedback history). */
withDefaults(defineProps<{ rows: readonly FeedbackRow[]; editable?: boolean; showStudent?: boolean }>(), {
  editable: false,
  showStudent: false,
})
const emit = defineEmits<{ edit: [row: FeedbackRow] }>()

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages, classes: classesMessages })

const ABOUT_KEYS: Record<FeedbackAbout, string> = {
  QUIZ_ATTEMPT: 'teacherStudents.history.about.QUIZ_ATTEMPT',
  LESSON: 'teacherStudents.history.about.LESSON',
  GENERAL: 'teacherStudents.history.about.GENERAL',
}

function aboutText(row: FeedbackRow): string {
  return t(ABOUT_KEYS[row.about], { lesson: row.lessonTitle ?? '', percent: row.attemptPercent ?? 0 })
}
</script>
