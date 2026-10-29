<template>
  <div class="teacher-live-page page-stack">
    <PageHeaderBlock :title="t('teacherSessions.title')" :description="summary">
      <template #actions>
        <button type="button" class="btn btn-primary" data-testid="schedule-session" @click="openForm(null)">
          <IconGlyph name="calendar" :size="16" />{{ t('teacherSessions.schedule') }}
        </button>
      </template>
    </PageHeaderBlock>
    <RouteTabs :tabs="tabs" :label="t('teacherSessions.tabsLabel')" />
    <DataNotice :error="actionError" :success="actionSuccess" />

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="resource.reload">
      <StatePanel
        v-if="!rows.length"
        tone="empty"
        icon="calendar"
        spacious
        :title="tab === 'past' ? t('teacherSessions.emptyPastTitle') : t('teacherSessions.emptyUpcomingTitle')"
        :message="tab === 'past' ? t('teacherSessions.emptyPastMessage') : t('teacherSessions.emptyUpcomingMessage')"
      >
        <template v-if="tab !== 'past'" #actions>
          <button type="button" class="btn btn-primary" @click="openForm(null)">
            {{ t('teacherSessions.schedule') }}
          </button>
        </template>
      </StatePanel>
      <ol v-else class="teacher-timetable">
        <li
          v-for="day in days"
          :key="day.key"
          class="teacher-timetable-day"
          :class="{ 'is-today': relativeDay(day.at) === 'today' }"
        >
          <h2 class="teacher-timetable-date">
            <span class="sr-only">{{ dayHeading(day.at) }}</span>
            <!-- Read like the date tiles elsewhere: weekday (or Today), day, month. -->
            <span class="teacher-timetable-weekday" aria-hidden="true">{{ dayLabel(day.at) }}</span>
            <span class="teacher-timetable-daynum" aria-hidden="true">{{ dateTile(day.at, locale).day }}</span>
            <span class="type-caption" aria-hidden="true">{{ dateTile(day.at, locale).month }}</span>
          </h2>
          <ul class="teacher-timetable-rows">
            <li
              v-for="session in day.items"
              :key="session.id"
              class="teacher-timetable-row"
              :class="{ 'is-cancelled': session.status === 'CANCELLED', 'is-next': session.id === nextId }"
              :data-testid="`teacher-session-${session.id}`"
            >
              <span class="teacher-agenda-time">
                <strong>{{ formatTime(session.startsAt) }}</strong>
                <span>{{ formatTime(session.endsAt) }}</span>
              </span>
              <span class="teacher-timetable-copy">
                <span class="teacher-agenda-title">
                  <strong>{{ session.title }}</strong>
                  <span v-if="session.id === nextId" class="teacher-next-tag">{{ t('teacherSessions.next') }}</span>
                </span>
                <span class="type-caption">{{ rowFacts(session) }}</span>
                <span class="type-caption">
                  {{ t(PLATFORM_KEYS[session.platform]) }}
                  <template v-if="session.status === 'CANCELLED' && session.cancelledAt">
                    · {{ t('teacherSessions.cancelledOn', { date: formatShortDate(session.cancelledAt) }) }}
                  </template>
                  <template v-else-if="session.rescheduledFrom">
                    · {{ t('teacherSessions.movedFrom', { time: formatTime(session.rescheduledFrom) }) }}
                  </template>
                </span>
              </span>
              <span class="teacher-timetable-seats">
                <template v-if="tab === 'past'">
                  <strong>
                    {{
                      t('teacherSessions.attended', { attended: session.attendedCount, booked: session.bookedCount })
                    }}
                  </strong>
                  <span v-if="session.status === 'COMPLETED'" class="type-caption">
                    {{ session.recordingUrl ? t('teacherSessions.recordingAdded') : t('teacherSessions.noRecording') }}
                  </span>
                </template>
                <template v-else>
                  <span class="teacher-agenda-booking">
                    <span v-if="session.capacity" class="teacher-meter" aria-hidden="true">
                      <span :style="{ width: `${bookedPercent(session.bookedCount, session.capacity)}%` }" />
                    </span>
                    <strong>{{ bookedLine(session) }}</strong>
                  </span>
                  <span
                    v-if="session.capacity && session.status !== 'CANCELLED' && session.status !== 'FULL'"
                    class="type-caption"
                  >
                    {{ t('classes.session.seatsLeft', Math.max(0, session.capacity - session.bookedCount)) }}
                  </span>
                </template>
              </span>
              <span class="teacher-timetable-state">
                <StatusPill
                  v-if="session.status !== 'SCHEDULED'"
                  :label="t(SESSION_STATUS_KEYS[session.status])"
                  :tone="SESSION_STATUS_TONES[session.status]"
                />
              </span>
              <button
                type="button"
                class="btn btn-link teacher-timetable-open"
                :aria-label="t('teacherSessions.openLabel', { title: session.title })"
                @click="openDetail(session.id)"
              >
                {{ t('teacherSessions.open') }}<IconGlyph name="chevronRight" :size="16" />
              </button>
            </li>
          </ul>
        </li>
      </ol>
    </ResourceSection>

    <AppOverlay
      variant="drawer"
      :open="detail !== null"
      :title="detail?.session.title ?? ''"
      :subtitle="detail ? `${formatWeekdayDate(detail.session.startsAt)} · ${timeRange(detail.session)}` : ''"
      test-id="teacher-session-detail"
      @close="closeDetail"
    >
      <template v-if="detail">
        <div class="teacher-session-summary">
          <StatusPill
            :label="t(SESSION_STATUS_KEYS[detail.session.status])"
            :tone="SESSION_STATUS_TONES[detail.session.status]"
          />
          <span class="teacher-agenda-booking">
            <span v-if="detail.session.capacity" class="teacher-meter" aria-hidden="true">
              <span :style="{ width: `${bookedPercent(detail.session.bookedCount, detail.session.capacity)}%` }" />
            </span>
            <span class="type-caption">{{ bookedLine(detail.session) }}</span>
          </span>
        </div>
        <dl class="kv-list">
          <dt>{{ t('teacherSessions.detail.class') }}</dt>
          <dd>{{ detail.session.classTitle }}</dd>
          <template v-if="detail.session.lessonTitle">
            <dt>{{ t('teacherSessions.detail.lesson') }}</dt>
            <dd>{{ detail.session.lessonTitle }}</dd>
          </template>
          <dt>{{ t('teacherSessions.detail.platform') }}</dt>
          <dd>{{ t(PLATFORM_KEYS[detail.session.platform]) }}</dd>
          <dt>{{ t('teacherSessions.detail.link') }}</dt>
          <dd>
            <a
              v-if="isWebLink(detail.session.meetingLink)"
              :href="detail.session.meetingLink"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ t('teacherSessions.detail.openLink') }}<span class="sr-only"> {{ t('classes.opensInNewTab') }}</span>
            </a>
          </dd>
          <dt>{{ t('teacherSessions.detail.seats') }}</dt>
          <dd>
            {{
              detail.session.capacity
                ? t('teacherSessions.bookedOf', {
                    booked: detail.session.bookedCount,
                    capacity: detail.session.capacity,
                  })
                : t('teacherSessions.bookedOpen', { booked: detail.session.bookedCount })
            }}
          </dd>
          <template v-if="detail.session.status !== 'COMPLETED' && detail.session.status !== 'CANCELLED'">
            <dt>{{ t('teacherSessions.detail.joinOpens') }}</dt>
            <dd>{{ formatTime(detail.joinOpensAt) }}</dd>
          </template>
        </dl>

        <h3 class="card-title">{{ t('teacherSessions.detail.students') }}</h3>
        <p v-if="!detail.students.length" class="type-caption">{{ t('teacherSessions.detail.noStudents') }}</p>
        <ul v-else class="teacher-live-students">
          <li v-for="student in detail.students" :key="student.studentId">
            <PortraitAvatar :size="32" :name="student.name" />
            <span class="teacher-live-student">
              <strong>{{ student.name }}</strong>
              <span class="type-caption">
                {{
                  student.classProgressPercent !== null
                    ? t('teacherSessions.detail.studentFacts', {
                        level: student.xpLevel,
                        progress: student.classProgressPercent,
                      })
                    : t('teacherSessions.detail.studentFactsNoProgress', { level: student.xpLevel })
                }}
              </span>
            </span>
            <StatusPill
              :label="t(ATTENDANCE_KEYS[student.attendance])"
              :tone="
                student.attendance === 'ATTENDED'
                  ? 'success'
                  : student.attendance === 'DID_NOT_JOIN'
                    ? 'neutral'
                    : 'info'
              "
            />
          </li>
        </ul>

        <form v-if="detail.session.status === 'COMPLETED'" class="form-grid" @submit.prevent="saveRecording">
          <div class="field">
            <label class="field-label" for="session-recording">{{ t('teacherSessions.detail.recording') }}</label>
            <input
              id="session-recording"
              v-model="recordingUrl"
              class="input"
              type="url"
              maxlength="500"
              placeholder="https://"
              aria-describedby="session-recording-hint"
            />
            <p id="session-recording-hint" class="field-hint">{{ t('teacherSessions.detail.recordingHint') }}</p>
          </div>
          <button type="submit" class="btn btn-secondary" :disabled="busy || !recordingUrl.trim()" :aria-busy="busy">
            {{ t('teacherSessions.detail.saveRecording') }}
          </button>
        </form>
      </template>
      <template v-if="detail && isUpcoming(detail.session)" #footer>
        <button type="button" class="btn btn-danger-soft" @click="cancelOpen = true">
          {{ t('teacherSessions.detail.cancel') }}
        </button>
        <button type="button" class="btn btn-primary" @click="openForm(detail.session)">
          <IconGlyph name="edit" :size="16" />{{ t('teacherSessions.detail.edit') }}
        </button>
      </template>
    </AppOverlay>

    <LiveSessionDrawer
      :open="formOpen"
      :session="editing"
      :options="options.data.value"
      :busy="busy"
      :error="formError"
      @close="formOpen = false"
      @save="saveSession"
    />

    <ConfirmDialog
      :open="cancelOpen"
      :title="t('teacherSessions.cancel.title')"
      :message="
        detail
          ? t('teacherSessions.cancel.message', {
              title: detail.session.title,
              day: formatWeekdayDate(detail.session.startsAt),
            })
          : ''
      "
      :confirm-label="t('teacherSessions.cancel.confirm')"
      :cancel-label="t('teacherSessions.cancel.keep')"
      tone="danger"
      :busy="busy"
      @cancel="cancelOpen = false"
      @confirm="cancelSession"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { teacherSessionsApi } from '@/api/classes'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import LiveSessionDrawer from '@/components/classes/LiveSessionDrawer.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherSessionsMessages } from '@/plugins/teacherSessionsMessages'
