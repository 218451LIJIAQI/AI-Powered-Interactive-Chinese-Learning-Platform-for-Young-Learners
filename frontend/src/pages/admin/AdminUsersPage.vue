<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('nav.users') }}</h1>

    <RouteTabs :tabs="tabs" :label="t('adminUsers.tabsLabel')">
      <button class="btn btn-secondary" type="button" data-testid="admin-users-invite" @click="openInvite">
        <IconGlyph name="mail" :size="16" />
        <span>{{ t('adminUsers.inviteAdmin') }}</span>
      </button>
    </RouteTabs>

    <div class="admin-users" :class="{ 'has-detail': showDetail }">
      <section class="panel admin-table-card">
        <h2 id="admin-users-list-title" class="sr-only">{{ t('nav.users') }}</h2>
        <form class="admin-filters" role="search" @submit.prevent>
          <div class="field is-search">
            <label class="field-label" for="admin-users-search">{{ t('common.search') }}</label>
            <span class="input-with-icon">
              <IconGlyph class="input-icon" name="search" :size="16" />
              <input
                id="admin-users-search"
                v-model="searchTerm"
                class="input"
                type="search"
                maxlength="200"
                autocomplete="off"
                :placeholder="t('adminUsers.searchPlaceholder')"
                data-testid="admin-users-search"
              />
            </span>
          </div>
          <div class="field">
            <label class="field-label" for="admin-users-role">{{ t('adminUsers.roleLabel') }}</label>
            <select id="admin-users-role" v-model="role" class="select" data-testid="admin-users-role">
              <option value="">{{ t('adminUsers.roleAll') }}</option>
              <option v-for="option in USER_ROLES" :key="option" :value="option">{{ t(`roles.${option}`) }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="admin-users-account">{{ t('adminUsers.accountLabel') }}</label>
            <select id="admin-users-account" v-model="account" class="select" data-testid="admin-users-account">
              <option value="">{{ t('adminUsers.accountAll') }}</option>
              <option value="ACTIVE">{{ t('adminUsers.accountActive') }}</option>
              <option value="DEACTIVATED">{{ t('adminUsers.accountDeactivated') }}</option>
            </select>
          </div>
        </form>

        <AdminPendingInvitesStrip
          ref="invitesStripRef"
          @toast="showToast"
          @hide-toast="hideToast"
          @accepted="onInviteAccepted"
        />

        <StatePanel
          v-if="users.loading.value"
          tone="loading"
          :title="t('adminUsers.loadingTitle')"
          :message="t('adminUsers.loadingHint')"
        />
        <StatePanel
          v-else-if="users.error.value"
          tone="error"
          :title="t('adminUsers.loadErrorTitle')"
          :message="users.error.value"
          @retry="retryUsers"
        />
        <template v-else-if="items.length">
          <div
            class="data-table-wrap admin-table-wrap"
            role="region"
            aria-labelledby="admin-users-list-title"
            tabindex="0"
          >
            <table class="data-table admin-table admin-users-table">
              <thead>
                <tr>
                  <th scope="col" class="admin-users-col-user">{{ t('adminUsers.colUser') }}</th>
                  <th scope="col">{{ t('adminUsers.roleLabel') }}</th>
                  <th scope="col">{{ t('adminUsers.accountLabel') }}</th>
                  <th scope="col" class="admin-users-col-joined">{{ t('adminUsers.joined') }}</th>
                  <th scope="col" class="col-actions admin-col-chevron">
                    <span class="sr-only">{{ t('adminUsers.colDetails') }}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="user in items"
                  :key="user.id"
                  :class="{ 'is-selected': user.id === selectedId }"
                  data-testid="admin-users-row"
                  @click="onUserRowClick(user.id)"
                >
                  <td>
                    <span class="admin-cell-person">
                      <PortraitAvatar :size="36" :name="user.fullName" />
                      <span class="admin-cell-title">
                        <strong>{{ user.fullName }}</strong>
                        <span>{{ user.email }}</span>
                      </span>
                    </span>
                  </td>
                  <td>
                    <span class="tag is-outline">{{ t(`roles.${user.role}`) }}</span>
                  </td>
                  <td>
                    <StatusPill
                      :label="user.active ? t('adminUsers.accountActive') : t('adminUsers.accountDeactivated')"
                      :tone="accountTone(user.active)"
                    />
                  </td>
                  <td class="admin-cell-date admin-users-col-joined">{{ formatShortDate(user.joinedAt) }}</td>
                  <td class="col-actions">
                    <button
                      class="btn btn-ghost btn-icon btn-sm"
                      type="button"
                      :aria-label="t('adminUsers.openUser', { name: user.fullName })"
                      :aria-current="user.id === selectedId ? 'true' : undefined"
                      @click.stop="selectUser(user.id)"
                    >
                      <IconGlyph name="chevronRight" :size="16" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <PaginationBar
            :page="displayedPage"
            :total-pages="totalPages"
            :label="t('adminUsers.pagination')"
            icon-only
            @update:page="goToPage"
          />
        </template>
        <StatePanel
          v-else
          tone="empty"
          icon="search"
          spacious
          :title="keyword ? t('adminUsers.emptyTitleSearch', { term: keyword }) : t('adminUsers.emptyTitle')"
          :message="searchOnly ? t('adminUsers.emptyHintSearch') : t('adminUsers.emptyHint')"
        >
          <template v-if="hasFilters" #actions>
            <button class="btn btn-secondary" type="button" data-testid="admin-users-clear" @click="clearFilters">
              {{ searchOnly ? t('common.clearSearch') : t('common.clearFilters') }}
            </button>
          </template>
        </StatePanel>
      </section>

      <AdminUserDetailPanel
        v-if="showDetail"
        ref="detailPanelRef"
        :selected-id="selectedId"
        :user="selectedUser"
        :loading="detail.loading.value"
        :error="detail.error.value"
        :saving="saving"
        :access-error="accessError"
        @retry="detail.reload"
        @deactivate="openDeactivate"
        @reactivate="reactivate"
      />
    </div>

    <DeactivateUserDialog
      v-if="deactivateOpen && selectedUser"
      :user="selectedUser"
      :busy="saving"
      :error="dialogError"
      @cancel="deactivateOpen = false"
      @clear-error="dialogError = null"
      @confirm="confirmDeactivate"
    />

    <AdminInviteDialog v-if="inviteOpen" @close="inviteOpen = false" @sent="onInviteSent" />

    <AppToast :message="toast" :action="toastAction" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'

import { adminUsersApi } from '@/api/ops'
import AdminInviteDialog from '@/components/admin/AdminInviteDialog.vue'
import AdminPendingInvitesStrip from '@/components/admin/AdminPendingInvitesStrip.vue'
import AdminUserDetailPanel from '@/components/admin/AdminUserDetailPanel.vue'
import DeactivateUserDialog from '@/components/admin/DeactivateUserDialog.vue'
import { accountTone } from '@/components/admin/adminDisplay'
import { useSelectedRecord } from '@/components/admin/useSelectedRecord'
import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageFilters, useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { USER_ROLES, type UserRole } from '@/types/api'
import type { AdminUserDetail } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import { firstQueryText, pageFromQuery, pageQuery, queryOption } from '@/utils/routeQuery'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 10
const ACCOUNT_OPTIONS = ['ACTIVE', 'DEACTIVATED'] as const
type AccountFilter = (typeof ACCOUNT_OPTIONS)[number]

const { t } = useLocalMessages({ adminUsers: adminUsersMessages })
const route = useRoute()
const router = useRouter()
const { toast, toastAction, showToast, hideToast } = useToast()

const detailPanelRef = ref<InstanceType<typeof AdminUserDetailPanel> | null>(null)
const invitesStripRef = ref<InstanceType<typeof AdminPendingInvitesStrip> | null>(null)

// The list, kept in the address (q, role, account and page) so that a reload or a link back keeps it.

const {
  searchTerm,
  keyword,
  clear: clearSearch,
  restore: restoreSearch,
} = useDebouncedSearch(firstQueryText(route.query.q))
const role = ref<UserRole | ''>(queryOption(route.query.role, USER_ROLES) ?? '')
const account = ref<AccountFilter | ''>(queryOption(route.query.account, ACCOUNT_OPTIONS) ?? '')
const page = ref(pageFromQuery(route.query.page))
const hasFilters = computed(() => Boolean(keyword.value || role.value || account.value))

// When the initial page is unfiltered, the main list response already carries the total account count.
const accounts = useResource(() => adminUsersApi.list({ page: 0, size: 1 }), hasFilters.value)
const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminUsers.tabAllUsers'),
    to: { name: 'admin-users' },
    icon: 'users',
    count: accounts.data.value?.totalItems,
    testId: 'admin-users-tab-all',
  },
])

