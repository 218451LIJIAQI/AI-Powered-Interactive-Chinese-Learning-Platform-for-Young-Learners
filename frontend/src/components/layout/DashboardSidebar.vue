<template>
  <aside class="sidebar">
    <AppBrand class="sidebar-brand" :to="homePath">
      <template #subtitle>
        <span data-testid="sidebar-role">{{ t(roleLabelKey) }}</span>
      </template>
    </AppBrand>

    <nav class="sidebar-nav" :aria-label="t('dashboard.sidebarNavigation')">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.href"
        class="sidebar-link"
        :class="{ 'is-active': item.active, 'is-locked': item.locked }"
        :aria-current="item.active ? 'page' : undefined"
        :aria-label="item.locked ? t('dashboard.lockedItem', { label: item.label }) : undefined"
      >
        <IconGlyph :name="item.icon" />
        <span class="sidebar-link-label">{{ item.label }}</span>
        <IconGlyph v-if="item.locked" name="lock" :size="14" class="sidebar-link-lock" />
        <span
          v-else-if="item.count > 0"
          class="sidebar-count"
          :aria-label="t('dashboard.countBadge', { count: item.count })"
          >{{ item.count > 99 ? '99+' : item.count }}</span
        >
      </RouterLink>
    </nav>

    <div class="sidebar-spacer" />

    <RouterLink
      v-if="supportItem"
      :to="supportItem.href"
      class="sidebar-link sidebar-support"
      :class="{ 'is-active': supportItem.active }"
      :aria-current="supportItem.active ? 'page' : undefined"
    >
      <IconGlyph :name="supportItem.icon" />
      <span class="sidebar-link-label">{{ supportItem.label }}</span>
      <span
        v-if="supportItem.count > 0"
        class="sidebar-count"
        :aria-label="t('dashboard.countBadge', { count: supportItem.count })"
        >{{ supportItem.count }}</span
      >
    </RouterLink>

    <component
      :is="status.to ? RouterLink : 'div'"
      v-if="status"
      :to="status.to"
      class="sidebar-status"
      data-testid="sidebar-status"
    >
      <div class="sidebar-status-row" :class="{ 'has-pill': status.pill }">
        <span v-if="status.level" class="sidebar-status-tile" aria-hidden="true">{{ status.level }}</span>
        <span class="sidebar-status-copy">
          <span class="sidebar-status-title">{{ status.title }}</span>
          <span v-if="status.meta" class="sidebar-status-meta">
            <IconGlyph v-if="status.level" name="streak" :size="12" />
            {{ status.meta }}
          </span>
        </span>
        <StatusPill v-if="status.pill" :label="status.pill" :tone="status.tone" />
      </div>
      <ProgressBar
        v-if="status.progress !== null"
        class="is-sm"
        :progress="status.progress"
        :label="status.progressLabel"
      />
    </component>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { isNavItemActive, roleNavigation, roleSupportNav, type RoleNavItem } from '@/config/navigation'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { useAuthStore } from '@/stores/auth'
import type { HealthStatusResponse, RewardSummaryResponse } from '@/types/api'
import { defaultRouteForRole } from '@/utils/role'
import { xpLevelFor } from '@/utils/xpLevel'

import AppBrand from './AppBrand.vue'
import { useLayoutRole } from './useLayoutRole'

interface Props {
  /** The signed-in student's reward summary (level, XP, streak); the layout reads it again after learning. */
  rewardSummary?: RewardSummaryResponse | null
  /** Badge counts keyed by `RoleNavItem.countKey`. */
  navCounts?: Readonly<Record<string, number>>
  /** The admin's last health check: the response, or null when the server did not answer. */
  health?: HealthStatusResponse | null
}

const props = withDefaults(defineProps<Props>(), {
  rewardSummary: null,
  navCounts: () => ({}),
  health: undefined,
})

interface SidebarItem {
  readonly to: string
  readonly href: string
  readonly label: string
  readonly icon: string
  readonly active: boolean
  readonly locked: boolean
  readonly count: number
}