import type { BookedStudent, LiveSessionPayload, TeacherSession, TeacherSessionDetail } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import {
  PLATFORM_KEYS,
  SESSION_STATUS_KEYS,
  SESSION_STATUS_TONES,
  bookedPercent,
  dateTile,
  groupByDay,
  isWebLink,
  relativeDay,
} from '@/utils/classes'
import { formatShortDate, formatTime, formatWeekdayDate } from '@/utils/format'
import '@/styles/classes.css'

const { t, locale } = useLocalMessages({ teacherSessions: teacherSessionsMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()
const { actionError, actionSuccess, runAction, showError, showSuccess } = useActionFeedback()

const ATTENDANCE_KEYS: Record<BookedStudent['attendance'], string> = {
  BOOKED: 'teacherSessions.detail.attendance.BOOKED',
  ATTENDED: 'teacherSessions.detail.attendance.ATTENDED',
  DID_NOT_JOIN: 'teacherSessions.detail.attendance.DID_NOT_JOIN',
}

const tab = computed(() => (route.params.tab === 'past' ? 'past' : 'upcoming'))
const resource = useResource(() => teacherSessionsApi.list())
const options = useResource(() => teacherSessionsApi.options(), false)
const rows = computed(() => (tab.value === 'past' ? resource.data.value?.past : resource.data.value?.upcoming) ?? [])
const days = computed(() => groupByDay(rows.value))
const activeUpcoming = computed(
  () => resource.data.value?.upcoming.filter((session) => session.status !== 'CANCELLED') ?? [],
)
/** The first upcoming session that will happen, marked Next. */
const nextId = computed(() => (tab.value === 'past' ? null : (activeUpcoming.value[0]?.id ?? null)))

const summary = computed(() => {
  if (!resource.data.value) return ''
  const bookings = activeUpcoming.value.reduce((sum, session) => sum + session.bookedCount, 0)
  return [
    t('teacherSessions.summaryUpcoming', activeUpcoming.value.length),
    t('teacherSessions.summaryBookings', bookings),
  ].join(' · ')
})

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('teacherSessions.upcoming'),
    to: { name: 'teacher-live-sessions' },
    icon: 'calendar',
    count: resource.data.value ? activeUpcoming.value.length : null,
  },
  {
    label: t('teacherSessions.past'),
    to: { name: 'teacher-live-sessions', params: { tab: 'past' } },
    icon: 'history',
    count: resource.data.value?.past.length ?? null,
  },
])

