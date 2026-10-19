<template>
  <div class="page-stack student-home-page">
    <PageHeaderBlock
      :title="t('studentHome.pageTitle', { name: firstName })"
      :description="t('studentHome.pageDescription')"
      icon="home"
    />

    <DataNotice
      :using-fallback="usingFallback"
      :fallback-title="t('studentHome.previewTitle')"
      :fallback-message="t('studentHome.previewMessage')"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-home-layout">
        <div class="student-home-main">
          <section class="student-hero-card" aria-labelledby="student-hero-title">
            <div class="student-hero-copy">
              <span class="student-hero-chip">
                <IconGlyph name="learn" :size="16" />
                {{ t('studentHome.currentLesson') }}
              </span>
              <h2 id="student-hero-title" class="student-hero-title">{{ displayLessonTitle }}</h2>
              <p class="student-hero-text">{{ displayNextStep }}</p>
              <button class="btn btn-accent btn-lg student-hero-cta" type="button" @click="continueLearning">
                {{ t('studentHome.continueLearning') }}
                <IconGlyph name="chevronRight" :size="18" />
              </button>
              <dl class="student-hero-metrics" :aria-label="t('studentHome.heroMetrics')">
                <div v-for="metric in heroMetrics" :key="metric.label" class="student-hero-metric">
                  <dt>{{ metric.label }}</dt>
                  <dd>{{ metric.value }}</dd>
                </div>
              </dl>
            </div>

            <div class="student-hero-side">
              <div class="student-hero-words">
                <p class="student-hero-side-title">
                  <IconGlyph name="language" :size="16" />
                  {{ t('studentHome.warmUpWords') }}
                </p>
                <ul class="student-hero-word-list">
                  <li v-for="word in heroWords" :key="word.hanzi" class="student-hero-word">
                    <span class="cjk-text" lang="zh-CN">{{ word.hanzi }}</span>
                    <small>{{ word.pinyin }}</small>
                  </li>
                </ul>
                <p class="student-hero-focus">
                  <IconGlyph name="trophy" :size="16" />
                  <span>
                    <strong>{{ t('studentHome.todayFocus') }}</strong>
                    {{ heroFocusText }}
                  </span>
                </p>
              </div>
              <PandaIllustration
                class="student-hero-mascot"
                :size="132"
                outfit="var(--color-primary-500)"
                accent="var(--color-accent-400)"
                pose="wave"
              />
            </div>

            <div class="student-hero-checklist">
              <p class="student-hero-checklist-title">{{ t('studentHome.lessonChecklist') }}</p>
              <ul class="student-hero-checklist-list">
                <li
                  v-for="item in checklist"
                  :key="item.title"
                  class="student-hero-check"
                  :class="{ 'is-done': item.done }"
                >
                  <IconGlyph :name="completionIcon(item.done)" :size="16" />
                  <span>{{ item.title }}</span>
                  <span class="sr-only">
                    ({{ item.done ? t('studentHome.checklistDone') : t('studentHome.checklistTodo') }})
                  </span>
                </li>
              </ul>
            </div>
          </section>

          <div class="student-home-duo">
            <section class="panel student-home-card" aria-labelledby="student-path-title">
              <div class="student-home-card-head">
                <span class="media-badge small" aria-hidden="true"><IconGlyph name="progress" :size="18" /></span>
                <h2 id="student-path-title" class="card-title">{{ t('studentHome.learningPath') }}</h2>
              </div>

              <ol class="student-path-grid">
                <li v-for="node in pathNodes" :key="node.id">
                  <button
                    class="student-path-node"
                    :class="{ 'is-complete': node.complete, 'is-current': node.current }"
                    type="button"
                    :aria-label="pathNodeAriaLabel(node)"
                    @click="clickNode(node)"
                  >
                    <span class="student-path-icon" aria-hidden="true">
                      <IconGlyph :name="node.icon" :size="22" />
                    </span>
                    <span
                      v-if="node.complete || node.current"
                      class="student-path-node-badge"
                      :class="pathNodeBadgeClass(node)"
                      aria-hidden="true"
                    >
                      <IconGlyph :name="pathNodeBadgeIcon(node)" :size="12" />
                    </span>
                    <strong class="student-path-title">{{ node.id }}. {{ node.title }}</strong>
                    <span class="student-path-progress" aria-hidden="true"
                      ><span :style="{ width: node.progressPercent + '%' }"
                    /></span>
                    <span class="student-path-status">{{ node.statusLabel }}</span>
                  </button>
                </li>
              </ol>
            </section>

            <section class="panel student-home-card" aria-labelledby="student-tools-title">
              <div class="student-home-card-head">
                <span class="media-badge small" aria-hidden="true"><IconGlyph name="practice" :size="18" /></span>
                <h2 id="student-tools-title" class="card-title">{{ t('studentHome.practiceTools') }}</h2>
              </div>

              <div class="student-tools-grid">
                <button
                  v-for="tool in practiceTools"
                  :key="tool.title"
                  class="student-tool-button"
                  :class="{ 'is-wide': tool.wide }"
                  type="button"
                  @click="openPracticeTool(tool.to)"
                >
                  <span class="student-tool-icon" :class="`is-${tool.tone}`" aria-hidden="true">
                    <IconGlyph :name="tool.icon" :size="22" />
                  </span>
                  <span class="student-tool-label">{{ tool.title }}</span>
                </button>
              </div>
            </section>
          </div>

          <div class="student-home-duo">
            <section class="panel student-home-card student-home-feature" aria-labelledby="student-live-title">
              <div class="student-home-card-head">
                <span class="media-badge small" aria-hidden="true"><IconGlyph name="calendar" :size="18" /></span>
                <h2 id="student-live-title" class="card-title">{{ t('studentHome.livePractice') }}</h2>
              </div>
              <div class="student-home-feature-body">
                <div class="student-home-feature-copy">
                  <strong>{{ t('studentHome.liveSpeaking') }}</strong>
                  <p>{{ t('studentHome.liveSpeakingHint') }}</p>
                  <button class="btn btn-secondary" type="button" @click="openClasses">
                    {{ t('studentHome.viewClasses') }}
                  </button>
                </div>
                <div class="student-home-scene is-classroom" aria-hidden="true">
                  <span class="student-home-scene-board cjk-text" lang="zh-CN">你好！</span>
                  <PandaIllustration
                    :size="88"
                    outfit="var(--color-primary-500)"
                    accent="var(--color-accent-400)"
                    compact
                  />
                </div>
              </div>
            </section>

            <section class="panel student-home-card student-home-feature" aria-labelledby="student-recorded-title">
              <div class="student-home-card-head">
                <span class="media-badge small" aria-hidden="true"><IconGlyph name="play" :size="18" /></span>
                <h2 id="student-recorded-title" class="card-title">{{ t('studentHome.recordedLesson') }}</h2>
              </div>
              <div class="student-home-feature-body">
                <div class="student-home-feature-copy">
                  <strong>{{ displayLessonTitle }}</strong>
                  <p>{{ t('studentHome.recordedHint') }}</p>
                  <button class="btn btn-secondary" type="button" @click="continueLearning">
                    <IconGlyph name="play" :size="16" />
                    {{ t('studentHome.watchNow') }}
                  </button>
                </div>
                <div class="student-home-scene is-meadow" aria-hidden="true">
                  <span class="student-home-scene-play"><IconGlyph name="play" :size="18" /></span>
                  <PandaIllustration
                    :size="84"
                    outfit="var(--color-primary-500)"
                    accent="var(--color-accent-400)"
                    compact
                  />
                </div>
              </div>
            </section>
          </div>
        </div>

        <aside class="student-home-side">
          <section class="panel student-home-card" aria-labelledby="student-progress-title">
            <div class="student-home-card-head">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="progress" :size="18" /></span>
              <h2 id="student-progress-title" class="card-title">{{ t('studentHome.myProgress') }}</h2>
            </div>
            <ProgressRing
              :progress="overallProgress"
              :title="t('studentHome.courseProgress')"
              :subtitle="t('studentHome.progressHint')"
            />
          </section>

          <section class="panel student-home-card" aria-labelledby="student-goals-title">
            <div class="student-home-card-head">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="trophy" :size="18" /></span>
              <h2 id="student-goals-title" class="card-title">{{ t('studentHome.learningGoals') }}</h2>
              <span class="student-home-card-meta">{{ t('studentHome.liveProgress') }}</span>
            </div>
            <ul class="student-goal-list">
              <li v-for="goal in goals" :key="goal.title" class="student-goal-item" :class="{ 'is-done': goal.done }">
                <span class="student-goal-icon" aria-hidden="true"><IconGlyph :name="goal.icon" :size="18" /></span>
                <span class="student-goal-title">{{ goal.title }}</span>
                <span class="student-goal-count" :class="{ 'is-note': goal.countIsNote }">{{ goal.count }}</span>
                <ProgressBar
                  class="is-sm student-goal-bar"
                  :progress="goal.progress"
                  :tone="goal.tone"
                  :label="goal.title"
                />
                <span class="student-goal-status">
                  <IconGlyph :name="completionIcon(goal.done)" :size="14" />
                  {{ goal.done ? t('studentHome.goalReached') : t('studentHome.goalOpen') }}
                </span>
              </li>
            </ul>
          </section>

          <section class="panel student-home-card" :aria-label="t('studentHome.rewardsTitle')">
            <ul class="student-reward-list">
              <li v-for="reward in rewards" :key="reward.label" class="student-reward-item">
                <span class="student-reward-icon" aria-hidden="true"><IconGlyph :name="reward.icon" :size="22" /></span>
                <span class="student-reward-copy">
                  <strong>{{ reward.value }}</strong>
                  <span>{{ reward.label }}</span>
                </span>
              </li>
            </ul>
          </section>

          <section class="panel student-home-card student-ai-card" aria-labelledby="student-ai-title">
            <div class="student-home-card-head">
              <span class="media-badge small student-ai-badge" aria-hidden="true"
                ><IconGlyph name="ai" :size="18"
              /></span>
              <h2 id="student-ai-title" class="card-title">{{ t('studentHome.aiHelper') }}</h2>
            </div>
            <p class="student-ai-bubble">{{ t('studentHome.aiGreeting', { name: firstName }) }}</p>
            <button class="btn btn-secondary btn-block" type="button" @click="openAiHelper">
              <IconGlyph name="ai" :size="16" />
              {{ t('studentHome.chatWithAi') }}
            </button>
            <AiDisclaimer :show-guidance-link="false" />
          </section>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { studentApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
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
    'zh-CN': { studentHome: studentHomeMessages['zh-CN'] },
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
  lessons: [],
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
type LessonVariant = 'campus' | 'greeting' | 'listening' | 'numbers' | 'pinyin' | 'tones'
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
  lessonId: number | null
}

