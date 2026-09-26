<template>
  <div class="language-switcher" :class="[`is-${size}`]">
    <span v-if="showLabel" class="language-switcher-label">{{ label || t('chrome.language') }}</span>
    <div class="pill-switch" :class="{ 'pill-switch--compact': size === 'compact' }">
      <button
        v-for="option in options"
        :key="option.value"
        :class="{ 'is-active': isActiveLocale(option.value) }"
        :aria-label="option.ariaLabel"
        :aria-pressed="isActiveLocale(option.value)"
        type="button"
        @click="selectLocale(option.value)"
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

type SupportedLocale = 'en' | 'ms' | 'zh'

interface LocaleOption {
  readonly label: string
  readonly value: SupportedLocale
  readonly ariaLabel: string
}

interface Props {
  showLabel?: boolean
  label?: string
  size?: 'default' | 'compact'
}

withDefaults(defineProps<Props>(), {
  showLabel: false,
  label: '',
  size: 'default',
})

const ui = useUiStore()
const { t } = useI18n()

const options = computed<readonly LocaleOption[]>(() => [
  { label: 'EN', value: 'en', ariaLabel: t('chrome.switchEnglish') },
  { label: 'BM', value: 'ms', ariaLabel: t('chrome.switchMalay') },
  { label: '简体中文', value: 'zh', ariaLabel: t('chrome.switchChinese') },
])

function isActiveLocale(locale: SupportedLocale): boolean {
  return ui.locale === locale
}

function selectLocale(locale: SupportedLocale): void {
  if (!isActiveLocale(locale)) {
    ui.setLocale(locale)
  }
}
</script>

<style scoped>
.language-switcher {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.language-switcher.is-compact {
  gap: 10px;
}

.language-switcher-label {
  color: var(--text-soft);
  font-size: 0.94rem;
  font-weight: 600;
}
</style>
