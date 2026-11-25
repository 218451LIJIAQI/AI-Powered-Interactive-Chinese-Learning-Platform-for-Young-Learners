<template>
  <header class="public-nav">
    <div class="public-nav-inner">
      <AppBrand to="/" class="public-brand" />

      <nav class="public-nav-links" :aria-label="t('chrome.primaryNavigation')">
        <RouterLink
          v-for="item in items"
          :key="item.to"
          :to="item.to"
          class="public-nav-link"
          :aria-current="item.active ? 'page' : undefined"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="public-nav-actions">
        <LanguageSwitcher class="public-languages" />
        <div class="public-nav-account">
          <RouterLink v-if="!auth.isAuthenticated" class="public-sign-in" to="/auth">
            {{ t('common.signIn') }}
          </RouterLink>
          <RouterLink
            class="btn public-nav-cta"
            :to="auth.isAuthenticated ? homePath : { path: '/auth', query: { tab: 'register' } }"
          >
            {{ auth.isAuthenticated ? t('shell.openMyHome') : t('shell.createAccount') }}
            <IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { isNavItemActive, publicNav } from '@/config/navigation'
import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'

import AppBrand from './AppBrand.vue'

const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n()

const homePath = computed(() => defaultRouteForRole(auth.activeRole))

const items = computed(() =>
  publicNav.map((item) => ({ label: t(item.labelKey), to: item.to, active: isNavItemActive(item.to, route) })),
)
</script>
