<template>
  <div class="student-live-page page-stack">
    <PageHeaderBlock :title="t('classCatalogue.tabs.live')" :description="t('studentClasses.live.lede')" />
    <StudentClassesTabs />
    <DataNotice :error="actionError" :success="actionSuccess" />

    <ResourceSection
      class="page-stack"
      :loading="resource.loading.value"
      :error="resource.error.value"
      @retry="resource.reload"
    >
      <template v-if="sessions">
        <StatePanel
          v-if="!sessions.upcoming.length && !sessions.past.length"
          tone="empty"
          icon="live"
          spacious
          :title="t('studentClasses.live.emptyTitle')"
          :message="t('studentClasses.live.emptyMessage')"
        >
          <template #actions>
            <RouterLink class="btn btn-primary" :to="{ name: 'student-classes-find' }">
              {{ t('classCatalogue.findClasses') }}
            </RouterLink>
          </template>
        </StatePanel>

        <template v-else>
          <!-- Upcoming: an agenda, one block per day, the sessions of the day in time order. -->
          <section class="student-live-section" aria-labelledby="live-upcoming-title">
            <div class="section-header">
              <h2 id="live-upcoming-title" class="section-title">{{ t('studentClasses.live.upcoming') }}</h2>
              <p class="section-description">
                {{
                  t('studentClasses.live.upcomingFacts', {
                    sessions: t('studentClasses.live.sessions', sessions.upcoming.length),
                    booked: sessions.bookedCount,
                  })
                }}
              </p>
            </div>
            <p v-if="!sessions.upcoming.length" class="student-live-none">{{ t('studentClasses.live.noUpcoming') }}</p>
            <div v-else class="student-live-agenda">
              <div
                v-for="day in upcomingDays"
                :key="day.key"
                class="student-live-day"
                role="group"
                :aria-label="formatWeekdayDate(day.at)"
              >
                <div class="student-live-day-head">
                  <SessionDateTile :at="day.at" show-month />
                  <span v-if="relativeDay(day.at) === 'today'" class="page-eyebrow student-classes-pigment">{{
                    t('studentClasses.live.today')
                  }}</span>
                </div>
                <ul class="student-live-list">
                  <li
                    v-for="session in day.items"
                    :key="session.id"
                    class="student-live-row"
                    :class="{
                      'is-cancelled': session.status === 'CANCELLED',
                      'is-booked': session.booked && session.status !== 'CANCELLED',
                    }"
                    :data-testid="`session-${session.id}`"
                  >
                    <span class="student-live-time">
                      <strong>{{ formatTime(session.startsAt) }}</strong>
                      <span>{{ t('studentClasses.live.until', { time: formatTime(session.endsAt) }) }}</span>
                      <span>{{ t(PLATFORM_KEYS[session.platform]) }}</span>
                    </span>
                    <div class="student-live-copy">
                      <strong class="student-live-title">{{ session.title }}</strong>
                      <span class="student-live-teacher">
                        <PortraitAvatar :size="24" :name="session.teacherName" />
                        <span>
                          {{
                            t('studentClasses.live.teacherLine', {
                              teacher: session.teacherName,
                              classTitle: session.classTitle,
                            })
                          }}<template v-if="session.lessonTitle">
                            · {{ t('studentClasses.live.lessonLine', { lesson: session.lessonTitle }) }}</template
                          >
                        </span>
                      </span>
                      <div v-if="hasState(session)" class="student-live-state">
                        <StatusPill
                          v-if="session.booked && session.status !== 'CANCELLED'"
                          :label="t('classes.session.booked')"
                          tone="success"
                          icon="check"
                        />
                        <StatusPill
                          v-if="showsStatus(session)"
                          :label="t(SESSION_STATUS_KEYS[session.status])"
                          :tone="SESSION_STATUS_TONES[session.status]"
                        />
                        <StatusPill
                          v-if="session.rescheduledFrom && session.status !== 'CANCELLED'"
                          :label="t('classes.session.movedFrom', { time: formatTime(session.rescheduledFrom) })"
                          tone="info"
                          icon="calendar"
                        />
                        <span v-if="showsSeats(session)" class="type-caption">
                          {{ t('classes.session.seatsLeft', session.seatsLeft ?? 0) }}
                        </span>
                      </div>
                      <p v-if="session.status === 'CANCELLED' && session.cancelledAt" class="student-live-note">
                        <IconGlyph name="alert" :size="14" />
                        {{ t('studentClasses.live.cancelledOn', { date: formatShortDate(session.cancelledAt) }) }}
                      </p>
                      <p v-else-if="session.booked && !session.canJoin" class="student-live-note">
                        <IconGlyph name="clock" :size="14" />
                        {{ t('studentClasses.live.joinOpens', { time: formatTime(session.joinOpensAt) }) }}
                      </p>
                      <p v-else-if="session.bookingCancelled && !session.booked" class="student-live-note">
                        {{ t('studentClasses.live.bookingCancelled') }}
                      </p>
                    </div>
                    <div class="student-live-actions">
                      <button
                        v-if="session.booked && session.status !== 'CANCELLED'"
                        type="button"
                        class="btn btn-sm"
                        :class="session.canJoin ? 'btn-primary' : 'btn-outline'"
                        :disabled="!session.canJoin || busyId === session.id"
                        :aria-label="t('studentClasses.live.joinLabel', { title: session.title })"
                        @click="join(session)"
                      >
                        <IconGlyph name="video" :size="16" />{{ t('studentClasses.live.join') }}
                      </button>
                      <button
                        v-else-if="session.canBook"
                        type="button"
                        class="btn btn-secondary btn-sm"
                        :disabled="busyId === session.id"
                        :aria-busy="busyId === session.id"
                        :aria-label="t('studentClasses.live.bookLabel', { title: session.title })"
                        @click="book(session)"
                      >
                        <IconGlyph name="calendarCheck" :size="16" />{{ t('studentClasses.live.book') }}
                      </button>
                      <!-- A full session is a state, not a disabled action. -->
                      <StatusPill
                        v-else-if="session.status === 'FULL'"
                        :label="t('studentClasses.live.full')"
                        tone="warning"
                      />
                      <button
                        type="button"
                        class="btn btn-link"
                        :aria-label="t('studentClasses.live.detailsLabel', { title: session.title })"
                        @click="selected = session"
                      >
                        {{ t('studentClasses.live.details') }}<IconGlyph name="chevronRight" :size="16" />
                      </button>
                    </div>
                  </li>
                </ul>
              </div>
            </div>
          </section>

          <!-- Past: the sessions she booked, newest first, with their recordings. -->
          <section class="student-live-section is-past" aria-labelledby="live-past-title">
            <div class="section-header">
              <h2 id="live-past-title" class="section-title">{{ t('studentClasses.live.past') }}</h2>
              <p class="section-description">
                {{
                  t('studentClasses.live.pastFacts', {
                    recordings: t('studentClasses.live.recordings', recordingCount),
                  })
                }}
              </p>
            </div>
            <p v-if="!sessions.past.length" class="student-live-none">{{ t('studentClasses.live.noPast') }}</p>
            <div v-else class="student-live-agenda">
              <div
                v-for="day in pastDays"
                :key="day.key"
                class="student-live-day"
                role="group"
                :aria-label="formatWeekdayDate(day.at)"
              >
                <div class="student-live-day-head">
                  <SessionDateTile :at="day.at" show-month />
                </div>
                <ul class="student-live-list">
                  <li v-for="session in day.items" :key="session.id" class="student-live-row">
                    <span class="student-live-time">
                      <strong>{{ formatTime(session.startsAt) }}</strong>
                      <span>{{ t('studentClasses.live.until', { time: formatTime(session.endsAt) }) }}</span>
                      <span>{{ t(PLATFORM_KEYS[session.platform]) }}</span>
                    </span>
                    <div class="student-live-copy">
                      <strong class="student-live-title">{{ session.title }}</strong>
                      <span class="student-live-teacher">
                        <PortraitAvatar :size="24" :name="session.teacherName" />
                        <span>
                          {{
                            t('studentClasses.live.teacherLine', {
                              teacher: session.teacherName,
                              classTitle: session.classTitle,
                            })
                          }}<template v-if="session.lessonTitle">
                            · {{ t('studentClasses.live.lessonLine', { lesson: session.lessonTitle }) }}</template
                          >
                        </span>
                      </span>
                      <div class="student-live-state">
                        <StatusPill
                          :label="
                            session.attended ? t('studentClasses.live.attended') : t('studentClasses.live.missed')
                          "
                          :tone="session.attended ? 'success' : 'neutral'"
                          :icon="session.attended ? 'check' : ''"
                        />
                      </div>
                    </div>
                    <div class="student-live-actions">
                      <a
                        v-if="session.recordingUrl && isWebLink(session.recordingUrl)"
                        class="btn btn-secondary btn-sm"
                        :href="session.recordingUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        :aria-label="`${t('studentClasses.live.recordingLabel', { title: session.title })} ${t('classes.opensInNewTab')}`"
                      >
                        <IconGlyph name="play" :size="16" />{{ t('studentClasses.live.recording') }}
                      </a>
                      <button
                        type="button"
                        class="btn btn-link"
                        :aria-label="t('studentClasses.live.detailsLabel', { title: session.title })"
                        @click="selected = session"
                      >
                        {{ t('studentClasses.live.details') }}<IconGlyph name="chevronRight" :size="16" />
                      </button>
                    </div>
                  </li>
                </ul>
              </div>
            </div>
          </section>
        </template>
      </template>
    </ResourceSection>

    <AppOverlay
      variant="drawer"
      :open="selected !== null"
      :title="selected?.title ?? ''"
      :subtitle="selected ? whenLine(selected) : ''"
      test-id="session-details"
      @close="selected = null"
    >
      <template v-if="selected">
        <div class="student-live-drawer-teacher">
          <PortraitAvatar :size="40" :name="selected.teacherName" />
          <span>
            <span class="type-caption">{{ t('studentClasses.live.drawerTeacher') }}</span>
            <strong>{{ selected.teacherName }}</strong>
          </span>
        </div>
        <div class="student-live-state">
          <StatusPill :label="t(SESSION_STATUS_KEYS[selected.status])" :tone="SESSION_STATUS_TONES[selected.status]" />
          <StatusPill v-if="selected.booked" :label="t('classes.session.booked')" tone="success" icon="check" />
        </div>
        <dl class="kv-list">
          <dt>{{ t('studentClasses.live.drawerClass') }}</dt>
          <dd>
            <RouterLink :to="{ name: 'student-class', params: { classId: selected.classId } }">{{
              selected.classTitle
            }}</RouterLink>
          </dd>
          <template v-if="selected.lessonId">
            <dt>{{ t('studentClasses.live.drawerLesson') }}</dt>
            <dd>
              <RouterLink
                :to="{
                  name: 'student-class-lesson',
                  params: { classId: selected.classId, lessonId: selected.lessonId },
                }"
                >{{ selected.lessonTitle }}</RouterLink
              >
            </dd>
          </template>
          <dt>{{ t('studentClasses.live.drawerWhen') }}</dt>
          <dd>{{ whenLine(selected) }}</dd>
          <dt>{{ t('studentClasses.live.drawerPlatform') }}</dt>
          <dd>{{ t(PLATFORM_KEYS[selected.platform]) }}</dd>
          <dt>{{ t('studentClasses.live.drawerSeats') }}</dt>
          <dd>
            {{
              selected.capacity
                ? t('studentClasses.live.seatsBooked', { booked: selected.bookedCount, capacity: selected.capacity })
                : t('studentClasses.live.seatsOpen', { booked: selected.bookedCount })
            }}
          </dd>
        </dl>
        <p v-if="selected.rescheduledFrom" class="type-caption">
          {{
            t('classes.session.movedFrom', {
              time: `${formatWeekdayDate(selected.rescheduledFrom)}, ${formatTime(selected.rescheduledFrom)}`,
            })
          }}
        </p>
        <p v-if="selected.booked && !selected.canJoin && selected.status !== 'COMPLETED'" class="callout is-compact">
          <IconGlyph name="clock" :size="16" />
          <span>{{ t('studentClasses.live.joinOpens', { time: formatTime(selected.joinOpensAt) }) }}</span>
        </p>
      </template>
      <template v-if="selected" #footer>
        <button
          v-if="selected.booked && canCancel(selected)"
          type="button"
          class="btn btn-danger-soft"
          @click="cancelTarget = selected"
        >
          {{ t('studentClasses.live.cancelBooking') }}
        </button>
        <button
          v-if="selected.booked && selected.canJoin"
          type="button"
          class="btn btn-primary"
          @click="join(selected)"
        >
          {{ t('studentClasses.live.join') }}
        </button>
        <button v-else-if="selected.canBook" type="button" class="btn btn-primary" @click="book(selected)">
          {{ t('studentClasses.live.book') }}
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="cancelTarget !== null"
      :title="t('studentClasses.live.cancelTitle')"
      :message="
        cancelTarget
          ? t('studentClasses.live.cancelMessage', {
              title: cancelTarget.title,
              day: formatWeekdayDate(cancelTarget.startsAt),
            })
          : ''
      "
      :confirm-label="t('studentClasses.live.cancelConfirm')"
      :cancel-label="t('studentClasses.live.keepBooking')"
      tone="danger"
      :busy="busyId !== null"
      @cancel="cancelTarget = null"
      @confirm="cancelBooking"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import SessionDateTile from '@/components/classes/SessionDateTile.vue'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { StudentSession } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import {
  PLATFORM_KEYS,
  SESSION_STATUS_KEYS,
  SESSION_STATUS_TONES,
  groupByDay,
  isWebLink,
  relativeDay,
} from '@/utils/classes'
import { formatShortDate, formatTime, formatWeekdayDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({
  studentClasses: studentClassesMessages,
  classCatalogue: classCatalogueMessages,
  classes: classesMessages,
})
const { actionError, actionSuccess, runAction, showError } = useActionFeedback()

const resource = useResource(() => studentClassesApi.liveSessions())
const sessions = computed(() => resource.data.value)
const recordingCount = computed(() => sessions.value?.past.filter((session) => session.recordingUrl).length ?? 0)

const upcomingDays = computed(() => groupByDay(sessions.value?.upcoming ?? []))
const pastDays = computed(() => groupByDay(sessions.value?.past ?? []))

const selected = ref<StudentSession | null>(null)
const cancelTarget = ref<StudentSession | null>(null)
const busyId = ref<number | null>(null)

/** "Scheduled" is the normal case and "Full" shows on the action, so only the other states get a pill. */
function showsStatus(session: StudentSession): boolean {
  if (session.status === 'RESCHEDULED') return !session.rescheduledFrom
  return session.status === 'CANCELLED' || session.status === 'COMPLETED'
}

function showsSeats(session: StudentSession): boolean {
  return session.seatsLeft !== null && session.canBook
}

function hasState(session: StudentSession): boolean {
  return (
    (session.booked && session.status !== 'CANCELLED') ||
    showsStatus(session) ||
    Boolean(session.rescheduledFrom && session.status !== 'CANCELLED') ||
    showsSeats(session)
  )
}

function whenLine(session: StudentSession): string {
  return t('studentClasses.live.whenLine', {
    day: formatWeekdayDate(session.startsAt),
    time: t('classes.session.timeRange', { start: formatTime(session.startsAt), end: formatTime(session.endsAt) }),
    platform: t(PLATFORM_KEYS[session.platform]),
  })
}

function canCancel(session: StudentSession): boolean {
  return session.status !== 'CANCELLED' && new Date(session.startsAt).getTime() > Date.now()
}

async function refresh(): Promise<void> {
  await resource.reload()
  if (selected.value) {
    const id = selected.value.id
    selected.value =
      [...(sessions.value?.upcoming ?? []), ...(sessions.value?.past ?? [])].find((s) => s.id === id) ?? null
  }
}

async function book(session: StudentSession): Promise<void> {
  busyId.value = session.id
  const done = await runAction(t('studentClasses.live.bookError'), async () => {
    await studentClassesApi.book(session.id)
    return t('studentClasses.live.booked', { title: session.title })
  })
  busyId.value = null
  if (done) await refresh()
}

async function cancelBooking(): Promise<void> {
  const session = cancelTarget.value
  if (!session) return
  busyId.value = session.id
  const done = await runAction(t('studentClasses.live.cancelError'), async () => {
    await studentClassesApi.cancelBooking(session.id)
    return t('studentClasses.live.cancelled', { title: session.title })
  })
  busyId.value = null
  cancelTarget.value = null
  if (done) await refresh()
}

/** Opens the meeting in a new tab; the server records attendance. The tab opens first so pop-up blockers allow it. */
async function join(session: StudentSession): Promise<void> {
  const tab = window.open('', '_blank')
  busyId.value = session.id
  try {
    const { meetingLink } = await studentClassesApi.joinSession(session.id)
    if (tab && isWebLink(meetingLink)) {
      tab.opener = null
      tab.location.href = meetingLink
    } else if (isWebLink(meetingLink)) {
      window.location.assign(meetingLink)
    }
    await refresh()
  } catch (reason) {
    tab?.close()
    showError(resolveApiError(reason, t('studentClasses.live.joinError')))
  } finally {
    busyId.value = null
  }
}
</script>