interface LearningGoal {
  title: string
  count: string
  /** The count is a short phrase (e.g. "No lessons started"), shown as a muted note instead of a number. */
  countIsNote?: boolean
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

const heroMetrics = computed(() => [
  { label: t('studentHome.level'), value: dashboard.value.currentLevel || t('studentHome.notSet') },
  { label: t('studentHome.progress'), value: `${overallProgress.value}%` },
  { label: t('studentHome.streak'), value: formatDayCount(dashboard.value.dailyStreak) },
])
const heroWords = [
  { hanzi: '你好', pinyin: 'ni hao' },
  { hanzi: '声调', pinyin: 'sheng diao' },
  { hanzi: '练习', pinyin: 'lian xi' },
]
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
      completionPercent: 0,
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
      lessonId: lesson.lessonId,
    }
  })
})

const practiceTools = computed(() => [
  { title: t('studentHome.flashcards'), icon: 'file', tone: 'success', to: practiceRoute('FLASHCARD') },
  { title: t('studentHome.quiz'), icon: 'quiz', tone: 'primary', to: '/student/quiz' },
  { title: t('studentHome.listening'), icon: 'live', tone: 'info', to: practiceRoute('LISTENING_EXERCISE') },
  { title: t('studentHome.speaking'), icon: 'moderation', tone: 'warning', to: '/student/ai-helper' },
  { title: t('studentHome.writing'), icon: 'clip', tone: 'primary', to: practiceRoute('TRACING_EXERCISE') },
  { title: t('studentHome.matching'), icon: 'fun', tone: 'accent', to: practiceRoute('MATCHING_GAME') },
  {
    title: t('studentHome.dragDrop'),
    icon: 'practice',
    tone: 'info',
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
      countIsNote: !started,
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

// Only real reward data from the dashboard API (D21: no derived "gems").
const rewards = computed(() => [
  { label: t('studentHome.points'), value: nonNegativeInteger(dashboard.value.points), icon: 'star' },
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
  if (normalized.includes('university') || normalized.includes('campus')) return 'campus'
  if (normalized.includes('number')) return 'numbers'
  if (normalized.includes('listen')) return 'listening'
  return 'greeting'
}

function iconForLesson(title: string): string {
  const variant = variantForLesson(title)
  if (variant === 'pinyin' || variant === 'tones') return 'language'
  if (variant === 'campus') return 'students'
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
      : t('studentHome.upNext')
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

<style scoped>
/* Layout: main column + side column; the side column moves below the main column at 1200px and narrower. */
.student-home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: var(--page-gap);
  align-items: start;
}

.student-home-main,
.student-home-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  min-width: 0;
}

.student-home-duo {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: var(--grid-gap);
}

.student-home-card {
  display: grid;
  gap: var(--space-4);
  align-content: start;
}

.student-home-card-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.student-home-card-head .card-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}

.student-home-card-meta {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

/* Hero: current lesson */
.student-hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 300px);
  gap: var(--space-6);
  padding: var(--space-8);
  overflow: hidden;
  border-radius: var(--radius-2xl);
  color: var(--text-on-primary);
  background:
    radial-gradient(circle at 92% 6%, var(--color-accent-a48), transparent 30%),
    radial-gradient(circle at 4% 100%, var(--color-white-a16), transparent 36%), var(--gradient-brand);
  box-shadow: var(--shadow-primary);
}

.student-hero-copy {
  display: grid;
  gap: var(--space-4);
  align-content: start;
  justify-items: start;
  min-width: 0;
}

.student-hero-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--color-white-a32);
  border-radius: var(--radius-pill);
  background: var(--surface-on-brand);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-hero-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-5xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
  overflow-wrap: anywhere;
}

