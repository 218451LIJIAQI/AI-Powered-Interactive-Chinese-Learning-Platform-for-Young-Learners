<template>
  <footer class="public-footer">
    <div class="public-footer-inner">
      <p class="public-footer-note">
        <span>{{ t('shell.footerCopyright', { year }) }}</span>
        <!-- The landing page opens with 你好, "hello"; the footer closes it with 再见, "see you again". One line of
             text, like the hero's "nǐ hǎo · hello": the spaces in it are the spaces shown. -->
        <span class="public-footer-bye">
          <span class="hanzi" lang="zh-CN">再见</span> <span lang="zh-Latn-pinyin">zàijiàn</span> ·
          {{ t('shell.footerBye') }}
        </span>
      </p>

      <nav :aria-label="t('shell.footerNavigation')">
        <ul class="public-footer-links">
          <li v-for="link in links" :key="link.to">
            <RouterLink class="public-footer-link" :to="link.to">{{ link.label }}</RouterLink>
          </li>
        </ul>
      </nav>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import { routeTitle } from '@/router/pageTitle'

/**
 * The footer of the public site (PublicLayout shows it under the pages whose route asks for it: the landing page): one
 * quiet line, as tall as the header. The year on the left; on the right the pages the header has no room for, so the
 * message form, the Safety centre and the policies are one click from the first page. What the header already offers
 * (the classes, Help, the account and the language switch) stays in view as the page scrolls and is not repeated.
 */
const { t } = useI18n()

const year = new Date().getFullYear()

/** A page is named as its own title names it (the browser tab and the page's h1), so a link says where it leads. */
const links = computed(() => [
  { label: t('shell.footerContact'), to: '/help#contact' },
  { label: routeTitle({ name: 'safety' }), to: '/safety' },
  { label: routeTitle({ name: 'terms' }), to: '/terms' },
  { label: routeTitle({ name: 'privacy' }), to: '/privacy' },
  { label: routeTitle({ name: 'responsible-ai' }), to: '/responsible-ai' },
])
</script>
