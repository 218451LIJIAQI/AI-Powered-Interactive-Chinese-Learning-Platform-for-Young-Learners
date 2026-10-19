<template>
  <div class="page-stack admin-users-page">
    <PageHeaderBlock :title="t('adminUsers.title')" :description="t('adminUsers.description')" icon="users">
      <template #actions>
        <button class="btn btn-secondary" type="button" @click="openAnalytics">
          <IconGlyph name="chart" :size="16" />
          <span>{{ t('adminUsers.analytics') }}</span>
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="usingFallback"
      :error="actionError"
      :success="actionSuccess"
      :fallback-title="t('adminUsers.previewTitle')"
      :fallback-message="t('adminUsers.previewMessage')"
      :error-title="t('adminUsers.unavailableTitle')"
      :detail="directoryNoticeDetail"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-split">
        <article class="panel admin-panel">
          <div class="tabs" role="group" :aria-label="t('adminUsers.roleFilterAria')">
            <button
              v-for="tab in tabs"
              :key="tab.value"
              class="tab-button"
              :class="{ 'is-active': roleFilter === tab.value }"
              type="button"
              :aria-pressed="roleFilter === tab.value"
              :data-testid="`admin-users-role-${tab.value.toLowerCase()}`"
              @click="setRoleFilter(tab.value)"
            >
              {{ tab.label }}
            </button>
          </div>

          <div class="admin-toolbar">
            <div class="field admin-toolbar-search">
              <label for="admin-users-search">{{ t('adminUsers.searchLabel') }}</label>
              <div class="input-with-icon">
                <IconGlyph class="input-icon" name="search" :size="16" />
                <input
                  id="admin-users-search"
                  v-model="searchTerm"
                  class="input"
                  type="search"
                  maxlength="200"
                  data-testid="admin-users-search"
                  :aria-label="t('adminUsers.searchAria')"
                  :placeholder="t('adminUsers.search')"
                />
              </div>
            </div>
            <div class="field">
              <label for="admin-users-active-filter">{{ t('adminUsers.activeFilterLabel') }}</label>
              <select
                id="admin-users-active-filter"
                v-model="activeFilter"
                class="select"
                data-testid="admin-users-active-filter"
              >
                <option v-for="option in activeFilterOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div class="field">
              <label for="admin-users-teacher-status-filter">{{ t('adminUsers.teacherStatusLabel') }}</label>
              <select
                id="admin-users-teacher-status-filter"
                v-model="teacherStatusFilter"
                class="select"
                data-testid="admin-users-teacher-status-filter"
              >
                <option v-for="option in teacherStatusOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <button class="btn btn-ghost" type="button" :disabled="!hasActiveFilters" @click="clearFilters">
              {{ t('adminUsers.clear') }}
            </button>
          </div>

          <div class="admin-result-meta">
            <strong data-testid="admin-users-total">{{ t('adminUsers.totalUsers', { count: totalItems }) }}</strong>
            <span>{{ selectedUserMetaLabel }}</span>
          </div>

          <div
            v-if="pagedUsers.length"
            class="data-table-wrap admin-table-wrap"
            role="region"
            :aria-label="t('adminUsers.tableLabel')"
            tabindex="0"
          >
            <table class="data-table admin-table admin-users-table">
              <thead>
                <tr>
                  <th scope="col">{{ t('adminUsers.user') }}</th>
                  <th scope="col">{{ t('adminUsers.status') }}</th>
                  <th scope="col">{{ t('adminUsers.accountState') }}</th>
                  <th scope="col">{{ t('adminUsers.lastActive') }}</th>
                  <th scope="col">{{ t('adminUsers.joinedDate') }}</th>
                  <th scope="col" class="col-actions">
                    <span class="sr-only">{{ t('adminUsers.actions') }}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="user in pagedUsers"
                  :key="user.userId"
                  data-testid="admin-users-row"
                  :class="{ 'is-selected': selectedUser?.userId === user.userId }"
                  @click="selectUser(user.userId)"
                >
                  <td>
                    <div class="admin-table-cell">
                      <PortraitAvatar :size="40" rounded="soft" :name="user.fullName" :tone="user.avatarTone" />
                      <div class="admin-table-cell-copy">
                        <strong>{{ user.fullName }}</strong>
                        <span>{{ user.email }}</span>
                        <span class="admin-users-role-line">
                          <span class="tag">{{ user.roleLabel }}</span>
                          <small>{{ user.internalId }}</small>
                        </span>
                      </div>
                    </div>
                  </td>
                  <td :data-label="t('adminUsers.status')">
                    <StatusPill :label="user.statusLabel" :tone="user.statusTone" />
                  </td>
                  <td :data-label="t('adminUsers.accountState')">
                    <StatusPill :label="user.accountLabel" :tone="user.accountTone" />
                  </td>
                  <td class="admin-table-nowrap" :data-label="t('adminUsers.lastActive')">
                    {{ user.lastActiveLabel }}
                  </td>
                  <td class="admin-table-nowrap" :data-label="t('adminUsers.joinedDate')">{{ user.joinedLabel }}</td>
                  <td class="col-actions">
                    <button
                      class="btn btn-ghost btn-icon btn-sm"
                      type="button"
                      :aria-label="t('adminUsers.openDetailsAria', { name: user.fullName })"
                      :aria-pressed="selectedUser?.userId === user.userId"
                      @click.stop="selectUser(user.userId)"
                    >
                      <IconGlyph name="chevronRight" :size="16" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <StatePanel
            v-else
            compact
            icon="search"
            :eyebrow="t('adminUsers.noMatches')"
            :title="t('adminUsers.noUsers')"
            :message="hasActiveFilters ? t('adminUsers.adjustFilters') : t('adminUsers.recordsPending')"
          >
            <template v-if="hasActiveFilters" #actions>
              <button class="btn btn-secondary" type="button" @click="clearFilters">
                {{ t('adminUsers.clearFilters') }}
              </button>
            </template>
          </StatePanel>

          <PaginationBar
            :info="`${t('adminUsers.pageOf', { page: currentPage + 1, total: displayTotalPages })} · ${paginationLabel}`"
            :previous-label="t('adminUsers.previousPage')"
            :next-label="t('adminUsers.nextPage')"
            :previous-disabled="currentPage <= 0"
            :next-disabled="currentPage + 1 >= totalPages"
            icon-only
            test-id-prefix="admin-users"
            @previous="goToPreviousPage"
            @next="goToNextPage"
          >
            <template #start>
              <div class="admin-users-page-size">
                <label for="admin-users-page-size">{{ t('adminUsers.rowsPerPage') }}</label>
                <select
                  id="admin-users-page-size"
                  v-model.number="pageSize"
                  class="select"
                  data-testid="admin-users-page-size"
                >
                  <option v-for="size in ADMIN_USER_PAGE_SIZES" :key="size" :value="size">{{ size }}</option>
                </select>
              </div>
            </template>
          </PaginationBar>
        </article>

        <aside class="admin-stack" :aria-label="t('adminUsers.detailsLabel')">
          <template v-if="selectedUser">
            <article class="panel admin-panel">
              <div class="admin-profile">
                <PortraitAvatar
                  :size="56"
                  rounded="soft"
                  :name="selectedUser.fullName"
                  :tone="selectedUser.avatarTone"
                />
                <div class="admin-profile-copy">
                  <h2 class="admin-profile-name">{{ selectedUser.fullName }}</h2>
                  <span class="admin-profile-meta">{{ selectedUser.email }}</span>
                  <span class="admin-profile-meta">{{ selectedUser.internalId }}</span>
                  <div class="admin-users-profile-tags">
                    <span class="tag">{{ selectedUser.roleLabel }}</span>
                    <StatusPill :label="selectedUser.statusLabel" :tone="selectedUser.statusTone" />
                  </div>
                </div>
                <button
                  class="btn btn-ghost btn-icon btn-sm"
                  type="button"
                  :aria-label="t('adminUsers.closePanel')"
                  @click="clearSelection"
                >
                  <IconGlyph name="close" :size="18" />
                </button>
              </div>

              <hr class="divider" />

              <div class="card-header">
                <h3 class="card-title">{{ t('adminUsers.accountInfo') }}</h3>
                <button class="btn btn-link" type="button" @click="openProgressTarget">
                  {{ t('adminUsers.openRelevant') }}
                </button>
              </div>
              <dl class="kv-list">
                <dt>{{ t('adminUsers.joinedDate') }}</dt>
                <dd>{{ selectedUser.joinedLabel }}</dd>
                <dt>{{ t('adminUsers.lastActive') }}</dt>
                <dd>{{ selectedUser.lastActiveLabel }}</dd>
                <dt>{{ t('adminUsers.accountState') }}</dt>
                <dd>{{ selectedUser.accountLabel }}</dd>
                <dt>{{ t('adminUsers.profileStatus') }}</dt>
                <dd>{{ selectedUser.statusLabel }}</dd>
              </dl>

              <hr class="divider" />

              <h3 class="card-title">{{ t('adminUsers.accountActions') }}</h3>
              <div class="admin-users-actions">
                <button
                  class="btn"
                  :class="selectedUser.active ? 'btn-danger-soft' : 'btn-secondary'"
                  type="button"
                  data-testid="admin-users-toggle-status"
                  :disabled="userStatusActionDisabled"
                  :aria-busy="submittingUserStatus"
                  @click="toggleSelectedUserStatus"
                >
                  {{ selectedUserStatusActionLabel }}
                </button>
                <button class="btn btn-secondary" type="button" @click="openPrimaryAction">
                  {{ primaryActionLabel }}
                </button>
                <button class="btn btn-secondary" type="button" @click="openSecondaryAction">
                  {{ secondaryActionLabel }}
                </button>
                <button
                  v-if="selectedUser.roleKey === 'TEACHER'"
                  class="btn btn-secondary"
                  type="button"
                  @click="openTeacherVerification"
                >
                  {{ t('adminUsers.reviewTeacher') }}
                </button>
                <button class="btn btn-secondary" type="button" @click="openRecoveryAction">
                  {{ t('adminUsers.openSettingsAudit') }}
                </button>
              </div>
              <p class="field-hint">{{ t('adminUsers.actionsHint') }}</p>
            </article>

            <article class="panel admin-panel">
              <div class="card-header">
                <h2 class="card-title">{{ t('adminUsers.followupRoutes') }}</h2>
                <button class="btn btn-link" type="button" @click="openActivityTarget">
                  {{ t('adminUsers.openRoute') }}
                </button>
              </div>
              <ul class="list-divided admin-feed">
                <li v-for="item in selectedFollowUpRoutes" :key="item.title" class="admin-feed-item">
                  <span class="admin-icon-badge is-sm" :class="`admin-tone-${item.tone}`" aria-hidden="true">
                    <IconGlyph :name="item.icon" :size="16" />
                  </span>
                  <div class="admin-feed-copy">
                    <span class="admin-feed-title">{{ item.title }}</span>
                    <span class="admin-feed-text">{{ item.copy }}</span>
                  </div>
                  <button
                    class="btn btn-link"
                    type="button"
                    :aria-label="t('adminUsers.openRouteAria', { title: item.title })"
                    @click="openAdminRoute(item.path)"
                  >
                    {{ t('adminUsers.open') }}
                  </button>
                </li>
              </ul>
            </article>

            <article class="panel admin-panel">
              <div class="card-header">
                <h2 class="card-title">{{ t('adminUsers.auditLatest') }}</h2>
                <button class="btn btn-link" type="button" @click="openSettings">
                  {{ t('adminUsers.viewAll') }}
                </button>
              </div>
              <ul v-if="selectedAudit.length" class="list-divided admin-feed">
                <li v-for="item in selectedAudit" :key="item.id" class="admin-feed-item">
                  <div class="admin-feed-copy">
                    <span class="admin-feed-title">{{ item.title }}</span>
                    <span class="admin-feed-meta">{{ item.time }} · {{ item.meta }}</span>
                  </div>
                </li>
              </ul>
              <p v-else class="field-hint">{{ t('adminUsers.noAudit') }}</p>
            </article>
          </template>
          <StatePanel
            v-else
            compact
            icon="users"
            :eyebrow="t('adminUsers.selection')"
            :title="t('adminUsers.pickUser')"
            :message="pagedUsers.length ? t('adminUsers.chooseUserHint') : t('adminUsers.broadenFilters')"
          />
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { adminApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import type { AvatarTone } from '@/components/common/avatarTone'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import type { AdminTone } from '@/components/admin/adminTone'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { useResource } from '@/composables/useResource'
import { adminAuditLogs, adminUsers } from '@/demo/data'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import type { AdminUserListItemResponse, PageResponse } from '@/types/api'
import {
  ADMIN_USER_PAGE_SIZES,
  buildAdminUserQuery,
  clampAdminUserPage,
  filterDemoAdminUsers,
  type AdminUserActiveFilter,
  type AdminUserRoleFilter,
  type AdminUserTeacherStatusFilter,
} from '@/utils/adminUserDirectory'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatDateTime } from '@/utils/format'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { adminUsers: adminUsersMessages.en },
    ms: { adminUsers: adminUsersMessages.ms },
    'zh-CN': { adminUsers: adminUsersMessages['zh-CN'] },
  },
})
const { statusLabel: enumLabel } = useStatusLabels()

