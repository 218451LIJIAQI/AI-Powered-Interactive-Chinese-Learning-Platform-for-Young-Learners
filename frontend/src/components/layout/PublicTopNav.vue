<template>
  <header class="public-nav">
    <AppBrand to="/" />

    <nav class="public-nav-links" :aria-label="t('chrome.primaryNavigation')">
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
        <a
          :href="href"
          class="public-nav-link"
          :class="{ 'router-link-active': isNavItemActive(item.to, route) }"
          :aria-current="isNavItemActive(item.to, route) ? 'page' : undefined"
          @click="navigate"
        >
          {{ item.label }}
        </a>
      </RouterLink>
    </nav>

    <div class="public-nav-actions">
      <LanguageSwitcher />
      <RouterLink v-if="auth.isAuthenticated" class="btn btn-primary" :to="homePath">
        {{ t('shell.openMyHome') }}
      </RouterLink>
      <template v-else>
        <RouterLink class="btn btn-ghost" :to="SIGN_IN_ROUTE">
          {{ t('shell.signIn') }}
        </RouterLink>
        <RouterLink class="btn btn-primary" :to="SIGN_UP_ROUTE">
          {{ t('shell.createAccount') }}
        </RouterLink>
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'

import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { isNavItemActive, publicNav } from '@/config/navigation'
import { publicShellMessages } from '@/plugins/publicShellMessages'
import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'

import AppBrand from './AppBrand.vue'

const SIGN_IN_ROUTE = { path: '/auth' } satisfies RouteLocationRaw
const SIGN_UP_ROUTE = { path: '/auth', query: { tab: 'register', role: 'student' } } satisfies RouteLocationRaw

const route = useRoute()
const auth = useAuthStore()
const { t } = useLocalMessages({ shell: publicShellMessages })

const homePath = computed(() => defaultRouteForRole(auth.activeRole))

const items = computed(() => publicNav.map((item) => ({ label: t(item.labelKey), to: item.to })))
</script>
