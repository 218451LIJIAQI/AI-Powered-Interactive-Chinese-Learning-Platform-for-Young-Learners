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
          <a :href="href" class="sidebar-link" :class="{ 'router-link-active': isLinkActive(item.to) }" @click="navigate">
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
      <span v-else class="muted sidebar-footer-subtitle">Sign in to open lessons, practice, classes, rewards, and AI tools.</span>
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
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'
import type { NavItem } from '@/config/navigation'

import AppBrand from './AppBrand.vue'

const auth = useAuthStore()
const route = useRoute()
const { t } = useI18n()

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const isGuestCatalog = computed(() => route.path.startsWith('/courses'))
const sceneRole = computed(() => (isGuestCatalog.value ? 'student' : role.value.toLowerCase()))
const showFooterProgress = computed(() => !isGuestCatalog.value)

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

type NavigationGroup = {
  key: string
  title: string
  ariaLabel: string
  items: Array<NavItem & { label: string }>
}

function pickItems(indices: number[]) {
  return indices
    .map((index) => localizedItems.value[index])
    .filter((item): item is NavItem & { label: string } => Boolean(item))
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
      return 'Level 3 Teacher'
    case 'ADMIN':
      return 'Admin Level'
    default:
      return 'Level 3 Little Learner'
  }
})

const footerSubtitle = computed(() => {
  if (isGuestCatalog.value) return 'Browse before you sign in'

  switch (role.value) {
    case 'TEACHER':
      return 'Verified educator workspace'
    case 'ADMIN':
      return 'Super Administrator'
    default:
      return 'Keep learning and growing'
  }
})

const progress = computed(() => {
  if (isGuestCatalog.value) return 0

  switch (role.value) {
    case 'TEACHER':
      return 65
    case 'ADMIN':
      return 100
    default:
      return 55
  }
})

const progressLabel = computed(() => {
  if (isGuestCatalog.value) return 'Public browsing'

  switch (role.value) {
    case 'TEACHER':
      return '780 / 1,200 XP'
    case 'ADMIN':
      return 'Full Access'
    default:
      return '220 / 400 XP'
  }
})

const progressCaption = computed(() => {
  if (isGuestCatalog.value) return 'sign in for the student workspace'

  switch (role.value) {
    case 'TEACHER':
      return 'teaching XP'
    case 'ADMIN':
      return 'privileges'
    default:
      return 'next level'
  }
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

const showcasePandaSize = computed(() => {
  if (isGuestCatalog.value) return 118

  switch (role.value) {
    case 'TEACHER':
      return 122
    case 'ADMIN':
      return 118
    default:
      return 118
  }
})

const showcaseOutfit = computed(() => {
  if (isGuestCatalog.value) return '#ef6b40'

  switch (role.value) {
    case 'TEACHER':
      return '#7d56ff'
    case 'ADMIN':
      return '#ef6b40'
    default:
      return '#ef6b40'
  }
})

const showcaseAccent = computed(() => '#f6d056')

const footerProgressTone = computed(() => {
  if (isGuestCatalog.value) return ''

  switch (role.value) {
    case 'TEACHER':
      return 'warning'
    case 'ADMIN':
      return 'success'
    default:
      return ''
  }
})

function isLinkActive(to: string) {
  const [pathPart, queryPart] = to.split('?')
  const targetPath = pathPart.split('#')[0] || '/'

  if (targetPath === '/') {
    return route.path === '/'
  }

  if (route.path !== targetPath && !route.path.startsWith(`${targetPath}/`)) {
    return false
  }

  if (!queryPart) {
    return Object.keys(route.query).length === 0
  }

  const search = queryPart.split('#')[0]
  const params = new URLSearchParams(search)
  for (const [key, value] of params.entries()) {
    if (String(route.query[key] ?? '') !== value) {
      return false
    }
  }

  return true
}
</script>
