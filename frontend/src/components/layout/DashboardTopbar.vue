<template>
  <header class="dashboard-topbar">
    <div class="topbar-leading">
      <button
        type="button"
        class="btn btn-ghost btn-icon topbar-menu-button"
        aria-controls="app-sidebar"
        :aria-expanded="navOpen"
        :aria-label="t('dashboard.openNavigation')"
        @click="emit('toggle-navigation')"
      >
        <IconGlyph name="menu" :size="22" />
      </button>
      <AppBrand compact class="topbar-brand" />

      <div class="topbar-context">
        <div class="topbar-role-badge" :class="`is-${roleTone}`">
          <IconGlyph :name="roleIcon" :size="16" />
          <span>{{ roleLabel }}</span>
        </div>

        <div class="topbar-context-copy">
          <strong>{{ contextTitle }}</strong>
          <span>{{ contextSubtitle }}</span>
        </div>

        <div v-if="showDateChip" class="topbar-date-chip">
          <IconGlyph name="calendar" :size="16" class="topbar-date-icon" />
          <span>{{ dateLabel }}</span>
        </div>
      </div>
    </div>

    <div class="topbar-actions">
      <LanguageSwitcher size="compact" class="topbar-language" />

      <div v-if="showStreak" class="topbar-stat">
        <span class="topbar-stat-fire"><IconGlyph name="streak" :size="18" /></span>
        <div class="topbar-stat-copy">
          <strong>{{ streakCount }}</strong>
          <span>{{ t('dashboard.dayStreak') }}</span>
        </div>
      </div>

      <RouterLink
        v-if="showNotifications"
        :to="notificationPath"
        class="topbar-icon-button"
        :aria-label="notificationAriaLabel"
        data-testid="topbar-notifications"
      >
        <IconGlyph name="bell" :size="20" class="topbar-icon-glyph" />
        <span v-if="notificationCount" class="topbar-notification-badge" aria-hidden="true">{{
          notificationBadgeLabel
        }}</span>
      </RouterLink>

      <div v-if="showUserMenu" ref="userMenuRef" class="topbar-user-menu">
        <button
          type="button"
          class="topbar-user"
          aria-haspopup="menu"
          aria-controls="topbar-user-dropdown"
          :aria-expanded="userMenuOpen"
          @click="toggleUserMenu"
        >
          <PortraitAvatar class="topbar-user-portrait" :size="36" :name="displayName" />
          <span class="topbar-user-copy">
            <strong class="topbar-user-name">{{ displayName }}</strong>
            <span class="topbar-user-role">{{ profileLabel }}</span>
          </span>
          <IconGlyph name="chevronDown" :size="16" class="topbar-user-caret" />
        </button>

        <div
          v-show="userMenuOpen"
          id="topbar-user-dropdown"
          ref="userDropdownRef"
          class="menu topbar-user-dropdown"
          role="menu"
          :aria-label="t('dashboard.accountMenu')"
          @keydown="handleMenuKeydown"
        >
          <RouterLink
            v-if="role !== 'ADMIN'"
            :to="profilePath"
            class="menu-item"
            role="menuitem"
            @click="closeUserMenu"
          >
            <IconGlyph name="profile" :size="18" />
            <span>{{ globalT('nav.profile') }}</span>
          </RouterLink>
          <RouterLink
            v-if="showAccountLink"
            :to="accountPath"
            class="menu-item"
            role="menuitem"
            data-testid="topbar-account"
            @click="closeUserMenu"
          >
            <IconGlyph name="account" :size="18" />
            <span>{{ globalT('nav.account') }}</span>
          </RouterLink>
          <template v-if="showSessionAction">
            <div class="menu-divider" role="separator" />
            <button
              type="button"
              class="menu-item is-danger"
              role="menuitem"
              :disabled="signingOut"
              :aria-busy="signingOut"
              @click="handleSignOut"
            >
              <IconGlyph name="logout" :size="18" />
              <span>{{ signOutLabel }}</span>
            </button>
          </template>
        </div>
      </div>

      <RouterLink v-else :to="profilePath" class="topbar-user">
        <PortraitAvatar class="topbar-user-portrait" :size="36" />
        <span class="topbar-user-copy">
          <strong class="topbar-user-name">{{ displayName }}</strong>
          <span class="topbar-user-role">{{ profileLabel }}</span>
        </span>
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { notificationsApi, studentApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { useResource } from '@/composables/useResource'
import { adminNotifications, studentNotifications, teacherNotifications } from '@/demo/data'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { getDisplayLocale } from '@/utils/format'
import { NOTIFICATION_READ_EVENT } from '@/utils/notifications'

import { useOverlayFocus } from './useOverlayFocus'

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
    label: 'dashboard.adminHub',
    icon: 'verification',
    profileLabel: 'dashboard.platformOwner',
    defaultDisplayName: 'dashboard.adminName',
    contextTitle: 'dashboard.adminContext',
    contextSubtitle: 'dashboard.adminSubtitle',
  },
  TEACHER: {
    tone: 'teacher',
    label: 'dashboard.teacherWorkspace',
    icon: 'classes',
    profileLabel: 'dashboard.teachingProfile',
    defaultDisplayName: 'dashboard.teacherName',
    contextTitle: 'dashboard.teacherContext',
    contextSubtitle: 'dashboard.teacherSubtitle',
  },
  STUDENT: {
    tone: 'student',
    label: 'dashboard.studentJourney',
    icon: 'learn',
    profileLabel: 'dashboard.learnerProfile',
    defaultDisplayName: 'dashboard.learnerName',
    contextTitle: 'dashboard.studentContext',
    contextSubtitle: 'dashboard.studentSubtitle',
  },
}

