<script setup lang="ts">
import AppHeader from '../components/common/AppHeader.vue'

const links = [
  { labelKey: 'nav.home', name: 'landing' },
  { labelKey: 'nav.courses', name: 'courses' },
  { labelKey: 'nav.faq', name: 'faq' },
  { labelKey: 'nav.login', name: 'login' },
  { labelKey: 'nav.register', name: 'register' },
]
</script>

<template>
  <div class="site-shell site-shell--public">
    <AppHeader :links="links" variant="public" />
    <main class="site-content">
      <router-view />
    </main>
  </div>
</template>