.student-hero-text {
  max-width: 52ch;
  margin: 0;
  color: var(--color-primary-50);
  font-size: var(--font-size-base);
  line-height: var(--line-height-normal);
}

.student-hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  width: 100%;
  margin: var(--space-2) 0 0;
}

.student-hero-metric {
  display: grid;
  gap: var(--space-0-5);
  align-content: start;
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--color-white-a24);
  border-radius: var(--radius-lg);
  background: var(--surface-on-brand);
}

.student-hero-metric dt {
  color: var(--color-primary-50);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-hero-metric dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: break-word;
}

.student-hero-side {
  display: grid;
  gap: var(--space-3);
  justify-items: center;
  align-content: space-between;
  min-width: 0;
}

.student-hero-words {
  display: grid;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-4);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  color: var(--text-primary);
  box-shadow: var(--shadow-md);
}

.student-hero-side-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  color: var(--text-brand);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-hero-word-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-hero-word {
  display: grid;
  justify-items: center;
  gap: var(--space-0-5);
  min-width: 0;
  padding: var(--space-2) var(--space-1);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  text-align: center;
}

.student-hero-word .cjk-text {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-snug);
}

.student-hero-word small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-hero-focus {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-2-5) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-accent-50);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
}

.student-hero-focus > svg {
  flex: 0 0 auto;
  color: var(--color-accent-800);
}

