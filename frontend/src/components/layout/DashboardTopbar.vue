<template>
  <header class="dashboard-topbar">
    <div class="topbar-leading">
      <div class="topbar-context">
        <div class="topbar-role-badge" :class="`is-${roleTone}`">
          <IconGlyph :name="roleIcon" :size="16" />
          <span>{{ roleLabel }}</span>
        </div>

        <div class="topbar-context-copy">
          <strong>{{ contextTitle }}</strong>
          <span>{{ contextSubtitle }}</span>
        </div>

        <div v-if="showDateChip" class="topbar-date-chip hidden-mobile">
          <IconGlyph name="calendar" :size="16" class="topbar-date-icon" />
          <span>{{ dateLabel }}</span>
          <IconGlyph name="chevronDown" :size="14" class="topbar-date-caret" />
        </div>
      </div>
    </div>

    <div class="topbar-actions">
      <LanguageSwitcher size="compact" />

      <div v-if="showStreak" class="topbar-stat hidden-mobile">
        <span class="topbar-stat-fire"><IconGlyph name="streak" :size="18" /></span>
        <div class="stack topbar-stat-copy">
          <strong>{{ streakCount }}</strong>
          <span class="muted">day streak</span>
        </div>
      </div>

      <RouterLink
        v-if="showNotifications"
        :to="notificationPath"
        class="topbar-icon-button hidden-mobile"
        :aria-label="notificationAriaLabel"
      >
        <IconGlyph name="bell" :size="18" class="topbar-icon-glyph" />
        <span v-if="notificationCount" class="topbar-notification-badge" aria-hidden="true">{{
          notificationCount
        }}</span>
      </RouterLink>

      <button
        v-if="showSessionAction"
        class="topbar-signout"
        type="button"
        :disabled="signingOut"
        :aria-busy="signingOut"
        @click="handleSignOut"
      >
        {{ signOutLabel }}
      </button>

      <RouterLink :to="profilePath" class="topbar-user">
        <PortraitAvatar class="topbar-user-portrait" :size="52" :kind="userPortraitKind" />
        <div class="topbar-user-copy">
          <strong class="topbar-user-name">{{ displayName }}</strong>
          <span class="topbar-user-role">{{ profileLabel }}</span>
        </div>
        <IconGlyph name="chevronDown" :size="14" class="topbar-user-caret" />
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { notificationsApi, studentApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'

interface RoleTopbarCopy {
  readonly tone: string
  readonly label: string
  readonly icon: string
  readonly profileLabel: string
  readonly defaultDisplayName: string
  readonly contextTitle: string
  readonly contextSubtitle: string
}

const ROLE_TOPBAR_COPY: Record<UserRole, RoleTopbarCopy> = {
  ADMIN: {
    tone: 'admin',
    label: 'Admin hub',
    icon: 'verification',
    profileLabel: 'Platform owner',
    defaultDisplayName: 'Admin',
    contextTitle: 'Keep operations clear and safe',
    contextSubtitle: 'Queue health, moderation, finance, and audit context stay one glance away.',
  },
  TEACHER: {
    tone: 'teacher',
    label: 'Teacher workspace',
    icon: 'classes',
    profileLabel: 'Teaching profile',
    defaultDisplayName: 'Li Wei',
    contextTitle: 'Guide each learner with clarity',
    contextSubtitle: 'Lessons, classes, and feedback stay connected across your teaching flow.',
  },
  STUDENT: {
    tone: 'student',
    label: 'Student journey',
    icon: 'learn',
    profileLabel: 'Learner profile',
    defaultDisplayName: 'Learner',
    contextTitle: 'Learning dashboard',
    contextSubtitle: 'Lessons, rewards, classes, and AI help are tuned for young Mandarin learners.',
  },
}

const GUEST_TOPBAR_COPY = {
  tone: 'guest',
  label: 'Guest preview',
  icon: 'courses',
  profileLabel: 'Preview workspace',
  displayName: 'Browse as guest',
  contextTitle: 'Explore lessons before you commit',
  contextSubtitle: 'Catalog, pricing, and support are visible without sign-in.',
} as const

const EMPTY_STREAK = { currentStreak: 0, bestStreak: 0, lastLearningDate: null } as const
const NOTIFICATION_READ_EVENT = 'mandarin-spark:notification-read'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const isGuestCatalog = computed(() => route.path.startsWith('/courses'))
const activeRoleCopy = computed(() => ROLE_TOPBAR_COPY[role.value])
const unreadResource = useResource(
  () => (showNotifications.value ? notificationsApi.unreadCount() : Promise.resolve({ unreadCount: 0 })),
  { unreadCount: 0 },
  false,
)
const streakResource = useResource(
  () =>
    role.value === 'STUDENT' && !isGuestCatalog.value && !auth.isPreviewSession
      ? studentApi.streak()
      : Promise.resolve(EMPTY_STREAK),
  undefined,
)
const streakCount = computed(() => (role.value === 'STUDENT' ? (streakResource.data.value?.currentStreak ?? 0) : 0))
const roleTone = computed(() => (isGuestCatalog.value ? GUEST_TOPBAR_COPY.tone : activeRoleCopy.value.tone))
const roleLabel = computed(() => (isGuestCatalog.value ? GUEST_TOPBAR_COPY.label : activeRoleCopy.value.label))
const roleIcon = computed(() => (isGuestCatalog.value ? GUEST_TOPBAR_COPY.icon : activeRoleCopy.value.icon))

function firstDisplayNamePart(name: string): string | null {
  return name.trim().split(/\s+/)[0] || null
}

const displayName = computed(() => {
  if (isGuestCatalog.value) return GUEST_TOPBAR_COPY.displayName
  if (role.value === 'STUDENT') {
    return (
      auth.currentUser?.studentProfile?.displayName ??
      firstDisplayNamePart(auth.displayName) ??
      activeRoleCopy.value.defaultDisplayName
    )
  }

  return auth.displayName || activeRoleCopy.value.defaultDisplayName
})

const userPortraitKind = computed<'female' | 'male' | 'hijab' | 'student'>(() =>
  isGuestCatalog.value || role.value === 'STUDENT' ? 'student' : 'female',
)

const profilePath = computed(() => {
  if (isGuestCatalog.value) return '/auth'

  switch (role.value) {
    case 'TEACHER':
      return '/teacher/profile'
    case 'ADMIN':
      return '/admin/settings'
    default:
      return '/student/profile'
  }
})
const notificationPath = computed(() =>
  role.value === 'ADMIN' ? '/admin/settings?section=support' : profilePath.value,
)
const profileLabel = computed(() =>
  isGuestCatalog.value ? GUEST_TOPBAR_COPY.profileLabel : activeRoleCopy.value.profileLabel,
)
const contextTitle = computed(() => {
  if (isGuestCatalog.value) return GUEST_TOPBAR_COPY.contextTitle
  if (role.value === 'ADMIN')
    return route.path.includes('/analytics') ? 'Monitor platform signals' : activeRoleCopy.value.contextTitle
  if (role.value === 'TEACHER')
    return route.path.includes('/classes') ? 'Run live classes with confidence' : activeRoleCopy.value.contextTitle
  return route.path.includes('/profile') ? 'Celebrate steady progress' : activeRoleCopy.value.contextTitle
})
const contextSubtitle = computed(() =>
  isGuestCatalog.value ? GUEST_TOPBAR_COPY.contextSubtitle : activeRoleCopy.value.contextSubtitle,
)

const isDashboardHome = computed(() => route.path.endsWith('/dashboard'))
const showNotifications = computed(() => {
  if (isGuestCatalog.value) return false
  if (!auth.isAuthenticated) return false
  if (auth.isPreviewSession) return false
  if (role.value === 'ADMIN') return !isDashboardHome.value
  if (role.value === 'TEACHER') return !route.path.includes('/teacher/content')
  return !route.path.includes('/student/learn')
})
const showStreak = computed(() => role.value === 'STUDENT' && !isGuestCatalog.value && streakCount.value > 0)
const showDateChip = computed<boolean>(() => role.value === 'TEACHER' && route.path.includes('/teacher/classes'))

const dateFormatter = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  weekday: 'short',
})
const dateLabel = computed<string>(() => dateFormatter.format(new Date()))