/** Row-level presentation keys (derived per row; never used to filter the partial list client-side). */
type StatusKey = 'ACTIVE' | 'PENDING' | 'INACTIVE'
type AccountKey = 'VERIFIED' | 'VERIFICATION' | 'SUSPENDED'
type PrimaryRole = Exclude<AdminUserRoleFilter, 'ALL'>
/** StatusPill tones used for the status and account-state columns. */
type PillTone = 'success' | 'warning' | 'danger'
type AdminUserRoute =
  | '/admin/analytics'
  | '/admin/content'
  | '/admin/moderation'
  | '/admin/settings'
  | '/admin/teacher-verification'

const SEARCH_DEBOUNCE_MS = 300
const roleAvatarTones: Record<PrimaryRole, AvatarTone> = {
  ADMIN: 'primary',
  TEACHER: 'info',
  STUDENT: 'success',
}
const roleIdPrefixes: Record<PrimaryRole, string> = {
  ADMIN: 'A',
  TEACHER: 'T',
  STUDENT: 'S',
}

/* Server-side directory state (FR-ADM-02): every change below triggers a new GET /admin/users request. */
const searchTerm = ref('')
const appliedKeyword = ref('')
const roleFilter = ref<AdminUserRoleFilter>('ALL')
const activeFilter = ref<AdminUserActiveFilter>('ALL')
const teacherStatusFilter = ref<AdminUserTeacherStatusFilter>('ALL')
/** 0-based page index, as the API expects. */
const currentPage = ref(0)
const pageSize = ref<number>(ADMIN_USER_PAGE_SIZES[0])

