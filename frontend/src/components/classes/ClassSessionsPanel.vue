<template>
  <section class="panel class-sessions" :aria-labelledby="headingId">
    <div class="class-card-head">
      <h2 :id="headingId" class="card-title">{{ t('studentClasses.classPage.sessionsTitle') }}</h2>
      <RouterLink
        class="btn btn-link"
        :to="{ name: 'student-live-sessions' }"
        :aria-label="t('studentClasses.classPage.allSessionsLabel')"
      >
        {{ t('studentClasses.classPage.allSessions') }}<IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
    </div>
    <p v-if="!sessions.length" class="type-caption">{{ t('studentClasses.classPage.noSessions') }}</p>
    <ul v-else class="row-list">
      <li v-for="session in sessions" :key="session.id">
        <RouterLink class="class-sessions-item" :to="{ name: 'student-live-sessions' }">
          <SessionDateTile :at="session.startsAt" />
          <span class="row-list-copy">
            <strong class="row-list-title"><CjkText :text="session.title" /></strong>
            <span class="row-list-meta">
              {{ formatShortDate(session.startsAt) }} ·
              {{
                t('classes.session.timeRange', { start: formatTime(session.startsAt), end: formatTime(session.endsAt) })
              }}
            </span>
          </span>
          <IconGlyph class="class-row-chevron" name="chevronRight" :size="16" />
        </RouterLink>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { SessionBrief } from '@/types/classes'
import { formatShortDate, formatTime } from '@/utils/format'

import SessionDateTile from './SessionDateTile.vue'

/** The right rail of a class: its upcoming live sessions, each opening the Live sessions page where they are joined. */
defineProps<{ sessions: readonly SessionBrief[] }>()

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const headingId = useId()
</script>
