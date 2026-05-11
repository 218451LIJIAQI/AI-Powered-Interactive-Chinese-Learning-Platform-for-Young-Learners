import { computed, onMounted, onUnmounted, ref } from 'vue'

import { APP_EVENTS } from '../constants/appEvents'
import { notificationService } from '../services/notificationService'

// Shared reactive state across the entire app (ok for cross-component
// coordination — a single source of truth for the badge).
const unreadCount = ref(0)
const loading = ref(false)

async function refreshUnreadCount() {
  loading.value = true
  try {
    const result = await notificationService.getUnreadCount()
    unreadCount.value = result.unreadCount
  } catch {
    // Silently reset on transient failures — the badge is non-critical UI.
    unreadCount.value = 0
  } finally {
    loading.value = false
  }
}

/** Explicitly clear the cached count (e.g. on logout). */
export function resetUnreadCount() {
  unreadCount.value = 0
  loading.value = false
}

export function useNotificationUnreadCount(enabled = true) {
  const shouldShowBadge = computed(() => enabled && unreadCount.value > 0)

  function handleRefreshEvent() {
    if (enabled) {
      void refreshUnreadCount()
    }
  }

  onMounted(() => {
    if (!enabled) {
      return
    }
    void refreshUnreadCount()
    window.addEventListener(APP_EVENTS.NOTIFICATIONS_REFRESH, handleRefreshEvent)
  })

  onUnmounted(() => {
    // Always remove the listener — cheaper than conditional and immune to
    // the consumer flipping `enabled` between registration and teardown.
    window.removeEventListener(APP_EVENTS.NOTIFICATIONS_REFRESH, handleRefreshEvent)
  })

  return {
    unreadCount,
    loading,
    shouldShowBadge,
    refreshUnreadCount,
  }
}