const userQuery = computed(() =>
  buildAdminUserQuery({
    keyword: appliedKeyword.value,
    role: roleFilter.value,
    active: activeFilter.value,
    teacherStatus: teacherStatusFilter.value,
    page: currentPage.value,
    size: pageSize.value,
  }),
)

const usersResource = useResource(
  () => adminApi.users(userQuery.value),
  () => filterDemoAdminUsers(adminUsers.items, userQuery.value),
)
const auditResource = useResource(() => adminApi.auditLogs(), adminAuditLogs)

const emptyUserPage: PageResponse<AdminUserListItemResponse> = {
  items: [],
  page: 0,
  size: 0,
  totalItems: 0,
  totalPages: 0,
}

const users = computed(() => usersResource.data.value ?? emptyUserPage)
const totalItems = computed(() => users.value.totalItems ?? users.value.items.length)
const totalPages = computed(() => users.value.totalPages ?? 0)
const displayTotalPages = computed(() => Math.max(1, totalPages.value))
const auditLogs = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? adminAuditLogs : []))

const pageLoading = computed(() => usersResource.loading.value || auditResource.loading.value)
const pageError = computed(() => usersResource.error.value || auditResource.error.value)
const usingFallback = computed(() => usersResource.usingFallback.value || auditResource.usingFallback.value)

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(searchTerm, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchTimer = null
    appliedKeyword.value = value.trim()
  }, SEARCH_DEBOUNCE_MS)
})
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

