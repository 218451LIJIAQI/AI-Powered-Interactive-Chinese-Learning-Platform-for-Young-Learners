<template>
  <div class="language-switcher">
    <span v-if="showLabel" class="language-switcher-label">{{ t('chrome.language') }}</span>
    <div class="pill-switch">
      <button
        v-for="option in options"
        :key="option.value"
        :aria-label="`${option.label}, ${option.ariaLabel}`"
        :aria-pressed="ui.locale === option.value"
        type="button"
        @click="ui.setLocale(option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useUiStore } from '@/stores/ui'
import type { AppLanguage } from '@/types/api'

interface LocaleOption {
  readonly label: string
  readonly value: AppLanguage
  readonly ariaLabel: string
}

interface Props {
  showLabel?: boolean
}

withDefaults(defineProps<Props>(), {
  showLabel: false,
})

const ui = useUiStore()
const { t } = useI18n()

const options = computed<readonly LocaleOption[]>(() => [
  { label: 'EN', value: 'en', ariaLabel: t('chrome.switchEnglish') },
  { label: 'BM', value: 'ms', ariaLabel: t('chrome.switchMalay') },
  { label: '简体中文', value: 'zh-CN', ariaLabel: t('chrome.switchChinese') },
])
</script>

<style scoped>
:where(.language-switcher) {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.language-switcher-label {
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}
</style>
