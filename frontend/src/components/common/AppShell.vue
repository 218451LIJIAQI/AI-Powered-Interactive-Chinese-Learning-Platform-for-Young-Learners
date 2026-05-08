<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LanguageSwitcher from './LanguageSwitcher.vue'
import ThemeToggle from './ThemeToggle.vue'
import { useAuthStore } from '../../stores/authStore'

type NavItem = {
  key: string
  labelKey: string
  icon: string
  routeName?: string
  disabled?: boolean
  chip?: string
}

const props = defineProps<{
  variant: 'public' | 'student' | 'teacher' | 'admin'
  title: string
  subtitle: string
  navItems: NavItem[]
  footerLinks?: { labelKey: string; routeName: string }[]
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const isPublic = computed(() => props.variant === 'public')
const currentUserName = computed(() => authStore.currentUser?.fullName ?? '')
const activeKey = computed(() => String(route.name ?? ''))

async function handleNavigate(routeName?: string, disabled?: boolean) {
  if (!routeName || disabled) {
    if (disabled) {
      ElMessage.info(t('common.comingSoon'))
    }
    return
  }

  await router.push({ name: routeName })
}

async function handleLogout() {
  await authStore.logout()
  ElMessage.success(t('auth.messages.logoutSuccess'))
  await router.push('/login')
}
</script>

<template>
  <div class="app-shell" :class="`app-shell--${variant}`">
    <header class="shell-topbar">
      <div class="shell-brand">
        <router-link to="/" class="brand-mark">
          <span class="brand-mark__orb" />
          <span>{{ t('common.appName') }}</span>
        </router-link>
        <div class="shell-brand__copy">
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
      </div>

      <div class="shell-topbar__actions">
        <div v-if="!isPublic && currentUserName" class="header-user-chip">
          <el-icon><UserFilled /></el-icon>
          <span>{{ currentUserName }}</span>
        </div>
        <LanguageSwitcher />
        <ThemeToggle />
        <el-button v-if="!isPublic" type="danger" plain size="small" @click="handleLogout">
          {{ t('auth.logout') }}
        </el-button>
      </div>
    </header>

    <div class="shell-body" :class="{ 'shell-body--public': isPublic }">
      <aside class="shell-sidebar">
        <el-menu :default-active="activeKey" class="shell-menu">
          <el-menu-item
            v-for="item in navItems"
            :key="item.key"
            :index="item.routeName ?? item.key"
            :disabled="item.disabled"
            @click="handleNavigate(item.routeName, item.disabled)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ t(item.labelKey) }}</span>
            <el-tag v-if="item.chip" size="small" effect="plain" round class="shell-menu__chip">
              {{ item.chip }}
            </el-tag>
          </el-menu-item>
        </el-menu>
      </aside>

      <div class="shell-main">
        <main class="shell-content">
          <router-view />
        </main>

        <footer v-if="isPublic" class="public-footer">
          <div class="public-footer__brand">{{ t('common.appName') }}</div>
          <div class="public-footer__links">
            <router-link
              v-for="link in footerLinks"
              :key="link.routeName"
              :to="{ name: link.routeName }"
              class="public-footer__link"
            >
              {{ t(link.labelKey) }}
            </router-link>
          </div>
        </footer>
      </div>
    </div>
  </div>
</template>