/* A filter change always restarts at the first page; a page change alone just loads that page. */
const filterKey = computed(() =>
  JSON.stringify([
    appliedKeyword.value,
    roleFilter.value,
    activeFilter.value,
    teacherStatusFilter.value,
    pageSize.value,
  ]),
)
watch(filterKey, () => {
  if (currentPage.value !== 0) {
    currentPage.value = 0
    return
  }
  void usersResource.reload()
})
watch(currentPage, () => {
  void usersResource.reload()
})
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const submittingUserStatus = ref(false)
const userStatusActionDisabled = computed(() => usingFallback.value || submittingUserStatus.value)
const directoryNoticeDetail = computed(() =>
  actionError.value ? t('adminUsers.errorContext') : usingFallback.value ? t('adminUsers.previewContext') : null,
)

const tabs = computed<Array<{ label: string; value: AdminUserRoleFilter }>>(() => [
  { label: t('adminUsers.allUsers'), value: 'ALL' },
  { label: t('adminUsers.students'), value: 'STUDENT' },
  { label: t('adminUsers.teachers'), value: 'TEACHER' },
  { label: t('adminUsers.admins'), value: 'ADMIN' },
])
const activeFilterOptions = computed<Array<{ label: string; value: AdminUserActiveFilter }>>(() => [
  { label: t('adminUsers.allAccess'), value: 'ALL' },
  { label: t('adminUsers.activeOnly'), value: 'ACTIVE' },
  { label: t('adminUsers.inactiveOnly'), value: 'INACTIVE' },
])
const teacherStatusOptions = computed<Array<{ label: string; value: AdminUserTeacherStatusFilter }>>(() => [
  { label: t('adminUsers.allTeacherStatuses'), value: 'ALL' },
  { label: t('adminUsers.teacherPending'), value: 'PENDING' },
  { label: t('adminUsers.teacherApproved'), value: 'APPROVED' },
  { label: t('adminUsers.teacherRejected'), value: 'REJECTED' },
])

