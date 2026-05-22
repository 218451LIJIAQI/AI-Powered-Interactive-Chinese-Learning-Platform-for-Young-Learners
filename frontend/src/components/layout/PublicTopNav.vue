<template>
  <header class="public-nav">
    <AppBrand compact />

    <nav class="nav-links public-nav-links" aria-label="Primary">
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" custom v-slot="{ href, navigate }">
        <a :href="href" class="public-nav-link" :class="{ 'router-link-active': isNavActive(item.to) }" @click="navigate">
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
      <button class="btn btn-secondary public-nav-button" type="button" @click="openSignIn">
        Sign In
      </button>
      <button class="btn btn-accent public-nav-button public-nav-button--primary" type="button" @click="openSignUp">
        Create Account
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { publicNav } from '@/config/navigation'

import AppBrand from './AppBrand.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const items = computed(() => publicNav.map((item) => ({ label: t(item.labelKey), to: item.to })))

function openSignIn() {
  void router.push({ path: '/auth', query: { tab: 'signin' } })
}

function openSignUp() {
  void router.push({ path: '/auth', query: { tab: 'register', role: 'student' } })
}

function isNavActive(to: string) {
  const [pathPart, hashPart] = to.split('#')
  const targetPath = pathPart || '/'
  const targetHash = hashPart ? `#${hashPart}` : ''

  if (targetHash) {
    return route.path === targetPath && route.hash === targetHash
  }

  if (targetPath === '/') {
    return route.path === '/' && !route.hash
  }

  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}
</script>
