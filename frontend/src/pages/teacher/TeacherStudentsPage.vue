<template>
  <div class="page-stack teacher-students">
    <PageHeaderBlock :title="t('teacherStudents.title')" hide-title />
    <AppToast :message="toast" />

    <ResourceSection :loading="roster.loading.value" :error="roster.error.value" @retry="roster.reload">
      <!-- Nothing yet: the page is one empty card with the next step. -->
      <section v-if="roster.data.value && !roster.data.value.total" class="panel teacher-empty-page">
        <StatePanel
          tone="empty"
          icon="students"
          spacious
          :title="t('teacherStudents.emptyTitle')"
          :message="t('teacherStudents.emptyMessage')"
        >
          <template #actions>
            <RouterLink class="btn btn-primary" :to="{ name: 'teacher-classes' }">
              {{ t('teacherStudents.goToClasses') }}
            </RouterLink>
          </template>
        </StatePanel>
      </section>

      <div v-else class="list-detail">
        <aside class="panel teacher-card" aria-labelledby="teacher-roster-title">
          <div class="title-with-count">
            <h2 id="teacher-roster-title" class="card-title">{{ t('teacherStudents.myStudents') }}</h2>
            <span class="count-badge">{{ roster.data.value?.total }}</span>
          </div>
          <FilterChips v-model="filter" :label="t('teacherStudents.filterLabel')" :options="filterOptions" />
          <p v-if="!visibleStudents.length" class="type-caption">{{ t('teacherStudents.noMatch') }}</p>
          <ul v-else class="teacher-roster">
            <li v-for="row in visibleStudents" :key="row.studentId">
              <button
                type="button"
                class="select-card teacher-roster-row"
                :aria-pressed="row.studentId === selectedId"
                :data-testid="`roster-${row.studentId}`"
                @click="select(row.studentId)"
              >
                <PortraitAvatar :size="36" :name="row.name" />
                <span class="teacher-roster-copy">
                  <span class="teacher-roster-top">
                    <span class="row-list-title">{{ row.name }}</span>
                    <span class="row-list-title">{{ row.progressPercent }}%</span>
                  </span>
                  <span class="row-list-meta">
                    {{
                      row.quizAverage !== null
                        ? t('teacherStudents.quizAverage', { average: `${row.quizAverage}%` })
                        : t('teacherStudents.noQuiz')
                    }}
                  </span>
                  <StatusPill
                    :label="row.needsSupport ? t('teacherStudents.needsSupport') : t('teacherStudents.onTrack')"
                    :tone="row.needsSupport ? 'strong' : 'neutral'"
                  />
                </span>
              </button>
            </li>
          </ul>
        </aside>

        <div class="page-stack">
          <section v-if="!selectedId" class="panel">
            <StatePanel
              tone="empty"
              icon="students"
              :title="t('teacherStudents.title')"
              :message="t('teacherStudents.selectStudent')"
            />
          </section>
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
          <template v-else-if="student">
            <section class="panel teacher-card" aria-labelledby="teacher-student-name">
              <header class="teacher-student-head">
                <PortraitAvatar :size="56" :name="student.name" />
                <div class="teacher-student-who">
                  <div class="teacher-title-row">
                    <h2 id="teacher-student-name" class="section-title">{{ student.name }}</h2>
                    <StatusPill
                      :label="student.needsSupport ? t('teacherStudents.needsSupport') : t('teacherStudents.onTrack')"
                      :tone="student.needsSupport ? 'strong' : 'neutral'"
                    />
                  </div>
                  <p class="type-caption">{{ student.classTitle }}</p>
                  <p class="type-caption">
                    {{
                      student.lastActiveAt
                        ? t('teacherStudents.detail.lastActive', { date: formatShortDate(student.lastActiveAt) })
                        : t('teacherStudents.detail.neverActive')
                    }}
                  </p>
                  <div v-if="student.classes.length > 1" class="field teacher-student-class">
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
              <dl class="teacher-figures">
                <div class="teacher-figure">
                  <dt>{{ t('teacherStudents.detail.classProgress') }}</dt>
                  <dd>{{ student.progressPercent }}%</dd>
                </div>
                <div class="teacher-figure">
                  <dt>{{ t('teacherStudents.detail.lessonsCompleted') }}</dt>
                  <dd>
                    {{
                      t('teacherStudents.detail.lessonsOf', {
                        done: student.lessonsCompleted,
                        total: student.lessonCount,
                      })
                    }}
                  </dd>
                </div>
                <div class="teacher-figure">
                  <dt>{{ t('teacherStudents.detail.quizAverage') }}</dt>
                  <dd>{{ student.quizAverage !== null ? `${student.quizAverage}%` : '—' }}</dd>
                </div>
              </dl>
            </section>

            <section class="panel teacher-card" aria-labelledby="teacher-student-progress">
              <h3 id="teacher-student-progress" class="card-title">{{ t('teacherStudents.detail.progressTitle') }}</h3>
              <p v-if="!startedLessons.length" class="type-caption">{{ t('teacherStudents.detail.noLessons') }}</p>
              <ul v-else class="row-list teacher-student-lessons">
                <li v-for="lesson in startedLessons" :key="lesson.lessonId">
                  <div class="teacher-student-line">
                    <strong class="teacher-student-lesson">{{ lesson.title }}</strong>
                    <StatusPill
                      :label="
                        lesson.status === 'IN_PROGRESS'
                          ? t('teacherStudents.detail.inProgress')
                          : t('teacherStudents.detail.completed')
                      "
                      :tone="lesson.status === 'IN_PROGRESS' ? 'info' : 'success'"
                    />
                  </div>
                  <ProgressBar class="is-sm" :progress="lesson.percent" :label="lesson.title" />
                  <div class="teacher-student-line row-list-meta">
                    <span>{{ t('teacherStudents.detail.percentComplete', { percent: lesson.percent }) }}</span>
                    <span v-if="lesson.bestQuizPercent !== null">{{
                      t('teacherStudents.detail.bestQuiz', { percent: lesson.bestQuizPercent })
                    }}</span>
                  </div>
                </li>
              </ul>
            </section>

            <section class="panel teacher-card" aria-labelledby="teacher-student-feedback">
              <h3 id="teacher-student-feedback" class="card-title">{{ t('teacherStudents.detail.feedbackTitle') }}</h3>
              <p v-if="!student.feedback.length" class="type-caption">{{ t('teacherStudents.detail.noFeedback') }}</p>
              <FeedbackList v-else :rows="student.feedback" />
            </section>
          </template>
        </div>
      </div>
    </ResourceSection>

    <FeedbackDrawer
      :open="feedbackOpen"
      :title="t('teacherStudents.write.title')"
      :subtitle="feedbackSubtitle"
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
import { RouterLink, useRoute } from 'vue-router'