type CombinedUser = {
  userId: number
  fullName: string
  email: string
  active: boolean
  roleLabel: string
  roleKey: PrimaryRole
  statusLabel: string
  statusKey: StatusKey
  statusTone: PillTone
  accountLabel: string
  accountKey: AccountKey
  accountTone: PillTone
  lastActiveLabel: string
  joinedLabel: string
  internalId: string
  avatarTone: AvatarTone
}

interface FollowUpRoute {
  title: string
  copy: string
  icon: string
  tone: AdminTone
  path: AdminUserRoute
}

/** The current server page, in server order (no client-side filtering, sorting or slicing). */
const pagedUsers = computed<CombinedUser[]>(() =>
  users.value.items.map((item) => {
    const primaryRole = getPrimaryRole(item.roles)
    const teacherStatus = item.teacherStatus ?? null
    const statusKey = getStatusKey(item.active, primaryRole, teacherStatus)
    const accountKey = getAccountKey(item.active, primaryRole, teacherStatus)

    return {
      userId: item.userId,
      fullName: item.fullName,
      email: item.email,
      active: item.active,
      roleLabel: roleLabel(primaryRole),
      roleKey: primaryRole,
      statusLabel: statusLabel(statusKey),
      statusKey,
      statusTone: getStatusTone(statusKey),
      accountLabel: accountLabel(accountKey),
      accountKey,
      accountTone: getAccountTone(accountKey),
      lastActiveLabel: formatDate(item.updatedAt),
      joinedLabel: formatDate(item.createdAt),
      internalId: `${roleIdPrefixes[primaryRole]}-${String(item.userId).padStart(6, '0')}`,
      avatarTone: roleAvatarTones[primaryRole],
    }
  }),
)

