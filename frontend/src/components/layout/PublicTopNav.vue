<template>
  <header class="public-nav">
    <a href="#main-content" class="skip-to-main">Skip to main content</a>
    <AppBrand compact />

    <nav class="nav-links public-nav-links" aria-label="Primary">
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
        <span>Safe for ages 7-12</span>
        <span>Designed for families, learners, and teachers</span>
      </div>
      <LanguageSwitcher size="compact" />
      <button class="btn btn-secondary public-nav-button" type="button" @click="openSignIn">Sign In</button>
      <button class="btn btn-accent public-nav-button public-nav-button--primary" type="button" @click="openSignUp">
        Create Account
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * Sticky top navigation for unauthenticated routes (landing,
 * catalogue, course detail, terms / privacy). Shows the brand,
 * the public nav items, a language switcher, and sign-in /
 * account-creation CTAs.
 *
 * Hidden by the parent {@link PublicLayout} for the auth pages
 * themselves so the auth chrome has the full viewport.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { publicNav } from '@/config/navigation'

import AppBrand from './AppBrand.vue'

interface LocalizedNavItem {
  readonly label: string
  readonly to: string
}

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const items = computed<LocalizedNavItem[]>(() => publicNav.map((item) => ({ label: t(item.labelKey), to: item.to })))

function openSignIn(): void {
  void router.push({ path: '/auth', query: { tab: 'signin' } })
}

function openSignUp(): void {
  void router.push({ path: '/auth', query: { tab: 'register', role: 'student' } })
}

function isNavActive(to: string): boolean {
  const hashIdx = to.indexOf('#')
  const targetPath = (hashIdx >= 0 ? to.slice(0, hashIdx) : to) || '/'
  const targetHash = hashIdx >= 0 ? to.slice(hashIdx) : ''

  if (targetHash) {
    return route.path === targetPath && route.hash === targetHash
  }

  if (targetPath === '/') {
    return route.path === '/' && !route.hash
  }

  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}
</script>
