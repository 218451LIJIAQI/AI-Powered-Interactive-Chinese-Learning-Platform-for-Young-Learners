<template>
  <div class="auth-page">
    <!-- The brand side: one character in its 田字格 square, its pinyin and meaning, and one line of promise.
         The sign-in pages have no site header, so the brand leads back to the landing page. -->
    <div class="auth-brand">
      <AppBrand to="/" />
      <figure class="auth-brand-word">
        <span class="hanzi-tile auth-brand-tile" lang="zh-CN" aria-hidden="true">{{ copy.hanzi }}</span>
        <figcaption class="auth-brand-gloss">
          <span class="sr-only">{{ copy.hanzi }}</span>
          <span class="auth-brand-pinyin" lang="zh-Latn-pinyin">{{ copy.pinyin }}</span>
          <span class="auth-brand-meaning">{{ t(copy.meaning) }}</span>
        </figcaption>
      </figure>
      <p class="auth-brand-promise">{{ t(copy.promise) }}</p>
    </div>

    <section class="auth-panel">
      <div class="auth-panel-top">
        <LanguageSwitcher show-label />
      </div>
      <div class="auth-panel-body">
        <slot />
      </div>
      <footer class="auth-panel-foot">
        <p class="auth-panel-safety">
          <IconGlyph name="shield" :size="16" />
          <span>{{ t(copy.safety) }}</span>
        </p>
        <nav class="auth-panel-links" :aria-label="t('authShell.linksLabel')">
          <RouterLink to="/help">{{ t('authShell.help') }}</RouterLink>
          <RouterLink to="/privacy">{{ t('authShell.privacy') }}</RouterLink>
          <RouterLink to="/terms">{{ t('authShell.terms') }}</RouterLink>
        </nav>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { authShellMessages } from '@/plugins/authShellMessages'

/**
 * The split frame of the sign-in, create-account, recovery and admin-invite pages: a calm brand side (a character in
 * a large 田字格 square, its pinyin and meaning, one line of promise) beside the form. The page puts its own h1 in the
 * form.
 */
interface Props {
  variant?: 'signin' | 'register' | 'recovery'
}

const props = withDefaults(defineProps<Props>(), { variant: 'signin' })

const { t } = useLocalMessages({ authShell: authShellMessages })

/** 回 "return" to sign back in, 学 "learn" to start, 钥 "key" to recover the account. */
const COPY = {
  signin: {
    hanzi: '回',
    pinyin: 'huí',
    meaning: 'authShell.signInMeaning',
    promise: 'authShell.signInPromise',
    safety: 'authShell.signInSafety',
  },
  register: {
    hanzi: '学',
    pinyin: 'xué',
    meaning: 'authShell.registerMeaning',
    promise: 'authShell.registerPromise',
    safety: 'authShell.signInSafety',
  },
  recovery: {
    hanzi: '钥',
    pinyin: 'yào',
    meaning: 'authShell.recoveryMeaning',
    promise: 'authShell.recoveryPromise',
    safety: 'authShell.recoverySafety',
  },
} as const

const copy = computed(() => COPY[props.variant])
</script>