const GUEST_TOPBAR_COPY = {
  tone: 'guest',
  label: 'dashboard.guestPreview',
  icon: 'courses',
  profileLabel: 'dashboard.previewWorkspace',
  displayName: 'dashboard.browseGuest',
  contextTitle: 'dashboard.guestContext',
  contextSubtitle: 'dashboard.guestSubtitle',
} as const

interface Props {
  /** Whether the off-canvas navigation (below 900px) is open; drives the menu button's aria-expanded. */
  navOpen?: boolean
}

withDefaults(defineProps<Props>(), { navOpen: false })

const emit = defineEmits<{
  'toggle-navigation': []
}>()

const EMPTY_STREAK = { currentStreak: 0, bestStreak: 0, lastLearningDate: null } as const

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t: globalT } = useI18n({ useScope: 'global' })
const { t, locale } = useI18n({
  messages: {
    en: { dashboard: dashboardMessages.en },
    ms: { dashboard: dashboardMessages.ms },
    'zh-CN': { dashboard: dashboardMessages['zh-CN'] },
  },
})

// A signed-in user keeps their own role chrome, also on the public catalogue (/courses); guests use previewRole.
const role = computed<UserRole>(
  () => (auth.isAuthenticated ? auth.activeRole : (route.meta.previewRole as UserRole | undefined)) ?? 'STUDENT',
)
const isGuestCatalog = computed(() => route.path.startsWith('/courses') && !auth.isAuthenticated)
const activeRoleCopy = computed(() => ROLE_TOPBAR_COPY[role.value])
const PREVIEW_NOTIFICATIONS = {
  STUDENT: studentNotifications,
  TEACHER: teacherNotifications,
  ADMIN: adminNotifications,
} as const
const unreadResource = useResource(
  () => (showNotifications.value ? notificationsApi.unreadCount() : Promise.resolve({ unreadCount: 0 })),
  () => ({ unreadCount: PREVIEW_NOTIFICATIONS[role.value].filter((item) => item.status === 'UNREAD').length }),
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
const roleLabel = computed(() => t(isGuestCatalog.value ? GUEST_TOPBAR_COPY.label : activeRoleCopy.value.label))
const roleIcon = computed(() => (isGuestCatalog.value ? GUEST_TOPBAR_COPY.icon : activeRoleCopy.value.icon))

function firstDisplayNamePart(name: string): string | null {
  return name.trim().split(/\s+/)[0] || null
}

const displayName = computed(() => {
  if (isGuestCatalog.value) return t(GUEST_TOPBAR_COPY.displayName)
  if (role.value === 'STUDENT') {
    return (
      auth.currentUser?.studentProfile?.displayName ??
      firstDisplayNamePart(auth.displayName) ??
      t(activeRoleCopy.value.defaultDisplayName)
    )
  }

  return auth.displayName || t(activeRoleCopy.value.defaultDisplayName)
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
const rolePathPrefix = computed(() =>
  role.value === 'TEACHER' ? '/teacher' : role.value === 'ADMIN' ? '/admin' : '/student',
)
// D20: the bell opens the role's notifications page; the badge shows the server unread count.
const notificationPath = computed(() => `${rolePathPrefix.value}/notifications`)
const accountPath = computed(() => `${rolePathPrefix.value}/account`)

const profileLabel = computed(() =>
  t(isGuestCatalog.value ? GUEST_TOPBAR_COPY.profileLabel : activeRoleCopy.value.profileLabel),
)
const contextTitle = computed(() => {
  if (isGuestCatalog.value) return t(GUEST_TOPBAR_COPY.contextTitle)
  if (role.value === 'ADMIN')
    return route.path.includes('/analytics')
      ? t('dashboard.adminAnalyticsContext')
      : t(activeRoleCopy.value.contextTitle)
  if (role.value === 'TEACHER')
    return route.path.includes('/classes') ? t('dashboard.teacherClassesContext') : t(activeRoleCopy.value.contextTitle)
  return route.path.includes('/profile') ? t('dashboard.studentProfileContext') : t(activeRoleCopy.value.contextTitle)
})
const contextSubtitle = computed(() =>
  t(isGuestCatalog.value ? GUEST_TOPBAR_COPY.contextSubtitle : activeRoleCopy.value.contextSubtitle),
)

const hasWorkspaceSession = computed(() => auth.isAuthenticated || auth.isPreviewSession)
const showNotifications = computed(() => !isGuestCatalog.value && hasWorkspaceSession.value)
const showAccountLink = computed(() => !isGuestCatalog.value && hasWorkspaceSession.value)
const showStreak = computed(() => role.value === 'STUDENT' && !isGuestCatalog.value && streakCount.value > 0)
const showDateChip = computed<boolean>(() => role.value === 'TEACHER' && route.path.includes('/teacher/classes'))

const dateLabel = computed<string>(() =>
  new Intl.DateTimeFormat(getDisplayLocale(locale.value), {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)

const notificationCount = computed<number>(() =>
  showNotifications.value ? (unreadResource.data.value?.unreadCount ?? 0) : 0,
)
const notificationBadgeLabel = computed<string>(() =>
  notificationCount.value > 99 ? '99+' : String(notificationCount.value),
)
const notificationAriaLabel = computed<string>(() =>
  notificationCount.value > 0
    ? t('dashboard.unreadNotifications', { count: notificationCount.value })
    : t('dashboard.notifications'),
)
const showSessionAction = computed<boolean>(() => auth.isAuthenticated && !isGuestCatalog.value)
const showUserMenu = computed<boolean>(() => !isGuestCatalog.value)

const userMenuOpen = ref<boolean>(false)
const userMenuRef = ref<HTMLElement | null>(null)
const userDropdownRef = ref<HTMLElement | null>(null)

const { focusables: menuItems } = useOverlayFocus({
  active: userMenuOpen,
  container: userDropdownRef,
  onClose: closeUserMenu,
})

function toggleUserMenu(): void {
  userMenuOpen.value = !userMenuOpen.value
}

function closeUserMenu(): void {
  userMenuOpen.value = false
}

function handleMenuKeydown(event: KeyboardEvent): void {
  const items = menuItems()
  if (!items.length) return
  const index = items.indexOf(document.activeElement as HTMLElement)
  let next: HTMLElement | undefined
  if (event.key === 'ArrowDown') next = items[(index + 1) % items.length]
  else if (event.key === 'ArrowUp') next = items[(index - 1 + items.length) % items.length]
  else if (event.key === 'Home') next = items[0]
  else if (event.key === 'End') next = items[items.length - 1]
  else if (event.key === 'Tab') closeUserMenu()
  if (next) {
    event.preventDefault()
    next.focus()
  }
}

function handleDocumentPointer(event: PointerEvent): void {
  if (userMenuOpen.value && !userMenuRef.value?.contains(event.target as Node)) closeUserMenu()
}
const signingOut = ref<boolean>(false)
const signOutLabel = computed<string>(() => (signingOut.value ? t('dashboard.signingOut') : t('dashboard.signOut')))

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
  document.addEventListener('pointerdown', handleDocumentPointer)
})

watch(showNotifications, (visible) => {
  if (visible) {
    refreshUnreadCount()
  }
})

watch(
  () => route.fullPath,
  () => closeUserMenu(),
)

onBeforeUnmount(() => {
  window.removeEventListener(NOTIFICATION_READ_EVENT, refreshUnreadCount)
  document.removeEventListener('pointerdown', handleDocumentPointer)
})
</script>