const hasActiveFilters = computed(
  () =>
    Boolean(searchTerm.value.trim()) ||
    roleFilter.value !== 'ALL' ||
    activeFilter.value !== 'ALL' ||
    teacherStatusFilter.value !== 'ALL',
)

const selectedUserId = ref<number | null>(null)
const selectedUser = computed(() => {
  if (selectedUserId.value === null) return null
  return pagedUsers.value.find((item) => item.userId === selectedUserId.value) ?? null
})

watch(
  pagedUsers,
  (items, previousItems) => {
    if (!items.length) {
      selectedUserId.value = null
      return
    }

    if (selectedUserId.value === null) {
      if (!(previousItems?.length ?? 0)) {
        selectedUserId.value = items[0]?.userId ?? null
      }
      return
    }

    if (!items.some((item) => item.userId === selectedUserId.value)) {
      selectedUserId.value = items[0]?.userId ?? null
    }
  },
  { immediate: true, deep: true },
)

const selectedUserStatusActionLabel = computed(() => {
  if (!selectedUser.value) return t('adminUsers.updateAccount')
  if (submittingUserStatus.value) {
    return selectedUser.value.active ? t('adminUsers.suspending') : t('adminUsers.reactivating')
  }
  return selectedUser.value.active ? t('adminUsers.suspend') : t('adminUsers.reactivate')
})
const selectedUserMetaLabel = computed(() => {
  if (selectedUser.value) return t('adminUsers.focusedOn', { name: selectedUser.value.fullName })
  if (!pagedUsers.value.length) {
    return hasActiveFilters.value ? t('adminUsers.noFilterMatch') : t('adminUsers.recordsPending')
  }
  return t('adminUsers.chooseContext')
})
const paginationLabel = computed(() => {
  const size = users.value.size || pageSize.value
  const start = pagedUsers.value.length ? currentPage.value * size + 1 : 0
  const end = pagedUsers.value.length ? start + pagedUsers.value.length - 1 : 0
  return t('adminUsers.pagination', { start, end, total: totalItems.value })
})
const selectedFollowUpRoutes = computed<FollowUpRoute[]>(() => {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    return [
      {
        title: t('adminUsers.teacherVerification'),
        copy: t('adminUsers.teacherVerificationHint'),
        icon: 'status',
        tone: 'success',
        path: '/admin/teacher-verification',
      },
      {
        title: t('adminUsers.contentReview'),
        copy: t('adminUsers.contentReviewHint'),
        icon: 'content',
        tone: 'info',
        path: '/admin/content',
      },
      {
        title: t('adminUsers.auditTrail'),
        copy: t('adminUsers.auditTrailHint'),
        icon: 'history',
        tone: 'primary',
        path: '/admin/settings',
      },
    ]
  }

  if (selectedUser.value?.roleKey === 'ADMIN') {
    return [
      {
        title: t('adminUsers.auditLogs'),
        copy: t('adminUsers.auditLogsHint'),
        icon: 'status',
        tone: 'success',
        path: '/admin/settings',
      },
      {
        title: t('adminUsers.platformAnalytics'),
        copy: t('adminUsers.platformAnalyticsHint'),
        icon: 'analytics',
        tone: 'info',
        path: '/admin/analytics',
      },
      {
        title: t('adminUsers.moderation'),
        copy: t('adminUsers.moderationHint'),
        icon: 'moderation',
        tone: 'primary',
        path: '/admin/moderation',
      },
    ]
  }

  return [
    {
      title: t('adminUsers.learnerAnalytics'),
      copy: t('adminUsers.learnerAnalyticsHint'),
      icon: 'analytics',
      tone: 'success',
      path: '/admin/analytics',
    },
    {
      title: t('adminUsers.auditLogs'),
      copy: t('adminUsers.accountAuditHint'),
      icon: 'history',
      tone: 'info',
      path: '/admin/settings',
    },
    {
      title: t('adminUsers.supportSettings'),
      copy: t('adminUsers.supportSettingsHint'),
      icon: 'settings',
      tone: 'primary',
      path: '/admin/settings',
    },
  ]
})

