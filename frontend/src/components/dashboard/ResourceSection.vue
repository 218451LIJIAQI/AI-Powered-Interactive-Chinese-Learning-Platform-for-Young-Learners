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
      :message="error || 'We could not reach the learning service right now.'"
      eyebrow="Needs attention"
      tone="error"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="$emit('retry')">Try again</button>
      </template>
    </StatePanel>
  </section>
</template>

<script setup lang="ts">
/**
 * Three-state wrapper around the loading / error / loaded
 * presentation of a dashboard resource section. Pages typically
 * pair this with `useResource` so the `loading` / `error` props
 * map directly to the composable's reactive refs.
 *
 * When error is present a "Try again" button emits `retry` so the
 * page can call `reload()` from `useResource` — recoverable failure
 * UX without per-page boilerplate.
 */
import StatePanel from '@/components/common/StatePanel.vue'

defineProps<{
  loading?: boolean
  error?: string | null
}>()

defineEmits<{
  retry: []
}>()
</script>
