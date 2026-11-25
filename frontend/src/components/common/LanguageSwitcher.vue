<template>
  <div class="pill-switch" role="group" :aria-label="t('chrome.language')">
    <button
      v-for="option in options"
      :key="option.value"
      :aria-label="`${option.label}, ${option.ariaLabel}`"
      :aria-pressed="appLocale === option.value"
      type="button"
      @click="choose(option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAuthStore } from '@/stores/auth'
import { appLocale, setLocale } from '@/plugins/i18n'
import type { AppLanguage } from '@/types/api'

interface LocaleOption {
  readonly label: string
  readonly value: AppLanguage
  readonly ariaLabel: string
}

const auth = useAuthStore()
const { t } = useI18n()

const options = computed<readonly LocaleOption[]>(() => [
  { label: 'EN', value: 'en', ariaLabel: t('chrome.switchEnglish') },
  { label: 'BM', value: 'ms', ariaLabel: t('chrome.switchMalay') },
  { label: '中文', value: 'zh-CN', ariaLabel: t('chrome.switchChinese') },
])

/** Switches the interface; a signed-in account keeps the choice (its next sign-in and its e-mails use it). */
function choose(locale: AppLanguage): void {
  setLocale(locale)
  void auth.savePreferredLanguage(locale)
}
</script>