function listQuery(): LocationQueryRaw {
  return {
    q: keyword.value || undefined,
    role: role.value || undefined,
    account: account.value || undefined,
    page: pageQuery(page.value),
  }
}

const users = useResource(() =>
  adminUsersApi.list({
    keyword: keyword.value || undefined,
    role: role.value || undefined,
    active: account.value ? account.value === 'ACTIVE' : undefined,
    page: page.value,
    size: PAGE_SIZE,
  }),
)

watch(users.data, (loaded) => {
  if (loaded && !hasFilters.value) accounts.replace(loaded)
})
watch(hasFilters, (filtered) => {
  if (filtered && accounts.data.value === null && !accounts.loading.value) void accounts.reload()
})

function retryUsers(): void {
  void users.reload()
  if (hasFilters.value && accounts.data.value === null && !accounts.loading.value) void accounts.reload()
}

const { items, displayedPage, totalPages, goToPage } = useServerPage(users, page, {
  stepBackFromEmpty: true,
  onPageChange: writeAddress,
})

function writeAddress(): void {
  void router.replace({ query: { ...route.query, ...listQuery() } })
}

const followAddress = usePageFilters([keyword, role, account], page, users, () => void select(null))

watch([() => route.query.q, () => route.query.role, () => route.query.account, () => route.query.page], () => {
  if (route.name !== 'admin-users') return
  followAddress(pageFromQuery(route.query.page), () => {
    restoreSearch(firstQueryText(route.query.q))
    role.value = queryOption(route.query.role, USER_ROLES) ?? ''
    account.value = queryOption(route.query.account, ACCOUNT_OPTIONS) ?? ''
  })
})

