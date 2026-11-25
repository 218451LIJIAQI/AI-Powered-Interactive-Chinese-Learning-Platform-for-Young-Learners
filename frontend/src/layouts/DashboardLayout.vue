<template>
  <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
  <!-- Focus mode: a learning task fills the screen; the page draws its own top bar. -->
  <main v-if="route.meta.focus" id="main-content" class="focus-main" tabindex="-1">
    <RouterView :key="route.path" />
  </main>
  <div v-else class="dashboard-shell">
    <DashboardSidebar v-if="!navigationOpen" class="sidebar-docked" v-bind="sidebarProps" />
    <AppOverlay v-else :title="t('chrome.workspaceNavigation')" variant="drawer" @close="navigationOpen = false">
      <DashboardSidebar v-bind="sidebarProps" />
    </AppOverlay>
    <div class="content-shell">
      <DashboardTopbar
        :navigation-open="navigationOpen"
        :notification-count="unreadNotifications.data.value?.unreadCount ?? 0"
        @open-navigation="navigationOpen = true"
      />
      <main id="main-content" class="page-stack dashboard-main" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { isNavigationFailure, NavigationFailureType, RouterView, useRoute, useRouter } from 'vue-router'

import { xpApi } from '@/api/learn'
import { notificationsApi, shellApi, systemApi } from '@/api/services'
import AppOverlay from '@/components/common/AppOverlay.vue'
import DashboardSidebar from '@/components/layout/DashboardSidebar.vue'
import DashboardTopbar from '@/components/layout/DashboardTopbar.vue'
import { navigationCountsKey, unreadNotificationsKey } from '@/composables/navigationCounts'
import { progressVersion } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { useWorkspaceRole } from '@/composables/useWorkspaceRole'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()

const role = useWorkspaceRole()
const loadShellOnMount = !route.meta.focus && auth.isAuthenticated
const navigationOpen = ref(false)
const compactWindow = window.matchMedia?.('(max-width: 63.9375rem)')
async function dockNavigation(): Promise<void> {
  if (compactWindow?.matches || !navigationOpen.value) return
  navigationOpen.value = false
  await nextTick()
  const target =
    document.querySelector<HTMLElement>('.sidebar-docked .is-active') ?? document.getElementById('main-content')
  target?.focus({ preventScroll: true })
}
onMounted(() => compactWindow?.addEventListener('change', dockNavigation))
onBeforeUnmount(() => compactWindow?.removeEventListener('change', dockNavigation))
onBeforeUnmount(
  router.afterEach((_to, _from, failure) => {
    if (!failure || isNavigationFailure(failure, NavigationFailureType.duplicated)) navigationOpen.value = false
  }),
)

// The sidebar's rank, XP and streak change after lessons, practice and reviews, and this layout stays mounted across
// pages, so a signed-in learner's reward summary is read again after each navigation and each progress report.
const { data: rewardSummary, reload: reloadRewardSummary } = useResource(
  xpApi.rewards,
  loadShellOnMount && role.value === 'STUDENT',
)
// Sidebar badges (mistakes waiting, open AI reports and support requests) follow the same rhythm.
const navCountsResource = useResource(shellApi.navCounts, loadShellOnMount)
const latestNavCounts = computed(() =>
  navCountsResource.error.value || navCountsResource.loading.value ? null : navCountsResource.data.value,
)
provide(navigationCountsKey, { latest: latestNavCounts })
const unreadNotifications = useResource(notificationsApi.unreadCount, loadShellOnMount)
provide(unreadNotificationsKey, unreadNotifications)

// The card shows no badge until the first check finishes. A completed check can carry a null (offline) result.
const { data: healthCheck, reload: reloadHealth } = useResource(
  async () => ({ health: await systemApi.health().catch(() => null) }),
  loadShellOnMount && role.value === 'ADMIN',
)
const sidebarProps = computed(() => ({
  rewardSummary: rewardSummary.value,
  navCounts: navCountsResource.data.value,
  health: healthCheck.value?.health,
}))

watch([() => route.path, progressVersion], () => {
  if (route.meta.focus || !auth.isAuthenticated) return
  if (role.value === 'STUDENT') void reloadRewardSummary()
  void navCountsResource.reload()
  void unreadNotifications.reload()
  if (role.value === 'ADMIN') void reloadHealth()
})
</script>
