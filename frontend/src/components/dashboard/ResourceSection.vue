<template>
  <section class="stack resource-section">
    <slot v-if="!loading && !error" />
    <StatePanel
      v-else-if="loading"
      icon="progress"
      title="Loading data"
      message="Fetching the latest data for this workspace. Layout and actions will appear as soon as the request completes."
      eyebrow="Loading"
      tone="loading"
    />
    <StatePanel
      v-else
      icon="alert"
      title="This section is unavailable"
      :message="error || 'The current API route could not be reached.'"
      eyebrow="Needs attention"
      tone="error"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="$emit('retry')">Retry</button>
      </template>
    </StatePanel>
  </section>
</template>

<script setup lang="ts">
import StatePanel from '@/components/common/StatePanel.vue'

defineProps<{
  loading?: boolean
  error?: string | null
}>()

defineEmits<{
  retry: []
}>()
</script>
