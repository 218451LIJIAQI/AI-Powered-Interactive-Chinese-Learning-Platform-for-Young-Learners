<template>
  <div class="page-stack student-home-page">
    <DataNotice
      :using-fallback="usingFallback"
      :error="pageError"
      :fallback-title="t('studentHome.previewTitle')"
      :fallback-message="t('studentHome.previewMessage')"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-home-layout">
        <div class="student-home-main">
          <article class="student-hero-card">
            <div class="student-hero-copy">
              <div class="chip student-hero-chip">
                <IconGlyph name="learn" :size="16" />
                {{ t('studentHome.currentLesson') }}
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
                {{ t('studentHome.continueLearning') }}
                <span class="student-hero-cta-arrow"><IconGlyph name="chevronRight" :size="18" /></span>
              </button>
            </div>

            <div class="student-hero-side">
              <article class="student-hero-focus-card">
                <div class="student-hero-focus-head">
                  <IconGlyph name="language" :size="16" />
                  <span>{{ t('studentHome.warmUpWords') }}</span>
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
                  <strong>{{ t('studentHome.todayFocus') }}</strong>
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
              <strong>{{ t('studentHome.lessonChecklist') }}</strong>
              <div
                v-for="item in checklist"
                :key="item.title"
                class="student-hero-check"
                :class="{ 'student-hero-check--muted': !item.done }"
              >
                <IconGlyph :name="completionIcon(item.done)" :size="16" />
                <span>{{ item.title }}</span>
              </div>
            </div>
          </article>

          <div class="student-home-duo student-home-duo--primary">
            <article class="panel student-path-card">
              <div class="row student-home-section-head">
                <div class="media-badge small"><IconGlyph name="progress" :size="18" /></div>
                <div class="stack student-home-title-stack">
                  <strong>{{ t('studentHome.learningPath') }}</strong>
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
                    :aria-label="pathNodeAriaLabel(node)"
                    @click="clickNode(node)"
                    @keydown.enter.prevent="clickNode(node)"
                    @keydown.space.prevent="clickNode(node)"
                  >
                    <span
                      v-if="node.complete || node.current"
                      class="student-path-node-badge"
                      :class="pathNodeBadgeClass(node)"
                    >
                      <IconGlyph :name="pathNodeBadgeIcon(node)" :size="14" />
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
                  <strong>{{ t('studentHome.practiceTools') }}</strong>
                </div>
              </div>

              <div class="student-tools-grid">
                <button
                  v-for="tool in practiceTools"
                  :key="tool.title"
                  class="student-tool-button"
                  :class="{ 'student-tool-button--wide': tool.wide }"
                  type="button"
                  @click="openPracticeTool(tool.to)"
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
                  <strong>{{ t('studentHome.livePractice') }}</strong>
                </div>
              </div>
              <div class="student-class-body">
                <div class="student-class-copy">
                  <div class="stack student-home-copy-stack student-home-copy-stack--wide">
                    <strong>{{ t('studentHome.liveSpeaking') }}</strong>
                    <span class="muted">{{ t('studentHome.liveSpeakingHint') }}</span>
                  </div>
                  <button class="btn btn-primary student-class-button" type="button" @click="openClasses">
                    {{ t('studentHome.viewClasses') }}
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
                  <strong>{{ t('studentHome.recordedLesson') }}</strong>
                </div>
              </div>
              <div class="student-recorded-body">
                <div class="student-recorded-copy">
                  <div class="stack student-home-copy-stack">
                    <strong>{{ displayLessonTitle }}</strong>
                    <span class="muted">{{ t('studentHome.recordedHint') }}</span>
                  </div>
                  <button class="btn btn-secondary student-watch-button" type="button" @click="continueLearning">
                    {{ t('studentHome.watchNow') }}
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
              <strong>{{ t('studentHome.myProgress') }}</strong>
            </div>
            <ProgressRing
              :progress="overallProgress"
              :title="t('studentHome.courseProgress')"
              :subtitle="t('studentHome.progressHint')"
            />
          </article>

          <article class="panel student-side-card">
            <div class="row-between student-home-section-head">
              <div class="row">
                <div class="media-badge small"><IconGlyph name="trophy" :size="18" /></div>
                <strong>{{ t('studentHome.learningGoals') }}</strong>
              </div>
              <span class="muted student-goal-reset">{{ t('studentHome.liveProgress') }}</span>
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
                  <IconGlyph :name="completionIcon(goal.done)" :size="15" />
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
              <strong>{{ t('studentHome.aiHelper') }}</strong>
            </div>
            <div class="student-ai-bubble">
              {{ t('studentHome.aiGreeting', { name: firstName }) }}
            </div>
            <button class="btn student-ai-button" type="button" @click="openAiHelper">
              <IconGlyph name="ai" :size="16" />
              {{ t('studentHome.chatWithAi') }}
            </button>
            <div class="student-ai-fish"><IconGlyph name="fun" :size="28" /></div>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { studentDashboard, studentProgress } from '@/demo/data'
