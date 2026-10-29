<template>
  <div class="teacher-feedback-page page-stack">
    <PageHeaderBlock :title="t('teacherStudents.title')" :description="t('teacherStudents.history.lede')" />
    <TeacherStudentsTabs provides="both" :student-count="roster.data.value?.total ?? null" :feedback-count="allCount" />
    <DataNotice :error="actionError" :success="actionSuccess" />

    <section class="teacher-feedback-board" :aria-label="t('teacherStudents.tabHistory')">
      <form class="teacher-feedback-filters" :aria-label="t('teacherStudents.history.filters')" @submit.prevent>
        <div class="field">
          <label class="field-label" for="feedback-student">{{ t('teacherStudents.history.student') }}</label>
          <select id="feedback-student" v-model="studentId" class="select">
            <option :value="null">{{ t('teacherStudents.history.allStudents') }}</option>
            <option v-for="student in students" :key="student.studentId" :value="student.studentId">
              {{ student.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="feedback-type">{{ t('teacherStudents.history.type') }}</label>
          <select id="feedback-type" v-model="type" class="select">
            <option :value="null">{{ t('teacherStudents.history.allTypes') }}</option>
            <option v-for="value in FEEDBACK_TYPES" :key="value" :value="value">
              {{ t(FEEDBACK_TYPE_KEYS[value]) }}
            </option>
          </select>
        </div>
      </form>

      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        :title="t('classes.loading')"
        :message="t('teacherStudents.tabHistory')"
      />
      <StatePanel
        v-else-if="resource.error.value"
        tone="error"
        icon="alert"
        :title="t('teacherStudents.history.loadError')"
        :message="resource.error.value"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
        </template>
      </StatePanel>
      <div v-else-if="rows.length" class="panel teacher-feedback-sheet">
        <FeedbackList :rows="rows" editable show-student @edit="editing = $event" />
      </div>
      <StatePanel
        v-else
        tone="empty"
        icon="chat"
        :title="t('teacherStudents.history.emptyTitle')"
        :message="filtered ? t('teacherStudents.history.noMatch') : t('teacherStudents.history.emptyMessage')"
      />
    </section>

    <FeedbackDrawer
      :open="editing !== null"
      :title="t('teacherStudents.history.editTitle')"
      :subtitle="
        editing
          ? t('teacherStudents.history.sentTo', { name: editing.studentName, classTitle: editing.classTitle ?? '' })
          : ''
      "
      :initial="editing"
      :busy="saving"
      :error="saveError"
      @close="editing = null"
      @save="saveEdit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { teacherStudentsApi } from '@/api/classes'
import FeedbackDrawer, { type FeedbackDraft } from '@/components/classes/FeedbackDrawer.vue'
import FeedbackList from '@/components/classes/FeedbackList.vue'
import TeacherStudentsTabs from '@/components/classes/TeacherStudentsTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { FeedbackRow, FeedbackType } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { FEEDBACK_TYPES, FEEDBACK_TYPE_KEYS } from '@/utils/classes'
import '@/styles/classes.css'

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages, classes: classesMessages })
const { actionError, actionSuccess, showSuccess } = useActionFeedback()

const studentId = ref<number | null>(null)
const type = ref<FeedbackType | null>(null)
const filtered = computed(() => studentId.value !== null || type.value !== null)

const resource = useResource(() =>
  teacherStudentsApi.feedback({ studentId: studentId.value ?? undefined, type: type.value ?? undefined }),
)
const roster = useResource(() => teacherStudentsApi.roster())
const rows = computed(() => resource.data.value ?? [])
const students = computed(() => roster.data.value?.students ?? [])
/** The tab count is every feedback sent, whatever the filters. */
const allCount = ref<number | null>(null)

watch([studentId, type], () => void resource.reload())
watch(rows, (current) => {
  if (!filtered.value) allCount.value = current.length
})

const editing = ref<FeedbackRow | null>(null)
const saving = ref(false)
const saveError = ref<string | null>(null)

watch(editing, () => {
  saveError.value = null
})

async function saveEdit(draft: FeedbackDraft): Promise<void> {
  if (!editing.value) return
  saving.value = true
  try {
    await teacherStudentsApi.updateFeedback(editing.value.id, { type: draft.type, message: draft.message })
    editing.value = null
    showSuccess(t('teacherStudents.history.saved'))
    await resource.reload()
  } catch (reason) {
    saveError.value = resolveApiError(reason, t('teacherStudents.history.error'))
  } finally {
    saving.value = false
  }
}
</script>
