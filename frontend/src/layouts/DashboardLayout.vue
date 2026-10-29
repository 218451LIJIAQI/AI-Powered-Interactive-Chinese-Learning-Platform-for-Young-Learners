<template>
  <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
  <!-- Focus mode: a learning task fills the screen; the page draws its own top bar. -->
  <main v-if="route.meta.focus" id="main-content" class="focus-main" tabindex="-1">
    <RouterView />
  </main>
  <div v-else class="dashboard-shell">
    <DashboardSidebar :reward-summary="rewardSummary" :nav-counts="navCounts ?? undefined" :health="health" />
    <div class="content-shell">
      <DashboardTopbar />
      <main id="main-content" class="page-stack dashboard-main" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'

import { studentApi, systemApi } from '@/api/services'
import { shellApi } from '@/api/shell'
import DashboardSidebar from '@/components/layout/DashboardSidebar.vue'
import DashboardTopbar from '@/components/layout/DashboardTopbar.vue'
import { useProgressVersion } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import type { HealthStatusResponse, RewardSummaryResponse } from '@/types/api'

const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n()

const isStudent = computed<boolean>(() => auth.isAuthenticated && auth.activeRole === 'STUDENT')
const isAdmin = computed<boolean>(() => auth.isAuthenticated && auth.activeRole === 'ADMIN')

// The sidebar's level, XP and streak change after lessons, practice and reviews, and this layout stays mounted across
// pages, so a signed-in student's reward summary is read again after each navigation and each progress report.
const { data: rewardSummary, reload: reloadRewardSummary } = useResource<RewardSummaryResponse | null>(() =>
  isStudent.value ? studentApi.rewards() : Promise.resolve(null),
)
// Sidebar badges (mistakes waiting, review queue, open reports, support replies) follow the same rhythm.
const { data: navCounts, reload: reloadNavCounts } = useResource(() =>
  auth.isAuthenticated ? shellApi.navCounts() : Promise.resolve({}),
)
// Admins see the system status card: the health check's answer, or null when the server did not answer.
const health = ref<HealthStatusResponse | null | undefined>(undefined)
async function checkHealth(): Promise<void> {
  if (!isAdmin.value) return
  health.value = await systemApi.health().catch(() => null)
}
void checkHealth()

const progressVersion = useProgressVersion()
watch([() => route.path, progressVersion], () => {
  if (route.meta.focus) return
  if (isStudent.value) void reloadRewardSummary()
  if (auth.isAuthenticated) void reloadNavCounts()
  void checkHealth()
})
</script>
