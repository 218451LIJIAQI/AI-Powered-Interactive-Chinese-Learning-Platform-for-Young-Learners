<template>
  <div class="page-stack student-profile-page">
    <section class="student-profile-header">
      <span class="student-profile-kicker">My learning home</span>
      <h1>Hello, {{ displayName }}!</h1>
      <p>Review your learning progress, rewards, teacher feedback, and payment history in one place.</p>
    </section>

    <DataNotice
      :using-fallback="usingFallback"
      :error="noticeError"
      :success="actionSuccess"
      fallback-title="Preview student profile"
      fallback-message="Progress, rewards, feedback, activities, and payments are showing safe preview data for this preview session."
      error-title="Profile details need attention"
      :detail="profileNoticeDetail"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-profile-layout">
        <div class="student-profile-main">
          <article class="student-profile-hero">
            <div class="student-profile-identity">
              <PortraitAvatar :size="120" kind="student" accent="#ffb54a" />
              <div class="student-profile-identity-copy">
                <span class="student-profile-identity-eyebrow">Student snapshot</span>
                <h2>{{ displayName }}</h2>
                <span class="student-profile-role">{{ currentLevelTitle }}</span>
                <small>{{ profileEmailLabel }}</small>
                <div class="student-profile-member-chip">{{ accountModeLabel }}</div>
              </div>
            </div>

            <div class="student-profile-hero-stats">
              <article class="student-profile-stat-card">
                <small>Level</small>
                <strong>{{ currentLevelNumber }}</strong>
                <span>{{ formatPercent(rewards.progressToNextLevelPercent) }} to next level</span>
              </article>
              <article class="student-profile-stat-card">
                <small>Current Streak</small>
                <strong>{{ currentStreakCount }}</strong>
                <span>{{ streakDateLabel }}</span>
              </article>
              <article class="student-profile-stat-card">
                <small>Overall Progress</small>
                <strong>{{ formatPercent(overallProgressPercent) }}</strong>
                <span>{{ completedLessonCount }} completed lesson(s)</span>
              </article>
              <article class="student-profile-stat-card">
                <small>Quiz Average</small>
                <strong>{{ formatPercent(progress.quizAverage) }}</strong>
                <span>{{ lastActivitySummaryLabel }}</span>
              </article>
            </div>
          </article>

          <div class="student-profile-grid">
            <article class="panel student-profile-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Recent Badges</strong>
                </div>
                <button type="button" class="student-link-button" @click="openRewards">
                  View Rewards
                </button>
              </div>

              <div v-if="badgeCards.length" class="student-profile-badge-grid">
                <article v-for="badge in badgeCards" :key="badge.id" class="student-profile-badge-item">
                  <div class="student-profile-badge-icon">{{ badge.initials }}</div>
                  <strong>{{ badge.title }}</strong>
                  <span>{{ badge.subtitle }}</span>
                </article>
              </div>
              <StatePanel
                v-else
                icon="rewards"
                title="No badges yet"
                message="Badges will appear here as you keep learning."
              />
            </article>

            <article class="panel student-profile-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Recent Points Activity</strong>
                </div>
                <button type="button" class="student-link-button" @click="openRewards">
                  Open Rewards
                </button>
              </div>

              <div v-if="pointsFeed.length" class="student-profile-list">
                <article v-for="item in pointsFeed" :key="item.id" class="student-profile-list-row">
                  <div class="student-profile-list-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.subtitle }}</span>
                    <small>{{ item.time }}</small>
                  </div>
                  <strong class="student-profile-list-value">{{ item.value }}</strong>
                </article>
              </div>
              <StatePanel
                v-else
                icon="rewards"
                title="No points history"
                message="Points history will appear here after you complete activities."
              />
            </article>

            <article class="panel student-profile-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Recent Lessons</strong>
                </div>
                <button type="button" class="student-link-button" @click="openLearn">
                  Open Learn
                </button>
              </div>

              <div v-if="recentLessons.length" class="student-profile-list">
                <article v-for="lesson in recentLessons" :key="lesson.lessonId" class="student-profile-list-row">
                  <div class="student-profile-list-copy">
                    <strong>{{ lesson.lessonTitle }}</strong>
                    <span>{{ lesson.statusLabel }}</span>
                    <small>{{ lesson.lastAccessedLabel }}</small>
                  </div>
                  <strong class="student-profile-list-value">{{ lesson.progressLabel }}</strong>
                </article>
              </div>
              <StatePanel
                v-else
                icon="learn"
                title="No recent lessons"
                message="Your recent lesson activity will appear here after you start learning."
              />
            </article>

            <article class="panel student-profile-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Teacher Feedback</strong>
                </div>
                <button type="button" class="student-link-button" @click="openPractice">
                  Open Practice
                </button>
              </div>

              <div v-if="feedback.length" class="student-profile-feedback-list">
                <article v-for="item in feedback.slice(0, 4)" :key="item.id" class="student-profile-feedback-item">
                  <div class="student-profile-feedback-copy">
                    <strong>{{ feedbackTitle(item) }}</strong>
                    <p>{{ item.message }}</p>
                    <span>{{ item.teacherName }} - {{ feedbackTypeLabel(item) }}</span>
                  </div>
                  <small>{{ formatDateTime(item.createdAt) }}</small>
                </article>
              </div>
              <StatePanel
                v-else
                icon="help"
                title="No feedback yet"
                message="Teacher feedback will appear here after your teacher sends it."
              />
            </article>
          </div>
        </div>

        <aside class="student-profile-side">
          <article class="panel student-profile-card">
            <div class="student-section-heading">
              <strong>Learning Snapshot</strong>
            </div>

            <ProgressRing :progress="overallProgressPercent" title="Keep going" subtitle="Your student progress" />

            <div class="student-profile-side-stats">
              <div>
                <small>Lessons Started</small>
                <strong>{{ startedLessonCount }}</strong>
              </div>
              <div>
                <small>Activities Loaded</small>
                <strong>{{ activities.items.length }}</strong>
              </div>
              <div>
                <small>Weak Areas</small>
                <strong>{{ progress.weakAreas.length }}</strong>
              </div>
            </div>
          </article>

          <article class="panel student-profile-card">
            <div class="row-between">
              <div class="student-section-heading">
                <strong>Recent Notifications</strong>
              </div>
              <button
                type="button"
                class="student-link-button"
                :disabled="markingNotificationsRead || !hasUnreadNotifications"
                @click="markAllNotificationsRead"
              >
                {{ markAllNotificationsLabel }}
              </button>
            </div>

            <div v-if="notifications.length" class="student-profile-list">
              <NotificationLinkButton
                v-for="item in notifications"
                :key="item.id"
                :notification-id="item.id"
                :target-path="item.targetPath"
                fallback-to="/student/profile"
                @read="markStudentNotificationRead"
              >
                <article class="student-profile-list-row">
                  <div class="student-profile-list-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.message }}</span>
                    <small>{{ formatDateTime(item.createdAt) }}</small>
                  </div>
                  <StatusPill
                    :label="notificationStatusLabel(item.status)"
                    :tone="notificationStatusTone(item.status)"
                  />
                </article>
              </NotificationLinkButton>
            </div>
            <StatePanel
              v-else
              icon="bell"
              title="No notifications"
              message="Notifications from classes, payments, and learning reminders will appear here."
            />
          </article>

          <article class="panel student-profile-card">
            <div class="row-between">
              <div class="student-section-heading">
                <strong>Activity Library</strong>
              </div>
              <button type="button" class="student-link-button" @click="openPractice">
                Open Practice
              </button>
            </div>

            <div v-if="activityRows.length" class="student-profile-list">
              <article v-for="item in activityRows" :key="item.id" class="student-profile-list-row">
                <div class="student-profile-list-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.type }}</span>
                  <small>{{ item.tags }}</small>
                </div>
                <strong class="student-profile-list-value">{{ item.status }}</strong>
              </article>
            </div>
            <StatePanel
              v-else
              icon="practice"
              title="No activities available"
              message="The activity library is empty for this student right now."
            />
          </article>

          <article class="panel student-profile-card">
            <div class="student-section-heading">
              <strong>Account Context</strong>
            </div>

            <div class="student-profile-settings-list">
              <article class="student-profile-setting-row">
                <span>Preferred Language</span>
                <small>{{ preferredLanguageLabel }}</small>
              </article>
              <article class="student-profile-setting-row">
                <span>AI Helper Route</span>
                <small>Available from student navigation</small>
              </article>
              <article class="student-profile-setting-row">
                <span>Email</span>
                <small>{{ profileAccountEmailLabel }}</small>
              </article>
            </div>
          </article>

          <article class="panel student-profile-card">
            <div class="row-between">
              <div class="student-section-heading">
                <strong>Payments and Refunds</strong>
              </div>
            </div>

            <div v-if="payments.length" class="student-profile-payment-list">
              <article
                v-for="payment in payments.slice(0, 4)"
                :key="payment.paymentId"
                class="student-profile-payment-item"
              >
                <div>
                  <strong>{{ payment.orderNumber }}</strong>
                  <span>{{ payment.providerName }} - {{ payment.paymentStatus }}</span>
                </div>
                <strong>{{ formatCurrency(payment.amount, payment.currencyCode) }}</strong>
              </article>
            </div>
            <StatePanel
              v-else
              icon="finance"
              title="No payments"
              message="Payment records will appear here once you purchase content."
            />

            <div v-if="refunds.length" class="student-profile-refund-list">
              <article v-for="refund in refunds.slice(0, 3)" :key="refund.id" class="student-profile-refund-item">
                <div>
                  <strong>{{ refund.orderNumber }}</strong>
                  <span>{{ refund.reason }}</span>
                </div>
                <StatusPill :label="refund.refundStatus" tone="warning" />
              </article>
            </div>

            <ActionFormCard
              title="Request refund"
              subtitle="Submit a refund request for an existing order."
              icon="finance"
              submit-label="Submit refund request"
              submitting-label="Submitting..."
              :submitting="submittingRefund"
              :disabled="refundFormDisabled"
              @submit="submitRefund"
            >
              <div class="field">
                <label for="student-refund-order">Order</label>
                <select
                  id="student-refund-order"
                  v-model.number="refundForm.orderId"
                  class="select"
                  :disabled="refundFormDisabled || !refundablePayments.length"
                >
                  <option :value="0">
                    {{ refundOrderPlaceholder }}
                  </option>
                  <option v-for="payment in refundablePayments" :key="payment.paymentId" :value="payment.orderId">
                    {{ payment.orderNumber }} · {{ formatCurrency(payment.amount, payment.currencyCode) }} ·
                    {{ payment.providerName }}
                  </option>
                </select>
              </div>
              <div class="field">
                <label for="student-refund-reason">Reason</label>
                <textarea
                  id="student-refund-reason"
                  v-model="refundForm.reason"
                  class="textarea"
                  maxlength="1000"
                  placeholder="Explain why you need a refund."
                  :disabled="refundFormDisabled"
                />
              </div>
            </ActionFormCard>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { authApi, notificationsApi, studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import {
  activityLibrary,
  demoUsers,
  studentFeedbackList,
  studentPayments,
  studentProgress as studentProgressDemo,
  studentRefunds,
  studentRewards,
} from '@/demo/data'
import { resolveApiError } from '@/utils/apiError'
import {
  clampPercent,
  formatCurrency,
  formatDate,
  formatDateTime,
  formatPercent,
  nonNegativeInteger,
  toFiniteNumber,
} from '@/utils/format'
import { formatTeacherFeedbackType } from '@/utils/teacherFeedback'

type NotificationStatusTone = 'info' | 'warning'
type FeedbackSummary = {
  lessonTitle?: string | null
  quizTitle?: string | null
  feedbackType?: string | null
}

const router = useRouter()

const profileResource = useResource(() => authApi.me(), demoUsers.STUDENT)
const feedbackResource = useResource(() => studentApi.feedback(), studentFeedbackList)
const paymentsResource = useResource(() => studentApi.payments(), studentPayments)
const refundsResource = useResource(() => studentApi.refunds(), studentRefunds)
const rewardsResource = useResource(() => studentApi.rewards(), studentRewards)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const activitiesResource = useResource(() => studentApi.activityLibrary({ size: 20 }), activityLibrary)
const notificationsResource = useResource(() => notificationsApi.list({ size: 6 }), [])

const emptyProfile = {
  id: 0,
  fullName: 'Student',
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
const feedback = computed(() =>
  (feedbackResource.data.value ?? (feedbackResource.usingFallback.value ? studentFeedbackList : [])).map((item) => ({
    ...item,
    feedbackType: formatTeacherFeedbackType(item.feedbackType),
  })),
)
const payments = computed(
  () => paymentsResource.data.value ?? (paymentsResource.usingFallback.value ? studentPayments : []),
)
const refunds = computed(
  () => refundsResource.data.value ?? (refundsResource.usingFallback.value ? studentRefunds : []),
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
    paymentsResource.usingFallback.value ||
    refundsResource.usingFallback.value ||
    rewardsResource.usingFallback.value ||
    progressResource.usingFallback.value ||
    activitiesResource.usingFallback.value ||
    notificationsResource.usingFallback.value,
)
const pageLoading = computed(
  () =>
    profileResource.loading.value ||
    feedbackResource.loading.value ||
    paymentsResource.loading.value ||
    refundsResource.loading.value ||
    rewardsResource.loading.value ||
    progressResource.loading.value ||
    activitiesResource.loading.value ||
    notificationsResource.loading.value,
)
const pageError = computed(
  () =>
    profileResource.error.value ||
    feedbackResource.error.value ||
    paymentsResource.error.value ||
    refundsResource.error.value ||
    rewardsResource.error.value ||
    progressResource.error.value ||
    activitiesResource.error.value ||
    notificationsResource.error.value,
)
const noticeError = computed(() => actionError.value || pageError.value)
const profileNoticeDetail = computed(() => {
  if (noticeError.value) {
    return 'Your most recent profile snapshot remains visible so you can still review progress and account context.'
  }

  if (usingFallback.value) {
    return 'Preview mode keeps profile, rewards, feedback, and payment sections testable even without the full backend.'
  }

  if (actionSuccess.value) {
    return 'Your request completed and the profile snapshot has been refreshed.'
  }

  return null
})

const displayName = computed(
  () => profile.value.studentProfile?.displayName || profile.value.fullName.split(' ')[0] || 'Student',
)
const profileEmailLabel = computed(() => profile.value.email || 'No email available')
const profileAccountEmailLabel = computed(() => profile.value.email || '--')
const currentLevelNumber = computed(() => Math.max(1, nonNegativeInteger(rewards.value.currentLevelNumber, 1)))
const currentStreakCount = computed(() => nonNegativeInteger(rewards.value.streak.currentStreak))
const completedLessonCount = computed(() => nonNegativeInteger(progress.value.completedLessons))
const startedLessonCount = computed(() => nonNegativeInteger(progress.value.totalLessonsStarted))
const overallProgressPercent = computed(() => clampPercent(progress.value.overallCompletionPercent))
const currentLevelTitle = computed(
  () => profile.value.studentProfile?.currentLevel || `Level ${currentLevelNumber.value}`,
)
const accountModeLabel = computed(() => (usingFallback.value ? 'Preview profile data' : 'Live student account'))
const lastActivitySummaryLabel = computed(() => progress.value.lastActivitySummary || 'No recent summary available')
const streakDateLabel = computed(() =>
  rewards.value.streak.lastLearningDate
    ? `Last active ${formatDate(rewards.value.streak.lastLearningDate)}`
    : 'No learning date yet',
)
const preferredLanguageLabel = computed(() => {
  const value = profile.value.preferredLanguage || 'en'
  if (value === 'ms') return 'Bahasa Melayu'
  if (value === 'zh') return 'Chinese'
  return 'English'
})
const hasUnreadNotifications = computed(() => notifications.value.some((item) => item.status !== 'READ'))
const markAllNotificationsLabel = computed(() => (markingNotificationsRead.value ? 'Marking...' : 'Mark all read'))
const activeRefundOrderIds = computed(
  () =>
    new Set(
      refunds.value
        .filter((item) => item.refundStatus === 'PENDING' || item.refundStatus === 'APPROVED')
        .map((item) => item.orderId),
    ),
)
const refundablePayments = computed(() =>
  payments.value.filter((item) => item.paymentStatus === 'PAID' && !activeRefundOrderIds.value.has(item.orderId)),
)
const refundOrderPlaceholder = computed(() =>
  refundablePayments.value.length ? 'Select an order' : 'No refundable order available',
)
const refundReadOnly = computed(() => paymentsResource.usingFallback.value || refundsResource.usingFallback.value)
const refundFormDisabled = computed(() => refundReadOnly.value || submittingRefund.value)

const badgeCards = computed(() =>
  rewards.value.badges.slice(0, 4).map((badge) => ({
    id: badge.badgeId,
    title: badge.name,
    subtitle: badge.awardedAt ? `Awarded ${formatDate(badge.awardedAt)}` : badge.description,
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
    subtitle: item.sourceType,
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
          ? 'Completed'
          : completionPercent > 0
            ? 'In progress'
            : 'Not started',
      lastAccessedLabel: lesson.lastAccessedAt ? formatDateTime(lesson.lastAccessedAt) : 'No last access timestamp',
    }
  }),
)

const activityRows = computed(() =>
  activities.value.items.slice(0, 4).map((item) => ({
    id: item.id,
    title: item.title,
    type: item.type.replaceAll('_', ' '),
    tags: item.tags.length ? item.tags.join(', ') : 'No tags',
    status: item.completed ? 'Done' : 'Open',
  })),
)

const refundForm = reactive({
  orderId: 0,
  reason: '',
})
const submittingRefund = ref(false)
const markingNotificationsRead = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

watch(
  refundablePayments,
  (items) => {
    if (items.some((item) => item.orderId === refundForm.orderId)) return
    refundForm.orderId = items[0]?.orderId ?? 0
  },
  { immediate: true },
)

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
  return item.lessonTitle || item.quizTitle || 'Teacher feedback'
}

function feedbackTypeLabel(item: FeedbackSummary): string {
  return item.feedbackType || 'General'
}

function notificationStatusLabel(status: string): string {
  return status === 'READ' ? 'Read' : 'Unread'
}

function notificationStatusTone(status: string): NotificationStatusTone {
  return status === 'READ' ? 'info' : 'warning'
}

function markStudentNotificationRead(notificationId: number): void {
  notificationsResource.data.value = notifications.value.map((item) =>
    item.id === notificationId ? { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() } : item,
  )
}

async function markAllNotificationsRead(): Promise<void> {
  if (markingNotificationsRead.value) return
  if (notificationsResource.usingFallback.value) {
    actionError.value = 'Notifications cannot be changed while using preview data.'
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
    window.dispatchEvent(new CustomEvent('mandarin-spark:notification-read', { detail: { all: true } }))
    actionSuccess.value =
      result.updatedCount > 0
        ? `Marked ${result.updatedCount} notification(s) as read.`
        : 'All notifications are already read.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to mark notifications as read.')
  } finally {
    markingNotificationsRead.value = false
  }
}