function clearFilters(): void {
  clearSearch()
  role.value = ''
  account.value = ''
}

/** Only the search box narrows the list: the empty state then offers "Clear search", not "Clear filters". */
const searchOnly = computed(() => Boolean(keyword.value) && !role.value && !account.value)

// The selected user (`?user=<id>`), shown in the side column.

const { selectedId, detail, select } = useSelectedRecord('user', adminUsersApi.get, listQuery, () =>
  users.data.value?.page === page.value ? items.value[0]?.id : undefined,
)
const selectedUser = detail.data
const showDetail = computed(() => selectedId.value !== null || items.value.length > 0 || users.loading.value)

function onUserRowClick(id: number): void {
  if (window.getSelection()?.toString()) return
  void selectUser(id)
}

async function selectUser(id: number): Promise<void> {
  await select(id)
  await nextTick()
  detailPanelRef.value?.focus()
}

// Deactivate (asks for a reason, A2.05) and reactivate (runs at once: it is easy to take back, X1.20).

const deactivateOpen = ref(false)
const dialogError = ref<string | null>(null)
const accessError = ref<string | null>(null)
const pendingAccess = reactive(new Set<number>())
const saving = computed(() => selectedId.value !== null && pendingAccess.has(selectedId.value))
const isUnmounted = useIsUnmounted()
let selectionVersion = 0

watch(selectedId, () => {
  selectionVersion++
  deactivateOpen.value = false
  accessError.value = null
})

function openInvite(): void {
  if (toastAction.value) hideToast()
  inviteOpen.value = true
}

function openDeactivate(): void {
  if (saving.value) return
  if (toastAction.value) hideToast()
  dialogError.value = null
  accessError.value = null
  deactivateOpen.value = true
}

async function changeAccess(
  targetId: number,
  change: () => Promise<AdminUserDetail>,
  done: string,
  failed: string,
  showError: (message: string, sameSelection: boolean) => void,
): Promise<boolean> {
  pendingAccess.add(targetId)
  const version = selectionVersion
  try {
    const updatedUser = await change()
    if (isUnmounted()) return false
    void users.reload()
    if (selectedId.value === updatedUser.id) {
      detail.replace(updatedUser)
      showToast(done)
    }
    return version === selectionVersion
  } catch (failure) {
    if (!isUnmounted() && selectedId.value === targetId) {
      showError(resolveApiError(failure, failed), version === selectionVersion)
    }
    return false
  } finally {
    pendingAccess.delete(targetId)
  }
}

async function confirmDeactivate(reason: string): Promise<void> {
  const user = selectedUser.value
  if (!user || pendingAccess.has(user.id)) return
  dialogError.value = null
  const done = await changeAccess(
    user.id,
    () => adminUsersApi.deactivate(user.id, reason),
    t('adminUsers.deactivated', { name: user.fullName }),
    t('adminUsers.deactivateFailed'),
    (message, sameSelection) => {
      if (sameSelection && deactivateOpen.value) dialogError.value = message
      else accessError.value = message
    },
  )
  if (done) {
    deactivateOpen.value = false
    await nextTick()
    if (!document.activeElement || document.activeElement === document.body) {
      detailPanelRef.value?.focus()
    }
  }
}

async function reactivate(): Promise<void> {
  const user = selectedUser.value
  if (!user || pendingAccess.has(user.id)) return
  accessError.value = null
  await changeAccess(
    user.id,
    () => adminUsersApi.reactivate(user.id),
    t('adminUsers.reactivated', { name: user.fullName }),
    t('adminUsers.reactivateFailed'),
    (message) => (accessError.value = message),
  )
}

// Admin invites

const inviteOpen = ref(false)

function onInviteSent(email: string): void {
  showToast(t('adminUsers.inviteSent', { email }))
  inviteOpen.value = false
  invitesStripRef.value?.reloadAfterInvite()
}

function onInviteAccepted(): void {
  void users.reload()
  if (hasFilters.value) void accounts.reload()
}
</script>