const primaryActionLabel = computed(() => {
  if (selectedUser.value?.roleKey === 'TEACHER') return t('adminUsers.openTeacherReview')
  if (selectedUser.value?.roleKey === 'STUDENT') return t('adminUsers.openAnalytics')
  return t('adminUsers.openAuditLogs')
})

const secondaryActionLabel = computed(() => {
  if (selectedUser.value?.roleKey === 'TEACHER') return t('adminUsers.openContentReview')
  if (selectedUser.value?.roleKey === 'STUDENT') return t('adminUsers.openSupport')
  return t('adminUsers.openPlatformAnalytics')
})

const selectedAudit = computed(() =>
  auditLogs.value.slice(0, 5).map((item) => ({
    id: item.id,
    title: auditActionLabel(item.actionType),
    time: formatDateTime(item.createdAt),
    meta: item.actorEmail || t('adminUsers.system'),
  })),
)

function clearFilters(): void {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  searchTerm.value = ''
  appliedKeyword.value = ''
  roleFilter.value = 'ALL'
  activeFilter.value = 'ALL'
  teacherStatusFilter.value = 'ALL'
  currentPage.value = 0
}

function setRoleFilter(role: AdminUserRoleFilter): void {
  roleFilter.value = role
}

function goToPreviousPage(): void {
  currentPage.value = Math.max(0, currentPage.value - 1)
}

function goToNextPage(): void {
  if (currentPage.value + 1 < totalPages.value) currentPage.value += 1
}

function selectUser(userId: number): void {
  selectedUserId.value = userId
}

function openAdminRoute(path: AdminUserRoute): void {
  void router.push(path)
}

function openAnalytics(): void {
  openAdminRoute('/admin/analytics')
}

function openSettings(): void {
  openAdminRoute('/admin/settings')
}

function openProgressTarget(): void {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    openAdminRoute('/admin/teacher-verification')
    return
  }

  openAdminRoute('/admin/analytics')
}

function openActivityTarget(): void {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    openAdminRoute('/admin/teacher-verification')
    return
  }

  openSettings()
}

/* Targets mirror primaryActionLabel / secondaryActionLabel for each role. */
function openPrimaryAction(): void {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    openAdminRoute('/admin/teacher-verification')
    return
  }
  if (selectedUser.value?.roleKey === 'STUDENT') {
    openAnalytics()
    return
  }

  openSettings()
}

function openSecondaryAction(): void {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    openAdminRoute('/admin/content')
    return
  }
  if (selectedUser.value?.roleKey === 'ADMIN') {
    openAnalytics()
    return
  }

  openSettings()
}

function openTeacherVerification(): void {
  openAdminRoute('/admin/teacher-verification')
}

function openRecoveryAction(): void {
  openSettings()
}

