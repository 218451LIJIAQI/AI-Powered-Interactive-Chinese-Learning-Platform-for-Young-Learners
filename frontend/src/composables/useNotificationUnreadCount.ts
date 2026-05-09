import { computed, onMounted, onUnmounted, ref } from 'vue'

import { notificationService } from '../services/notificationService'

const unreadCount = ref(0)
const loading = ref(false)

async function refreshUnreadCount() {
  loading.value = true
  try {
    const result = await notificationService.getUnreadCount()
    unreadCount.value = result.unreadCount
  } catch {
    unreadCount.value = 0
  } finally {
    loading.value = false
  }
}

export function useNotificationUnreadCount(enabled = true) {
  const shouldShowBadge = computed(() => enabled && unreadCount.value > 0)

  function handleRefreshEvent() {
    if (enabled) {
      void refreshUnreadCount()
    }
  }

  onMounted(() => {
    if (enabled) {
      void refreshUnreadCount()
      window.addEventListener('notifications:refresh', handleRefreshEvent)
    }
  })

  onUnmounted(() => {
    if (enabled) {
      window.removeEventListener('notifications:refresh', handleRefreshEvent)
    }
  })

  return {
    unreadCount,
    loading,
    shouldShowBadge,
    refreshUnreadCount,
  }
}