import { teacherStudentsApi } from '@/api/classes'
import FeedbackDrawer, { type FeedbackDraft } from '@/components/classes/FeedbackDrawer.vue'
import FeedbackList from '@/components/classes/FeedbackList.vue'
import AppToast from '@/components/common/AppToast.vue'
import FilterChips, { type FilterChipOption } from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import '@/styles/classes-teacher.css'

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages, classes: classesMessages })
const route = useRoute()
const { toast, showToast } = useToast()

const roster = useResource(() => teacherStudentsApi.roster())
const filter = ref<'ALL' | 'SUPPORT'>('ALL')
const filterOptions = computed<FilterChipOption<'ALL' | 'SUPPORT'>[]>(() => [
  { value: 'ALL', label: t('teacherStudents.filterAll'), count: roster.data.value?.total ?? 0 },
  { value: 'SUPPORT', label: t('teacherStudents.filterSupport'), count: roster.data.value?.needsSupportCount ?? 0 },
])
const visibleStudents = computed(() =>
  (roster.data.value?.students ?? []).filter((student) => filter.value === 'ALL' || student.needsSupport),
)

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

// ----- writing feedback -----

const feedbackOpen = ref(false)
const sending = ref(false)
const sendError = ref<string | null>(null)

/** The class is only named when the student is in several of the teacher's classes. */
const feedbackSubtitle = computed(() => {
  const current = student.value
  if (!current) return ''
  return current.classes.length > 1
    ? t('teacherStudents.write.subtitleClass', { name: current.name, classTitle: current.classTitle })
    : t('teacherStudents.write.subtitle', { name: current.name })
})

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
    showToast(t('teacherStudents.write.sent', { name: student.value.name }))
    await detail.reload()
  } catch (reason) {
    sendError.value = resolveApiError(reason, t('teacherStudents.write.error'))
  } finally {
    sending.value = false
  }
}
</script>
