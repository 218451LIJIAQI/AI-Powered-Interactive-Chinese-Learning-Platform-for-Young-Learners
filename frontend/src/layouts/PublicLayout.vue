<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppShell from '../components/common/AppShell.vue'

const { t } = useI18n()

const navItems = [
  { key: 'landing', labelKey: 'nav.home', routeName: 'landing', icon: 'House' },
  { key: 'courses', labelKey: 'nav.courses', routeName: 'courses', icon: 'Reading' },
  { key: 'faq', labelKey: 'nav.faq', routeName: 'faq', icon: 'QuestionFilled' },
  { key: 'login', labelKey: 'nav.login', routeName: 'login', icon: 'User' },
  { key: 'register', labelKey: 'nav.register', routeName: 'register', icon: 'EditPen' },
]

const footerLinks = [
  { labelKey: 'nav.terms', routeName: 'terms' },
  { labelKey: 'nav.privacy', routeName: 'privacy' },
  { labelKey: 'nav.childSafety', routeName: 'child-safety' },
  { labelKey: 'nav.aiDisclaimer', routeName: 'ai-disclaimer' },
]
</script>

<template>
  <AppShell
    variant="public"
    :title="t('shell.publicTitle')"
    :subtitle="t('shell.publicSubtitle')"
    :nav-items="navItems"
    :footer-links="footerLinks"
  />
</template>
