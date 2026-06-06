<template>
  <section class="stack resource-section">
    <slot v-if="!loading && !error" />
    <StatePanel
      v-else-if="loading"
      icon="progress"
      title="Loading your content"
      message="Just a moment while we get things ready for you!"
      eyebrow="Loading"
      tone="loading"
    />
    <StatePanel
      v-else
      icon="alert"
      title="Something went wrong"
      :message="error || DEFAULT_ERROR_MESSAGE"
      eyebrow="Needs attention"
      tone="error"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="retry">Try again</button>
      </template>
    </StatePanel>
  </section>
</template>

<script setup lang="ts">
import StatePanel from '@/components/common/StatePanel.vue'

const DEFAULT_ERROR_MESSAGE = 'We could not reach the learning service right now.'

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
