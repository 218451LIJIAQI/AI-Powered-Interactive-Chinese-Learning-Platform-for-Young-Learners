<template>
  <div class="page-stack student-home-page">
    <DataNotice
      :using-fallback="usingFallback"
      :error="pageError"
      fallback-title="Preview learning dashboard"
      fallback-message="Showing safe sample lesson, progress, and reward data for this preview session."
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-home-layout">
        <div class="student-home-main">
          <article class="student-hero-card">
            <div class="student-hero-copy">
              <div class="chip student-hero-chip">
                <IconGlyph name="learn" :size="16" />
                Current Lesson
              </div>

              <div class="student-hero-progress-strip">
                <article v-for="metric in heroMetrics" :key="metric.label" class="student-hero-progress-card">
                  <strong>{{ metric.value }}</strong>
                  <span>{{ metric.label }}</span>
                </article>
              </div>

              <h2>{{ displayLessonTitle }}</h2>
              <p>{{ displayNextStep }}</p>

              <button class="btn btn-accent student-hero-cta" type="button" @click="continueLearning">
                Continue Learning
                <span class="student-hero-cta-arrow"><IconGlyph name="chevronRight" :size="18" /></span>
              </button>
            </div>

            <div class="student-hero-side">
              <article class="student-hero-focus-card">
                <div class="student-hero-focus-head">
                  <IconGlyph name="language" :size="16" />
                  <span>Warm-up words</span>
                </div>
                <div class="student-hero-word-grid">
                  <span v-for="word in heroWords" :key="word.hanzi" class="student-hero-word-chip">
                    <strong>{{ word.hanzi }}</strong>
                    <small>{{ word.pinyin }}</small>
                  </span>
                </div>
              </article>

              <article class="student-hero-mini-goal">
                <span class="student-hero-mini-icon"><IconGlyph name="trophy" :size="16" /></span>
                <div>
                  <strong>Today’s focus</strong>
                  <small>{{ heroFocusText }}</small>
                </div>
              </article>

              <div class="student-hero-tone-lane" aria-hidden="true">
                <span v-for="tone in heroToneMarks" :key="tone" />
              </div>

              <div class="student-hero-mascot">
                <PandaIllustration :size="128" outfit="#7e52ff" accent="#f6d056" pose="wave" />
              </div>

              <span class="student-hero-character student-hero-character--one" aria-hidden="true">汉</span>
              <span class="student-hero-character student-hero-character--two" aria-hidden="true">拼</span>
            </div>

            <div class="student-hero-checklist">
              <strong>Lesson checklist:</strong>
              <div
                v-for="item in checklist"
                :key="item.title"
                class="student-hero-check"
                :class="{ 'student-hero-check--muted': !item.done }"
              >
                <IconGlyph :name="item.done ? 'status' : 'history'" :size="16" />
                <span>{{ item.title }}</span>
              </div>
            </div>
          </article>

          <div class="student-home-duo student-home-duo--primary">
            <article class="panel student-path-card">
              <div class="row student-home-section-head">
                <div class="media-badge small"><IconGlyph name="progress" :size="18" /></div>
                <div class="stack student-home-title-stack">
                  <strong>My Learning Path</strong>
                </div>
              </div>

              <div class="student-path-board">
                <div class="student-path-grid">
                  <article
                    v-for="node in pathNodes"
                    :key="node.id"
                    class="student-path-node"
                    :class="{ 'is-complete': node.complete, 'is-current': node.current }"
                    tabindex="0"
                    role="button"
                    :aria-label="`Learning step ${node.id}: ${node.title}. Status: ${node.complete ? 'Complete' : node.current ? 'Current' : 'Locked'}.`"
                    @click="clickNode(node)"
                    @keydown.enter.prevent="clickNode(node)"
                    @keydown.space.prevent="clickNode(node)"
                  >
                    <span
                      v-if="node.complete || node.current"
                      class="student-path-node-badge"
                      :class="node.complete ? 'is-check' : 'is-current'"
                    >
                      <IconGlyph :name="node.complete ? 'status' : 'flash'" :size="14" />
                    </span>
                    <div class="student-path-icon" :class="`is-${node.variant}`">
                      <IconGlyph :name="node.icon" :size="28" />
                    </div>
                    <strong>{{ node.id }}. {{ node.title }}</strong>
                    <div class="student-path-progress"><span :style="{ width: node.progressPercent + '%' }" /></div>
                    <div class="student-path-status">{{ node.statusLabel }}</div>
                  </article>
                </div>
              </div>
            </article>

            <article class="panel student-tools-card">
              <div class="row student-home-section-head">
                <div class="media-badge small"><IconGlyph name="practice" :size="18" /></div>
                <div class="stack student-home-title-stack">
                  <strong>Practice Tools</strong>
                </div>
              </div>

              <div class="student-tools-grid">
                <button
                  v-for="tool in practiceTools"
                  :key="tool.title"
                  class="student-tool-button"
                  :class="{ 'student-tool-button--wide': tool.wide }"
                  type="button"
                  @click="router.push(tool.to)"
                >
                  <span class="student-tool-icon" :style="{ '--tool-color': tool.color }">
                    <IconGlyph :name="tool.icon" :size="24" />
                  </span>
                  <span>{{ tool.title }}</span>
                </button>
              </div>
            </article>
          </div>

          <div class="student-home-duo student-home-duo--practice">
            <article class="panel student-class-card">
              <div class="row-between student-home-section-head student-home-section-head--compact">
                <div class="row">
                  <div class="media-badge small"><IconGlyph name="calendar" :size="18" /></div>
                  <strong>Live Practice</strong>
                </div>
              </div>
              <div class="student-class-body">
                <div class="student-class-copy">
                  <div class="stack student-home-copy-stack student-home-copy-stack--wide">
                    <strong>Live speaking practice</strong>
                    <span class="muted">Join the next teacher-guided session.</span>
                  </div>
                  <button
                    class="btn btn-primary student-class-button"
                    type="button"
                    @click="router.push('/student/classes')"
                  >
                    View Classes
                  </button>
                </div>
                <div class="student-class-scene">
                  <div class="student-class-board">Hello!</div>
                  <div class="student-class-table" />
                  <PandaIllustration
                    class="student-class-teacher"
                    :size="92"
                    outfit="#7e52ff"
                    accent="#f6d056"
                    compact
                  />
                </div>
              </div>
            </article>

            <article class="panel student-recorded-card">
              <div class="row-between student-home-section-head student-home-section-head--compact">
                <div class="row">
                  <div class="media-badge small"><IconGlyph name="play" :size="18" /></div>
                  <strong>Recorded Lesson</strong>
                </div>
              </div>
              <div class="student-recorded-body">
                <div class="student-recorded-copy">
                  <div class="stack student-home-copy-stack">
                    <strong>{{ displayLessonTitle }}</strong>
                    <span class="muted">Review this unit and reinforce recent words.</span>
                  </div>
                  <button class="btn btn-secondary student-watch-button" type="button" @click="continueLearning">
                    Watch Now
                  </button>
                </div>
                <div class="student-recorded-preview">
                  <PandaIllustration
                    class="student-recorded-scene"
                    :size="88"
                    outfit="#7e52ff"
                    accent="#f6d056"
                    compact
                  />
                  <div class="student-recorded-duration">{{ nextLessonDuration }}</div>
                </div>
              </div>
            </article>
          </div>
        </div>

        <aside class="student-home-side">
          <article class="panel student-side-card">
            <div class="row student-home-section-head">
              <div class="media-badge small"><IconGlyph name="progress" :size="18" /></div>
              <strong>My Progress</strong>
            </div>
            <ProgressRing :progress="overallProgress" title="Course Progress" subtitle="Keep the steady rhythm." />
          </article>

          <article class="panel student-side-card">
            <div class="row-between student-home-section-head">
              <div class="row">
                <div class="media-badge small"><IconGlyph name="trophy" :size="18" /></div>
                <strong>Learning Goals</strong>
              </div>
              <span class="muted student-goal-reset">Live progress</span>
            </div>
            <div class="student-goal-list">
              <div v-for="goal in goals" :key="goal.title" class="student-goal-item">
                <div class="row">
                  <span class="student-goal-icon"><IconGlyph :name="goal.icon" :size="18" /></span>
                  <span>{{ goal.title }}</span>
                </div>
                <strong>{{ goal.count }}</strong>
                <ProgressBar :progress="goal.progress" :tone="goal.tone" />
                <span class="student-goal-status" :class="{ 'is-done': goal.done }">
                  <IconGlyph :name="goal.done ? 'status' : 'history'" :size="15" />
                </span>
              </div>
            </div>
          </article>

          <article class="panel student-reward-bar">
            <div v-for="reward in rewards" :key="reward.label" class="student-reward-item">
              <span class="student-reward-icon"><IconGlyph :name="reward.icon" :size="24" /></span>
              <div>
                <strong>{{ reward.value }}</strong>
                <p>{{ reward.label }}</p>
              </div>
            </div>
          </article>

          <article class="student-ai-card">
            <div class="row student-home-section-head student-home-section-head--compact">
              <div class="media-badge small student-ai-badge"><IconGlyph name="ai" :size="18" /></div>
              <strong>SparkBot AI Helper</strong>
            </div>
            <div class="student-ai-bubble">
              Hi {{ firstName }}! I can help with tones, words, sentences, and quiz review.
            </div>
            <button class="btn student-ai-button" type="button" @click="router.push('/student/ai-helper')">
              <IconGlyph name="ai" :size="16" />
              Chat with SparkBot
            </button>
            <div class="student-ai-fish"><IconGlyph name="fun" :size="28" /></div>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Student dashboard hero — the first page a learner sees after sign-in.
 *
 * <p>Aggregates the learner's "what's next" picture: current lesson,
 * progress, practice tools, rewards, quiz snapshot, and study rhythm.
 * Dashboard and progress resources load independently so the page can
 * show precise progress while still falling back safely in preview
 * mode.</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { studentDashboard, studentProgress } from '@/demo/data'
