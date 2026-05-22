<template>
  <div class="page-shell">
    <div class="dashboard-shell" :class="shellClass">
      <DashboardSidebar />
      <div class="content-shell">
        <DashboardTopbar />
        <main class="page-stack dashboard-main" :class="mainClass">
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
import type { UserRole } from '@/types/api'

const route = useRoute()

const previewRole = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? 'STUDENT')
const roleSlug = computed(() => previewRole.value.toLowerCase())

const shellClass = computed(() => [`dashboard-shell--${roleSlug.value}`])
const mainClass = computed(() => [`dashboard-main--${roleSlug.value}`])
</script>
