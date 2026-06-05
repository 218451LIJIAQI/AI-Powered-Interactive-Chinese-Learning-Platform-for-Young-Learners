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
        <span :style="{ width: `${progress}%` }" />
      </div>
      <div v-if="showFooterProgress" class="row-between sidebar-footer-meta">
        <strong>{{ progressLabel }}</strong>
        <span class="muted">{{ progressCaption }}</span>
      </div>
      <span v-else-if="!auth.isAuthenticated && !isGuestCatalog" class="muted sidebar-footer-subtitle"
        >Sign in to open lessons, practice, classes, rewards, and AI tools.</span
      >
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
/**
 * Vertical navigation rail for the authenticated dashboard layout.
 *
 * Reads {@link roleNavigation} for the active role and renders one
 * `<RouterLink>` per item, applying the active-route highlight via
 * `useRoute()`. The persona's labels come from i18n so the rail
 * reads correctly in EN / BM / 简体中文 without per-role hardcoding.
 *
 * Hidden on mobile in favour of the topbar drawer.
 */
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import { publicNav, roleNavigation } from '@/config/navigation'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'
import type { NavItem } from '@/config/navigation'

import AppBrand from './AppBrand.vue'

const auth = useAuthStore()
const route = useRoute()
const { t } = useI18n()

const isPreview = computed(() => auth.isPreviewSession)
const studentProfile = computed(() => auth.currentUser?.studentProfile ?? null)

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const isGuestCatalog = computed(() => route.path.startsWith('/courses'))
const sceneRole = computed(() => (isGuestCatalog.value ? 'student' : role.value.toLowerCase()))
const items = computed(() => (isGuestCatalog.value ? publicNav : roleNavigation[role.value]))
const localizedItems = computed(() => items.value.map((item) => ({ ...item, label: t(item.labelKey) })))

const sidebarClass = computed(() => `sidebar--${sceneRole.value}`)
const roleBadge = computed(() => {
  if (isGuestCatalog.value) return 'Public preview'
  if (role.value === 'ADMIN') return 'Admin'
  if (role.value === 'TEACHER') return 'Teacher'
  return 'Student'
})
const roleHeading = computed(() => {
  if (isGuestCatalog.value) return 'Course discovery that feels trustworthy'
  if (role.value === 'ADMIN') return 'Operational control without visual overload'
  if (role.value === 'TEACHER') return 'A calm teaching cockpit for classes and content'
  return 'A cheerful learning path with rewards and next steps'
})
const roleSummary = computed(() => {
  if (isGuestCatalog.value) return 'Compare courses, learning modes, and value before signing in.'
  if (role.value === 'ADMIN') return 'Review users, moderation, finance, and settings from a steadier shell.'
  if (role.value === 'TEACHER') return 'Move between learners, live sessions, and content without losing context.'
  return 'Keep lessons, practice, classes, achievements, and AI help within easy reach.'
})

interface NavigationGroup {
  readonly key: string
  readonly title: string
  readonly ariaLabel: string
  readonly items: Array<NavItem & { label: string }>
}

function pickItems(indices: readonly number[]): Array<NavItem & { label: string }> {
  return indices
    .map((index) => localizedItems.value[index])
    .filter((item): item is NavItem & { label: string } => !!item)
}

