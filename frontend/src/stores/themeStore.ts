import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark'

const THEME_KEY = 'ai-chinese-learning-theme'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem(THEME_KEY) as ThemeMode) || 'light')

  watch(
    mode,
    (value) => {
      document.documentElement.setAttribute('data-theme', value)
      localStorage.setItem(THEME_KEY, value)
    },
    { immediate: true },
  )

  const isDark = computed(() => mode.value === 'dark')

  function setMode(value: ThemeMode) {
    mode.value = value
  }

  function toggleTheme() {
    mode.value = mode.value === 'light' ? 'dark' : 'light'
  }

  return {
    mode,
    isDark,
    setMode,
    toggleTheme,
  }
})
