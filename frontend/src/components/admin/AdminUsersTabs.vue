<template>
  <RouteTabs :tabs="tabs" :label="t('adminUsers.tabsLabel')"
    ><template v-if="$slots.default" #default><slot /></template
  ></RouteTabs>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { adminUsersApi } from '@/api/ops'
import { shellApi } from '@/api/shell'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'

/**
 * The Users section's tabs: All users with the number of accounts, Teacher applications with those waiting for a decision
 * (a yellow badge, hidden at zero). The Teacher applications page passes the length of the list it shows (null while it
 * loads), so the badge follows its decisions; the other pages read the sidebar's nav count.
 */
interface Props {
  applications?: number | null
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ adminUsers: adminUsersMessages })
const navCounts = useResource(() => shellApi.navCounts(), props.applications === undefined)
const accounts = useResource(() => adminUsersApi.list({ page: 0, size: 1 }))

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminUsers.tabAllUsers'),
    to: { name: 'admin-users' },
    icon: 'users',
    count: accounts.data.value?.totalItems ?? null,
    testId: 'admin-users-tab-all',
  },
  {
    label: t('adminUsers.tabApplications'),
    to: { name: 'admin-teacher-applications' },
    icon: 'verification',
    count: (props.applications ?? navCounts.data.value?.teacherApplications) || null,
    attention: true,
    testId: 'admin-users-tab-applications',
  },
])
</script>