const navigationGroups = computed<NavigationGroup[]>(() => {
  if (isGuestCatalog.value) {
    return [
      {
        key: 'explore',
        title: 'EXPLORE',
        ariaLabel: 'Public preview navigation',
        items: localizedItems.value,
      },
    ]
  }

  if (role.value === 'ADMIN') {
    return [
      { key: 'overview', title: 'OVERVIEW', ariaLabel: 'Admin overview', items: pickItems([0, 7, 6]) },
      { key: 'operations', title: 'OPERATIONS', ariaLabel: 'Admin operations', items: pickItems([1, 2, 5]) },
      { key: 'trust', title: 'TRUST & CONTENT', ariaLabel: 'Admin trust and content', items: pickItems([3, 4]) },
      { key: 'platform', title: 'PLATFORM', ariaLabel: 'Admin platform settings', items: pickItems([8]) },
    ]
  }

  if (role.value === 'TEACHER') {
    return [
      { key: 'overview', title: 'OVERVIEW', ariaLabel: 'Teacher overview', items: pickItems([0, 5]) },
      { key: 'teaching', title: 'TEACHING', ariaLabel: 'Teacher teaching tools', items: pickItems([2, 1]) },
      { key: 'learners', title: 'LEARNERS', ariaLabel: 'Teacher learner management', items: pickItems([3, 4]) },
      { key: 'profile', title: 'PROFILE', ariaLabel: 'Teacher profile settings', items: pickItems([6]) },
    ]
  }

  return [
    {
      key: 'journey',
      title: 'JOURNEY',
      ariaLabel: 'Student learning navigation',
      items: pickItems([0, 1, 2, 3]),
    },
    {
      key: 'motivation',
      title: 'MOTIVATION',
      ariaLabel: 'Student progress and rewards navigation',
      items: pickItems([4, 5]),
    },
    {
      key: 'support',
      title: 'SUPPORT',
      ariaLabel: 'Student support navigation',
      items: pickItems([6, 7]),
    },
  ]
})

const footerTitle = computed(() => {
  if (isGuestCatalog.value) return 'Public course catalog'

  switch (role.value) {
    case 'TEACHER':
      return auth.displayName || 'Teacher'
    case 'ADMIN':
      return 'Admin workspace'
    default:
      // Use real level from student profile if available
      if (!isPreview.value && studentProfile.value?.currentLevel) {
        return studentProfile.value.currentLevel
      }
      return isPreview.value ? 'Preview learner' : 'Student'
  }
})

const footerSubtitle = computed(() => {
  if (isGuestCatalog.value) return 'Browse before you sign in'

  switch (role.value) {
    case 'TEACHER':
      return auth.currentUser?.teacherProfile?.teacherStatus === 'APPROVED' ? 'Verified educator' : 'Educator workspace'
    case 'ADMIN':
      return 'Administrator'
    default:
      return 'Keep learning and growing'
  }
})

const progress = computed(() => {
  if (isGuestCatalog.value) return 0

  // Only show real progress for students; teacher/admin sidebar footer
  // doesn't have a progress concept, so hide the bar for them.
  if (role.value !== 'STUDENT') return 0

  if (!isPreview.value && studentProfile.value) {
    return Math.max(0, Math.min(100, Math.round(studentProfile.value.totalPoints / 4)))
  }

  return isPreview.value ? 55 : 0
})

const progressLabel = computed(() => {
  if (isGuestCatalog.value) return 'Public browsing'
  if (role.value !== 'STUDENT') return ''

  if (!isPreview.value && studentProfile.value) {
    return `${studentProfile.value.totalPoints} pts`
  }

  return isPreview.value ? 'Preview XP' : ''
})

const progressCaption = computed(() => {
  if (isGuestCatalog.value) return 'sign in for the student workspace'
  if (role.value !== 'STUDENT') return ''
  return isPreview.value ? 'preview session' : 'earned so far'
})

const footerIcon = computed(() => {
  if (isGuestCatalog.value) return 'search'

  switch (role.value) {
    case 'TEACHER':
      return 'teacher'
    case 'ADMIN':
      return 'shield'
    default:
      return 'trophy'
  }
})

const footerDecorationIcon = computed(() => {
  if (isGuestCatalog.value) return 'learn'

  switch (role.value) {
    case 'TEACHER':
      return 'fun'
    case 'ADMIN':
      return 'dashboard'
    default:
      return 'fun'
  }
})

const showcasePandaSize = computed<number>(() => (!isGuestCatalog.value && role.value === 'TEACHER' ? 122 : 118))

const showcaseOutfit = computed<string>(() =>
  !isGuestCatalog.value && role.value === 'TEACHER' ? '#7d56ff' : '#ef6b40',
)

const showcaseAccent = '#f6d056'

const showFooterProgress = computed<boolean>(() => !isGuestCatalog.value && role.value === 'STUDENT')

const footerProgressTone = computed<string>(() => (showFooterProgress.value && isPreview.value ? 'warning' : ''))

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