import { studentHomeMessages } from '@/plugins/studentHomeMessages'
import type { StudentDashboardResponse, StudentProgressSummaryResponse } from '@/types/api'
import { clampPercent, nonNegativeInteger, ratioPercent } from '@/utils/format'
import { inferPracticeActivityFromText, practiceRoute } from '@/utils/practiceRoute'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { studentHome: studentHomeMessages.en },
    ms: { studentHome: studentHomeMessages.ms },
    zh: { studentHome: studentHomeMessages.zh },
  },
})
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

type CompletionIcon = 'history' | 'status'
type LessonVariant = 'family' | 'greeting' | 'listening' | 'numbers' | 'pinyin' | 'tones'
type PathNodeBadgeClass = 'is-check' | 'is-current'
type PathNodeBadgeIcon = 'flash' | 'status'
type ProgressTone = 'info' | 'success' | 'warning'

interface PathNode {
  id: number
  title: string
  icon: string
  progressPercent: number
  statusLabel: string
  complete: boolean
  current: boolean
  variant: LessonVariant
  lessonId: number | null
}

interface LearningGoal {
  title: string
  count: string
  progress: number
  tone: ProgressTone
  icon: string
  done: boolean
}

const firstName = computed(() => (dashboard.value.fullName.split(' ')[0] || t('studentHome.learner')).trim())
const overallProgress = computed(() =>
  safePercent(progress.value.overallCompletionPercent || dashboard.value.learningProgressPercent),
)
const quizAveragePercent = computed(() => safePercent(progress.value.quizAverage))
const nextLesson = computed(() => {
  const unfinished = dashboard.value.unfinishedLessons[0]
  const recent = progress.value.recentLessons[0]

  return {
    id: unfinished?.lessonId ?? recent?.lessonId ?? null,
    title: unfinished?.lessonTitle ?? recent?.lessonTitle ?? t('studentHome.fallbackLesson'),
    completionPercent: safePercent(unfinished?.completionPercent ?? recent?.completionPercent ?? 0),
  }
})
const nextStep = computed(() => dashboard.value.recommendedNextStep || t('studentHome.fallbackNextStep'))
const displayLessonTitle = computed(() => normalizeLessonTitle(nextLesson.value.title))
const displayNextStep = computed(() => shortenSentence(nextStep.value, 118))
const nextLessonDuration = computed(() => (nextLesson.value.id ? '18 min' : '--'))

const heroMetrics = computed(() => [
  { label: t('studentHome.level'), value: dashboard.value.currentLevel || t('studentHome.notSet') },
  { label: t('studentHome.progress'), value: `${overallProgress.value}%` },
  { label: t('studentHome.streak'), value: formatDayCount(dashboard.value.dailyStreak) },
])
const heroWords = [
  { hanzi: '你好', pinyin: 'ni hao' },
  { hanzi: '声调', pinyin: 'tones' },
  { hanzi: '练习', pinyin: 'practice' },
]
const heroToneMarks = [1, 2, 3, 4]
const heroFocusText = computed(() =>
  quizAveragePercent.value >= 80 ? t('studentHome.keepRhythm') : t('studentHome.retryQuiz'),
)

