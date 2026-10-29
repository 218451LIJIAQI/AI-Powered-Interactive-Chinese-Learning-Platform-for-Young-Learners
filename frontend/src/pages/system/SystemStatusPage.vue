<template>
  <div class="system-page">
    <header class="system-top">
      <AppBrand to="/" />
      <!-- FRLANG01: the language can be changed on every page, these included. -->
      <LanguageSwitcher show-label />
    </header>

    <main id="main-content" class="system-card" tabindex="-1" aria-labelledby="system-title">
      <!-- One character in its 田字格 square: 迷 "lost" for a missing page, 锁 "lock" for a page of another role. -->
      <div class="system-visual" role="img" :aria-label="t(page.tileLabel)">
        <span class="hanzi-tile system-tile" lang="zh-CN" aria-hidden="true">{{ page.hanzi }}</span>
        <p class="system-word" aria-hidden="true">
          <span class="system-pinyin" lang="zh-Latn-pinyin">{{ page.pinyin }}</span>
          <span>{{ t(page.meaning) }}</span>
        </p>
      </div>

      <div class="system-copy">
        <p class="system-kicker">
          <span class="system-code">{{ page.code }}</span>
          <span>{{ t(page.kicker) }}</span>
        </p>
        <h1 id="system-title">{{ t(page.title) }}</h1>
        <p class="system-message">{{ t(page.message) }}</p>

        <div class="system-actions">
          <template v-if="kind === 'not-found'">
            <RouterLink class="btn btn-primary btn-lg" :to="homeRoute" data-testid="system-home">
              {{ t('system.returnHome') }}
            </RouterLink>
            <RouterLink class="btn btn-ghost btn-lg" to="/classes">{{ t('system.exploreClasses') }}</RouterLink>
          </template>
          <template v-else-if="auth.isAuthenticated">
            <button
              class="btn btn-primary btn-lg"
              type="button"
              :disabled="signingOut"
              :aria-busy="signingOut"
              data-testid="system-switch-account"
              @click="useAnotherAccount"
            >
              {{ signingOut ? t('system.signingOut') : t('system.useAnotherAccount') }}
            </button>
            <RouterLink class="btn btn-ghost btn-lg" :to="homeRoute" data-testid="system-home">
              {{ t('system.myHome') }}
            </RouterLink>
          </template>
          <template v-else>
            <RouterLink class="btn btn-primary btn-lg" to="/auth">{{ t('system.signIn') }}</RouterLink>
            <RouterLink class="btn btn-ghost btn-lg" to="/" data-testid="system-home">
              {{ t('system.returnHome') }}
            </RouterLink>
          </template>
        </div>

        <nav class="system-links-nav" :aria-label="t('system.quickLinks')">
          <ul class="system-links">
            <li v-for="link in links" :key="link.label">
              <RouterLink class="system-link" :to="link.to">
                <IconGlyph class="system-link-icon" :name="link.icon" :size="18" />
                <span class="system-link-copy">
                  <strong>{{ t(link.label) }}</strong>
                  <span>{{ t(link.hint) }}</span>
                </span>
                <IconGlyph class="system-link-arrow" name="chevronRight" :size="18" />
              </RouterLink>
            </li>
          </ul>
        </nav>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { systemMessages } from '@/plugins/systemMessages'
import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'
import '@/styles/ops-public.css'

interface QuickLink {
  readonly to: RouteLocationRaw
  readonly icon: string
  readonly label: string
  readonly hint: string
}

const props = defineProps<{ kind: 'not-found' | 'unauthorized' }>()

const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ system: systemMessages })

const PAGES = {
  'not-found': {
    kicker: 'system.notFoundKicker',
    title: 'system.notFoundTitle',
    message: 'system.notFoundMessage',
    code: '404',
    hanzi: '迷',
    pinyin: 'mí',
    meaning: 'system.lost',
    tileLabel: 'system.notFoundTile',
  },
  unauthorized: {
    kicker: 'system.noAccessKicker',
    title: 'system.noAccessTitle',
    message: 'system.noAccessMessage',
    code: '403',
    hanzi: '锁',
    pinyin: 'suǒ',
    meaning: 'system.locked',
    tileLabel: 'system.noAccessTile',
  },
} as const

const page = computed(() => PAGES[props.kind])
const homeRoute = computed(() => defaultRouteForRole(auth.isAuthenticated ? auth.activeRole : null))

const links = computed<readonly QuickLink[]>(() => {
  if (props.kind === 'unauthorized') {
    return [
      { to: '/classes', icon: 'classes', label: 'system.exploreClasses', hint: 'system.classesHint' },
      {
        to: { path: '/help', hash: '#getting-started' },
        icon: 'students',
        label: 'system.roles',
        hint: 'system.rolesHint',
      },
    ]
  }
  const account: QuickLink = auth.isAuthenticated
    ? { to: homeRoute.value, icon: 'learn', label: 'system.myHome', hint: 'system.myHomeHint' }
    : { to: '/auth', icon: 'profile', label: 'system.signIn', hint: 'system.signInHint' }
  return [account, { to: '/help', icon: 'info', label: 'system.help', hint: 'system.helpHint' }]
})

const signingOut = ref(false)

/** Signs the current account out (on the server too) and opens the sign-in page for the other one. */
async function useAnotherAccount(): Promise<void> {
  signingOut.value = true
  try {
    await auth.logout()
  } finally {
    signingOut.value = false
  }
  await router.push('/auth')
}
</script>
