<template>
  <div class="language-switcher" :class="[`is-${size}`]">
    <span v-if="showLabel" class="language-switcher-label">{{ label }}</span>
    <div class="pill-switch" :class="{ 'pill-switch--compact': size === 'compact' }">
      <button
        v-for="option in options"
        :key="option.value"
        :class="{ 'is-active': option.value === locale }"
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

import { useUiStore } from '@/stores/ui'

withDefaults(
  defineProps<{
    showLabel?: boolean
    label?: string
    size?: 'default' | 'compact'
  }>(),
  {
    showLabel: false,
    label: 'Language / 语言',
    size: 'default',
  },
)

const ui = useUiStore()
const locale = computed(() => ui.locale)

const options = [
  { label: 'EN', value: 'en' as const },
  { label: 'BM', value: 'ms' as const },
  { label: '简体中文', value: 'zh' as const },
]
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
