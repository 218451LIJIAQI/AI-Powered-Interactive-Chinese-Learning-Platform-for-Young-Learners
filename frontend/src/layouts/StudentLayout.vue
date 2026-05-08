<script setup lang="ts">
import AppHeader from '../components/common/AppHeader.vue'

const links = [{ labelKey: 'nav.studentDashboard', name: 'student-dashboard' }]
</script>

<template>
  <div class="site-shell site-shell--student">
    <AppHeader :links="links" variant="dashboard" />
    <main class="dashboard-content">
      <router-view />
    </main>
  </div>
</template>
