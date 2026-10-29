<template>
  <header class="dashboard-topbar">
    <span class="topbar-section" data-testid="topbar-section">{{ sectionLabel }}</span>

    <div class="topbar-actions">
      <LanguageSwitcher />

      <RouterLink
        :to="`${rolePath}/notifications`"
        class="topbar-icon-button"
        :class="{ 'is-active': route.path === `${rolePath}/notifications` }"
        :aria-label="notificationAriaLabel"
        data-testid="topbar-notifications"
      >
        <IconGlyph name="bell" />
        <span v-if="notificationCount" class="topbar-notification-badge" aria-hidden="true">{{
          notificationCount > 99 ? '99+' : notificationCount
        }}</span>
      </RouterLink>

      <RouterLink
        :to="`${rolePath}/account`"
        class="topbar-user"
        :class="{ 'is-active': route.path === `${rolePath}/account` }"
        :aria-label="t('dashboard.openAccount', { name: displayName })"
        data-testid="topbar-account"
      >
        <PortraitAvatar :size="34" :name="displayName" />
        <span class="topbar-user-copy">
          <strong class="topbar-user-name">{{ displayName }}</strong>
          <span class="topbar-user-role">{{ t(roleLabelKey) }}</span>
        </span>
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { notificationsApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { activeNavItem } from '@/config/navigation'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { routeTitle } from '@/router/pageTitle'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { NOTIFICATION_READ_EVENT } from '@/utils/notifications'

import { useLayoutRole } from './useLayoutRole'

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const { t: globalT } = useI18n({ useScope: 'global' })
const { t } = useLocalMessages({ dashboard: dashboardMessages })
const { role, roleLabelKey } = useLayoutRole()

const rolePath = computed(() => `/${role.value.toLowerCase()}`)

/** The header names the section the page belongs to (the active sidebar item); other pages show their own title. */
const sectionLabel = computed(() => {
  const item = activeNavItem(role.value, route)
  return item ? globalT(item.labelKey) : routeTitle(route, ui.locale)
})

const displayName = computed(() => auth.displayName)

// FRNOTIF02: the bell opens the role's notifications page; the badge shows the server's unread count.
const unreadResource = useResource(() => notificationsApi.unreadCount(), false)
const notificationCount = computed<number>(() => unreadResource.data.value?.unreadCount ?? 0)
const notificationAriaLabel = computed<string>(() =>
  notificationCount.value > 0
    ? t('dashboard.unreadNotifications', { count: notificationCount.value })
    : t('dashboard.notifications'),
)

function refreshUnreadCount(): void {
  if (auth.isAuthenticated) void unreadResource.reload()
}

onMounted(() => {
  refreshUnreadCount()
  window.addEventListener(NOTIFICATION_READ_EVENT, refreshUnreadCount)
})

onBeforeUnmount(() => {
  window.removeEventListener(NOTIFICATION_READ_EVENT, refreshUnreadCount)
})
</script>
