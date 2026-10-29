<template>
  <div class="teacher-students-page page-stack">
    <PageHeaderBlock :title="t('teacherStudents.title')" :description="summary" />
    <TeacherStudentsTabs provides="students" :student-count="roster.data.value?.total ?? null" />
    <DataNotice :error="actionError" :success="actionSuccess" />

    <ResourceSection :loading="roster.loading.value" :error="roster.error.value" @retry="roster.reload">
      <StatePanel
        v-if="roster.data.value && !roster.data.value.total"
        tone="empty"
        icon="students"
        spacious
        :title="t('teacherStudents.emptyTitle')"
        :message="t('teacherStudents.emptyMessage')"
      />
      <div v-else class="teacher-students-layout">
        <aside class="teacher-roster" aria-labelledby="teacher-roster-title">
          <h2 id="teacher-roster-title" class="teacher-roster-title">
            <!-- The count is on the tab and the "All" filter already. -->
            {{ t('teacherStudents.myStudents') }}
          </h2>
          <div class="input-with-icon">
            <IconGlyph name="search" class="input-icon" :size="18" />
            <input
              v-model="search"
              class="input"
              type="search"
              maxlength="100"
              :aria-label="t('teacherStudents.search')"
              :placeholder="t('teacherStudents.search')"
            />
          </div>
          <SegmentedControl v-model="filter" :label="t('teacherStudents.filterLabel')" :options="filterOptions" />
          <p v-if="!visibleStudents.length" class="type-caption">{{ t('teacherStudents.noMatch') }}</p>
          <ul class="teacher-roster-rows">
            <li v-for="row in visibleStudents" :key="row.studentId">
              <button
                type="button"
                class="teacher-roster-row"
                :class="{ 'is-selected': row.studentId === selectedId }"
                :aria-pressed="row.studentId === selectedId"
                :data-testid="`roster-${row.studentId}`"
                @click="select(row.studentId)"
              >
                <PortraitAvatar :size="36" :name="row.name" :tone="row.needsSupport ? 'warning' : 'primary'" />
                <span class="teacher-roster-copy">
                  <span class="teacher-roster-top">
                    <strong>{{ row.name }}</strong>
                    <span class="teacher-roster-percent">{{ row.progressPercent }}%</span>
                  </span>
                  <span class="teacher-meter is-wide" aria-hidden="true">
                    <span :style="{ width: `${row.progressPercent}%` }" />
                  </span>
                  <span class="type-caption">
                    {{
                      row.quizAverage !== null
                        ? t('teacherStudents.rowFacts', { level: row.xpLevel, average: `${row.quizAverage}%` })
                        : t('teacherStudents.rowFactsNoQuiz', { level: row.xpLevel })
                    }}
                  </span>
                  <span v-if="row.needsSupport" class="teacher-roster-flag">
                    <IconGlyph name="alert" :size="14" />{{ t('teacherStudents.needsSupport') }}
                  </span>
                </span>
              </button>
            </li>
          </ul>
        </aside>

        <div class="teacher-dossier">
          <StatePanel
            v-if="!selectedId"
            tone="empty"
            icon="students"
            :title="t('teacherStudents.tabStudents')"
            :message="t('teacherStudents.selectStudent')"
          />
          <StatePanel
            v-else-if="detail.loading.value && !student"
            tone="loading"
            :title="t('classes.loading')"
            :message="t('teacherStudents.selectStudent')"
          />
          <StatePanel
            v-else-if="detail.error.value && !student"
            tone="error"
            icon="alert"
            :title="t('teacherStudents.detail.loadError')"
            :message="detail.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('classes.retry') }}</button>
            </template>
          </StatePanel>
          <article v-else-if="student" class="panel teacher-dossier-sheet" aria-labelledby="teacher-dossier-name">
            <header class="teacher-dossier-head">
              <PortraitAvatar :size="56" :name="student.name" :tone="student.needsSupport ? 'warning' : 'primary'" />
              <div class="teacher-dossier-who">
                <div class="teacher-dossier-name">
                  <h2 id="teacher-dossier-name" class="section-title">{{ student.name }}</h2>
                  <StatusPill
                    :label="student.needsSupport ? t('teacherStudents.needsSupport') : t('teacherStudents.onTrack')"
                    :tone="student.needsSupport ? 'warning' : 'success'"
                    :icon="student.needsSupport ? 'alert' : ''"
                  />
                </div>
                <p class="type-caption">
                  {{ t('teacherStudents.detail.facts', { level: student.xpLevel, classTitle: student.classTitle }) }}
                  ·
                  {{
                    student.lastActiveAt
                      ? t('teacherStudents.detail.lastActive', { date: formatShortDate(student.lastActiveAt) })
                      : t('teacherStudents.detail.neverActive')
                  }}
                </p>
                <div v-if="student.classes.length > 1" class="field teacher-dossier-class">
                  <label class="field-label" for="student-class">{{ t('teacherStudents.detail.classLabel') }}</label>
                  <select id="student-class" v-model="classId" class="select">
                    <option v-for="item in student.classes" :key="item.id" :value="item.id">{{ item.title }}</option>
                  </select>
                </div>
              </div>
              <button type="button" class="btn btn-primary" data-testid="write-feedback" @click="feedbackOpen = true">
                <IconGlyph name="edit" :size="16" />{{ t('teacherStudents.detail.writeFeedback') }}
              </button>
            </header>

            <dl class="teacher-figures-list teacher-dossier-figures">
              <div class="teacher-figure">
                <dt>{{ t('teacherStudents.detail.classProgress') }}</dt>
                <dd class="stat-figure">{{ student.progressPercent }}%</dd>
              </div>
              <div class="teacher-figure">
                <dt>{{ t('teacherStudents.detail.lessonsCompleted') }}</dt>
                <dd class="stat-figure">
                  {{
                    t('teacherStudents.detail.lessonsOf', {
                      done: student.lessonsCompleted,
                      total: student.lessonCount,
                    })
                  }}
                </dd>
              </div>
              <div class="teacher-figure">
                <dt>{{ t('teacherStudents.detail.mastered') }}</dt>
                <dd class="stat-figure">{{ student.mastered }}</dd>
              </div>
              <div class="teacher-figure">
                <dt>{{ t('teacherStudents.detail.quizAverage') }}</dt>
                <dd class="stat-figure">{{ student.quizAverage !== null ? `${student.quizAverage}%` : '—' }}</dd>
              </div>
            </dl>

            <TeacherGoalCard :student-id="student.studentId" :student-name="student.name" />

            <div class="teacher-dossier-grid">
              <section class="teacher-dossier-section" aria-labelledby="teacher-dossier-progress">
                <div>
                  <h3 id="teacher-dossier-progress" class="card-title">
                    {{ t('teacherStudents.detail.progressTitle') }}
                  </h3>
                  <p class="type-caption">
                    {{ t('teacherStudents.detail.progressFacts', { classTitle: student.classTitle }) }}
                  </p>
                </div>
                <p v-if="!startedLessons.length" class="type-caption">{{ t('teacherStudents.detail.noLessons') }}</p>
                <ul v-else class="teacher-dossier-lessons">
                  <li v-for="lesson in startedLessons" :key="lesson.lessonId">
                    <div class="teacher-dossier-line">
                      <strong>{{ lesson.title }}</strong>
                      <MasteryChip :mastery="lesson.status" />
                    </div>
                    <ProgressBar class="is-sm" :progress="lesson.percent" :label="lesson.title" />
                    <div class="teacher-dossier-line type-caption">
                      <span>{{
                        t('teacherStudents.detail.lessonLine', {
                          percent: lesson.percent,
                          video: videoText(lesson.videoWatched),
                        })
                      }}</span>
                      <span v-if="lesson.bestQuizPercent !== null">{{
                        t('teacherStudents.detail.bestQuiz', { percent: lesson.bestQuizPercent })
                      }}</span>
                    </div>
                  </li>
                </ul>
              </section>

              <section class="teacher-dossier-section" aria-labelledby="teacher-dossier-results">
                <div>
                  <h3 id="teacher-dossier-results" class="card-title">
                    {{ t('teacherStudents.detail.resultsTitle') }}
                  </h3>
                  <p class="type-caption">
                    {{
                      t('teacherStudents.detail.resultsFacts', {
                        attempts: t('teacherStudents.detail.attempts', student.quizResults.length),
                        average: student.quizAverage ?? 0,
                      })
                    }}
                  </p>
                </div>
                <p v-if="!student.quizResults.length" class="type-caption">
                  {{ t('teacherStudents.detail.noResults') }}
                </p>
                <div
                  v-else
                  class="data-table-wrap teacher-dossier-table"
                  role="region"
                  :aria-label="t('teacherStudents.detail.resultsTable', { name: student.name })"
                  tabindex="0"
                >
                  <table class="data-table">
                    <thead>
                      <tr>
                        <th scope="col">{{ t('teacherStudents.detail.quiz') }}</th>
                        <th scope="col" class="col-numeric">{{ t('teacherStudents.detail.date') }}</th>
                        <th scope="col" class="col-numeric">{{ t('teacherStudents.detail.marks') }}</th>
                        <th scope="col" class="col-numeric">{{ t('teacherStudents.detail.score') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="attempt in student.quizResults" :key="attempt.attemptId">
                        <td>{{ attempt.quizTitle }}</td>
                        <td class="col-numeric">{{ formatShortDate(attempt.completedAt) }}</td>
                        <td class="col-numeric">{{ attempt.score }} / {{ attempt.maxScore }}</td>
                        <td class="col-numeric">
                          <strong>{{ attempt.percent }}%</strong>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </div>

            <section class="teacher-dossier-section" aria-labelledby="teacher-dossier-weak">
              <div>
                <h3 id="teacher-dossier-weak" class="card-title">{{ t('teacherStudents.detail.weakTitle') }}</h3>
                <p class="type-caption">{{ t('teacherStudents.detail.weakFacts') }}</p>
              </div>
              <p v-if="!student.weakAreas.length" class="type-caption">{{ t('teacherStudents.detail.noWeak') }}</p>
              <ul v-else class="teacher-dossier-weak">
                <li v-for="area in student.weakAreas" :key="`${area.skill}-${area.quizId}`">
                  <span class="teacher-dossier-weak-score">{{
                    t('teacherStudents.detail.percentCorrect', { percent: area.percent })
                  }}</span>
                  <div class="teacher-dossier-weak-copy">
                    <strong>
                      {{ t(SKILL_KEYS[area.skill]) }}
                      <span v-if="area.examples.length" class="cjk-text" lang="zh-CN"
                        >({{ area.examples.join(', ') }})</span
                      >
                    </strong>
                    <span class="type-caption">
                      {{
                        t('teacherStudents.detail.weakLine', {
                          correct: area.correct,
                          total: area.total,
                          quiz: area.quizTitle,
                        })
                      }}
                      <template v-if="area.lastSeenAt">
                        · {{ t('teacherStudents.detail.lastSeen', { date: formatShortDate(area.lastSeenAt) }) }}
                      </template>
                    </span>
                  </div>
                </li>
              </ul>
            </section>

            <section class="teacher-dossier-section" aria-labelledby="teacher-dossier-feedback">
              <h3 id="teacher-dossier-feedback" class="card-title">{{ t('teacherStudents.detail.feedbackTitle') }}</h3>
              <p v-if="!student.feedback.length" class="type-caption">{{ t('teacherStudents.detail.noFeedback') }}</p>
              <FeedbackList v-else :rows="student.feedback" />
            </section>
          </article>
        </div>
      </div>
    </ResourceSection>

    <FeedbackDrawer
      :open="feedbackOpen"
      :title="t('teacherStudents.write.title')"
      :subtitle="
        student ? t('teacherStudents.write.subtitle', { name: student.name, classTitle: student.classTitle }) : ''
      "
      :targets="student?.feedbackTargets ?? []"
      :busy="sending"
      :error="sendError"
      @close="feedbackOpen = false"
      @save="sendFeedback"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { teacherStudentsApi } from '@/api/classes'
import SegmentedControl, { type SegmentedOption } from '@/components/common/SegmentedControl.vue'
import FeedbackDrawer, { type FeedbackDraft } from '@/components/classes/FeedbackDrawer.vue'
import FeedbackList from '@/components/classes/FeedbackList.vue'
import TeacherStudentsTabs from '@/components/classes/TeacherStudentsTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import MasteryChip from '@/components/common/MasteryChip.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import TeacherGoalCard from '@/components/plan/TeacherGoalCard.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import { resolveApiError } from '@/utils/apiError'
import { SKILL_KEYS } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import '@/styles/classes.css'
import '@/styles/plan.css'

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages, classes: classesMessages })
const route = useRoute()
const { actionError, actionSuccess, showSuccess } = useActionFeedback()

