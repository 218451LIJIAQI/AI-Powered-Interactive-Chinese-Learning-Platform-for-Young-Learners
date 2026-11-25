<template>
  <StatePanel heading-tag="h2" tone="error" spacious :title="title" :message="message">
    <template #actions>
      <button v-if="onRetry" class="btn btn-secondary" type="button" @click="onRetry">{{ t('common.retry') }}</button>
      <RouterLink class="btn btn-primary" :to="backTo">{{ backLabel }}</RouterLink>
    </template>
  </StatePanel>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import StatePanel from '@/components/common/StatePanel.vue'

defineProps<{
  title: string
  message: string | null
  backTo: RouteLocationRaw
  backLabel: string
  onRetry?: () => void
}>()

const { t } = useI18n({ useScope: 'global' })
</script>
