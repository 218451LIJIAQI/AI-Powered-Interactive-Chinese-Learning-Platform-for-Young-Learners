<template>
  <AppOverlay
    variant="drawer"
    :open="open"
    :title="session ? t('teacherSessions.form.editTitle') : t('teacherSessions.form.newTitle')"
    :subtitle="session ? t('teacherSessions.form.editHint') : ''"
    :busy="busy"
    test-id="session-form"
    @close="emit('close')"
  >
    <p v-if="!classes.length" class="alert alert-warning">{{ t('teacherSessions.form.noClasses') }}</p>
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
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-class`">
            {{ t('teacherSessions.form.class') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <select
            :id="`${formId}-class`"
            v-model="form.classId"
            class="select"
            required
            data-testid="session-class"
            @change="form.lessonId = null"
          >
            <option :value="null" disabled>{{ t('teacherSessions.form.chooseClass') }}</option>
            <option v-for="item in classes" :key="item.id" :value="item.id">{{ item.title }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-lesson`">{{ t('teacherSessions.form.lesson') }}</label>
          <select :id="`${formId}-lesson`" v-model="form.lessonId" class="select" :disabled="!lessons.length">
            <option :value="null">{{ t('teacherSessions.form.noLesson') }}</option>
            <option v-for="lesson in lessons" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
          </select>
        </div>
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
      <p class="field-hint">{{ t('teacherSessions.form.timeZone', { zone: timeZone }) }}</p>
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" :for="`${formId}-capacity`">{{ t('teacherSessions.form.capacity') }}</label>
          <input
            :id="`${formId}-capacity`"
            v-model.number="form.capacity"
            class="input"
            type="number"
            min="1"
            max="500"
            :aria-describedby="`${formId}-capacity-hint`"
          />
          <p :id="`${formId}-capacity-hint`" class="field-hint">{{ t('teacherSessions.form.capacityHint') }}</p>
        </div>
        <div class="field">
          <label class="field-label" :for="`${formId}-platform`">{{ t('teacherSessions.form.platform') }}</label>
          <select :id="`${formId}-platform`" v-model="form.platform" class="select">
            <option v-for="value in PLATFORMS" :key="value" :value="value">{{ t(PLATFORM_KEYS[value]) }}</option>
          </select>
        </div>
      </div>
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
          :aria-describedby="`${formId}-link-hint`"
          data-testid="session-link"
        />
        <p :id="`${formId}-link-hint`" class="field-hint">{{ t('teacherSessions.form.linkHint') }}</p>
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn btn-ghost" :disabled="busy" @click="emit('close')">
        {{ t('classes.cancel') }}
      </button>
      <button
        type="submit"
        class="btn btn-primary"
        :form="formId"
        :disabled="busy || !ready"
        :aria-busy="busy"
        data-testid="session-save"
      >
        {{ session ? t('teacherSessions.form.saveEdit') : t('teacherSessions.form.save') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherSessionsMessages } from '@/plugins/teacherSessionsMessages'
import type { LiveSessionPayload, MeetingPlatform, ScheduleOptions, TeacherSession } from '@/types/classes'
import { PLATFORM_KEYS } from '@/utils/classes'

import AppOverlay from '@/components/common/AppOverlay.vue'

/**
 * Schedule or edit a live session. Times are wall-clock times in the session's zone: the browser's zone for a new
 * session, the session's own zone when editing.
 */
const props = defineProps<{
  open: boolean
  session: TeacherSession | null
  options: ScheduleOptions | null
  busy: boolean
  error: string | null
}>()
const emit = defineEmits<{ save: [payload: LiveSessionPayload]; close: [] }>()

const { t } = useLocalMessages({ teacherSessions: teacherSessionsMessages, classes: classesMessages })
const formId = useId()
const PLATFORMS: readonly MeetingPlatform[] = ['ZOOM', 'GOOGLE_MEET', 'MICROSOFT_TEAMS', 'OTHER']
const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

interface SessionForm {
  title: string
  classId: number | null
  lessonId: number | null
  date: string
  start: string
  end: string
  capacity: number | null
  platform: MeetingPlatform
  link: string
}

const form = ref<SessionForm>(blank())
const classes = computed(() => props.options?.classes ?? [])
const lessons = computed(() => classes.value.find((item) => item.id === form.value.classId)?.lessons ?? [])
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
  return {
    title: '',
    classId: null,
    lessonId: null,
    date: '',
    start: '',
    end: '',
    capacity: null,
    platform: 'ZOOM',
    link: '',
  }
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
      lessonId: session.lessonId,
      date: session.localStart.slice(0, 10),
      start: session.localStart.slice(11, 16),
      end: session.localEnd.slice(11, 16),
      capacity: session.capacity,
      platform: session.platform,
      link: session.meetingLink,
    }
  },
  { immediate: true },
)

function save(): void {
  if (!ready.value) return
  emit('save', {
    title: form.value.title.trim(),
    classId: form.value.classId!,
    lessonId: form.value.lessonId,
    startTime: `${form.value.date}T${form.value.start}:00`,
    endTime: `${form.value.date}T${form.value.end}:00`,
    timeZone: timeZone.value,
    capacity: form.value.capacity || null,
    meetingLink: form.value.link.trim(),
    meetingPlatform: form.value.platform,
  })
}
</script>