function timeRange(session: TeacherSession): string {
  return t('classes.session.timeRange', { start: formatTime(session.startsAt), end: formatTime(session.endsAt) })
}

function dayLabel(at: string): string {
  const day = relativeDay(at)
  if (day === 'today') return t('teacherSessions.today')
  if (day === 'tomorrow') return t('teacherSessions.tomorrow')
  if (day === 'yesterday') return t('teacherSessions.yesterday')
  return dateTile(at, locale.value).weekday
}

function dayHeading(at: string): string {
  return relativeDay(at) ? `${dayLabel(at)}, ${formatWeekdayDate(at)}` : formatWeekdayDate(at)
}

function bookedLine(session: TeacherSession): string {
  return session.capacity
    ? t('teacherSessions.bookedLine', { booked: session.bookedCount, capacity: session.capacity })
    : t('teacherSessions.bookedLineOpen', { booked: session.bookedCount })
}

function rowFacts(session: TeacherSession): string {
  return session.lessonTitle
    ? t('teacherSessions.rowFacts', { classTitle: session.classTitle, lesson: session.lessonTitle })
    : session.classTitle
}

function isUpcoming(session: TeacherSession): boolean {
  return (
    session.status !== 'CANCELLED' &&
    session.status !== 'COMPLETED' &&
    new Date(session.startsAt).getTime() > Date.now()
  )
}