const roster = useResource(() => teacherStudentsApi.roster())
const summary = computed(() => {
  const data = roster.data.value
  if (!data?.total) return ''
  return [
    t('teacherStudents.summaryStudents', data.total),
    t('teacherStudents.summarySupport', data.needsSupportCount),
  ].join(' · ')
})
const search = ref('')
const filter = ref<'ALL' | 'SUPPORT'>('ALL')
const filterOptions = computed<SegmentedOption<'ALL' | 'SUPPORT'>[]>(() => [
  { value: 'ALL', label: t('teacherStudents.filterAll', { count: roster.data.value?.total ?? 0 }) },
  { value: 'SUPPORT', label: t('teacherStudents.filterSupport', { count: roster.data.value?.needsSupportCount ?? 0 }) },
])
const visibleStudents = computed(() => {
  const term = search.value.trim().toLowerCase()
  return (roster.data.value?.students ?? [])
    .filter((student) => filter.value === 'ALL' || student.needsSupport)
    .filter((student) => !term || student.name.toLowerCase().includes(term))
})

const selectedId = ref<number | null>(null)
const classId = ref<number | null>(null)
const detail = useResource(() => teacherStudentsApi.detail(selectedId.value!, classId.value), false)
const student = computed(() => (detail.data.value?.studentId === selectedId.value ? detail.data.value : null))
const startedLessons = computed(() => student.value?.lessons.filter((lesson) => lesson.status !== 'NOT_STARTED') ?? [])