import type { StudentDashboardResponse, StudentProgressSummaryResponse } from '@/types/api'
import { clampPercent, nonNegativeInteger, ratioPercent } from '@/utils/format'
import { inferPracticeActivityFromText, practiceRoute } from '@/utils/practiceRoute'

const router = useRouter()
const dashboardResource = useResource(() => studentApi.dashboard(), studentDashboard)
const progressResource = useResource(() => studentApi.progress(), studentProgress)

const emptyDashboard: StudentDashboardResponse = {
  fullName: '',
  currentLevel: '',
  learningProgressPercent: 0,
  dailyStreak: 0,
  points: 0,
  badgeCount: 0,
  recentQuizResults: [],
  weakAreas: [],
  unfinishedLessons: [],
  recentNotifications: [],
  recommendedNextStep: null,
}
const emptyProgress: StudentProgressSummaryResponse = {
  totalLessonsStarted: 0,
  completedLessons: 0,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  lastActivitySummary: null,
  recentLessons: [],
}

const dashboard = computed(
  () => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? studentDashboard : emptyDashboard),
)
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgress : emptyProgress),
)
const pageLoading = computed(() => dashboardResource.loading.value || progressResource.loading.value)
const pageError = computed(() => dashboardResource.error.value || progressResource.error.value)
const usingFallback = computed(() => dashboardResource.usingFallback.value || progressResource.usingFallback.value)

