<template>
  <aside
    id="app-sidebar"
    ref="sidebarRef"
    class="sidebar"
    :class="[sidebarClass, { 'is-open': open }]"
    :inert="drawerClosed"
    :role="drawerActive ? 'dialog' : undefined"
    :aria-modal="drawerActive ? 'true' : undefined"
    :aria-label="drawerActive ? t('dashboard.navigationMenu') : undefined"
  >
    <div class="sidebar-header">
      <AppBrand />
      <button
        ref="closeButtonRef"
        type="button"
        class="btn btn-ghost btn-icon sidebar-close"
        :aria-label="t('dashboard.closeNavigation')"
        @click="emit('close')"
      >
        <IconGlyph name="close" :size="20" />
      </button>
    </div>

    <div class="sidebar-intro">
      <div class="sidebar-intro-copy">
        <span class="sidebar-intro-badge">{{ roleBadge }}</span>
        <strong>{{ roleHeading }}</strong>
      </div>
      <PandaIllustration
        class="sidebar-intro-panda"
        :size="showcasePandaSize"
        :outfit="showcaseOutfit"
        :accent="showcaseAccent"
        compact
      />
      <p>{{ roleSummary }}</p>
    </div>

    <div v-for="group in navigationGroups" :key="group.key" class="sidebar-nav-group">
      <div v-if="group.title" class="sidebar-section-label">{{ group.title }}</div>
      <nav class="sidebar-nav" :aria-label="group.ariaLabel">
        <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
          <a
            :href="href"
            class="sidebar-link"
            :class="{ 'router-link-active': isLinkActive(item.to) }"
            :aria-current="isLinkActive(item.to) ? 'page' : undefined"
            :data-label="item.label"
            @click="navigate"
          >
            <IconGlyph :name="item.icon" :size="20" class="sidebar-icon" />
            <span>{{ item.label }}</span>
          </a>
        </RouterLink>
      </nav>
    </div>

    <div class="sidebar-language">
      <LanguageSwitcher size="compact" />
    </div>

    <div class="sidebar-footer">
      <div class="sidebar-footer-row">
        <span class="sidebar-footer-badge"><IconGlyph :name="footerIcon" :size="20" /></span>
        <div class="sidebar-footer-copy">
          <strong class="sidebar-footer-title">{{ footerTitle }}</strong>
          <span class="sidebar-footer-subtitle">{{ footerSubtitle }}</span>
        </div>
      </div>
      <template v-if="showFooterProgress">
        <ProgressBar
          class="is-sm sidebar-footer-progress"
          :progress="progress"
          :tone="footerProgressTone"
          :label="progressLabel"
        />
        <p class="sidebar-footer-meta">
          <strong>{{ progressLabel }}</strong>
          <span>{{ progressCaption }}</span>
        </p>
      </template>
      <p v-else-if="showSignInHint" class="sidebar-footer-hint">{{ t('dashboard.signInHint') }}</p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import { publicNav, roleNavigation, roleNavigationGroups } from '@/config/navigation'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'
import type { NavGroup, NavItem, RoleNavItem } from '@/config/navigation'

import AppBrand from './AppBrand.vue'
import { DRAWER_MEDIA_QUERY, useMediaQuery, useOverlayFocus } from './useOverlayFocus'

type LocalizedNavItem = NavItem & { label: string }

interface NavigationGroup {
  readonly key: string
  readonly title: string
  readonly ariaLabel: string
  readonly items: LocalizedNavItem[]
}

interface RoleSidebarCopy {
  readonly badge: string
  readonly heading: string
  readonly summary: string
  readonly footerIcon: string
}

interface Props {
  /** Drawer state below 900px; the sidebar is always visible on wider screens. */
  open?: boolean
}

const props = withDefaults(defineProps<Props>(), { open: false })

const emit = defineEmits<{
  close: []
}>()

