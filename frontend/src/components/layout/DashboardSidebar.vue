<template>
  <aside class="sidebar" :aria-label="t('dashboard.sidebar')">
    <AppBrand class="sidebar-brand" :to="homePath">
      <template #subtitle>
        <span data-testid="sidebar-role">{{ t(`roles.${role}`) }}</span>
      </template>
    </AppBrand>

    <nav class="sidebar-nav" :aria-label="t('dashboard.sidebarNavigation')">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="sidebar-link"
        :class="{ 'is-active': item.active }"
        :aria-current="item.active ? 'page' : undefined"
        :aria-label="item.count > 0 ? `${item.label}, ${t('dashboard.countBadge', { count: item.count })}` : undefined"
        :title="item.count > 0 ? `${item.label}, ${t('dashboard.countBadge', { count: item.count })}` : undefined"
      >
        <IconGlyph :name="item.icon" />
        <span class="sidebar-link-label">{{ item.label }}</span>
        <span v-if="item.count > 0" class="sidebar-count" aria-hidden="true">{{ formatBadgeCount(item.count) }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar-spacer" />

    <div v-if="role === 'ADMIN'" class="sidebar-status" data-testid="sidebar-status">
      <div class="sidebar-status-row" :class="{ 'has-pill': healthPill }">
        <span class="sidebar-status-copy">
          <span class="sidebar-status-title">{{ t('dashboard.systemStatus') }}</span>
        </span>
        <StatusPill v-if="healthPill" :label="healthPill.label" :tone="healthPill.tone" />
      </div>
    </div>
    <RouterLink v-else to="/student/progress" class="sidebar-status" data-testid="sidebar-status">
      <div class="sidebar-status-row">
        <span class="sidebar-status-tile" aria-hidden="true">{{ learner.rank.level }}</span>
        <span class="sidebar-status-copy">
          <span class="sidebar-status-title">{{ learner.title }}</span>
          <span class="sidebar-status-meta">
            <IconGlyph name="streak" :size="12" />
            {{ learner.meta }}
          </span>
        </span>
      </div>
      <ProgressBar
        class="is-sm"
        :progress="learner.rank.xpIntoLevel"
        :label="t('dashboard.nextRankProgress', { rank: learner.rank.level + 1 })"
      />
    </RouterLink>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useWorkspaceRole } from '@/composables/useWorkspaceRole'
import { isNavItemActive, roleNavigation, type RoleNavItem } from '@/config/navigation'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { useAuthStore } from '@/stores/auth'
import type { HealthStatusResponse, NavCounts, RewardSummaryResponse } from '@/types/api'
import { formatBadgeCount } from '@/utils/format'
import { defaultRouteForRole } from '@/utils/role'
import { xpLevelFor } from '@/utils/xpLevel'

import AppBrand from './AppBrand.vue'

/**
 * The signed-in sidebar of Learners and Admins: the brand with the role, the section roots of the role (with their
 * waiting counts), and a status card at the bottom: the learner's rank, XP and streak with the way to the next rank, or
 * the admin's system status.
 */
interface Props {
  /** The signed-in learner's reward summary (XP, streak); the layout reads it again after learning. */
  rewardSummary: RewardSummaryResponse | null
  /** Badge counts keyed by `RoleNavItem.countKey`, null until they are read. */
  navCounts: NavCounts | null
  /** The admin's last health check: the response, or null when the server did not answer. */
  health?: HealthStatusResponse | null
}

const props = defineProps<Props>()

const auth = useAuthStore()
const route = useRoute()
const { t } = useLocalMessages({ dashboard: dashboardMessages })

const role = useWorkspaceRole()
const homePath = computed(() => defaultRouteForRole(role.value))

function toSidebarItem(item: RoleNavItem) {
  return {
    to: item.to,
    label: t(item.labelKey),
    icon: item.icon,
    active: isNavItemActive(item.to, route),
    count: item.countKey ? (props.navCounts?.[item.countKey] ?? 0) : 0,
  }
}

const items = computed(() => roleNavigation[role.value].map(toSidebarItem))

/** "Rank 3 · Regular", "290 XP · 7-day streak" and the bar towards the next rank; a new learner is "Rank 1 · Starter". */
const learner = computed(() => {
  const totalXp = props.rewardSummary?.totalPoints ?? auth.currentUser?.studentProfile?.totalPoints ?? 0
  const streak = props.rewardSummary?.currentStreak ?? null
  const rank = xpLevelFor(totalXp)
  const meta =
    streak === null
      ? t('dashboard.xpOnly', { xp: totalXp })
      : streak > 0
        ? t('dashboard.xpAndStreak', { xp: totalXp, streak })
        : t('dashboard.xpNoStreak', { xp: totalXp })
  return {
    rank,
    title: t('dashboard.rankTitle', { rank: rank.level, title: t(rank.titleKey) }),
    meta,
  }
})

/** The admin card's badge: none until the first health check has answered. */
const healthPill = computed<{ readonly label: string; readonly tone: PillTone } | null>(() => {
  const health = props.health
  if (health === undefined) return null
  if (health?.status !== 'UP') return { label: t('dashboard.systemUnavailable'), tone: 'warning' }
  // Running without its AI provider is running, but worth a warning.
  if (health.ai === 'UNAVAILABLE') return { label: t('dashboard.systemNoAi'), tone: 'warning' }
  return { label: t('dashboard.systemRunning'), tone: 'success' }
})
</script>