// ----- details -----

const detail = ref<TeacherSessionDetail | null>(null)
const recordingUrl = ref('')
const busy = ref(false)
const cancelOpen = ref(false)

async function openDetail(sessionId: number): Promise<void> {
  try {
    detail.value = await teacherSessionsApi.detail(sessionId)
    recordingUrl.value = detail.value.session.recordingUrl ?? ''
  } catch (reason) {
    showError(resolveApiError(reason, t('teacherSessions.loadErrorTitle')))
  }
}

function closeDetail(): void {
  detail.value = null
  if (route.query.session) void router.replace({ query: {} })
}

/** The dashboard links to a session with ?session=<id>. */
watch(
  () => route.query.session,
  (value) => {
    if (typeof value === 'string' && /^\d+$/.test(value)) void openDetail(Number(value))
  },
  { immediate: true },
)

async function saveRecording(): Promise<void> {
  if (!detail.value) return
  const sessionId = detail.value.session.id
  busy.value = true
  await runAction(t('teacherSessions.detail.recordingError'), async () => {
    await teacherSessionsApi.saveRecording(sessionId, recordingUrl.value.trim())
    await Promise.all([resource.reload(), openDetail(sessionId)])
    return t('teacherSessions.detail.recordingSaved')
  })
  busy.value = false
}

async function cancelSession(): Promise<void> {
  if (!detail.value) return
  const { id, title } = detail.value.session
  busy.value = true
  await runAction(t('teacherSessions.cancel.error'), async () => {
    await teacherSessionsApi.cancel(id)
    await Promise.all([resource.reload(), openDetail(id)])
    return t('teacherSessions.cancel.done', { title })
  })
  busy.value = false
  cancelOpen.value = false
}

// ----- schedule and edit -----

const formOpen = ref(false)
const editing = ref<TeacherSession | null>(null)
const formError = ref<string | null>(null)

function openForm(session: TeacherSession | null): void {
  editing.value = session
  formError.value = null
  formOpen.value = true
  if (!options.data.value) void options.reload()
}

async function saveSession(payload: LiveSessionPayload): Promise<void> {
  busy.value = true
  formError.value = null
  try {
    const saved = editing.value
      ? await teacherSessionsApi.update(editing.value.id, payload)
      : await teacherSessionsApi.schedule(payload)
    formOpen.value = false
    await resource.reload()
    if (detail.value?.session.id === saved.id) await openDetail(saved.id)
    showSuccess(
      editing.value
        ? t('teacherSessions.form.updated', { title: saved.title })
        : t('teacherSessions.form.scheduled', { title: saved.title }),
    )
  } catch (reason) {
    formError.value = resolveApiError(reason, t('teacherSessions.form.error'))
  } finally {
    busy.value = false
  }
}
</script>
