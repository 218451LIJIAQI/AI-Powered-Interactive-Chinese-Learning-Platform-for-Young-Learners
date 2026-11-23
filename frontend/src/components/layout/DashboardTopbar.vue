<template>
  <header class="dashboard-topbar">
    <div class="topbar-location">
      <button
        type="button"
        class="btn btn-secondary btn-icon navigation-toggle"
        aria-haspopup="dialog"
        :aria-expanded="navigationOpen"
        :aria-label="t('chrome.workspaceNavigation')"
        :title="t('chrome.workspaceNavigation')"
        @click="emit('open-navigation')"
      >
        <IconGlyph name="sidebar" :size="18" />
      </button>
      <span class="topbar-section" data-testid="topbar-section">{{ sectionLabel }}</span>
    </div>

    <div class="topbar-actions">
      <LanguageSwitcher />

      <RouterLink
        :to="notificationsPath"
        class="topbar-icon-button"
        :class="{ 'is-active': route.path === notificationsPath }"
        :aria-current="route.path === notificationsPath ? 'page' : undefined"
        :aria-label="notificationAriaLabel"
        :title="notificationAriaLabel"
        data-testid="topbar-notifications"
      >
        <IconGlyph name="bell" />
        <span v-if="notificationCount > 0" class="topbar-notification-badge" aria-hidden="true">{{
          formatBadgeCount(notificationCount)
        }}</span>
      </RouterLink>

      <RouterLink
        :to="accountPath"
        class="topbar-user"
        :class="{ 'is-active': route.path === accountPath }"
        :aria-current="route.path === accountPath ? 'page' : undefined"
        :title="t('dashboard.openAccount', { name: displayName })"
        data-testid="topbar-account"
      >
        <PortraitAvatar :size="36" :name="displayName" />
        <strong class="topbar-user-name">{{ displayName }}</strong>
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useWorkspaceRole } from '@/composables/useWorkspaceRole'
import { activeNavItem } from '@/config/navigation'
import { dashboardMessages } from '@/plugins/dashboardMessages'
import { routeTitle } from '@/router/pageTitle'
import { useAuthStore } from '@/stores/auth'
import { formatBadgeCount } from '@/utils/format'
import { workspacePathForRole } from '@/utils/role'

const props = withDefaults(defineProps<{ notificationCount: number; navigationOpen?: boolean }>(), {
  navigationOpen: false,
})
const emit = defineEmits<{ 'open-navigation': [] }>()

const auth = useAuthStore()
const route = useRoute()
const { t } = useLocalMessages({ dashboard: dashboardMessages })

const role = useWorkspaceRole()
const notificationsPath = computed(() => workspacePathForRole(role.value, 'notifications'))
const accountPath = computed(() => workspacePathForRole(role.value, 'account'))

/** The header names the section the page belongs to (the active sidebar item); other pages show their own title. */
const sectionLabel = computed(() => {
  const item = activeNavItem(role.value, route)
  return item ? t(item.labelKey) : routeTitle(route)
})

const displayName = computed(() => auth.currentUser?.fullName ?? '')

const notificationAriaLabel = computed<string>(() =>
  props.notificationCount > 0
    ? t('dashboard.unreadNotifications', { count: props.notificationCount })
    : t('dashboard.notifications'),
)
</script>
