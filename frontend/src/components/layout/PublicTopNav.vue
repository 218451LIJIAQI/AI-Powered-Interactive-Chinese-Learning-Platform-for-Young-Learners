<template>
  <header class="public-nav" :class="{ 'is-menu-open': menuOpen }">
    <div class="public-nav-bar">
      <AppBrand compact />
      <button
        type="button"
        class="btn btn-ghost btn-icon public-nav-toggle"
        aria-controls="public-nav-panel"
        :aria-expanded="menuOpen"
        :aria-label="menuOpen ? t('chrome.closeMenu') : t('chrome.openMenu')"
        @click="menuOpen = !menuOpen"
      >
        <IconGlyph :name="menuOpen ? 'close' : 'menu'" :size="22" />
      </button>
    </div>

    <div id="public-nav-panel" ref="panelRef" class="public-nav-panel">
      <nav class="nav-links public-nav-links" :aria-label="t('chrome.primaryNavigation')">
        <RouterLink v-for="item in items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
          <a
            :href="href"
            class="public-nav-link"
            :class="{ 'router-link-active': isNavActive(item.to) }"
            :aria-current="isNavActive(item.to) ? 'page' : undefined"
            @click="navigate"
          >
            {{ item.label }}
          </a>
        </RouterLink>
      </nav>

      <div class="public-nav-actions">
        <div class="public-nav-trust">
          <span>{{ t('chrome.audience') }}</span>
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
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { publicNav } from '@/config/navigation'

import AppBrand from './AppBrand.vue'
import { DRAWER_MEDIA_QUERY, useMediaQuery, useOverlayFocus } from './useOverlayFocus'

interface LocalizedNavItem {
  readonly label: string
  readonly to: string
}

const SIGN_IN_ROUTE = { path: '/auth', query: { tab: 'signin' } } satisfies RouteLocationRaw
const SIGN_UP_ROUTE = { path: '/auth', query: { tab: 'register', role: 'student' } } satisfies RouteLocationRaw

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

// Below 900px the links and actions collapse into a menu panel under the bar.
const menuOpen = ref<boolean>(false)
const panelRef = ref<HTMLElement | null>(null)
const isCompact = useMediaQuery(DRAWER_MEDIA_QUERY)
const menuActive = computed<boolean>(() => menuOpen.value && isCompact.value)

useOverlayFocus({
  active: menuActive,
  container: panelRef,
  onClose: () => {
    menuOpen.value = false
  },
})

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)
watch(isCompact, (compact) => {
  if (!compact) menuOpen.value = false
})

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
