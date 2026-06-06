<template>
  <div class="language-switcher" :class="[`is-${size}`]">
    <span v-if="showLabel" class="language-switcher-label">{{ label }}</span>
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
  label: 'Language / 语言',
  size: 'default',
})

const ui = useUiStore()

const options: readonly LocaleOption[] = [
  { label: 'EN', value: 'en', ariaLabel: 'Switch to English' },
  { label: 'BM', value: 'ms', ariaLabel: 'Switch to Bahasa Melayu' },
  { label: '简体中文', value: 'zh', ariaLabel: 'Switch to Simplified Chinese' },
]

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
