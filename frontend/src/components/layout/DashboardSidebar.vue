<template>
  <aside class="sidebar" :class="sidebarClass">
    <AppBrand />

    <div class="sidebar-intro">
      <span class="sidebar-intro-badge">{{ roleBadge }}</span>
      <strong>{{ roleHeading }}</strong>
      <p>{{ roleSummary }}</p>
    </div>

    <section class="sidebar-showcase">
      <div class="sidebar-showcase-illustration">
        <div class="sidebar-showcase-bamboo" />
        <PandaIllustration
          class="sidebar-showcase-panda"
          :size="showcasePandaSize"
          :outfit="showcaseOutfit"
          :accent="showcaseAccent"
          compact
        />
        <div class="sidebar-showcase-skyline" />
      </div>
    </section>

    <div v-for="group in navigationGroups" :key="group.key" class="sidebar-nav-group">
      <div v-if="group.title" class="sidebar-section-label">{{ group.title }}</div>
      <nav class="sidebar-nav" :aria-label="group.ariaLabel">
        <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
          <a
            :href="href"
            class="sidebar-link"
            :class="{ 'router-link-active': isLinkActive(item.to) }"
            @click="navigate"
          >
            <IconGlyph :name="item.icon" class="sidebar-icon" />
            <span>{{ item.label }}</span>
          </a>
        </RouterLink>
      </nav>
    </div>

    <div class="sidebar-footer stack">
      <div class="row sidebar-footer-row">
        <div class="sidebar-footer-badge"><IconGlyph :name="footerIcon" :size="22" /></div>
        <div class="stack sidebar-footer-copy">
          <strong class="sidebar-footer-title">{{ footerTitle }}</strong>
          <span class="muted sidebar-footer-subtitle">{{ footerSubtitle }}</span>
        </div>
      </div>
      <div v-if="showFooterProgress" class="progress-bar sidebar-footer-progress" :class="footerProgressTone">
        <span :style="footerProgressStyle" />
      </div>
      <div v-if="showFooterProgress" class="row-between sidebar-footer-meta">
        <strong>{{ progressLabel }}</strong>
        <span class="muted">{{ progressCaption }}</span>
      </div>
      <span v-else-if="showSignInHint" class="muted sidebar-footer-subtitle">
        {{ t('dashboard.signInHint') }}
      </span>
      <div class="sidebar-footer-ornament"><IconGlyph :name="footerDecorationIcon" :size="26" /></div>
    </div>

    <div class="sidebar-bottom-scene" :class="`sidebar-bottom-scene--${sceneRole}`">
      <span class="sidebar-bottom-scene-wave" />
      <span class="sidebar-bottom-scene-leaf sidebar-bottom-scene-leaf--1" />
      <span class="sidebar-bottom-scene-leaf sidebar-bottom-scene-leaf--2" />
      <span class="sidebar-bottom-scene-flower" />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import { publicNav, roleNavigation } from '@/config/navigation'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'
import type { NavItem } from '@/config/navigation'

import AppBrand from './AppBrand.vue'

type LocalizedNavItem = NavItem & { label: string }

interface NavigationGroup {
  readonly key: string
  readonly title: string
  readonly ariaLabel: string
  readonly items: LocalizedNavItem[]
}

interface NavigationGroupConfig {
  readonly key: string
  readonly title: string
  readonly ariaLabel: string
  readonly itemIndices: readonly number[]
}

interface RoleSidebarCopy {
  readonly badge: string
  readonly heading: string
  readonly summary: string
  readonly footerIcon: string
  readonly footerDecorationIcon: string
}

const ROLE_SIDEBAR_COPY: Record<UserRole, RoleSidebarCopy> = {
  ADMIN: {
    badge: 'dashboard.adminBadge',
    heading: 'dashboard.adminHeading',
    summary: 'dashboard.adminSummary',
    footerIcon: 'shield',
    footerDecorationIcon: 'dashboard',
  },
  TEACHER: {
    badge: 'dashboard.teacherBadge',
    heading: 'dashboard.teacherHeading',
    summary: 'dashboard.teacherSummary',
    footerIcon: 'teacher',
    footerDecorationIcon: 'fun',
  },
  STUDENT: {
    badge: 'dashboard.studentBadge',
    heading: 'dashboard.studentHeading',
    summary: 'dashboard.studentSummary',
    footerIcon: 'trophy',
    footerDecorationIcon: 'fun',
  },
}

