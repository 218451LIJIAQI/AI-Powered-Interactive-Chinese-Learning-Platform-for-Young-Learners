<template>
  <div class="public-page pub-class-page">
    <template v-if="detail">
      <PageHeaderBlock :title="detail.summary.title" :description="detail.summary.description ?? ''">
        <template #before>
          <AppBreadcrumb
            :items="[{ label: t('classes.classes'), to: { name: 'classes' } }, { label: detail.summary.title }]"
          />
        </template>
        <template #meta>
          <span v-if="detail.summary.levelName" class="tag">{{ detail.summary.levelName }}</span>
          <span v-if="detail.summary.topicName" class="tag">{{ detail.summary.topicName }}</span>
          <span v-if="detail.summary.learningType" class="tag">{{
            t(LEARNING_TYPE_KEYS[detail.summary.learningType])
          }}</span>
        </template>
      </PageHeaderBlock>

      <div class="pub-class-grid">
        <!-- Teacher first: who teaches the class, then how to start it. Beside the syllabus on wide screens. -->
        <aside class="pub-class-aside" :aria-label="t('classCatalogue.detail.asideLabel')">
          <section class="pub-class-teacher" aria-labelledby="pub-class-teacher-title">
            <h2 id="pub-class-teacher-title" class="page-eyebrow">{{ t('classCatalogue.detail.aboutTeacher') }}</h2>
            <div class="pub-class-teacher-head">
              <PortraitAvatar :size="56" :name="detail.summary.teacher.name" />
              <div>
                <strong class="pub-class-teacher-name">{{ detail.summary.teacher.name }}</strong>
                <span v-if="detail.summary.teacher.verified" class="verified-mark">
                  <IconGlyph name="verified" :size="14" />{{ t('classCatalogue.detail.verifiedTeacher') }}
                </span>
              </div>
            </div>
            <p v-if="detail.summary.teacher.bio" class="pub-class-teacher-bio">{{ detail.summary.teacher.bio }}</p>
            <figure v-if="detail.teacherNote" class="pub-class-note">
              <blockquote>
                <p>{{ detail.teacherNote }}</p>
              </blockquote>
              <figcaption>{{ t('classCatalogue.detail.noteFrom', { name: detail.summary.teacher.name }) }}</figcaption>
            </figure>
          </section>

          <section class="panel pub-class-cta" aria-labelledby="pub-class-cta-title">
            <h2 id="pub-class-cta-title" class="card-title">{{ t('classCatalogue.detail.ctaTitle') }}</h2>
            <p class="pub-class-cta-facts">
              <strong>{{ t('classCatalogue.detail.free') }}</strong>
              · {{ t('classes.units', detail.summary.unitCount) }} ·
              {{ t('classes.lessons', detail.summary.lessonCount) }}
            </p>
            <template v-if="isStudent">
              <p class="card-subtitle">{{ t('classCatalogue.detail.ctaSignedIn') }}</p>
              <RouterLink
                class="btn btn-primary btn-block"
                :to="{ name: 'student-class', params: { classId: detail.summary.id } }"
              >
                {{ t('classCatalogue.detail.openClass') }}
              </RouterLink>
            </template>
            <template v-else>
              <p class="card-subtitle">{{ t('classCatalogue.detail.ctaText') }}</p>
              <RouterLink
                class="btn btn-primary btn-block"
                :to="{ path: '/auth', query: { tab: 'register', role: 'student' } }"
              >
                {{ t('classCatalogue.detail.start') }}
              </RouterLink>
              <RouterLink
                class="btn btn-secondary btn-block"
                :to="{ path: '/auth', query: { tab: 'signin', redirect: studentClassPath } }"
              >
                {{ t('classCatalogue.detail.signInToJoin') }}
              </RouterLink>
            </template>
            <h3 class="pub-class-expect-title">{{ t('classCatalogue.detail.whatToExpect') }}</h3>
            <ul class="pub-class-expect">
              <li><IconGlyph name="video" :size="16" />{{ t('classCatalogue.detail.expectLessons') }}</li>
              <li><IconGlyph name="quiz" :size="16" />{{ t('classCatalogue.detail.expectQuiz') }}</li>
              <li><IconGlyph name="live" :size="16" />{{ t('classCatalogue.detail.expectLive') }}</li>
              <li><IconGlyph name="check" :size="16" />{{ t('classCatalogue.detail.expectFree') }}</li>
            </ul>
          </section>
        </aside>

        <div class="pub-class-main">
          <section class="pub-class-syllabus" aria-labelledby="pub-class-units-title">
            <div class="section-header">
              <div>
                <h2 id="pub-class-units-title" class="section-title">
                  {{ t('classCatalogue.detail.unitsAndLessons') }}
                </h2>
                <p class="section-description">{{ t('classCatalogue.detail.unitsHint') }}</p>
              </div>
            </div>
            <p v-if="!detail.units.length" class="type-caption">{{ t('classCatalogue.detail.noLessons') }}</p>
            <div v-for="(unit, unitIndex) in detail.units" :key="unit.id" class="pub-class-unit">
              <p class="pub-class-unit-number" aria-hidden="true">
                {{ t('classCatalogue.detail.unitNumber', { number: unitIndex + 1 }) }}
              </p>
              <h3 class="pub-class-unit-title">{{ unit.title }}</h3>
              <!-- Lesson numbers are CSS counters, so each row's text starts with the lesson title. -->
              <ol class="pub-class-unit-lessons">
                <li v-for="lesson in unit.lessons" :key="lesson.id" class="pub-class-unit-lesson">
                  <strong>{{ lesson.title }}</strong>
                  <span class="type-caption">{{
                    t('classCatalogue.detail.lessonFacts', {
                      activities: t('classes.activities', lesson.activityCount),
                      quizzes: t('classCatalogue.detail.quizzes', lesson.quizCount),
                    })
                  }}</span>
                </li>
              </ol>
            </div>
          </section>

          <section class="pub-class-sessions" aria-labelledby="pub-class-sessions-title">
            <h2 id="pub-class-sessions-title" class="section-title">
              {{ t('classCatalogue.detail.upcomingSessions') }}
            </h2>
            <p v-if="!detail.liveSessions.length" class="pub-class-sessions-empty">
              {{ t('classCatalogue.detail.noSessions') }}
            </p>
            <ol v-else class="pub-class-timetable">
              <li v-for="session in detail.liveSessions" :key="session.id" class="pub-class-slot">
                <SessionDateTile :at="session.startsAt" :muted="session.status === 'FULL'" />
                <div class="pub-class-slot-copy">
                  <strong>{{ session.title }}</strong>
                  <span class="type-caption">
                    {{ formatWeekdayDate(session.startsAt) }} ·
                    {{
                      t('classes.session.timeRange', {
                        start: formatTime(session.startsAt),
                        end: formatTime(session.endsAt),
                      })
                    }}
                    · {{ t(PLATFORM_KEYS[session.platform]) }}
                  </span>
                  <span v-if="session.lessonTitle" class="type-caption">
                    {{ t('classCatalogue.detail.sessionLesson', { lesson: session.lessonTitle }) }}
                  </span>
                </div>
                <span v-if="session.status !== 'SCHEDULED'">
                  <StatusPill
                    :label="t(SESSION_STATUS_KEYS[session.status])"
                    :tone="SESSION_STATUS_TONES[session.status]"
                  />
                </span>
              </li>
            </ol>
          </section>
        </div>
      </div>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('classCatalogue.title') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classCatalogue.detail.unitsHint')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('classCatalogue.detail.loadErrorTitle')"
        :message="resource.error.value ?? t('classCatalogue.detail.notFound')"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'classes' }">
            {{ t('classCatalogue.detail.backToClasses') }}
          </RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { publicClassesApi } from '@/api/classes'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import SessionDateTile from '@/components/classes/SessionDateTile.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { useAuthStore } from '@/stores/auth'
import { LEARNING_TYPE_KEYS, PLATFORM_KEYS, SESSION_STATUS_KEYS, SESSION_STATUS_TONES } from '@/utils/classes'
import { formatTime, formatWeekdayDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })
const route = useRoute()
const auth = useAuthStore()

const classId = computed(() => Number(route.params.classId))
const resource = useResource(() => publicClassesApi.detail(classId.value))
const detail = computed(() => resource.data.value)
const isStudent = computed(() => auth.isAuthenticated && auth.hasRole('STUDENT'))
const studentClassPath = computed(() => `/student/classes/${classId.value}`)
</script>
