<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import enLocale from 'element-plus/es/locale/lang/en'
import msLocale from 'element-plus/es/locale/lang/ms'
import zhCnLocale from 'element-plus/es/locale/lang/zh-cn'
import { useI18n } from 'vue-i18n'

import { useIdlePrefetch } from './composables/useIdlePrefetch'
import { APP_EVENTS } from './constants/appEvents'
import { useLanguageStore } from './stores/languageStore'

const { t } = useI18n()
const languageStore = useLanguageStore()

// Warm up role-appropriate page chunks during browser idle time so first
// post-login navigation feels instant. Skips automatically on slow/data-saver
// connections.
useIdlePrefetch()

const elementLocale = computed(() => {
  if (languageStore.locale === 'ms') {
    return msLocale
  }

  if (languageStore.locale === 'zh') {
    return zhCnLocale
  }

  return enLocale
})

function handleForbidden(event: Event) {
  const detail = (event as CustomEvent<string>).detail
  ElMessage.error(detail || t('common.forbidden'))
}

onMounted(() => {
  window.addEventListener(APP_EVENTS.FORBIDDEN, handleForbidden)
})

onBeforeUnmount(() => {
  window.removeEventListener(APP_EVENTS.FORBIDDEN, handleForbidden)
})
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>
