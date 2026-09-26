<template>
  <header class="public-nav">
    <a href="#main-content" class="skip-to-main">{{ t('chrome.skipToMain') }}</a>
    <AppBrand compact />

    <nav class="nav-links public-nav-links" :aria-label="t('chrome.primaryNavigation')">
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
        <a
          :href="href"
          class="public-nav-link"
          :class="{ 'router-link-active': isNavActive(item.to) }"
          @click="navigate"
        >
          {{ item.label }}
        </a>
      </RouterLink>
    </nav>

    <div class="public-nav-actions">
      <div class="public-nav-trust hidden-mobile">
        <span>{{ t('chrome.safeAges') }}</span>
        <span>{{ t('chrome.designedFor') }}</span>
      </div>
      <LanguageSwitcher size="compact" />
      <button class="btn btn-secondary public-nav-button" type="button" @click="openSignIn">
        {{ t('chrome.signIn') }}
      </button>
      <button class="btn btn-accent public-nav-button public-nav-button--primary" type="button" @click="openSignUp">
        {{ t('chrome.createAccount') }}
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { publicNav } from '@/config/navigation'

import AppBrand from './AppBrand.vue'

interface LocalizedNavItem {
  readonly label: string
  readonly to: string
}

const SIGN_IN_ROUTE = { path: '/auth', query: { tab: 'signin' } } satisfies RouteLocationRaw
const SIGN_UP_ROUTE = { path: '/auth', query: { tab: 'register', role: 'student' } } satisfies RouteLocationRaw

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const items = computed<LocalizedNavItem[]>(() => publicNav.map((item) => ({ label: t(item.labelKey), to: item.to })))

function openSignIn(): void {
  void router.push(SIGN_IN_ROUTE)
}

function openSignUp(): void {
  void router.push(SIGN_UP_ROUTE)
}

function parseNavTarget(to: string): { path: string; hash: string } {
  const hashIdx = to.indexOf('#')

  return {
    path: (hashIdx >= 0 ? to.slice(0, hashIdx) : to) || '/',
    hash: hashIdx >= 0 ? to.slice(hashIdx) : '',
  }
}

function isNavActive(to: string): boolean {
  const { path, hash } = parseNavTarget(to)

  if (hash) {
    return route.path === path && route.hash === hash
  }

  if (path === '/') {
    return route.path === '/' && !route.hash
  }

  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>