const checklist = computed(() => [
  { title: t('studentHome.openNextLesson'), done: nextLesson.value.completionPercent > 0 },
  {
    title: t('studentHome.reviewPractice'),
    done: progress.value.recentLessons.some((lesson) => lesson.completionPercent >= 50),
  },
  { title: t('studentHome.buildAccuracy'), done: quizAveragePercent.value >= 80 },
  { title: t('studentHome.keepStreak'), done: nonNegativeInteger(dashboard.value.dailyStreak) > 0 },
])

const pathNodes = computed<PathNode[]>(() => {
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
    : [{ lessonId: 0, lessonTitle: t('studentHome.firstLesson'), completionPercent: 0, completed: false }]

  const plannedSteps = [
    {
      lessonId: -1,
      lessonTitle: t('studentHome.reviewCheckpoint'),
      completionPercent: quizAveragePercent.value,
      completed: quizAveragePercent.value >= 80,
    },
    {
      lessonId: -2,
      lessonTitle: t('studentHome.listeningPractice'),
      completionPercent: nextLesson.value.completionPercent > 0 ? 50 : 0,
      completed: false,
    },
    {
      lessonId: -3,
      lessonTitle: t('studentHome.nextQuizGoal'),
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
      title: normalizeLessonTitle(lesson.lessonTitle) || t('studentHome.learningStep'),
      icon: iconForLesson(lesson.lessonTitle),
      progressPercent: completionPercent,
      statusLabel: lessonStatusLabel(completed, current, completionPercent),
      complete: completed,
      current,
      variant: variantForLesson(lesson.lessonTitle),
      lessonId: lesson.lessonId,
    }
  })
})

const practiceTools = computed(() => [
  { title: t('studentHome.flashcards'), icon: 'file', color: '#6bc45c', to: practiceRoute('FLASHCARD') },
  { title: t('studentHome.quiz'), icon: 'quiz', color: '#9b6aff', to: '/student/quiz' },
  { title: t('studentHome.listening'), icon: 'live', color: '#2d8cff', to: practiceRoute('LISTENING_EXERCISE') },
  { title: t('studentHome.speaking'), icon: 'moderation', color: '#ff9c21', to: '/student/ai-helper' },
  { title: t('studentHome.writing'), icon: 'clip', color: '#8d59ff', to: practiceRoute('TRACING_EXERCISE') },
  { title: t('studentHome.matching'), icon: 'fun', color: '#ffc530', to: practiceRoute('MATCHING_GAME') },
  {
    title: t('studentHome.dragDrop'),
    icon: 'practice',
    color: '#00b8d9',
    to: practiceRoute('DRAG_DROP_ACTIVITY'),
    wide: true,
  },
])

const goals = computed<LearningGoal[]>(() => {
  const completedLessons = nonNegativeInteger(progress.value.completedLessons)
  const started = Math.max(nonNegativeInteger(progress.value.totalLessonsStarted), completedLessons)
  const completedPercent = ratioPercent(completedLessons, started)

  return [
    {
      title: t('studentHome.completeStarted'),
      count: started ? `${completedLessons} / ${started}` : t('studentHome.noLessonsStarted'),
      progress: completedPercent,
      tone: 'success',
      icon: 'learn',
      done: started > 0 && completedLessons >= started,
    },
    {
      title: t('studentHome.continueNext'),
      count: `${nextLesson.value.completionPercent}%`,
      progress: nextLesson.value.completionPercent,
      tone: 'info',
      icon: 'live',
      done: nextLesson.value.completionPercent >= 100,
    },
    {
      title: t('studentHome.buildAccuracy'),
      count: `${quizAveragePercent.value}%`,
      progress: quizAveragePercent.value,
      tone: 'warning',
      icon: 'clip',
      done: quizAveragePercent.value >= 80,
    },
  ]
})

