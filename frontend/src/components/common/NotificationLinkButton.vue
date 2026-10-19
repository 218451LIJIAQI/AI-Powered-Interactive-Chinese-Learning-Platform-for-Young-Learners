<template>
  <button type="button" class="notification-link-button" @click="openTarget">
    <slot />
  </button>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import { notificationsApi } from '@/api/services'
import { NOTIFICATION_READ_EVENT } from '@/utils/notifications'
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

function notificationTarget(): string {
  return normalizeAppRoute(props.targetPath) ?? normalizeAppRoute(props.fallbackTo) ?? '/'
}

function emitRead(notificationId: number): void {
  emit('read', notificationId)
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(NOTIFICATION_READ_EVENT, { detail: { notificationId } }))
  }
}

async function markReadInBackground(notificationId: number): Promise<void> {
  try {
    await notificationsApi.markAsRead(notificationId)
    emitRead(notificationId)
  } catch {
    // Navigation should not be blocked by a stale notification state.
  }
}

function openTarget(): void {
  if (props.notificationId) {
    void markReadInBackground(props.notificationId)
  }

  void router.push(notificationTarget())
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