async function submitRefund(): Promise<void> {
  if (submittingRefund.value) return
  if (refundReadOnly.value) {
    actionError.value = 'Refund requests cannot be submitted while preview payment data is active.'
    actionSuccess.value = null
    return
  }

  if (refundForm.orderId <= 0) {
    actionError.value = 'No order is available for refund right now.'
    actionSuccess.value = null
    return
  }

  if (!refundForm.reason.trim()) {
    actionError.value = 'Enter a refund reason before submitting this request.'
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingRefund.value = true
  try {
    await studentApi.createRefund({ orderId: refundForm.orderId, reason: refundForm.reason.trim() })
    await refundsResource.reload()
    refundForm.reason = ''
    refundForm.orderId = refundablePayments.value.find((item) => item.orderId !== refundForm.orderId)?.orderId ?? 0
    actionSuccess.value = 'Refund request submitted and refund history refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to submit this refund request.')
  } finally {
    submittingRefund.value = false
  }
}

function signedIntegerLabel(value: number): string {
  const rounded = Math.round(toFiniteNumber(value))
  return rounded > 0 ? `+${rounded}` : String(rounded)
}

function reloadAll(): void {
  void profileResource.reload()
  void feedbackResource.reload()
  void paymentsResource.reload()
  void refundsResource.reload()
  void rewardsResource.reload()
  void progressResource.reload()
  void activitiesResource.reload()
  void notificationsResource.reload()
}
</script>