const POINTS_PER_GEM = 6.8
const rewards = computed(() => [
  { label: t('studentHome.stars'), value: nonNegativeInteger(dashboard.value.points), icon: 'star' },
  { label: t('studentHome.gems'), value: nonNegativeInteger(dashboard.value.points / POINTS_PER_GEM), icon: 'rewards' },
  { label: t('studentHome.badges'), value: nonNegativeInteger(dashboard.value.badgeCount), icon: 'shield' },
])

function safePercent(value: number): number {
  return clampPercent(value)
}

function formatDayCount(value: number): string {
  const days = nonNegativeInteger(value)
  return `${days} ${t(days === 1 ? 'studentHome.day' : 'studentHome.days')}`
}

function variantForLesson(title: string): LessonVariant {
  const normalized = title.toLowerCase()
  if (normalized.includes('pinyin')) return 'pinyin'
  if (normalized.includes('tone')) return 'tones'
  if (normalized.includes('family')) return 'family'
  if (normalized.includes('number')) return 'numbers'
  if (normalized.includes('listen')) return 'listening'
  return 'greeting'
}

function iconForLesson(title: string): string {
  const variant = variantForLesson(title)
  if (variant === 'pinyin' || variant === 'tones') return 'language'
  if (variant === 'family') return 'students'
  if (variant === 'numbers') return 'quiz'
  if (variant === 'listening') return 'live'
  return 'practice'
}

function normalizeLessonTitle(title: string): string {
  return title
    .replace(/\s+\d{6,}\s*$/u, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 72)
}

function shortenSentence(value: string, limit: number): string {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (normalized.length <= limit) return normalized

  return `${normalized.slice(0, limit - 1).trim()}...`
}

function continueLearning(): void {
  if (!nextLesson.value.id) {
    openLearn()
    return
  }

  openStudentLearn(nextLesson.value.id)
}

function clickNode(node: Pick<PathNode, 'lessonId'>): void {
  if (node.lessonId !== null && node.lessonId > 0) {
    openStudentLearn(node.lessonId)
    return
  }

  openFallbackNodeRoute(node.lessonId)
}

function reloadAll(): void {
  void dashboardResource.reload()
  void progressResource.reload()
}

function completionIcon(done: boolean): CompletionIcon {
  return done ? 'status' : 'history'
}

function lessonStatusLabel(completed: boolean, current: boolean, completionPercent: number): string {
  if (completed) return t('studentHome.complete')
  if (current) return t('studentHome.inProgress', { percent: completionPercent })
  return completionPercent > 0 ? t('studentHome.started', { percent: completionPercent }) : t('studentHome.upNext')
}

function pathNodeAriaLabel(node: PathNode): string {
  const state = node.complete
    ? t('studentHome.complete')
    : node.current
      ? t('studentHome.current')
      : t('studentHome.locked')
  return t('studentHome.pathAria', { id: node.id, title: node.title, state })
}

function pathNodeBadgeClass(node: PathNode): PathNodeBadgeClass {
  return node.complete ? 'is-check' : 'is-current'
}

function pathNodeBadgeIcon(node: PathNode): PathNodeBadgeIcon {
  return node.complete ? 'status' : 'flash'
}

function openPracticeTool(target: RouteLocationRaw): void {
  void router.push(target)
}

function openClasses(): void {
  void router.push('/student/classes')
}

function openAiHelper(): void {
  void router.push('/student/ai-helper')
}

function openLearn(): void {
  void router.push('/student/learn')
}

function openQuiz(): void {
  void router.push('/student/quiz')
}

function openStudentLearn(lessonId: number): void {
  void router.push({
    name: 'student-learn',
    query: { lessonId: String(lessonId) },
  })
}

function openFallbackNodeRoute(lessonId: number | null): void {
  if (lessonId === -3) {
    openQuiz()
    return
  }

  if (lessonId === -2) {
    openPracticeTool(practiceRoute(inferPracticeActivityFromText('listening')))
    return
  }

  openLearn()
}
</script>
