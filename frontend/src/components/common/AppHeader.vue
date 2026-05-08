<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LanguageSwitcher from './LanguageSwitcher.vue'
import ThemeToggle from './ThemeToggle.vue'

type HeaderLink = {
  labelKey: string
  name: string
}

const props = defineProps<{
  links: HeaderLink[]
  variant?: 'public' | 'dashboard'
}>()

const route = useRoute()
const { t } = useI18n()

const isPublic = computed(() => props.variant !== 'dashboard')
</script>

<template>
  <header class="app-header" :class="{ 'app-header--dashboard': !isPublic }">
    <div class="brand-block">
      <router-link to="/" class="brand-mark">
        <span class="brand-mark__orb" />
        <span>{{ t('common.appName') }}</span>
      </router-link>
    </div>

    <nav class="main-nav">
      <router-link
        v-for="link in links"
        :key="link.name"
        :to="{ name: link.name }"
        class="main-nav__link"
        :class="{ 'is-active': route.name === link.name }"
      >
        {{ t(link.labelKey) }}
      </router-link>
    </nav>

    <div class="toolbar-group">
      <LanguageSwitcher />
      <ThemeToggle />
    </div>
  </header>
</template>
