<template>
  <section class="stack resource-section">
    <slot v-if="!loading && !error" />
    <StatePanel
      v-else-if="loading"
      :title="t('resourceSection.loadingTitle')"
      :message="t('resourceSection.loadingMessage')"
      :eyebrow="t('resourceSection.loadingEyebrow')"
      tone="loading"
      spacious
    />
    <StatePanel
      v-else
      icon="alert"
      :title="t('resourceSection.errorTitle')"
      :message="error ?? ''"
      :eyebrow="t('resourceSection.errorEyebrow')"
      tone="error"
      spacious
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="emit('retry')">{{ t('resourceSection.retry') }}</button>
      </template>
    </StatePanel>
  </section>
</template>

<script setup lang="ts">
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { resourceSectionMessages } from '@/plugins/resourceSectionMessages'

const { t } = useLocalMessages({ resourceSection: resourceSectionMessages })

interface Props {
  loading?: boolean
  error?: string | null
}

defineProps<Props>()

const emit = defineEmits<{
  retry: []
}>()
</script>
