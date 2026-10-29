<template>
  <footer class="public-footer">
    <div class="public-footer-brand">
      <AppBrand to="/" />
      <p class="public-footer-blurb">{{ t('shell.footerBlurb') }}</p>
    </div>
    <nav class="public-footer-columns" :aria-label="t('shell.footerLabel')">
      <section v-for="column in COLUMNS" :key="column.title" class="public-footer-column">
        <h2>{{ t(column.title) }}</h2>
        <ul>
          <li v-for="link in column.links" :key="link.label">
            <RouterLink :to="link.to">{{ t(link.label) }}</RouterLink>
          </li>
        </ul>
      </section>
    </nav>
    <p class="public-footer-copyright">{{ t('shell.copyright', { year }) }}</p>
  </footer>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

import AppBrand from '@/components/layout/AppBrand.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { publicShellMessages } from '@/plugins/publicShellMessages'

const { t } = useLocalMessages({ shell: publicShellMessages })

const year = new Date().getFullYear()

const COLUMNS = [
  {
    title: 'shell.columnLearn',
    links: [
      { label: 'shell.classes', to: '/classes' },
      { label: 'shell.howItWorks', to: '/#how-it-works' },
    ],
  },
  {
    title: 'shell.columnAbout',
    links: [
      { label: 'shell.aboutUs', to: '/#about' },
      { label: 'shell.teachWithUs', to: { path: '/auth', query: { tab: 'register', role: 'teacher' } } },
    ],
  },
  {
    title: 'shell.columnSafety',
    links: [
      { label: 'shell.safetyCentre', to: '/safety' },
      { label: 'shell.responsibleAi', to: '/responsible-ai' },
      { label: 'shell.privacy', to: '/privacy' },
      { label: 'shell.terms', to: '/terms' },
    ],
  },
  {
    title: 'shell.columnSupport',
    links: [
      { label: 'shell.help', to: '/help' },
      { label: 'shell.contact', to: '/help#contact' },
    ],
  },
] as const
</script>