.student-hero-focus strong {
  display: block;
  color: var(--text-primary);
}

.student-hero-checklist {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  color: var(--text-primary);
}

.student-hero-checklist-title {
  margin: 0;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-hero-checklist-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
  gap: var(--space-2) var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-hero-check {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-hero-check > svg {
  flex: 0 0 auto;
  color: var(--color-ink-500);
}

.student-hero-check.is-done {
  color: var(--text-primary);
}

.student-hero-check.is-done > svg {
  color: var(--color-success);
}

/* Learning path */
.student-path-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-path-node {
  position: relative;
  display: grid;
  justify-items: center;
  align-content: start;
  gap: var(--space-2);
  width: 100%;
  height: 100%;
  padding: var(--space-4) var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
  color: var(--text-primary);
  text-align: center;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);
}

.student-path-node:hover {
  border-color: var(--border-interactive);
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.student-path-node.is-current {
  border-color: var(--color-primary-300);
  background: var(--color-primary-50);
}

.student-path-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-secondary);
}

.student-path-node.is-current .student-path-icon {
  border-color: transparent;
  background: var(--color-primary-500);
  color: var(--text-on-primary);
}

.student-path-node.is-complete .student-path-icon {
  border-color: var(--color-success-border);
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.student-path-node-badge {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-round);
}

.student-path-node-badge.is-check {
  background: var(--color-success-strong);
  color: var(--color-white);
}

.student-path-node-badge.is-current {
  background: var(--color-accent-500);
  color: var(--text-on-accent);
}

.student-path-title {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-path-progress {
  display: block;
  width: 100%;
  height: 6px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--color-ink-100);
}

.student-path-progress > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary-500);
}

.student-path-node.is-complete .student-path-progress > span {
  background: var(--color-success);
}

.student-path-status {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-path-node.is-current .student-path-status {
  color: var(--text-brand);
}

.student-path-node.is-complete .student-path-status {
  color: var(--color-success-text);
}

/* Practice tools */
.student-tools-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2-5);
}

