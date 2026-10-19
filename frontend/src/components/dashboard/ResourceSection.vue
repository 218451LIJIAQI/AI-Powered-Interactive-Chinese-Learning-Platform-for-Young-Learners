<template>
  <section class="stack resource-section">
    <slot v-if="!loading && !error" />
    <StatePanel
      v-else-if="loading"
      icon="progress"
      :title="t('resourceSection.loadingTitle')"
      :message="t('resourceSection.loadingMessage')"
      :eyebrow="t('resourceSection.loadingEyebrow')"
      tone="loading"
    />
    <StatePanel
      v-else
      icon="alert"
      :title="t('resourceSection.errorTitle')"
      :message="error || t('resourceSection.defaultError')"
      :eyebrow="t('resourceSection.errorEyebrow')"
      tone="error"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="retry">{{ t('resourceSection.retry') }}</button>
      </template>
    </StatePanel>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import StatePanel from '@/components/common/StatePanel.vue'
import { resourceSectionMessages } from '@/plugins/resourceSectionMessages'

const { t } = useI18n({
  messages: {
    en: { resourceSection: resourceSectionMessages.en },
    ms: { resourceSection: resourceSectionMessages.ms },
    'zh-CN': { resourceSection: resourceSectionMessages['zh-CN'] },
  },
})

interface Props {
  loading?: boolean
  error?: string | null
}

defineProps<Props>()

const emit = defineEmits<{
  retry: []
}>()

const retry = () => emit('retry')
</script>
