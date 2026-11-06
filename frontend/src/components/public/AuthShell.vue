<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- The sign-in pages have no site header, so the brand leads back to the landing page. -->
      <div class="auth-side">
        <AppBrand to="/" />
        <figure class="auth-word">
          <span class="auth-word-tiles" lang="zh-CN">
            <span class="hanzi-tile auth-word-tile">你</span>
            <span class="hanzi-tile auth-word-tile">好</span>
          </span>
          <figcaption class="auth-word-gloss">
            <span lang="zh-Latn-pinyin">nǐ hǎo</span> · <span>{{ t('authShell.meaning') }}</span>
          </figcaption>
        </figure>
      </div>

      <section class="auth-panel">
        <div class="auth-panel-top">
          <LanguageSwitcher />
        </div>
        <slot />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { authShellMessages } from '@/plugins/authShellMessages'

/**
 * The card of the sign-in, create-account, recovery and admin-invite pages: a sunken brand side with the first word a
 * learner meets (你好) beside the white form side. The page puts its own h1 in the form side.
 */
const { t } = useLocalMessages({ authShell: authShellMessages })
</script>