const firstName = computed(() => (dashboard.value.fullName.split(' ')[0] || 'Learner').trim())
const overallProgress = computed(() =>
  safePercent(progress.value.overallCompletionPercent || dashboard.value.learningProgressPercent),
)
const quizAveragePercent = computed(() => safePercent(progress.value.quizAverage))
const nextLesson = computed(() => {
  const unfinished = dashboard.value.unfinishedLessons[0]
  const recent = progress.value.recentLessons[0]

  return {
    id: unfinished?.lessonId ?? recent?.lessonId ?? null,
    title: unfinished?.lessonTitle ?? recent?.lessonTitle ?? 'Continue Learning',
    completionPercent: safePercent(unfinished?.completionPercent ?? recent?.completionPercent ?? 0),
  }
})
const nextStep = computed(
  () => dashboard.value.recommendedNextStep || 'Continue your current lesson, then review one short practice activity.',
)
const displayLessonTitle = computed(() => normalizeLessonTitle(nextLesson.value.title))
const displayNextStep = computed(() => shortenSentence(nextStep.value, 118))
const nextLessonDuration = computed(() => (nextLesson.value.id ? '18 min' : '--'))

const heroMetrics = computed(() => [
  { label: 'Level', value: dashboard.value.currentLevel || 'Not set' },
  { label: 'Progress', value: `${overallProgress.value}%` },
  { label: 'Streak', value: formatDayCount(dashboard.value.dailyStreak) },
])
const heroWords = [
  { hanzi: '你好', pinyin: 'ni hao' },
  { hanzi: '声调', pinyin: 'tones' },
  { hanzi: '练习', pinyin: 'practice' },
]
const heroToneMarks = [1, 2, 3, 4]
const heroFocusText = computed(() =>
  quizAveragePercent.value >= 80 ? 'Keep the rhythm with one quick review.' : 'Review tones, then retry the quiz.',
)

