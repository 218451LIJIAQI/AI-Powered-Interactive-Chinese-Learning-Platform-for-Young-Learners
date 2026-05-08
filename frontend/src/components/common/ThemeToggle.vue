<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useThemeStore } from '../../stores/themeStore'

const themeStore = useThemeStore()
const { t } = useI18n()

const modelValue = computed({
  get: () => themeStore.isDark,
  set: () => themeStore.toggleTheme(),
})
</script>

<template>
  <el-tooltip :content="t('common.theme')" placement="bottom">
    <el-switch
      v-model="modelValue"
      inline-prompt
      :active-text="t('common.dark')"
      :inactive-text="t('common.light')"
      class="theme-toggle"
    />
  </el-tooltip>
</template>
