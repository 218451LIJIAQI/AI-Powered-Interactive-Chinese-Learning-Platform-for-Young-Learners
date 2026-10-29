<template>
  <section class="class-sessions" :aria-labelledby="headingId">
    <div class="class-sessions-head">
      <h2 :id="headingId" class="class-sessions-title">{{ title }}</h2>
      <RouterLink
        class="btn btn-link"
        :to="{ name: 'student-live-sessions' }"
        :aria-label="t('studentClasses.classPage.allSessionsLabel')"
      >
        {{ t('studentClasses.classPage.allSessions') }}<IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
    </div>
    <p v-if="!sessions.length" class="class-sessions-empty">
      {{ empty || t('studentClasses.classPage.noSessions') }}
    </p>
    <ul v-else class="class-sessions-list">
      <li v-for="session in sessions" :key="session.id">
        <RouterLink
          class="class-sessions-item"
          :class="{ 'is-cancelled': session.status === 'CANCELLED' }"
          :to="{ name: 'student-live-sessions' }"
        >
          <SessionDateTile :at="session.startsAt" :muted="session.status === 'CANCELLED'" show-month />
          <span class="class-sessions-copy">
            <strong class="class-sessions-name">{{ session.title }}</strong>
            <span class="type-caption">
              <span class="sr-only">{{ formatWeekdayDate(session.startsAt) }} · </span>
              {{
                t('classes.session.timeRange', { start: formatTime(session.startsAt), end: formatTime(session.endsAt) })
              }}
              · {{ t(PLATFORM_KEYS[session.platform]) }}
            </span>
            <span v-if="session.booked || session.status !== 'SCHEDULED'" class="class-sessions-state">
              <StatusPill v-if="session.booked" :label="t('classes.session.booked')" tone="success" icon="check" />
              <StatusPill
                v-if="session.status !== 'SCHEDULED'"
                :label="t(SESSION_STATUS_KEYS[session.status])"
                :tone="SESSION_STATUS_TONES[session.status]"
              />
            </span>
          </span>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { SessionBrief } from '@/types/classes'
import { PLATFORM_KEYS, SESSION_STATUS_KEYS, SESSION_STATUS_TONES } from '@/utils/classes'
import { formatTime, formatWeekdayDate } from '@/utils/format'

import SessionDateTile from './SessionDateTile.vue'

/**
 * The timetable of a class or a lesson: upcoming live sessions under date tiles, each linking to the student's Live
 * sessions page where they are booked and joined.
 */
withDefaults(defineProps<{ sessions: readonly SessionBrief[]; title: string; empty?: string }>(), { empty: '' })

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const headingId = useId()
</script>