const checklist = computed(() => [
  { title: 'Open the next lesson', done: nextLesson.value.completionPercent > 0 },
  {
    title: 'Review a practice set',
    done: progress.value.recentLessons.some((lesson) => lesson.completionPercent >= 50),
  },
  { title: 'Build quiz accuracy', done: quizAveragePercent.value >= 80 },
  { title: 'Keep your streak alive', done: nonNegativeInteger(dashboard.value.dailyStreak) > 0 },
])

const pathNodes = computed(() => {
  const lessons = [
    ...progress.value.recentLessons,
    ...dashboard.value.unfinishedLessons.map((lesson) => ({
      lessonId: lesson.lessonId,
      lessonTitle: lesson.lessonTitle,
      completionPercent: lesson.completionPercent,
      completed: lesson.completionPercent >= 100,
    })),
  ]
  const uniqueLessons = Array.from(new Map(lessons.map((lesson) => [lesson.lessonId, lesson])).values()).slice(0, 4)
  const source = uniqueLessons.length
    ? [...uniqueLessons]
    : [{ lessonId: 0, lessonTitle: 'Start your first lesson', completionPercent: 0, completed: false }]

  const plannedSteps = [
    {
      lessonId: -1,
      lessonTitle: 'Review checkpoint',
      completionPercent: quizAveragePercent.value,
      completed: quizAveragePercent.value >= 80,
    },
    {
      lessonId: -2,
      lessonTitle: 'Listening practice',
      completionPercent: nextLesson.value.completionPercent > 0 ? 50 : 0,
      completed: false,
    },
    {
      lessonId: -3,
      lessonTitle: 'Next quiz goal',
      completionPercent: 0,
      completed: false,
    },
  ]

  for (const step of plannedSteps) {
    if (source.length >= 4) break
    source.push(step)
  }

  return source.slice(0, 4).map((lesson, index) => {
    const completionPercent = safePercent(lesson.completionPercent)
    const completed = lesson.completed || completionPercent >= 100
    const current = lesson.lessonId === nextLesson.value.id && completionPercent < 100
    return {
      id: index + 1,
      title: normalizeLessonTitle(lesson.lessonTitle) || 'Learning step',
      icon: iconForLesson(lesson.lessonTitle),
      progressPercent: completionPercent,
      statusLabel: completed
        ? 'Complete'
        : current
          ? `${completionPercent}% in progress`
          : completionPercent > 0
            ? `${completionPercent}% started`
            : 'Up next',
      complete: completed,
      current,
      variant: variantForLesson(lesson.lessonTitle),
      lessonId: lesson.lessonId,
    }
  })
})