interface StatusCard {
  readonly title: string
  /** The student's level number, shown as a tile before the title. */
  readonly level?: number
  readonly pill: string
  readonly meta: string
  readonly tone: PillTone
  readonly to?: string
  readonly progress: number | null
  readonly progressLabel: string
}

const auth = useAuthStore()
const route = useRoute()
const { t: globalT } = useI18n({ useScope: 'global' })
const { t } = useLocalMessages({ dashboard: dashboardMessages })
const { role, isApplicant, roleLabelKey } = useLayoutRole()

const homePath = computed(() => defaultRouteForRole(role.value))

function toSidebarItem(item: RoleNavItem): SidebarItem {
  const locked = Boolean(item.needsApproval) && isApplicant.value
  return {
    to: item.to,
    href: locked ? '/teacher/locked' : item.to,
    label: globalT(item.labelKey),
    icon: item.icon,
    active: !locked && isNavItemActive(item.to, route),
    locked,
    count: item.countKey ? Math.max(0, props.navCounts[item.countKey] ?? 0) : 0,
  }
}

const items = computed<SidebarItem[]>(() => roleNavigation[role.value].map(toSidebarItem))
const supportItem = computed<SidebarItem | null>(() => {
  const item = roleSupportNav[role.value]
  return item ? toSidebarItem(item) : null
})

/** The card at the bottom of the sidebar. An approved teacher has none: their status needs no reminding. */
const status = computed<StatusCard | null>(() => {
  if (role.value === 'ADMIN') return adminStatus.value
  if (role.value === 'TEACHER') return isApplicant.value ? applicantStatus.value : null
  return studentStatus.value
})

const studentStatus = computed<StatusCard>(() => {
  const totalXp = props.rewardSummary?.totalPoints ?? auth.currentUser?.studentProfile?.totalPoints ?? 0
  const streak = props.rewardSummary?.currentStreak ?? 0
  const level = xpLevelFor(totalXp)
  return {
    title: t('dashboard.levelTitle', { level: level.level, title: globalT(level.titleKey) }),
    level: level.level,
    pill: '',
    meta: streak > 0 ? t('dashboard.xpAndStreak', { xp: totalXp, streak }) : t('dashboard.xpNoStreak', { xp: totalXp }),
    tone: 'neutral',
    to: '/student/progress',
    progress: level.xpIntoLevel,
    progressLabel: t('dashboard.nextLevelProgress', { level: level.level + 1 }),
  }
})

const applicantStatus = computed<StatusCard>(() => {
  const state = auth.currentUser?.teacherProfile?.verificationState ?? 'IN_REVIEW'
  const pill =
    state === 'NOT_SUBMITTED'
      ? t('dashboard.verificationNotSubmitted')
      : state === 'CHANGES_REQUESTED'
        ? t('dashboard.verificationChangesRequested')
        : t('dashboard.verificationInReview')
  return {
    title: t('dashboard.verification'),
    pill,
    meta: '',
    // The same tones as the verification badges on the pages: changes requested needs the applicant's action.
    tone: state === 'CHANGES_REQUESTED' ? 'strong' : state === 'NOT_SUBMITTED' ? 'outline' : 'info',
    to: '/teacher/profile',
    progress: null,
    progressLabel: '',
  }
})

const adminStatus = computed<StatusCard>(() => {
  const health = props.health
  const running = health?.status === 'UP'
  // As on the admin dashboard: running without its AI provider is running, but worth a warning.
  const withoutAi = running && health?.ai === 'UNAVAILABLE'
  return {
    title: t('dashboard.systemStatus'),
    pill:
      health === undefined
        ? ''
        : withoutAi
          ? t('dashboard.systemNoAi')
          : running
            ? t('dashboard.systemRunning')
            : t('dashboard.systemUnavailable'),
    meta: '',
    tone: running && !withoutAi ? 'success' : health === undefined ? 'neutral' : 'warning',
    progress: null,
    progressLabel: '',
  }
})
</script>