const ROLE_SIDEBAR_COPY: Record<UserRole, RoleSidebarCopy> = {
  ADMIN: {
    badge: 'dashboard.adminBadge',
    heading: 'dashboard.adminHeading',
    summary: 'dashboard.adminSummary',
    footerIcon: 'shield',
  },
  TEACHER: {
    badge: 'dashboard.teacherBadge',
    heading: 'dashboard.teacherHeading',
    summary: 'dashboard.teacherSummary',
    footerIcon: 'teacher',
  },
  STUDENT: {
    badge: 'dashboard.studentBadge',
    heading: 'dashboard.studentHeading',
    summary: 'dashboard.studentSummary',
    footerIcon: 'trophy',
  },
}

const PUBLIC_NAVIGATION_GROUP = {
  key: 'explore',
  title: 'dashboard.explore',
  ariaLabel: 'dashboard.publicNavigation',
} as const

const PREVIEW_PROGRESS_PERCENT = 55
const POINTS_PER_PROGRESS_PERCENT = 4
const showcaseAccent = '#f6d056'

const auth = useAuthStore()
const route = useRoute()
const { t: globalT } = useI18n({ useScope: 'global' })
const { t } = useI18n({
  messages: {
    en: { dashboard: dashboardMessages.en },
    ms: { dashboard: dashboardMessages.ms },
    'zh-CN': { dashboard: dashboardMessages['zh-CN'] },
  },
})

const isPreview = computed(() => auth.isPreviewSession)
const studentProfile = computed(() => auth.currentUser?.studentProfile ?? null)

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const isGuestCatalog = computed(() => route.path.startsWith('/courses'))
const sceneRole = computed(() => (isGuestCatalog.value ? 'student' : role.value.toLowerCase()))

const activeRoleCopy = computed(() => ROLE_SIDEBAR_COPY[role.value])

const sidebarClass = computed(() => `sidebar--${sceneRole.value}`)
const roleBadge = computed(() => t(isGuestCatalog.value ? 'dashboard.publicPreview' : activeRoleCopy.value.badge))
const roleHeading = computed(() => t(isGuestCatalog.value ? 'dashboard.guestHeading' : activeRoleCopy.value.heading))
const roleSummary = computed(() => t(isGuestCatalog.value ? 'dashboard.guestSummary' : activeRoleCopy.value.summary))

function localizeItem(item: NavItem): LocalizedNavItem {
  return {
    icon: item.icon,
    label: globalT(item.labelKey),
    labelKey: item.labelKey,
    to: item.to,
  }
}

function buildNavigationGroup(config: NavGroup, roleItems: readonly RoleNavItem[]): NavigationGroup {
  return {
    key: config.key,
    title: t(config.titleKey),
    ariaLabel: t(config.ariaLabelKey),
    items: roleItems.filter((item) => item.group === config.key).map(localizeItem),
  }
}

const navigationGroups = computed<NavigationGroup[]>(() => {
  if (isGuestCatalog.value) {
    return [
      {
        key: PUBLIC_NAVIGATION_GROUP.key,
        title: t(PUBLIC_NAVIGATION_GROUP.title),
        ariaLabel: t(PUBLIC_NAVIGATION_GROUP.ariaLabel),
        items: publicNav.map(localizeItem),
      },
    ]
  }

  const roleItems: readonly RoleNavItem[] = roleNavigation[role.value]
  const groups: readonly NavGroup[] = roleNavigationGroups[role.value]
  return groups.map((group) => buildNavigationGroup(group, roleItems)).filter((group) => group.items.length > 0)
})

const footerTitle = computed(() => {
  if (isGuestCatalog.value) return t('dashboard.publicCatalog')

  switch (role.value) {
    case 'TEACHER':
      return auth.displayName || t('dashboard.teacherName')
    case 'ADMIN':
      return t('dashboard.adminWorkspace')
    default:
      if (!isPreview.value && studentProfile.value?.currentLevel) {
        return studentProfile.value.currentLevel
      }
      return isPreview.value ? t('dashboard.previewLearner') : t('dashboard.studentBadge')
  }
})

