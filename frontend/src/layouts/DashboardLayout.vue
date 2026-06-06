<template>
  <div class="page-shell">
    <div class="dashboard-shell" :class="shellClass">
      <DashboardSidebar />
      <div class="content-shell">
        <DashboardTopbar />
        <main id="main-content" class="page-stack dashboard-main" :class="mainClass">
          <RouterView />
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import DashboardSidebar from '@/components/layout/DashboardSidebar.vue'
import DashboardTopbar from '@/components/layout/DashboardTopbar.vue'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'

const route = useRoute()
const auth = useAuthStore()

const activeRole = computed<UserRole>(
  () => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT',
)
const roleSlug = computed<string>(() => activeRole.value.toLowerCase())

const shellClass = computed<string>(() => `dashboard-shell--${roleSlug.value}`)
const mainClass = computed<string>(() => `dashboard-main--${roleSlug.value}`)
</script>