function clearSelection(): void {
  selectedUserId.value = null
}

async function toggleSelectedUserStatus(): Promise<void> {
  if (submittingUserStatus.value) return
  if (!selectedUser.value) return

  if (usingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('adminUsers.previewMutation')
    return
  }

  const nextActive = !selectedUser.value.active
  // Suspending signs the user out of every session, so it needs an explicit confirmation.
  if (!nextActive && !window.confirm(t('adminUsers.confirmSuspend', { name: selectedUser.value.fullName }))) return

  actionError.value = null
  actionSuccess.value = null
  submittingUserStatus.value = true

  try {
    await adminApi.updateUserStatus(selectedUser.value.userId, { active: nextActive })
    // Refresh the current server page; if the user dropped out of the active filter and the page
    // is now past the end, step back to the last page that still has results.
    await usersResource.reload()
    const lastPage = clampAdminUserPage(currentPage.value, totalPages.value)
    if (!pagedUsers.value.length && lastPage !== currentPage.value) currentPage.value = lastPage
    actionSuccess.value = nextActive ? t('adminUsers.reactivated') : t('adminUsers.suspended')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminUsers.updateError'))
  } finally {
    submittingUserStatus.value = false
  }
}

function reloadAll(): void {
  void usersResource.reload()
  void auditResource.reload()
}

function getPrimaryRole(roles: readonly string[]): PrimaryRole {
  if (roles.includes('ADMIN')) return 'ADMIN'
  if (roles.includes('TEACHER')) return 'TEACHER'
  return 'STUDENT'
}

function roleLabel(role: PrimaryRole): string {
  const keys: Record<PrimaryRole, string> = {
    ADMIN: 'admin',
    TEACHER: 'teacher',
    STUDENT: 'student',
  }
  return t(`adminUsers.${keys[role]}`)
}

function getStatusKey(active: boolean, role: PrimaryRole, teacherStatus: string | null): StatusKey {
  if (!active) return 'INACTIVE'
  if (role !== 'TEACHER') return 'ACTIVE'
  if (teacherStatus === 'PENDING') return 'PENDING'
  return teacherStatus === 'REJECTED' ? 'INACTIVE' : 'ACTIVE'
}

function statusLabel(status: StatusKey): string {
  const keys: Record<StatusKey, string> = {
    ACTIVE: 'statusActive',
    PENDING: 'statusPending',
    INACTIVE: 'statusInactive',
  }
  return t(`adminUsers.${keys[status]}`)
}

function getStatusTone(status: StatusKey): PillTone {
  if (status === 'ACTIVE') return 'success'
  return status === 'PENDING' ? 'warning' : 'danger'
}

function getAccountKey(active: boolean, role: PrimaryRole, teacherStatus: string | null): AccountKey {
  if (!active) return 'SUSPENDED'
  return role === 'TEACHER' && teacherStatus !== 'APPROVED' ? 'VERIFICATION' : 'VERIFIED'
}

function accountLabel(account: AccountKey): string {
  const keys: Record<AccountKey, string> = {
    VERIFIED: 'accountVerified',
    VERIFICATION: 'accountVerification',
    SUSPENDED: 'accountSuspended',
  }
  return t(`adminUsers.${keys[account]}`)
}

function getAccountTone(account: AccountKey): PillTone {
  if (account === 'VERIFIED') return 'success'
  return account === 'SUSPENDED' ? 'danger' : 'warning'
}

function auditActionLabel(action: string): string {
  return enumLabel('auditAction', action)
}
</script>

<style scoped>
.admin-users-table td:first-child {
  min-width: 240px;
}

.admin-users-role-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  margin-top: var(--space-0-5);
}

.admin-users-page-size {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.admin-users-page-size .select {
  width: auto;
}

.admin-users-profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-1);
}

.admin-users-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr));
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .admin-users-table td:first-child {
    min-width: 0;
  }
}
</style>