const ROLE_NAVIGATION_GROUPS: Record<UserRole, readonly NavigationGroupConfig[]> = {
  ADMIN: [
    { key: 'overview', title: 'dashboard.overview', ariaLabel: 'dashboard.adminOverview', itemIndices: [0, 7, 6] },
    {
      key: 'operations',
      title: 'dashboard.operations',
      ariaLabel: 'dashboard.adminOperations',
      itemIndices: [1, 2, 5],
    },
    { key: 'trust', title: 'dashboard.trustContent', ariaLabel: 'dashboard.adminTrust', itemIndices: [3, 4] },
    { key: 'platform', title: 'dashboard.platform', ariaLabel: 'dashboard.adminPlatform', itemIndices: [8] },
  ],
  TEACHER: [
    { key: 'overview', title: 'dashboard.overview', ariaLabel: 'dashboard.teacherOverview', itemIndices: [0, 5] },
    { key: 'teaching', title: 'dashboard.teaching', ariaLabel: 'dashboard.teacherTools', itemIndices: [2, 1] },
    { key: 'learners', title: 'dashboard.learners', ariaLabel: 'dashboard.teacherLearners', itemIndices: [3, 4] },
    { key: 'profile', title: 'dashboard.profile', ariaLabel: 'dashboard.teacherProfileSettings', itemIndices: [6] },
  ],
  STUDENT: [
    { key: 'journey', title: 'dashboard.journey', ariaLabel: 'dashboard.studentNavigation', itemIndices: [0, 1, 2, 3] },
    {
      key: 'motivation',
      title: 'dashboard.motivation',
      ariaLabel: 'dashboard.studentMotivation',
      itemIndices: [4, 5],
    },
    { key: 'support', title: 'dashboard.support', ariaLabel: 'dashboard.studentSupport', itemIndices: [6, 7] },
  ],
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
    zh: { dashboard: dashboardMessages.zh },
  },
})

const isPreview = computed(() => auth.isPreviewSession)
const studentProfile = computed(() => auth.currentUser?.studentProfile ?? null)

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const isGuestCatalog = computed(() => route.path.startsWith('/courses'))
const sceneRole = computed(() => (isGuestCatalog.value ? 'student' : role.value.toLowerCase()))
const items = computed(() => (isGuestCatalog.value ? publicNav : roleNavigation[role.value]))
const localizedItems = computed<LocalizedNavItem[]>(() =>
  items.value.map((item) => ({
    icon: item.icon,
    label: globalT(item.labelKey),
    labelKey: item.labelKey,
    to: item.to,
  })),
)
const activeRoleCopy = computed(() => ROLE_SIDEBAR_COPY[role.value])

const sidebarClass = computed(() => `sidebar--${sceneRole.value}`)
const roleBadge = computed(() => t(isGuestCatalog.value ? 'dashboard.publicPreview' : activeRoleCopy.value.badge))
const roleHeading = computed(() => t(isGuestCatalog.value ? 'dashboard.guestHeading' : activeRoleCopy.value.heading))
const roleSummary = computed(() => t(isGuestCatalog.value ? 'dashboard.guestSummary' : activeRoleCopy.value.summary))

function pickItems(indices: readonly number[]): LocalizedNavItem[] {
  return indices.reduce<LocalizedNavItem[]>((pickedItems, index) => {
    const item = localizedItems.value[index]
    if (item) {
      pickedItems.push(item)
    }
    return pickedItems
  }, [])
}

function buildNavigationGroup(config: NavigationGroupConfig): NavigationGroup {
  return {
    key: config.key,
    title: t(config.title),
    ariaLabel: t(config.ariaLabel),
    items: pickItems(config.itemIndices),
  }
}

const navigationGroups = computed<NavigationGroup[]>(() => {
  if (isGuestCatalog.value) {
    return [
      {
        key: PUBLIC_NAVIGATION_GROUP.key,
        title: t(PUBLIC_NAVIGATION_GROUP.title),
        ariaLabel: t(PUBLIC_NAVIGATION_GROUP.ariaLabel),
        items: localizedItems.value,
      },
    ]
  }

  return ROLE_NAVIGATION_GROUPS[role.value].map(buildNavigationGroup)
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

const footerDecorationIcon = computed(() =>
  isGuestCatalog.value ? 'learn' : activeRoleCopy.value.footerDecorationIcon,
)

const isTeacherScene = computed<boolean>(() => !isGuestCatalog.value && role.value === 'TEACHER')

const showcasePandaSize = computed<number>(() => (isTeacherScene.value ? 122 : 118))

const showcaseOutfit = computed<string>(() => (isTeacherScene.value ? '#7d56ff' : '#ef6b40'))

const footerProgressTone = computed<string>(() => (showFooterProgress.value && isPreview.value ? 'warning' : ''))
const footerProgressStyle = computed(() => ({ width: `${progress.value}%` }))
const showSignInHint = computed<boolean>(() => !auth.isAuthenticated && !isGuestCatalog.value)

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

  if (!queryPart) {
    return Object.keys(route.query).length === 0
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