.student-tool-button {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: var(--space-2);
  min-width: 0;
  min-height: 92px;
  padding: var(--space-3) var(--space-2);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  text-align: center;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.student-tool-button:hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
  box-shadow: var(--shadow-sm);
}

.student-tool-button:active {
  background: var(--surface-selected);
}

.student-tool-button.is-wide {
  grid-column: 1 / -1;
  grid-auto-flow: column;
  align-items: center;
  justify-content: center;
  min-height: 56px;
}

.student-tool-label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.student-tool-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
}

.student-tool-icon.is-primary {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-tool-icon.is-success {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.student-tool-icon.is-info {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.student-tool-icon.is-warning {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.student-tool-icon.is-accent {
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

/* Live class and recorded lesson cards */
.student-home-feature-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: var(--space-4);
  align-items: center;
}

.student-home-feature-copy {
  display: grid;
  gap: var(--space-2);
  justify-items: start;
  min-width: 0;
}

.student-home-feature-copy strong {
  font-size: var(--font-size-base);
  overflow-wrap: anywhere;
}

.student-home-feature-copy p {
  margin: 0 0 var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-home-scene {
  position: relative;
  display: grid;
  place-items: end center;
  height: 124px;
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.student-home-scene.is-classroom {
  background: linear-gradient(180deg, var(--color-accent-50), var(--color-accent-100));
}

.student-home-scene.is-meadow {
  background: linear-gradient(180deg, var(--color-info-soft) 0 58%, var(--color-success-soft) 58%);
}

.student-home-scene-board {
  position: absolute;
  z-index: var(--z-raised);
  top: var(--space-2);
  left: 50%;
  padding: var(--space-0-5) var(--space-2);
  border: 1px solid var(--color-accent-300);
  border-radius: var(--radius-sm);
  background: var(--surface-card);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  transform: translateX(-50%);
}

.student-home-scene-play {
  position: absolute;
  z-index: var(--z-raised);
  top: var(--space-2);
  right: var(--space-2);
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-round);
  background: var(--surface-card);
  color: var(--color-primary-600);
  box-shadow: var(--shadow-sm);
}

/* Side column */
.student-goal-list,
.student-reward-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-goal-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  gap: var(--space-1-5) var(--space-3);
  align-items: center;
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.student-goal-icon {
  grid-row: span 3;
  align-self: start;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-goal-title {
  min-width: 0;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.student-goal-count {
  font-family: var(--font-display);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
}

/* A phrase instead of a number: muted, and allowed to wrap so the goal title keeps its width. */
.student-goal-count.is-note {
  max-width: 14ch;
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--text-secondary);
  text-align: right;
  white-space: normal;
}

.student-goal-bar,
.student-goal-status {
  grid-column: 2 / -1;
}

.student-goal-status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-goal-item.is-done .student-goal-status {
  color: var(--color-success-text);
}

.student-reward-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.student-reward-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--color-accent-100);
  border-radius: var(--radius-lg);
  background: var(--color-accent-50);
}

.student-reward-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-round);
  background: var(--surface-card);
  color: var(--color-accent-800);
}

.student-reward-copy {
  display: grid;
  min-width: 0;
}

.student-reward-copy strong {
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-reward-copy span {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.student-ai-card {
  border-color: var(--color-primary-50);
  background: linear-gradient(160deg, var(--color-primary-50), var(--surface-card) 70%);
}

.student-ai-badge {
  background: var(--color-primary-500);
  color: var(--text-on-primary);
}

.student-ai-bubble {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xs) var(--radius-lg) var(--radius-lg) var(--radius-lg);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-ai-card :deep(.ai-disclaimer) {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

@media (max-width: 1200px) {
  .student-home-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-home-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
    align-items: start;
  }
}

@media (max-width: 900px) {
  .student-hero-card {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-6);
  }

  .student-hero-side {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: end;
  }
}

@media (max-width: 640px) {
  .student-hero-card {
    gap: var(--space-4);
    padding: var(--space-5) var(--space-4);
    border-radius: var(--radius-xl);
  }

  .student-hero-title {
    font-size: var(--font-size-3xl);
  }

  .student-hero-cta {
    width: 100%;
  }

  .student-hero-metric dd {
    font-size: var(--font-size-base);
  }

  .student-hero-side {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-hero-mascot {
    display: none;
  }

  .student-hero-checklist {
    padding: var(--space-4);
  }

  .student-home-feature-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-home-scene {
    display: none;
  }
}
</style>