const practiceTools = [
  { title: 'Flashcards', icon: 'file', color: '#6bc45c', to: practiceRoute('FLASHCARD') },
  { title: 'Quiz', icon: 'quiz', color: '#9b6aff', to: '/student/quiz' },
  { title: 'Listening', icon: 'live', color: '#2d8cff', to: practiceRoute('LISTENING_EXERCISE') },
  { title: 'Speaking', icon: 'moderation', color: '#ff9c21', to: '/student/ai-helper' },
  { title: 'Writing', icon: 'clip', color: '#8d59ff', to: practiceRoute('TRACING_EXERCISE') },
  { title: 'Matching', icon: 'fun', color: '#ffc530', to: practiceRoute('MATCHING_GAME') },
  { title: 'Drag & Drop', icon: 'practice', color: '#00b8d9', to: practiceRoute('DRAG_DROP_ACTIVITY'), wide: true },
]

const goals = computed(() => {
  const completedLessons = nonNegativeInteger(progress.value.completedLessons)
  const started = Math.max(nonNegativeInteger(progress.value.totalLessonsStarted), completedLessons)
  const completedPercent = ratioPercent(completedLessons, started)

  return [
    {
      title: 'Complete started lessons',
      count: started ? `${completedLessons} / ${started}` : 'No lessons started',
      progress: completedPercent,
      tone: 'success' as const,
      icon: 'learn',
      done: started > 0 && completedLessons >= started,
    },
    {
      title: 'Continue next lesson',
      count: `${nextLesson.value.completionPercent}%`,
      progress: nextLesson.value.completionPercent,
      tone: 'info' as const,
      icon: 'live',
      done: nextLesson.value.completionPercent >= 100,
    },
    {
      title: 'Build quiz accuracy',
      count: `${quizAveragePercent.value}%`,
      progress: quizAveragePercent.value,
      tone: 'warning' as const,
      icon: 'clip',
      done: quizAveragePercent.value >= 80,
    },
  ]
})

const POINTS_PER_GEM = 6.8
const rewards = computed(() => [
  { label: 'Stars', value: nonNegativeInteger(dashboard.value.points), icon: 'star' },
  { label: 'Gems', value: nonNegativeInteger(dashboard.value.points / POINTS_PER_GEM), icon: 'rewards' },
  { label: 'Badges', value: nonNegativeInteger(dashboard.value.badgeCount), icon: 'shield' },
])

function safePercent(value: number) {
  return clampPercent(value)
}

function formatDayCount(value: number) {
  const days = nonNegativeInteger(value)
  return `${days} ${days === 1 ? 'day' : 'days'}`
}

function variantForLesson(title: string) {
  const normalized = title.toLowerCase()
  if (normalized.includes('pinyin')) return 'pinyin'
  if (normalized.includes('tone')) return 'tones'
  if (normalized.includes('family')) return 'family'
  if (normalized.includes('number')) return 'numbers'
  if (normalized.includes('listen')) return 'listening'
  return 'greeting'
}

function iconForLesson(title: string) {
  const variant = variantForLesson(title)
  if (variant === 'pinyin' || variant === 'tones') return 'language'
  if (variant === 'family') return 'students'
  if (variant === 'numbers') return 'quiz'
  if (variant === 'listening') return 'live'
  return 'practice'
}

function normalizeLessonTitle(title: string) {
  return title
    .replace(/\s+\d{6,}\s*$/u, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 72)
}

function shortenSentence(value: string, limit: number) {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (normalized.length <= limit) return normalized

  return `${normalized.slice(0, limit - 1).trim()}...`
}

function continueLearning() {
  if (!nextLesson.value.id) {
    void router.push('/student/learn')
    return
  }

  void router.push({
    name: 'student-learn',
    query: { lessonId: String(nextLesson.value.id) },
  })
}

function clickNode(node: { lessonId: number | null; title: string }) {
  if (node.lessonId !== null && node.lessonId > 0) {
    void router.push({
      name: 'student-learn',
      query: { lessonId: String(node.lessonId) },
    })
  } else {
    if (node.title.toLowerCase().includes('quiz')) {
      void router.push('/student/quiz')
    } else if (node.title.toLowerCase().includes('listening') || node.title.toLowerCase().includes('practice')) {
      void router.push(practiceRoute(inferPracticeActivityFromText(node.title)))
    } else {
      void router.push('/student/learn')
    }
  }
}

function reloadAll() {
  void dashboardResource.reload()
  void progressResource.reload()
}
</script>