<style scoped>
.student-profile-header h1,
.student-profile-header p {
  margin: 0;
}

.student-profile-kicker {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  padding: 7px 13px;
  border-radius: 999px;
  background: rgba(111, 76, 255, 0.09);
  color: var(--primary);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.student-profile-header h1 {
  font-family: 'Fredoka', sans-serif;
  font-size: clamp(2.5rem, 3.5vw, 3.8rem);
  line-height: 1.04;
}

.student-profile-header p {
  margin-top: 10px;
  color: var(--text-soft);
  font-size: 1.02rem;
}

.student-profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.42fr) 380px;
  gap: 18px;
}

.student-profile-main,
.student-profile-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.student-profile-hero,
.student-profile-card {
  border-radius: 24px;
}

.student-profile-hero {
  display: grid;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(135deg, #b89cff 0%, #8f69ff 45%, #855ef6 100%);
  box-shadow: 0 24px 48px rgba(136, 105, 255, 0.16);
}

.student-profile-identity {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 18px;
  align-items: center;
  color: white;
}

.student-profile-identity-copy h2 {
  margin: 0;
  font-family: 'Fredoka', sans-serif;
  font-size: 2.3rem;
}

.student-profile-identity-eyebrow {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.94);
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.student-profile-role {
  display: inline-flex;
  align-items: center;
  margin-top: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(109, 71, 255, 0.34);
  font-weight: 700;
}

.student-profile-identity-copy small {
  display: block;
  margin-top: 12px;
  color: rgba(255, 255, 255, 0.84);
}

.student-profile-member-chip {
  width: fit-content;
  margin-top: 16px;
  padding: 9px 14px;
  border-radius: 14px;
  background: rgba(255, 246, 223, 0.96);
  color: #cb7b13;
  font-weight: 700;
}

.student-profile-hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.student-profile-stat-card {
  min-height: 120px;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.98);
  display: grid;
  gap: 8px;
}

