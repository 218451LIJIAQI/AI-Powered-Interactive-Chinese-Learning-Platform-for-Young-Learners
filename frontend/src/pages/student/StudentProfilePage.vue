<template>
  <div class="page-stack student-profile-page">
    <PageHeaderBlock
      :title="t('studentProfile.hello', { name: displayName })"
      :description="t('studentProfile.intro')"
      icon="profile"
    />

    <DataNotice
      :using-fallback="usingFallback"
      :error="actionError"
      :success="actionSuccess"
      :fallback-title="t('studentProfile.previewTitle')"
      :fallback-message="t('studentProfile.previewMessage')"
      :error-title="t('studentProfile.errorTitle')"
      :detail="profileNoticeDetail"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-profile-layout">
        <div class="student-profile-main">
          <article class="hero-card student-profile-hero">
            <div class="student-profile-identity">
              <PortraitAvatar :size="96" :name="profile.fullName" tone="accent" />
              <div class="student-profile-identity-copy">
                <span class="student-profile-eyebrow">{{ t('studentProfile.snapshot') }}</span>
                <h2>{{ displayName }}</h2>
                <span class="student-profile-role">{{ currentLevelTitle }}</span>
                <span class="student-profile-email">{{ profileEmailLabel }}</span>
                <span class="student-profile-mode">{{ accountModeLabel }}</span>
              </div>
            </div>

            <ul class="student-profile-stats">
              <li class="student-profile-stat">
                <span>{{ t('studentProfile.level') }}</span>
                <strong>{{ currentLevelNumber }}</strong>
                <small>{{
                  t('studentProfile.nextLevel', { progress: formatPercent(rewards.progressToNextLevelPercent) })
                }}</small>
              </li>
              <li class="student-profile-stat">
                <span>{{ t('studentProfile.streak') }}</span>
                <strong>{{ currentStreakCount }}</strong>
                <small>{{ streakDateLabel }}</small>
              </li>
              <li class="student-profile-stat">
                <span>{{ t('studentProfile.overallProgress') }}</span>
                <strong>{{ formatPercent(overallProgressPercent) }}</strong>
                <small>{{ t('studentProfile.completedLessons', { count: completedLessonCount }) }}</small>
              </li>
              <li class="student-profile-stat">
                <span>{{ t('studentProfile.quizAverage') }}</span>
                <strong>{{ formatPercent(progress.quizAverage) }}</strong>
                <small>{{ lastActivitySummaryLabel }}</small>
              </li>
            </ul>
          </article>

          <div class="student-profile-grid">
            <section class="panel student-profile-card" :aria-labelledby="`${baseId}-badges`">
              <div class="card-header">
                <h2 :id="`${baseId}-badges`" class="card-title">{{ t('studentProfile.recentBadges') }}</h2>
                <button type="button" class="btn btn-link" @click="openRewards">
                  {{ t('studentProfile.viewRewards') }}
                </button>
              </div>

              <ul v-if="badgeCards.length" class="student-profile-list">
                <li v-for="badge in badgeCards" :key="badge.id" class="student-profile-row has-icon">
                  <span class="student-profile-badge-icon" aria-hidden="true">{{ badge.initials }}</span>
                  <div class="student-profile-row-copy">
                    <strong>{{ badge.title }}</strong>
                    <span>{{ badge.subtitle }}</span>
                  </div>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="rewards"
                :title="t('studentProfile.noBadges')"
                :message="t('studentProfile.noBadgesHint')"
                compact
              />
            </section>

            <section class="panel student-profile-card" :aria-labelledby="`${baseId}-points`">
              <div class="card-header">
                <h2 :id="`${baseId}-points`" class="card-title">{{ t('studentProfile.recentPoints') }}</h2>
                <button type="button" class="btn btn-link" @click="openRewards">
                  {{ t('studentProfile.openRewards') }}
                </button>
              </div>

              <ul v-if="pointsFeed.length" class="student-profile-list">
                <li v-for="item in pointsFeed" :key="item.id" class="student-profile-row">
                  <div class="student-profile-row-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.subtitle }}</span>
                    <small>{{ item.time }}</small>
                  </div>
                  <strong class="student-profile-value is-positive">{{ item.value }}</strong>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="rewards"
                :title="t('studentProfile.noPoints')"
                :message="t('studentProfile.noPointsHint')"
                compact
              />
            </section>

            <section class="panel student-profile-card" :aria-labelledby="`${baseId}-lessons`">
              <div class="card-header">
                <h2 :id="`${baseId}-lessons`" class="card-title">{{ t('studentProfile.recentLessons') }}</h2>
                <button type="button" class="btn btn-link" @click="openLearn">
                  {{ t('studentProfile.openLearn') }}
                </button>
              </div>

              <ul v-if="recentLessons.length" class="student-profile-list">
                <li v-for="lesson in recentLessons" :key="lesson.lessonId" class="student-profile-row">
                  <div class="student-profile-row-copy">
                    <strong>{{ lesson.lessonTitle }}</strong>
                    <span>{{ lesson.statusLabel }}</span>
                    <small>{{ lesson.lastAccessedLabel }}</small>
                  </div>
                  <strong class="student-profile-value">{{ lesson.progressLabel }}</strong>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="learn"
                :title="t('studentProfile.noLessons')"
                :message="t('studentProfile.noLessonsHint')"
                compact
              />
            </section>

            <section class="panel student-profile-card" :aria-labelledby="`${baseId}-feedback`">
              <div class="card-header">
                <h2 :id="`${baseId}-feedback`" class="card-title">{{ t('studentProfile.feedback') }}</h2>
                <button type="button" class="btn btn-link" @click="openPractice">
                  {{ t('studentProfile.openPractice') }}
                </button>
              </div>

              <ul v-if="feedback.length" class="student-profile-list">
                <li v-for="item in feedback.slice(0, 4)" :key="item.id" class="student-profile-feedback">
                  <div class="student-profile-feedback-head">
                    <strong>{{ feedbackTitle(item) }}</strong>
                    <small>{{ formatDateTime(item.createdAt) }}</small>
                  </div>
                  <p>{{ item.message }}</p>
                  <span>{{ item.teacherName }} - {{ feedbackTypeLabel(item) }}</span>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="help"
                :title="t('studentProfile.noFeedback')"
                :message="t('studentProfile.noFeedbackHint')"
                compact
              />
            </section>
          </div>
        </div>

        <aside class="student-profile-side">
          <section class="panel student-profile-card" :aria-labelledby="`${baseId}-snapshot`">
            <h2 :id="`${baseId}-snapshot`" class="card-title">{{ t('studentProfile.learningSnapshot') }}</h2>

            <ProgressRing
              :progress="overallProgressPercent"
              :title="t('studentProfile.keepGoing')"
              :subtitle="t('studentProfile.studentProgress')"
            />

            <dl class="student-profile-side-stats">
              <div>
                <dt>{{ t('studentProfile.lessonsStarted') }}</dt>
                <dd>{{ startedLessonCount }}</dd>
              </div>
              <div>
                <dt>{{ t('studentProfile.activitiesLoaded') }}</dt>
                <dd>{{ activities.items.length }}</dd>
              </div>
              <div>
                <dt>{{ t('studentProfile.weakAreas') }}</dt>
                <dd>{{ progress.weakAreas.length }}</dd>
              </div>
            </dl>
            <RouterLink class="btn btn-link student-profile-card-link" to="/student/progress">
              {{ t('studentProfile.viewProgress') }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </section>

          <section class="panel student-profile-card" :aria-labelledby="`${baseId}-notifications`">
            <div class="card-header">
              <h2 :id="`${baseId}-notifications`" class="card-title">{{ t('studentProfile.notifications') }}</h2>
              <div class="student-profile-card-actions">
                <button
                  type="button"
                  class="btn btn-ghost btn-sm"
                  :disabled="markingNotificationsRead || !hasUnreadNotifications"
                  :aria-busy="markingNotificationsRead"
                  @click="markAllNotificationsRead"
                >
                  {{ markAllNotificationsLabel }}
                </button>
                <RouterLink
                  class="btn btn-link"
                  to="/student/notifications"
                  data-testid="profile-notifications-view-all"
                >
                  {{ t('studentProfile.viewAllNotifications') }}
                </RouterLink>
              </div>
            </div>

            <div v-if="notifications.length" class="student-profile-notification-list">
              <NotificationLinkButton
                v-for="item in notifications"
                :key="item.id"
                :notification-id="item.id"
                :target-path="item.targetPath"
                fallback-to="/student/notifications"
                @read="markStudentNotificationRead"
              >
                <span class="student-profile-row">
                  <span class="student-profile-row-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.message }}</span>
                    <small>{{ formatDateTime(item.createdAt) }}</small>
                  </span>
                  <StatusPill
                    :label="notificationStatusLabel(item.status)"
                    :tone="notificationStatusTone(item.status)"
                  />
                </span>
              </NotificationLinkButton>
            </div>
            <StatePanel
              v-else
              icon="bell"
              :title="t('studentProfile.noNotifications')"
              :message="t('studentProfile.noNotificationsHint')"
              compact
            />
          </section>

          <section class="panel student-profile-card" :aria-labelledby="`${baseId}-activities`">
            <div class="card-header">
              <h2 :id="`${baseId}-activities`" class="card-title">{{ t('studentProfile.activityLibrary') }}</h2>
              <button type="button" class="btn btn-link" @click="openPractice">
                {{ t('studentProfile.openPractice') }}
              </button>
            </div>

            <ul v-if="activityRows.length" class="student-profile-list">
              <li v-for="item in activityRows" :key="item.id" class="student-profile-row">
                <div class="student-profile-row-copy">
                  <strong>{{ item.title }}</strong>
                  <span class="student-profile-row-meta">
                    <span>{{ item.type }}</span>
                    <LearningStageTag :stage="item.learningStage" />
                  </span>
                  <small>{{ item.tags }}</small>
                </div>
                <span class="tag">{{ item.status }}</span>
              </li>
            </ul>
            <StatePanel
              v-else
              icon="practice"
              :title="t('studentProfile.noActivities')"
              :message="t('studentProfile.noActivitiesHint')"
              compact
            />
          </section>

          <section class="panel student-profile-card" :aria-labelledby="`${baseId}-account`">
            <div class="card-header">
              <h2 :id="`${baseId}-account`" class="card-title">{{ t('studentProfile.accountContext') }}</h2>
            </div>

            <dl class="kv-list student-profile-settings">
              <dt>{{ t('studentProfile.preferredLanguage') }}</dt>
              <dd>{{ preferredLanguageLabel }}</dd>
              <template v-if="institutionLabel">
                <dt>{{ t('studentProfile.institution') }}</dt>
                <dd>{{ institutionLabel }}</dd>
              </template>
              <dt>{{ t('studentProfile.aiRoute') }}</dt>
              <dd>{{ t('studentProfile.aiRouteHint') }}</dd>
              <dt>{{ t('studentProfile.email') }}</dt>
              <dd>{{ profileAccountEmailLabel }}</dd>
            </dl>

            <div class="card-footer">
              <RouterLink class="btn btn-secondary btn-sm" to="/student/account" data-testid="profile-account-link">
                <IconGlyph name="account" :size="16" />
                {{ t('studentProfile.editAccount') }}
              </RouterLink>
            </div>
          </section>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'

import { authApi, notificationsApi, studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import {
  activityLibrary,
  studentFeedbackList,
  studentProgress as studentProgressDemo,
  studentRewards,
} from '@/demo/data'
import { demoUsers } from '@/demo/users'
import { statusLabelKey, type StatusLabelGroup } from '@/plugins/statusLabelMessages'
import { studentProfileMessages } from '@/plugins/studentProfileMessages'
import type { AppLanguage } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import {
  clampPercent,
  formatDate,
  formatDateTime,
  formatPercent,
  nonNegativeInteger,
  toFiniteNumber,
} from '@/utils/format'
import { sortByLearningStage } from '@/utils/learningStage'
import { normalizeAppLanguage } from '@/utils/locale'
import { announceNotificationsRead, markNotificationRead } from '@/utils/notifications'

const { t } = useI18n({
  messages: {
    en: { studentProfile: studentProfileMessages.en },
    ms: { studentProfile: studentProfileMessages.ms },
    'zh-CN': { studentProfile: studentProfileMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

type NotificationStatusTone = 'info' | 'warning'
type FeedbackSummary = {
  lessonTitle?: string | null
  quizTitle?: string | null
  feedbackType?: string | null
}

const router = useRouter()
const baseId = useId()

const profileResource = useResource(() => authApi.me(), demoUsers.STUDENT)
const feedbackResource = useResource(() => studentApi.feedback(), studentFeedbackList)
const rewardsResource = useResource(() => studentApi.rewards(), studentRewards)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const activitiesResource = useResource(() => studentApi.activityLibrary({ size: 20 }), activityLibrary)
const notificationsResource = useResource(() => notificationsApi.list({ size: 6 }), [])

const emptyProfile = {
  id: 0,
  fullName: '',
  email: '',
  preferredLanguage: 'en',
  roles: ['STUDENT'],
  studentProfile: null,
  teacherProfile: null,
}
const emptyRewards = {
  totalPoints: 0,
  currentLevelNumber: 1,
  progressToNextLevelPercent: 0,
  streak: {
    currentStreak: 0,
    bestStreak: 0,
    lastLearningDate: null,
  },
  badges: [],
  recentPoints: [],
}
const emptyProgress = {
  totalLessonsStarted: 0,
  completedLessons: 0,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  lastActivitySummary: null,
  recentLessons: [],
}
const emptyActivities = {
  items: [],
  page: 0,
  size: 0,
  totalItems: 0,
  totalPages: 0,
}

const profile = computed(
  () => profileResource.data.value ?? (profileResource.usingFallback.value ? demoUsers.STUDENT : emptyProfile),
)
// Keep the raw feedbackType so feedbackTypeLabel() can translate it (formatting it first broke the lookup).
const feedback = computed(
  () => feedbackResource.data.value ?? (feedbackResource.usingFallback.value ? studentFeedbackList : []),
)
const rewards = computed(
  () => rewardsResource.data.value ?? (rewardsResource.usingFallback.value ? studentRewards : emptyRewards),
)
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)
const activities = computed(
  () => activitiesResource.data.value ?? (activitiesResource.usingFallback.value ? activityLibrary : emptyActivities),
)
const notifications = computed(() => notificationsResource.data.value ?? [])

const usingFallback = computed(
  () =>
    profileResource.usingFallback.value ||
    feedbackResource.usingFallback.value ||
    rewardsResource.usingFallback.value ||
    progressResource.usingFallback.value ||
    activitiesResource.usingFallback.value ||
    notificationsResource.usingFallback.value,
)
const pageLoading = computed(
  () =>
    profileResource.loading.value ||
    feedbackResource.loading.value ||
    rewardsResource.loading.value ||
    progressResource.loading.value ||
    activitiesResource.loading.value ||
    notificationsResource.loading.value,
)
const pageError = computed(
  () =>
    profileResource.error.value ||
    feedbackResource.error.value ||
    rewardsResource.error.value ||
    progressResource.error.value ||
    activitiesResource.error.value ||
    notificationsResource.error.value,
)
const profileNoticeDetail = computed(() => {
  if (actionError.value) {
    return t('studentProfile.errorSnapshot')
  }

  if (usingFallback.value) {
    return t('studentProfile.previewDetail')
  }

  if (actionSuccess.value) {
    return t('studentProfile.successDetail')
  }

  return null
})

const displayName = computed(
  () =>
    profile.value.studentProfile?.displayName || profile.value.fullName.split(' ')[0] || t('studentProfile.student'),
)
const profileEmailLabel = computed(() => profile.value.email || t('studentProfile.noEmail'))
const profileAccountEmailLabel = computed(() => profile.value.email || '--')
const currentLevelNumber = computed(() => Math.max(1, nonNegativeInteger(rewards.value.currentLevelNumber, 1)))
const currentStreakCount = computed(() => nonNegativeInteger(rewards.value.streak.currentStreak))
const completedLessonCount = computed(() => nonNegativeInteger(progress.value.completedLessons))
const startedLessonCount = computed(() => nonNegativeInteger(progress.value.totalLessonsStarted))
const overallProgressPercent = computed(() => clampPercent(progress.value.overallCompletionPercent))
const currentLevelTitle = computed(
  () =>
    profile.value.studentProfile?.currentLevel || t('studentProfile.levelNumber', { level: currentLevelNumber.value }),
)
const accountModeLabel = computed(() =>
  usingFallback.value ? t('studentProfile.previewData') : t('studentProfile.liveAccount'),
)
const lastActivitySummaryLabel = computed(() => progress.value.lastActivitySummary || t('studentProfile.noSummary'))
const streakDateLabel = computed(() =>
  rewards.value.streak.lastLearningDate
    ? t('studentProfile.lastActive', { date: formatDate(rewards.value.streak.lastLearningDate) })
    : t('studentProfile.noLearningDate'),
)
/** Language names are shown in their own language so every learner can recognise them. */
const languageNames: Record<AppLanguage, string> = {
  en: 'English',
  ms: 'Bahasa Melayu',
  'zh-CN': '简体中文',
}
const preferredLanguageLabel = computed(() => {
  const language = normalizeAppLanguage(profile.value.preferredLanguage)
  return language ? languageNames[language] : t('studentProfile.languageNotSet')
})
const institutionLabel = computed(() => profile.value.studentProfile?.institution?.trim() || '')
const hasUnreadNotifications = computed(() => notifications.value.some((item) => item.status !== 'READ'))
const markAllNotificationsLabel = computed(() =>
  markingNotificationsRead.value ? t('studentProfile.marking') : t('studentProfile.markAllRead'),
)
const badgeCards = computed(() =>
  rewards.value.badges.slice(0, 4).map((badge) => ({
    id: badge.badgeId,
    title: badge.name,
    subtitle: badge.awardedAt ? t('studentProfile.awarded', { date: formatDate(badge.awardedAt) }) : badge.description,
    initials:
      badge.name
        .split(' ')
        .filter(Boolean)
        .map((part) => part[0])
        .join('')
        .slice(0, 2)
        .toUpperCase() || '?',
  })),
)

const pointsFeed = computed(() =>
  rewards.value.recentPoints.slice(0, 4).map((item) => ({
    id: item.id,
    title: item.reason,
    subtitle: knownStatusLabel('pointsSource', item.sourceType, item.sourceType),
    time: formatDateTime(item.createdAt),
    value: signedIntegerLabel(item.pointsDelta),
  })),
)

const recentLessons = computed(() =>
  progress.value.recentLessons.slice(0, 4).map((lesson) => {
    const completionPercent = clampPercent(lesson.completionPercent)

    return {
      ...lesson,
      progressLabel: formatPercent(completionPercent),
      statusLabel:
        lesson.completed || completionPercent >= 100
          ? t('studentProfile.completed')
          : completionPercent > 0
            ? t('studentProfile.inProgress')
            : t('studentProfile.notStarted'),
      lastAccessedLabel: lesson.lastAccessedAt
        ? formatDateTime(lesson.lastAccessedAt)
        : t('studentProfile.noLastAccess'),
    }
  }),
)

const activityRows = computed(() =>
  sortByLearningStage(activities.value.items)
    .slice(0, 4)
    .map((item) => ({
      id: item.id,
      title: item.title,
      learningStage: item.learningStage ?? null,
      type: knownStatusLabel('activityType', item.type, item.type.replaceAll('_', ' ')),
      tags: item.tags.length ? item.tags.join(', ') : t('studentProfile.noTags'),
      status: item.completed ? t('studentProfile.done') : t('studentProfile.open'),
    })),
)

const markingNotificationsRead = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

function openRewards(): void {
  void router.push('/student/rewards')
}

function openLearn(): void {
  void router.push('/student/learn')
}

function openPractice(): void {
  void router.push('/student/practice')
}

function feedbackTitle(item: FeedbackSummary): string {
  return item.lessonTitle || item.quizTitle || t('studentProfile.feedback')
}

/** Shared translated feedback-type labels (A5 #15); a missing type is shown as General. */
function feedbackTypeLabel(item: FeedbackSummary): string {
  return statusLabel('feedbackType', item.feedbackType?.trim() || 'GENERAL')
}

/** Translated label for a known enum value; unknown values keep their previous display (`fallback`). */
function knownStatusLabel(group: StatusLabelGroup, value: string, fallback: string): string {
  return statusLabelKey(group, value) ? statusLabel(group, value) : fallback
}

function notificationStatusLabel(status: string): string {
  return status === 'READ' ? t('studentProfile.read') : t('studentProfile.unread')
}

function notificationStatusTone(status: string): NotificationStatusTone {
  return status === 'READ' ? 'info' : 'warning'
}

function markStudentNotificationRead(notificationId: number): void {
  notificationsResource.data.value = markNotificationRead(notifications.value, notificationId)
}

async function markAllNotificationsRead(): Promise<void> {
  if (markingNotificationsRead.value) return
  if (notificationsResource.usingFallback.value) {
    actionError.value = t('studentProfile.previewNotifications')
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  markingNotificationsRead.value = true
  try {
    const result = await notificationsApi.markAllRead()
    notificationsResource.data.value = notifications.value.map((item) =>
      item.status === 'READ' ? item : { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() },
    )
    announceNotificationsRead()
    actionSuccess.value =
      result.updatedCount > 0
        ? t('studentProfile.markedCount', { count: result.updatedCount })
        : t('studentProfile.alreadyRead')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentProfile.markReadError'))
  } finally {
    markingNotificationsRead.value = false
  }
}

function signedIntegerLabel(value: number): string {
  const rounded = Math.round(toFiniteNumber(value))
  return rounded > 0 ? `+${rounded}` : String(rounded)
}

function reloadAll(): void {
  void profileResource.reload()
  void feedbackResource.reload()
  void rewardsResource.reload()
  void progressResource.reload()
  void activitiesResource.reload()
  void notificationsResource.reload()
}
</script>

<style scoped>
.student-profile-page {
  display: grid;
  gap: var(--page-gap);
}

.student-profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: var(--page-gap);
  align-items: start;
}

.student-profile-main,
.student-profile-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  align-items: start;
  min-width: 0;
}

.student-profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  gap: var(--grid-gap);
}

/* Hero --------------------------------------------------------------------- */
.student-profile-hero {
  display: grid;
  gap: var(--space-6);
}

.student-profile-identity {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  min-width: 0;
}

.student-profile-identity-copy {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
  min-width: 0;
}

.student-profile-eyebrow {
  color: var(--text-on-primary);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.student-profile-identity-copy h2 {
  margin: 0;
  color: var(--text-on-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  overflow-wrap: anywhere;
}

.student-profile-role {
  padding: var(--space-0-5) var(--space-3);
  border-radius: var(--radius-pill);
  background: var(--surface-on-brand);
  color: var(--text-on-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-profile-email {
  color: var(--text-on-primary);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.student-profile-mode {
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  background: var(--color-accent-500);
  color: var(--text-on-accent);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.student-profile-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-profile-stat {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--color-white-a24);
  border-radius: var(--radius-lg);
  background: var(--surface-on-brand);
}

.student-profile-stat > span {
  color: var(--text-on-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-profile-stat strong {
  color: var(--text-on-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-profile-stat small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-on-primary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

/* Cards and lists ---------------------------------------------------------- */
.student-profile-card {
  display: grid;
  align-content: start;
  gap: var(--space-4);
}

.student-profile-card .card-header {
  align-items: center;
  flex-wrap: wrap;
}

.student-profile-card-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
}

.student-profile-card-link {
  justify-self: start;
}

.student-profile-list,
.student-profile-notification-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-profile-list > li {
  padding: var(--space-3) 0;
  border-top: 1px solid var(--border-subtle);
}

.student-profile-list > li:first-child {
  padding-top: 0;
  border-top: 0;
}

.student-profile-list > li:last-child {
  padding-bottom: 0;
}

.student-profile-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
}

.student-profile-row.has-icon {
  grid-template-columns: auto minmax(0, 1fr);
}

.student-profile-row-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.student-profile-row-copy strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-profile-row-copy > span {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.student-profile-row-copy small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-profile-row-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.student-profile-value {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.student-profile-value.is-positive {
  color: var(--color-success-text);
}

.student-profile-badge-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-primary-500);
  color: var(--text-on-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extrabold);
}

.student-profile-feedback {
  display: grid;
  gap: var(--space-1);
}

.student-profile-feedback-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
}

.student-profile-feedback-head strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.student-profile-feedback-head small,
.student-profile-feedback > span {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-profile-feedback p {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

/* Notification rows are buttons that open the related page. */
.student-profile-notification-list > .notification-link-button {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  transition: background-color var(--duration-fast) var(--ease-standard);
}

.student-profile-notification-list > .notification-link-button + .notification-link-button {
  margin-top: var(--space-1);
}

.student-profile-notification-list > .notification-link-button:hover {
  background: var(--surface-hover);
}

.student-profile-side-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.student-profile-side-stats div {
  display: grid;
  align-content: space-between;
  gap: var(--space-1);
  min-width: 0;
}

.student-profile-side-stats dt {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.student-profile-side-stats dd {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
}

.student-profile-settings dt,
.student-profile-settings dd {
  font-size: var(--font-size-sm);
}

.student-profile-card .card-footer {
  justify-content: flex-start;
}

@media (max-width: 1200px) {
  .student-profile-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-profile-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  }
}

@media (max-width: 640px) {
  .student-profile-identity {
    align-items: flex-start;
    gap: var(--space-4);
  }

  .student-profile-identity-copy h2 {
    font-size: var(--font-size-3xl);
  }

  .student-profile-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-profile-settings {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-1) 0;
  }

  .student-profile-settings dd {
    margin-bottom: var(--space-2);
    text-align: left;
  }
}
</style>
