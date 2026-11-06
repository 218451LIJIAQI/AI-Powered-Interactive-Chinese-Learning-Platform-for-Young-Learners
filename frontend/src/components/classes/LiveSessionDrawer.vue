<template>
  <AppOverlay
    :open="open"
    size="lg"
    :title="session ? t('teacherSessions.form.editTitle') : t('teacherSessions.form.newTitle')"
    :subtitle="session ? t('teacherSessions.form.editHint') : ''"
    :busy="busy"
    test-id="session-form"
    @close="emit('close')"
  >
    <p v-if="!classes.length && !session" class="alert alert-warning">{{ t('teacherSessions.form.noClasses') }}</p>
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <div class="field">
        <label class="field-label" :for="`${formId}-title`">
          {{ t('teacherSessions.form.title') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-title`"
          v-model="form.title"
          class="input cjk-text"
          required
          maxlength="200"
          :placeholder="t('teacherSessions.form.titlePlaceholder')"
          data-testid="session-title"
        />
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-class`">
          {{ t('teacherSessions.form.class') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <select :id="`${formId}-class`" v-model="form.classId" class="select" required data-testid="session-class">
          <option :value="null" disabled>{{ t('teacherSessions.form.chooseClass') }}</option>
          <option v-for="item in classes" :key="item.id" :value="item.id">{{ item.title }}</option>
        </select>
      </div>
      <div class="teacher-session-when">
        <div class="field">
          <label class="field-label" :for="`${formId}-date`">
            {{ t('teacherSessions.form.date') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${formId}-date`"
            v-model="form.date"
            class="input"
            type="date"
            required
            data-testid="session-date"
          />
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-start`">
            {{ t('teacherSessions.form.start') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${formId}-start`"
            v-model="form.start"
            class="input"
            type="time"
            required
            data-testid="session-start"
          />
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-end`">
            {{ t('teacherSessions.form.end') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`${formId}-end`"
            v-model="form.end"
            class="input"
            type="time"
            required
            data-testid="session-end"
          />
        </div>
      </div>
      <p v-if="timeZone !== browserZone" class="field-hint">
        {{ t('teacherSessions.form.timeZone', { zone: timeZone }) }}
      </p>
      <div class="field">
        <label class="field-label" :for="`${formId}-link`">
          {{ t('teacherSessions.form.link') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${formId}-link`"
          v-model="form.link"
          class="input"
          type="url"
          required
          maxlength="500"
          placeholder="https://"
          data-testid="session-link"
        />
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button
        v-if="session"
        type="button"
        class="btn btn-danger-soft is-far-left"
        :disabled="busy"
        data-testid="session-cancel"
        @click="emit('cancel-session')"
      >
        {{ t('teacherSessions.cancel.open') }}
      </button>
      <button type="button" class="btn btn-secondary" :disabled="busy" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        type="submit"
        class="btn btn-primary"
        :form="formId"
        :disabled="busy || !ready"
        :aria-busy="busy"
        data-testid="session-save"
      >
        <template v-if="session">{{ t('teacherSessions.form.saveEdit') }}</template>
        <template v-else><IconGlyph name="calendar" :size="16" />{{ t('teacherSessions.form.save') }}</template>
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'
import { teacherSessionsMessages } from '@/plugins/teacherSessionsMessages'
import type { LiveSessionPayload, ScheduleOptions, TeacherSession } from '@/types/classes'
import { platformFromLink } from '@/utils/teacherSessions'

/**
 * Schedule or edit a live session. Times are wall-clock times in the session's zone: the browser's zone for a new
 * session, the session's own zone when editing. A session has no seat limit or chosen lesson any more: a new one is
 * sent without them, an edited one keeps what it had (unless it moves to another class, whose lessons differ).
 */
const props = defineProps<{
  open: boolean
  session: TeacherSession | null
  options: ScheduleOptions | null
  busy: boolean
  error: string | null
}>()
const emit = defineEmits<{ save: [payload: LiveSessionPayload]; close: []; 'cancel-session': [] }>()

const { t } = useLocalMessages({ teacherSessions: teacherSessionsMessages, common: commonMessages })
const formId = useId()
const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

interface SessionForm {
  title: string
  classId: number | null
  date: string
  start: string
  end: string
  link: string
}

const form = ref<SessionForm>(blank())
/** The session's own class stays selectable even if it is no longer among the published ones. */
const classes = computed(() => {
  const list = props.options?.classes ?? []
  const session = props.session
  return session && !list.some((item) => item.id === session.classId)
    ? [{ id: session.classId, title: session.classTitle }, ...list]
    : list
})
const timeZone = computed(() => props.session?.timeZone ?? browserZone)
const ready = computed(() =>
  Boolean(
    form.value.title.trim() &&
    form.value.classId &&
    form.value.date &&
    form.value.start &&
    form.value.end &&
    form.value.link.trim(),
  ),
)

function blank(): SessionForm {
  return { title: '', classId: null, date: '', start: '', end: '', link: '' }
}

watch(
  () => [props.open, props.session] as const,
  ([open, session]) => {
    if (!open) return
    if (!session) {
      form.value = { ...blank(), classId: classes.value.length === 1 ? classes.value[0]!.id : null }
      return
    }
    form.value = {
      title: session.title,
      classId: session.classId,
      date: session.localStart.slice(0, 10),
      start: session.localStart.slice(11, 16),
      end: session.localEnd.slice(11, 16),
      link: session.meetingLink,
    }
  },
  { immediate: true },
)

function save(): void {
  if (!ready.value) return
  const session = props.session
  const sameClass = session?.classId === form.value.classId
  const link = form.value.link.trim()
  emit('save', {
    title: form.value.title.trim(),
    classId: form.value.classId!,
    lessonId: sameClass ? session!.lessonId : null,
    startTime: `${form.value.date}T${form.value.start}:00`,
    endTime: `${form.value.date}T${form.value.end}:00`,
    timeZone: timeZone.value,
    capacity: session?.capacity ?? null,
    meetingLink: link,
    meetingPlatform: platformFromLink(link),
  })
}
</script>