.student-profile-stat-card small,
.student-profile-stat-card span {
  color: var(--text-soft);
}

.student-profile-stat-card strong,
.student-profile-side-stats strong,
.student-profile-list-value {
  font-family: 'Fredoka', sans-serif;
}

.student-profile-stat-card strong {
  font-size: 1.9rem;
  line-height: 1;
}

.student-profile-grid,
.student-profile-side-stats {
  display: grid;
  gap: 18px;
}

.student-profile-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.student-profile-badge-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.student-profile-badge-item,
.student-profile-list-copy {
  display: grid;
  gap: 6px;
}

.student-profile-badge-item {
  text-align: center;
}

.student-profile-badge-item span,
.student-profile-list-copy span,
.student-profile-list-copy small,
.student-profile-feedback-copy span,
.student-profile-feedback-item small,
.student-profile-setting-row small,
.student-profile-payment-item span,
.student-profile-refund-item span {
  color: var(--text-soft);
}

.student-profile-badge-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto;
  border-radius: 20px;
  display: grid;
  place-items: center;
  background: linear-gradient(180deg, #8f69ff, #6946e8);
  color: white;
  font-family: 'Fredoka', sans-serif;
  font-size: 1.6rem;
}

.student-profile-list,
.student-profile-feedback-list,
.student-profile-payment-list,
.student-profile-refund-list,
.student-profile-settings-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.student-profile-list-row,
.student-profile-payment-item,
.student-profile-refund-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.student-profile-list-value {
  font-size: 1.05rem;
}

.student-profile-feedback-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.student-profile-feedback-item:last-child {
  border-bottom: 0;
}

.student-profile-feedback-copy p {
  margin: 6px 0 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.student-profile-side-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(232, 236, 247, 0.96);
}

.student-profile-side-stats small {
  display: block;
  color: var(--text-soft);
}

.student-profile-side-stats strong {
  display: block;
  margin-top: 6px;
  font-size: 1.7rem;
}

.student-profile-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 1420px) {
  .student-profile-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .student-profile-hero-stats,
  .student-profile-grid,
  .student-profile-badge-grid,
  .student-profile-side-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .student-profile-header {
    display: grid;
    gap: 10px;
  }

  .student-profile-identity,
  .student-profile-hero-stats,
  .student-profile-grid,
  .student-profile-badge-grid,
  .student-profile-side-stats,
  .student-profile-list-row,
  .student-profile-feedback-item,
  .student-profile-payment-item,
  .student-profile-refund-item {
    grid-template-columns: 1fr;
  }

  .student-profile-hero,
  .student-profile-card {
    border-radius: 22px;
  }

  .student-profile-stat-card {
    min-height: 0;
  }

  .student-profile-feedback-item {
    gap: 8px;
  }
}
</style>