const footerSubtitle = computed(() => {
  if (isGuestCatalog.value) return t('dashboard.browseBeforeSignIn')

  switch (role.value) {
    case 'TEACHER':
      return auth.currentUser?.teacherProfile?.teacherStatus === 'APPROVED'
        ? t('dashboard.verifiedEducator')
        : t('dashboard.educatorWorkspace')
    case 'ADMIN':
      return t('dashboard.administrator')
    default:
      return t('dashboard.keepLearning')
  }
})

const showFooterProgress = computed<boolean>(() => !isGuestCatalog.value && role.value === 'STUDENT')

const progress = computed<number>(() => {
  if (!showFooterProgress.value) return 0

  if (!isPreview.value && studentProfile.value) {
    return Math.max(0, Math.min(100, Math.round(studentProfile.value.totalPoints / POINTS_PER_PROGRESS_PERCENT)))
  }

  return isPreview.value ? PREVIEW_PROGRESS_PERCENT : 0
})

const progressLabel = computed(() => {
  if (!showFooterProgress.value) return ''

  if (!isPreview.value && studentProfile.value) {
    return t('dashboard.points', { count: studentProfile.value.totalPoints })
  }

  return isPreview.value ? t('dashboard.previewXp') : ''
})

const progressCaption = computed(() => {
  if (!showFooterProgress.value) return ''
  return isPreview.value ? t('dashboard.previewSession') : t('dashboard.earnedSoFar')
})

const footerIcon = computed(() => (isGuestCatalog.value ? 'search' : activeRoleCopy.value.footerIcon))

const isTeacherScene = computed<boolean>(() => !isGuestCatalog.value && role.value === 'TEACHER')

const showcasePandaSize = computed<number>(() => (isTeacherScene.value ? 70 : 66))

const showcaseOutfit = computed<string>(() => (isTeacherScene.value ? '#7d56ff' : '#ef6b40'))

const footerProgressTone = computed<'warning' | 'primary'>(() =>
  showFooterProgress.value && isPreview.value ? 'warning' : 'primary',
)
const showSignInHint = computed<boolean>(() => !auth.isAuthenticated && !isGuestCatalog.value)

const sidebarRef = ref<HTMLElement | null>(null)
const closeButtonRef = ref<HTMLElement | null>(null)
const isDrawerMode = useMediaQuery(DRAWER_MEDIA_QUERY)
const drawerActive = computed<boolean>(() => props.open && isDrawerMode.value)
const drawerClosed = computed<boolean>(() => !props.open && isDrawerMode.value)

useOverlayFocus({
  active: drawerActive,
  container: sidebarRef,
  initialFocus: closeButtonRef,
  onClose: () => emit('close'),
  trapFocus: true,
  lockScroll: true,
})

// Leaving the drawer breakpoint while the drawer is open closes it.
watch(isDrawerMode, (drawerMode) => {
  if (!drawerMode && props.open) emit('close')
})

function isLinkActive(to: string): boolean {
  const hashIdx = to.indexOf('#')
  const noHash = hashIdx >= 0 ? to.slice(0, hashIdx) : to
  const qIdx = noHash.indexOf('?')
  const targetPath = (qIdx >= 0 ? noHash.slice(0, qIdx) : noHash) || '/'
  const queryPart = qIdx >= 0 ? noHash.slice(qIdx + 1) : ''

  if (targetPath === '/') {
    return route.path === '/'
  }

  if (route.path !== targetPath && !route.path.startsWith(`${targetPath}/`)) {
    return false
  }

  // A plain item stays active on its page whatever query the page adds (e.g. /student/learn?lessonId=3).
  if (!queryPart) {
    return true
  }

  const params = new URLSearchParams(queryPart)
  for (const [key, value] of params.entries()) {
    if (String(route.query[key] ?? '') !== value) {
      return false
    }
  }

  return true
}
</script>
