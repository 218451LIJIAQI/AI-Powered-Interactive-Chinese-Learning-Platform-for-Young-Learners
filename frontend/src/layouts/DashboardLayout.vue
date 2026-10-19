<template>
  <div class="page-shell">
    <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
    <div class="dashboard-shell" :class="[shellClass, { 'is-nav-open': navOpen }]">
      <DashboardSidebar :open="navOpen" @close="closeNavigation" />
      <div v-if="navOpen" class="sidebar-scrim" aria-hidden="true" @click="closeNavigation" />
      <div class="content-shell">
        <DashboardTopbar :nav-open="navOpen" @toggle-navigation="toggleNavigation" />
        <main id="main-content" class="page-stack dashboard-main" :class="mainClass" tabindex="-1">
          <RouterView />
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRoute } from 'vue-router'

import DashboardSidebar from '@/components/layout/DashboardSidebar.vue'
import DashboardTopbar from '@/components/layout/DashboardTopbar.vue'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'

const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n()

const activeRole = computed<UserRole>(
  () => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT',
)
const roleSlug = computed<string>(() => activeRole.value.toLowerCase())

const shellClass = computed<string>(() => `dashboard-shell--${roleSlug.value}`)
const mainClass = computed<string>(() => `dashboard-main--${roleSlug.value}`)

// Below 900px the sidebar is an off-canvas drawer opened from the topbar menu button.
const navOpen = ref<boolean>(false)

function toggleNavigation(): void {
  navOpen.value = !navOpen.value
}

function closeNavigation(): void {
  navOpen.value = false
}

watch(
  () => route.fullPath,
  () => closeNavigation(),
)
</script>
