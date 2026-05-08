<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LanguageSwitcher from './LanguageSwitcher.vue'
import ThemeToggle from './ThemeToggle.vue'
import { useAuthStore } from '../../stores/authStore'

type HeaderLink = {
  labelKey: string
  name: string
}

const props = defineProps<{
  links: HeaderLink[]
  variant?: 'public' | 'dashboard'
}>()

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()

const isPublic = computed(() => props.variant !== 'dashboard')
const currentUserName = computed(() => authStore.currentUser?.fullName ?? '')

async function handleLogout() {
  await authStore.logout()
  ElMessage.success(t('auth.messages.logoutSuccess'))
  await router.push('/login')
}
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
      <div v-if="!isPublic && currentUserName" class="header-user-chip">
        <span>{{ currentUserName }}</span>
      </div>
      <LanguageSwitcher />
      <ThemeToggle />
      <el-button v-if="!isPublic" size="small" type="danger" plain @click="handleLogout">
        {{ t('auth.logout') }}
      </el-button>
    </div>
  </header>
</template>
