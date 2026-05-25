<template>
  <button type="button" class="notification-link-button" @click="openTarget">
    <slot />
  </button>
</template>

<script setup lang="ts">
/**
 * Notification list-item wrapper that:
 *   1. Normalises the stored `targetPath` through
 *      {@link normalizeAppRoute} so stale deep-links from old
 *      backend builds still resolve to a valid current route.
 *   2. Marks the notification read via the API on activation.
 *   3. Broadcasts a `mandarin-spark:notification-read` custom event
 *      so the badge count in DashboardTopbar can decrement
 *      immediately without re-fetching.
 *
 * Failures from the mark-read call are intentionally swallowed —
 * the user-facing navigation still proceeds, and the next page
 * refresh will reconcile the server state.
 */
import { useRouter } from 'vue-router'

import { notificationsApi } from '@/api/services'
import { normalizeAppRoute } from '@/utils/routeCompat'

interface Props {
  notificationId?: number
  targetPath?: string | null
  fallbackTo?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  read: [notificationId: number]
}>()

const router = useRouter()

function openTarget(): void {
  const target = normalizeAppRoute(props.targetPath) ?? props.fallbackTo ?? '/'

  if (props.notificationId) {
    const id = props.notificationId
    notificationsApi
      .markAsRead(id)
      .then(() => {
        emit('read', id)
        window.dispatchEvent(new CustomEvent('mandarin-spark:notification-read', { detail: { notificationId: id } }))
      })
      .catch(() => {
        /* swallowed — navigation proceeds regardless */
      })
  }

  void router.push(target)
}
</script>

<style scoped>
.notification-link-button {
  border: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  width: 100%;
  text-align: inherit;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
</style>