const notificationCount = computed<number>(() =>
  showNotifications.value ? (unreadResource.data.value?.unreadCount ?? 0) : 0,
)
const notificationAriaLabel = computed<string>(() =>
  notificationCount.value > 0 ? `Notifications - ${notificationCount.value} unread` : 'Notifications',
)
const showSessionAction = computed<boolean>(() => auth.isAuthenticated && !isGuestCatalog.value)
const signingOut = ref<boolean>(false)
const signOutLabel = computed<string>(() => (signingOut.value ? 'Signing out...' : 'Sign out'))

function refreshUnreadCount(): void {
  if (!showNotifications.value) return
  void unreadResource.reload()
}

async function handleSignOut(): Promise<void> {
  if (signingOut.value) return

  signingOut.value = true
  try {
    await auth.logout()
    await router.replace('/auth')
  } catch {
    /* sign-out failure — UI recovers on next interaction */
  } finally {
    signingOut.value = false
  }
}

onMounted(() => {
  refreshUnreadCount()
  window.addEventListener(NOTIFICATION_READ_EVENT, refreshUnreadCount)
})

watch(showNotifications, (visible) => {
  if (visible) {
    refreshUnreadCount()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener(NOTIFICATION_READ_EVENT, refreshUnreadCount)
})
</script>
