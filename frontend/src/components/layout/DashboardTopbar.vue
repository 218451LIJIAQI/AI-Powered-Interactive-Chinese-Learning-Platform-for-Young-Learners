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
        :aria-label="notificationCount > 0 ? `Notifications — ${notificationCount} unread` : 'Notifications'"
      >
        <IconGlyph name="bell" :size="18" class="topbar-icon-glyph" />
        <span v-if="notificationCount" class="topbar-notification-badge" aria-hidden="true">{{ notificationCount }}</span>
      </RouterLink>

      <button
        v-if="showSessionAction"
        class="topbar-signout"
        type="button"
        :disabled="signingOut"
        :aria-busy="signingOut"
        @click="handleSignOut"
      >
        {{ signingOut ? 'Signing out...' : 'Sign out' }}
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
/**
 * Authenticated topbar — page title, breadcrumb, notification
 * bell, mobile-nav toggle, language switcher, and the user menu.
 *
 * Polls the unread-notification count and listens for the
 * `mandarin-spark:notification-read` custom event so the badge
 * decrements in real time when the user opens an item, without
 * a full refresh.
 *
 * Click-outside is handled by a document listener wired in
 * onMounted / detached in onBeforeUnmount so the dropdown closes
 * cleanly without leaking handlers across navigations.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { notificationsApi, studentApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const isGuestCatalog = computed(() => route.path.startsWith('/courses'))
const unreadResource = useResource(
  () => (showNotifications.value ? notificationsApi.unreadCount() : Promise.resolve({ unreadCount: 0 })),
  { unreadCount: 0 },
  false,
)
const streakResource = useResource(
  () =>
    role.value === 'STUDENT' && !isGuestCatalog.value && !auth.isPreviewSession
      ? studentApi.streak()
      : Promise.resolve({ currentStreak: 0, bestStreak: 0, lastLearningDate: null }),
  undefined,
)
const streakCount = computed(() => (role.value === 'STUDENT' ? streakResource.data.value?.currentStreak ?? 0 : 0))
const roleTone = computed(() => {
  if (isGuestCatalog.value) return 'guest'
  if (role.value === 'ADMIN') return 'admin'
  if (role.value === 'TEACHER') return 'teacher'
  return 'student'
})
const roleLabel = computed(() => {
  if (isGuestCatalog.value) return 'Guest preview'
  if (role.value === 'ADMIN') return 'Admin hub'
  if (role.value === 'TEACHER') return 'Teacher workspace'
  return 'Student journey'
})
const roleIcon = computed(() => {
  if (isGuestCatalog.value) return 'courses'
  if (role.value === 'ADMIN') return 'verification'
  if (role.value === 'TEACHER') return 'classes'
  return 'learn'
})

const displayName = computed(() => {
  if (isGuestCatalog.value) return 'Browse as guest'
  if (role.value === 'STUDENT') {
    return auth.currentUser?.studentProfile?.displayName ?? auth.displayName.split(' ')[0] ?? 'Learner'
  }

  if (role.value === 'ADMIN') {
    return 'Admin'
  }

  return auth.displayName || 'Li Wei'
})

const userPortraitKind = computed<'female' | 'male' | 'hijab' | 'student'>(() => {
  if (isGuestCatalog.value) return 'student'
  if (role.value === 'STUDENT') return 'student'
  if (role.value === 'ADMIN') return 'female'
  return 'female'
})

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
const notificationPath = computed(() => (role.value === 'ADMIN' ? '/admin/settings?section=support' : profilePath.value))
const profileLabel = computed(() => {
  if (isGuestCatalog.value) return 'Preview workspace'
  if (role.value === 'ADMIN') return 'Platform owner'
  if (role.value === 'TEACHER') return 'Teaching profile'
  return 'Learner profile'
})
const contextTitle = computed(() => {
  if (isGuestCatalog.value) return 'Explore lessons before you commit'
  if (role.value === 'ADMIN') return route.path.includes('/analytics') ? 'Monitor platform signals' : 'Keep operations clear and safe'
  if (role.value === 'TEACHER') return route.path.includes('/classes') ? 'Run live classes with confidence' : 'Guide each learner with clarity'
  return route.path.includes('/profile') ? 'Celebrate steady progress' : 'Learning dashboard'
})
const contextSubtitle = computed(() => {
  if (isGuestCatalog.value) return 'Catalog, pricing, and support are visible without sign-in.'
  if (role.value === 'ADMIN') return 'Queue health, moderation, finance, and audit context stay one glance away.'
  if (role.value === 'TEACHER') return 'Lessons, classes, and feedback stay connected across your teaching flow.'
  return 'Lessons, rewards, classes, and AI help are tuned for young Mandarin learners.'
})

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
const showDateChip = computed(() => role.value === 'TEACHER' && route.path.includes('/teacher/classes'))
const dateLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)

const notificationCount = computed(() => (showNotifications.value ? unreadResource.data.value?.unreadCount ?? 0 : 0))
const showSessionAction = computed(() => auth.isAuthenticated && !isGuestCatalog.value)
const signingOut = ref(false)

function refreshUnreadCount() {
  if (!showNotifications.value) {
    return
  }

  void unreadResource.reload()
}

async function handleSignOut() {
  if (signingOut.value) {
    return
  }

  signingOut.value = true
  try {
    await auth.logout()
    await router.replace('/auth')
  } finally {
    signingOut.value = false
  }
}

onMounted(() => {
  refreshUnreadCount()
  window.addEventListener('mandarin-spark:notification-read', refreshUnreadCount)
})

watch(showNotifications, (visible) => {
  if (visible) {
    refreshUnreadCount()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('mandarin-spark:notification-read', refreshUnreadCount)
})
</script>
