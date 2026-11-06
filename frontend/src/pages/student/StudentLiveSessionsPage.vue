<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('classCatalogue.tabs.live')" hide-title />
    <StudentClassesTabs />
    <DataNotice :error="actionError" />

    <ResourceSection
      class="page-stack"
      :loading="resource.loading.value"
      :error="resource.error.value"
      @retry="resource.reload"
    >
      <template v-if="sessions">
        <div v-if="!sessions.upcoming.length && !sessions.past.length" class="panel student-classes-empty">
          <StatePanel
            tone="empty"
            icon="live"
            :title="t('studentClasses.live.emptyTitle')"
            :message="t('studentClasses.live.emptyMessage')"
          >
            <template #actions>
              <RouterLink class="btn btn-primary" :to="{ name: 'student-classes-find' }">
                {{ t('classCatalogue.findClasses') }}
              </RouterLink>
            </template>
          </StatePanel>
        </div>

        <template v-else>
          <section class="panel student-live-card" aria-labelledby="live-upcoming-title">
            <div class="class-card-head">
              <h2 id="live-upcoming-title" class="card-title">{{ t('studentClasses.live.upcoming') }}</h2>
              <span class="type-caption">{{ t('studentClasses.live.sessions', activeCount) }}</span>
            </div>
            <p v-if="!sessions.upcoming.length" class="type-caption">{{ t('studentClasses.live.noUpcoming') }}</p>
            <ul v-else class="row-list">
              <li
                v-for="session in sessions.upcoming"
                :key="session.id"
                class="student-live-row"
                :data-testid="`session-${session.id}`"
              >
                <SessionDateTile :at="session.startsAt" />
                <div class="row-list-copy">
                  <strong class="row-list-title"><CjkText :text="session.title" /></strong>
                  <span class="row-list-meta">{{ session.classTitle }}</span>
                  <span class="row-list-meta">{{ whenLine(session) }}</span>
                  <span v-if="session.rescheduledFrom && !isCancelled(session)" class="row-list-meta">
                    {{ t('classes.session.movedFrom', { time: movedFrom(session.rescheduledFrom) }) }}
                  </span>
                  <span v-if="joinNote(session)" class="student-live-note">
                    <IconGlyph name="clock" :size="14" />
                    {{ t('studentClasses.live.joinOpens', { time: formatTime(session.joinOpensAt) }) }}
                  </span>
                </div>
                <div class="row-list-actions">
                  <StatusPill v-if="isCancelled(session)" :label="t('classes.session.CANCELLED')" tone="outline" />
                  <!-- The session is full and she has no seat: Join could only fail, so the row says so instead. -->
                  <StatusPill v-else-if="isFullForHer(session)" :label="t('classes.session.FULL')" tone="outline" />
                  <button
                    v-else-if="showsJoin(session)"
                    type="button"
                    class="btn btn-sm"
                    :class="windowOpen(session) ? 'btn-primary' : 'btn-secondary'"
                    :disabled="!windowOpen(session) || busyId === session.id"
                    :aria-busy="busyId === session.id"
                    :aria-label="t('studentClasses.live.joinLabel', { title: session.title })"
                    @click="join(session)"
                  >
                    <IconGlyph name="video" :size="16" />{{ t('studentClasses.live.join') }}
                  </button>
                </div>
              </li>
            </ul>
          </section>

          <section class="panel student-live-card" aria-labelledby="live-past-title">
            <div class="class-card-head">
              <h2 id="live-past-title" class="card-title">{{ t('studentClasses.live.past') }}</h2>
              <span v-if="recordingCount" class="type-caption">
                {{ t('studentClasses.live.recordings', recordingCount) }}
              </span>
            </div>
            <p v-if="!sessions.past.length" class="type-caption">{{ t('studentClasses.live.noPast') }}</p>
            <ul v-else class="row-list">
              <li
                v-for="session in sessions.past"
                :key="session.id"
                class="student-live-row"
                :data-testid="`session-${session.id}`"
              >
                <SessionDateTile :at="session.startsAt" />
                <div class="row-list-copy">
                  <strong class="row-list-title"><CjkText :text="session.title" /></strong>
                  <span class="row-list-meta">{{ session.classTitle }}</span>
                  <span class="row-list-meta">{{ whenLine(session) }}</span>
                </div>
                <div class="row-list-actions">
                  <a
                    v-if="session.recordingUrl && isWebLink(session.recordingUrl)"
                    class="btn btn-secondary btn-sm"
                    :href="session.recordingUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    :aria-label="`${t('studentClasses.live.recordingLabel', { title: session.title })} ${t('classes.opensInNewTab')}`"
                  >
                    <IconGlyph name="play" :size="16" />{{ t('studentClasses.live.watchRecording') }}
                  </a>
                </div>
              </li>
            </ul>
          </section>
        </template>
      </template>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import SessionDateTile from '@/components/classes/SessionDateTile.vue'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import CjkText from '@/components/learning/CjkText.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { StudentSession } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { isWebLink, relativeDay } from '@/utils/classes'
