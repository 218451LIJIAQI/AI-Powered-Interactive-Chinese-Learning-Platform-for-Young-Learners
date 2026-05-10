<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useAuthStore } from '../../stores/authStore'

const { t } = useI18n()
const authStore = useAuthStore()

const homeRouteName = computed(() => {
  if (!authStore.isAuthenticated) {
    return 'landing'
  }

  if (authStore.hasRole('STUDENT')) {
    return 'student-dashboard'
  }

  if (authStore.hasRole('TEACHER')) {
    return 'teacher-dashboard'
  }

  if (authStore.hasRole('ADMIN')) {
    return 'admin-dashboard'
  }

  return 'landing'
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      :eyebrow="t('notFound.eyebrow')"
      :title="t('notFound.title')"
      :description="t('notFound.description')"
    />

    <el-card class="simple-page-card not-found-card" shadow="hover">
      <h2>{{ t('notFound.heading') }}</h2>
      <p>{{ t('notFound.body') }}</p>
      <div class="not-found-card__actions">
        <router-link :to="{ name: homeRouteName }">
          <el-button type="primary" size="large" round>{{ t('notFound.backHome') }}</el-button>
        </router-link>
        <router-link :to="{ name: 'faq' }">
          <el-button size="large" plain round>{{ t('nav.faq') }}</el-button>
        </router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.not-found-card {
  padding: 32px;
}

.not-found-card h2 {
  margin: 0 0 12px;
  font-size: clamp(1.6rem, 3vw, 2.2rem);
}

.not-found-card p {
  color: var(--text-secondary);
  line-height: 1.7;
  max-width: 720px;
}

.not-found-card__actions {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 24px;
}
</style>
