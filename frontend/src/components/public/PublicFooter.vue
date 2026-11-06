<template>
  <footer class="public-footer">
    <div class="public-footer-brand">
      <AppBrand to="/" />
      <p class="type-caption">{{ t('shell.copyright', { year }) }}</p>
    </div>
    <nav class="public-footer-columns" :aria-label="t('shell.footerLabel')">
      <section v-for="column in COLUMNS" :key="column.title">
        <h2 class="page-eyebrow">{{ t(column.title) }}</h2>
        <ul class="public-footer-links">
          <li v-for="link in column.links" :key="link.label">
            <RouterLink :to="link.to">{{ t(link.label) }}</RouterLink>
          </li>
        </ul>
      </section>
    </nav>
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
    title: 'shell.columnTeachers',
    links: [{ label: 'shell.teachWithUs', to: { path: '/auth', query: { tab: 'register', role: 'teacher' } } }],
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
    links: [{ label: 'shell.help', to: '/help' }],
  },
] as const
</script>