import { formatTime, formatWeekdayDate } from '@/utils/format'
import '@/styles/classes.css'

/** How often the page looks at the clock, so Join turns on by itself when the window opens. */
const CLOCK_TICK_MS = 30_000

const { t } = useLocalMessages({
  studentClasses: studentClassesMessages,
  classCatalogue: classCatalogueMessages,
  classes: classesMessages,
})
const { actionError, showError, clearFeedback } = useActionFeedback()

const resource = useResource(() => studentClassesApi.liveSessions())
const sessions = computed(() => resource.data.value)
const activeCount = computed(() => sessions.value?.upcoming.filter((session) => !isCancelled(session)).length ?? 0)
const recordingCount = computed(() => sessions.value?.past.filter((session) => session.recordingUrl).length ?? 0)
const busyId = ref<number | null>(null)

const now = ref(Date.now())
const clock = setInterval(() => (now.value = Date.now()), CLOCK_TICK_MS)
onBeforeUnmount(() => clearInterval(clock))

function isCancelled(session: StudentSession): boolean {
  return session.status === 'CANCELLED'
}

function isFullForHer(session: StudentSession): boolean {
  return !session.booked && session.status === 'FULL'
}

/** Joining is possible from 15 minutes before the start until the end (the server checks this too). */
function windowOpen(session: StudentSession): boolean {
  return now.value >= new Date(session.joinOpensAt).getTime() && now.value <= new Date(session.endsAt).getTime()
}

/** A session she can get into shows Join on its day; later days are only a timetable. */
function showsJoin(session: StudentSession): boolean {
  if (!session.booked && !session.canBook) return false
  return relativeDay(session.startsAt) === 'today' || windowOpen(session)
}

function joinNote(session: StudentSession): boolean {
  return !isCancelled(session) && showsJoin(session) && !windowOpen(session)
}

function whenLine(session: StudentSession): string {
  return t('studentClasses.live.whenLine', {
    day: formatWeekdayDate(session.startsAt),
    time: t('classes.session.timeRange', { start: formatTime(session.startsAt), end: formatTime(session.endsAt) }),
  })
}

function movedFrom(previous: string): string {
  return `${formatWeekdayDate(previous)}, ${formatTime(previous)}`
}

/**
 * Join is one click: the server only lets a booked student in, so a seat is booked first when she has none. Opens the
 * meeting in a new tab (opened first, so pop-up blockers allow it); the server records attendance.
 */
async function join(session: StudentSession): Promise<void> {
  const tab = window.open('', '_blank')
  busyId.value = session.id
  clearFeedback()
  try {
    if (!session.booked) await studentClassesApi.book(session.id)
    const { meetingLink } = await studentClassesApi.joinSession(session.id)
    if (tab && isWebLink(meetingLink)) {
      tab.opener = null
      tab.location.href = meetingLink
    } else if (isWebLink(meetingLink)) {
      window.location.assign(meetingLink)
    }
  } catch (reason) {
    tab?.close()
    showError(resolveApiError(reason, t('studentClasses.live.joinError')))
  } finally {
    busyId.value = null
    await resource.reload()
  }
}
</script>