function select(studentId: number): void {
  if (selectedId.value === studentId) return
  selectedId.value = studentId
  classId.value = null
  void detail.reload()
}

watch(classId, (next, previous) => {
  if (previous !== null && next !== null && next !== detail.data.value?.classId) void detail.reload()
})
watch(
  () => detail.data.value?.classId,
  (current) => {
    if (current) classId.value = current
  },
)

/** The dashboard links to a student with ?student=<id>; otherwise the first student is shown. */
watch(
  () => roster.data.value,
  (data) => {
    if (!data || selectedId.value) return
    const wanted = Number(route.query.student)
    const first = data.students.find((row) => row.studentId === wanted) ?? data.students[0]
    if (first) select(first.studentId)
  },
  { immediate: true },
)

function videoText(watched: boolean | null): string {
  if (watched === null) return t('teacherStudents.detail.noVideo')
  return watched ? t('teacherStudents.detail.videoWatched') : t('teacherStudents.detail.videoNotFinished')
}

// ----- writing feedback -----

const feedbackOpen = ref(false)
const sending = ref(false)
const sendError = ref<string | null>(null)

watch(feedbackOpen, () => {
  sendError.value = null
})

async function sendFeedback(draft: FeedbackDraft): Promise<void> {
  if (!student.value) return
  sending.value = true
  sendError.value = null
  try {
    await teacherStudentsApi.sendFeedback({
      studentId: student.value.studentId,
      classId: student.value.classId,
      type: draft.type,
      message: draft.message,
      quizAttemptId: draft.target?.kind === 'QUIZ_ATTEMPT' ? draft.target.id : null,
      lessonId: draft.target?.kind === 'LESSON' ? draft.target.id : null,
    })
    feedbackOpen.value = false
    showSuccess(t('teacherStudents.write.sent', { name: student.value.name }))
    await detail.reload()
  } catch (reason) {
    sendError.value = resolveApiError(reason, t('teacherStudents.write.error'))
  } finally {
    sending.value = false
  }
}
</script>
