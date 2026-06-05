<template>
  <div class="page-stack admin-users-page">
    <PageHeaderBlock
      title="Users"
      description="Review the user directory, role context, and manage live account access."
      icon="users"
      eyebrow="Operations"
    />

    <DataNotice
      :using-fallback="usingFallback"
      :error="actionError || pageError"
      :success="actionSuccess"
      fallback-title="Preview operations mode"
      fallback-message="User records, role context, and audit shortcuts are showing safe preview data for this preview session."
      error-title="User directory unavailable"
      :detail="directoryNoticeDetail"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="admin-users-layout">
        <article class="panel admin-users-table-card">
          <div class="admin-users-tabs">
            <button
              v-for="tab in tabs"
              :key="tab.value"
              class="admin-users-tab"
              :class="{ 'is-active': activeTab === tab.value }"
              type="button"
              @click="activeTab = tab.value"
            >
              {{ tab.label }}
            </button>
          </div>

          <div class="admin-users-toolbar">
            <div class="admin-users-toolbar-top">
              <label class="admin-users-search">
                <IconGlyph name="search" :size="18" />
                <input
                  v-model="searchTerm"
                  class="admin-users-search-input"
                  placeholder="Search users by name, email, or ID..."
                />
              </label>

              <button
                class="teacher-outline-button admin-users-compact-button is-icon"
                type="button"
                @click="cycleSortMode"
              >
                <IconGlyph name="filter" :size="16" />
                <span>{{ sortModeLabel }}</span>
              </button>

              <button
                class="teacher-outline-button admin-users-compact-button is-icon"
                type="button"
                @click="openAnalytics"
              >
                <IconGlyph name="chart" :size="16" />
                <span>Analytics</span>
              </button>
            </div>

            <div class="admin-users-toolbar-bottom">
              <div class="admin-users-toolbar-filters">
                <button
                  class="teacher-outline-button admin-users-compact-button"
                  type="button"
                  @click="cycleRoleFilter"
                >
                  {{ roleFilterLabel }}
                </button>
                <button
                  class="teacher-outline-button admin-users-compact-button"
                  type="button"
                  @click="cycleStatusFilter"
                >
                  {{ statusFilterLabel }}
                </button>
                <button
                  class="teacher-outline-button admin-users-compact-button"
                  type="button"
                  @click="cycleAccountFilter"
                >
                  {{ accountFilterLabel }}
                </button>
                <button
                  class="teacher-outline-button admin-users-compact-button"
                  type="button"
                  @click="cycleClassFilter"
                >
                  {{ classFilterLabel }}
                </button>
                <button
                  class="teacher-outline-button admin-users-compact-button is-icon"
                  type="button"
                  @click="cycleExtraFilter"
                >
                  <IconGlyph name="filter" :size="16" />
                  <span>{{ extraFilterLabel }}</span>
                </button>
              </div>

              <button class="student-link-button admin-users-clear" type="button" @click="clearFilters">Clear</button>
            </div>
          </div>

          <div class="admin-users-table-meta">
            <span>Total {{ filteredUsers.length }} users</span>
            <span>{{ selectedUserMetaLabel }}</span>
          </div>

          <div class="admin-users-table-shell">
            <template v-if="pagedUsers.length">
              <div class="admin-users-table-header">
                <span />
                <span>User</span>
                <span>Role</span>
                <span>Status</span>
                <span>Account State</span>
                <span>Last Active</span>
                <span>Joined Date</span>
                <span>Actions</span>
              </div>

              <article
                v-for="user in pagedUsers"
                :key="user.userId"
                class="admin-users-table-row"
                :class="{ 'is-active': selectedUser?.userId === user.userId }"
                tabindex="0"
                role="button"
                :aria-label="`Open account details for ${user.fullName}`"
                @click="selectUser(user.userId)"
                @keydown.enter.prevent="selectUser(user.userId)"
                @keydown.space.prevent="selectUser(user.userId)"
              >
                <label class="admin-users-check">
                  <input type="checkbox" :checked="selectedUser?.userId === user.userId" />
                  <span />
                </label>

                <div class="admin-users-user-cell">
                  <PortraitAvatar :size="52" rounded="soft" :kind="user.avatarKind" />
                  <div class="admin-users-user-copy">
                    <strong>{{ user.fullName }}</strong>
                    <span>{{ user.email }}</span>
                    <small>{{ user.internalId }}</small>
                  </div>
                </div>

                <span class="admin-users-role-pill" :class="`is-${user.roleTone}`">{{ user.roleLabel }}</span>

                <span class="admin-users-status-pill" :class="`is-${user.statusTone}`">
                  <i />
                  {{ user.statusLabel }}
                </span>

                <span class="admin-users-account-pill" :class="`is-${user.accountTone}`">
                  <i />
                  {{ user.accountLabel }}
                </span>

                <span>{{ user.lastActiveLabel }}</span>
                <span>{{ user.joinedLabel }}</span>

                <button
                  class="admin-users-more"
                  type="button"
                  aria-label="Open user details"
                  @click.stop="selectUser(user.userId)"
                >
                  <IconGlyph name="more" :size="18" />
                </button>
              </article>
            </template>
            <StatePanel
              v-else
              class="admin-users-empty-state"
              icon="search"
              eyebrow="No matches"
              title="No users match this view"
              :message="
                hasActiveFilters
                  ? 'Clear or adjust the current filters to bring more users back into view.'
                  : 'User records will appear here once the directory returns data.'
              "
            >
              <template v-if="hasActiveFilters" #actions>
                <button class="btn btn-secondary" type="button" @click="clearFilters">Clear filters</button>
              </template>
            </StatePanel>
          </div>

          <div class="admin-users-pagination">
            <div class="row admin-users-pagination-left">
              <span>Rows per page:</span>
              <span class="teacher-outline-button admin-users-page-size" aria-hidden="true">10</span>
            </div>

            <span>{{ paginationLabel }}</span>

            <div class="row admin-users-pagination-right">
              <button
                class="admin-users-page-button"
                type="button"
                aria-label="Previous users page"
                :disabled="currentPage === 1"
                @click="currentPage = Math.max(1, currentPage - 1)"
              >
                <IconGlyph name="chevronLeft" :size="16" />
              </button>

              <button
                v-for="page in totalPages"
                :key="page"
                class="admin-users-page-button"
                :class="{ 'is-active': currentPage === page }"
                type="button"
                @click="currentPage = page"
              >
                {{ page }}
              </button>

              <button
                class="admin-users-page-button"
                type="button"
                aria-label="Next users page"
                :disabled="currentPage === totalPages"
                @click="currentPage = Math.min(totalPages, currentPage + 1)"
              >
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>
          </div>
        </article>

        <aside class="admin-users-sidebar">
          <template v-if="selectedUser">
            <article class="panel admin-users-profile-card">
              <div class="admin-users-profile-head">
                <div class="admin-users-profile-main">
                  <PortraitAvatar :size="82" rounded="soft" :kind="selectedUser.avatarKind" />

                  <div class="stack admin-users-profile-copy">
                    <div class="row admin-users-profile-title">
                      <strong>{{ selectedUser.fullName }}</strong>
                      <span class="admin-users-status-pill" :class="`is-${selectedUser.statusTone}`">
                        <i />
                        {{ selectedUser.statusLabel }}
                      </span>
                    </div>

                    <div class="row admin-users-profile-meta">
                      <span>{{ selectedUser.email }}</span>
                      <small>{{ selectedUser.internalId }}</small>
                    </div>

                    <span class="admin-users-role-pill" :class="`is-${selectedUser.roleTone}`">
                      {{ selectedUser.roleLabel }}
                    </span>
                  </div>
                </div>

                <button class="admin-users-close" type="button" aria-label="Close user panel" @click="clearSelection">
                  <IconGlyph name="close" :size="20" />
                </button>
              </div>
            </article>

            <article class="panel admin-users-info-card">
              <div class="student-section-heading">
                <strong>Account Info</strong>
              </div>

              <div class="admin-users-info-grid">
                <div>
                  <span>Joined Date</span><strong>{{ selectedUser.joinedLabel }}</strong>
                </div>
                <div>
                  <span>Last Active</span><strong>{{ selectedUser.lastActiveLabel }}</strong>
                </div>
                <div>
                  <span>Account State</span><strong>{{ selectedUser.accountLabel }}</strong>
                </div>
                <div>
                  <span>Profile Status</span><strong>{{ selectedUser.statusLabel }}</strong>
                </div>
              </div>
            </article>

            <article class="panel admin-users-progress-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Directory Context</strong>
                </div>
                <button class="student-link-button" type="button" @click="openProgressTarget">
                  Open Relevant Page
                </button>
              </div>

              <div class="admin-users-progress-wrap admin-users-progress-wrap--context">
                <div class="admin-users-progress-copy">
                  <div>
                    <span>Role</span><strong>{{ selectedUser.roleLabel }}</strong>
                  </div>
                  <div>
                    <span>Account State</span><strong>{{ selectedUser.accountLabel }}</strong>
                  </div>
                  <div>
                    <span>Profile Status</span><strong>{{ selectedUser.statusLabel }}</strong>
                  </div>
                </div>
              </div>
            </article>

            <article class="panel admin-users-activity-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Follow-up Routes</strong>
                </div>
                <button class="student-link-button" type="button" @click="openActivityTarget">Open Route</button>
              </div>

              <div class="admin-users-activity-list">
                <article v-for="item in selectedFollowUpRoutes" :key="item.title" class="admin-users-activity-item">
                  <span class="admin-users-activity-icon" :class="`is-${item.tone}`"
                    ><IconGlyph :name="item.icon" :size="18"
                  /></span>
                  <div class="admin-users-activity-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.copy }}</span>
                  </div>
                  <button class="student-link-button" type="button" @click="router.push(item.path)">Open</button>
                </article>
              </div>
            </article>

            <article class="panel admin-users-audit-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <strong>Audit Log (Latest 5)</strong>
                </div>
                <button class="student-link-button" type="button" @click="router.push('/admin/settings')">
                  View All
                </button>
              </div>

              <div class="admin-users-audit-list">
                <article v-for="item in selectedAudit" :key="item.id" class="admin-users-audit-item">
                  <div class="admin-users-audit-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.time }}</span>
                  </div>
                  <small>{{ item.meta }}</small>
                </article>
              </div>
            </article>

            <article class="panel admin-users-actions-card">
              <div class="student-section-heading">
                <strong>Account Actions</strong>
              </div>

              <div class="admin-users-actions-grid">
                <button
                  class="teacher-outline-button"
                  type="button"
                  :disabled="userStatusActionDisabled"
                  @click="toggleSelectedUserStatus"
                >
                  {{
                    submittingUserStatus
                      ? selectedUser.active
                        ? 'Suspending...'
                        : 'Reactivating...'
                      : selectedUser.active
                        ? 'Suspend account'
                        : 'Reactivate account'
                  }}
                </button>
                <button class="teacher-outline-button" type="button" @click="openPrimaryAction">
                  {{ primaryActionLabel }}
                </button>
                <button class="teacher-outline-button" type="button" @click="openSecondaryAction">
                  {{ secondaryActionLabel }}
                </button>
                <button
                  v-if="selectedUser.roleKey === 'TEACHER'"
                  class="admin-users-action-safe"
                  type="button"
                  @click="openTeacherVerification"
                >
                  Review Teacher Verification
                </button>
                <button class="admin-users-action-safe" type="button" @click="openRecoveryAction">
                  Open Settings & Audit
                </button>
              </div>

              <small class="admin-users-actions-note">
                Suspend or restore live access here, then use verification, content, analytics, and audit routes for
                deeper follow-up.
              </small>
            </article>
          </template>
          <StatePanel
            v-else
            class="admin-users-empty-sidebar"
            icon="users"
            eyebrow="Selection"
            title="Pick a user to inspect details"
            :message="
              filteredUsers.length
                ? 'Choose a user from the directory to review account context, progress, and audit history.'
                : 'Broaden the current filters or refresh the directory to restore the user list.'
            "
          />
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Admin users index — search, filter, manage all users across roles.
 *
 * <h2>Destructive-action safety</h2>
 * <p>Live account access changes are blocked while preview data is
 * active, then refresh the user, teacher, and student directories
 * after the backend accepts the update.</p>
 *
 * <h2>Audit trail</h2>
 * <p>Every write here is logged via
 * {@code AdminAuditLogService} so a future investigation can trace
 * who deactivated which account when.</p>
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { adminApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminAuditLogs, adminStudents, adminTeachers, adminUsers } from '@/demo/data'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatDateTime } from '@/utils/format'

const router = useRouter()

type TabKey = 'ALL' | 'STUDENTS' | 'TEACHERS' | 'ADMINS'
type RoleFilter = 'ALL' | 'STUDENT' | 'TEACHER' | 'ADMIN'
type StatusFilter = 'ALL' | 'ACTIVE' | 'PENDING' | 'INACTIVE'
type AccountFilter = 'ALL' | 'VERIFIED' | 'VERIFICATION'
type ClassFilter = 'ALL' | 'ENROLLED' | 'PUBLISHED' | 'ADMIN'
type SortMode = 'joined' | 'name'
type ExtraFilter = 'filters' | 'recent' | 'verified'

const usersResource = useResource(() => adminApi.users(), adminUsers)
const teachersResource = useResource(() => adminApi.teachers(), adminTeachers)
const studentsResource = useResource(() => adminApi.students(), adminStudents)
const auditResource = useResource(() => adminApi.auditLogs(), adminAuditLogs)

const emptyUserPage = {
  items: [],
  page: 0,
  size: 0,
  totalItems: 0,
  totalPages: 0,
}

const users = computed(
  () => usersResource.data.value ?? (usersResource.usingFallback.value ? adminUsers : emptyUserPage),
)
const teachers = computed(
  () => teachersResource.data.value ?? (teachersResource.usingFallback.value ? adminTeachers : emptyUserPage),
)
const students = computed(
  () => studentsResource.data.value ?? (studentsResource.usingFallback.value ? adminStudents : emptyUserPage),
)
const auditLogs = computed(() => auditResource.data.value ?? (auditResource.usingFallback.value ? adminAuditLogs : []))

const pageLoading = computed(
  () =>
    usersResource.loading.value ||
    teachersResource.loading.value ||
    studentsResource.loading.value ||
    auditResource.loading.value,
)
const pageError = computed(
  () =>
    usersResource.error.value ||
    teachersResource.error.value ||
    studentsResource.error.value ||
    auditResource.error.value,
)
const usingFallback = computed(
  () =>
    usersResource.usingFallback.value ||
    teachersResource.usingFallback.value ||
    studentsResource.usingFallback.value ||
    auditResource.usingFallback.value,
)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const submittingUserStatus = ref(false)
const userStatusActionDisabled = computed(() => usingFallback.value || submittingUserStatus.value)
const directoryNoticeDetail = computed(() =>
  pageError.value
    ? 'Filters and selected-user context remain visible so you can still review the current data snapshot.'
    : usingFallback.value
      ? 'Preview data keeps the user directory, role breakdown, and recent activity flows testable.'
      : null,
)

const tabs: Array<{ label: string; value: TabKey }> = [
  { label: 'All Users', value: 'ALL' },
  { label: 'Students', value: 'STUDENTS' },
  { label: 'Teachers', value: 'TEACHERS' },
  { label: 'Admins', value: 'ADMINS' },
]

const searchTerm = ref('')
const activeTab = ref<TabKey>('ALL')
const roleFilter = ref<RoleFilter>('ALL')
const statusFilter = ref<StatusFilter>('ALL')
const accountFilter = ref<AccountFilter>('ALL')
const classFilter = ref<ClassFilter>('ALL')
const sortMode = ref<SortMode>('joined')
const extraFilter = ref<ExtraFilter>('filters')
const currentPage = ref(1)
const pageSize = 10

type CombinedUser = {
  userId: number
  fullName: string
  email: string
  active: boolean
  roleLabel: string
  roleKey: RoleFilter
  roleTone: 'green' | 'blue' | 'purple'
  statusLabel: string
  statusTone: 'active' | 'pending' | 'inactive'
  accountLabel: string
  accountTone: 'verified' | 'warning'
  lastActiveLabel: string
  joinedLabel: string
  internalId: string
  avatarKind: 'student' | 'female' | 'male' | 'hijab'
  classKey: ClassFilter
  displayOrder: number
}

const combinedUsers = computed<CombinedUser[]>(() => {
  const teacherByUserId = new Map(teachers.value.items.map((item) => [item.userId, item]))
  const studentUserIds = new Set(students.value.items.map((item) => item.userId))

  return users.value.items.map((item) => {
    const primaryRole = item.roles.includes('ADMIN') ? 'ADMIN' : item.roles.includes('TEACHER') ? 'TEACHER' : 'STUDENT'
    const teacher = teacherByUserId.get(item.userId)
    const teacherStatus = teacher?.teacherStatus ?? item.teacherStatus ?? null
    const isStudent = studentUserIds.has(item.userId)

    const roleLabel = primaryRole === 'ADMIN' ? 'Admin' : primaryRole === 'TEACHER' ? 'Teacher' : 'Student'
    const roleTone = primaryRole === 'ADMIN' ? 'purple' : primaryRole === 'TEACHER' ? 'blue' : 'green'
    const statusLabel = !item.active
      ? 'Inactive'
      : primaryRole === 'TEACHER'
        ? teacherStatus === 'PENDING'
          ? 'Pending'
          : teacherStatus === 'REJECTED'
            ? 'Inactive'
            : 'Active'
        : 'Active'
    const statusTone = statusLabel === 'Active' ? 'active' : statusLabel === 'Pending' ? 'pending' : 'inactive'
    const accountLabel = !item.active
      ? 'Suspended'
      : primaryRole === 'TEACHER' && teacherStatus !== 'APPROVED'
        ? 'Verification'
        : 'Verified'
    const accountTone = accountLabel === 'Verified' ? 'verified' : 'warning'

    return {
      userId: item.userId,
      fullName: item.fullName,
      email: item.email,
      active: item.active,
      roleLabel,
      roleKey: primaryRole,
      roleTone,
      statusLabel,
      statusTone,
      accountLabel,
      accountTone,
      lastActiveLabel: formatDate(item.updatedAt),
      joinedLabel: formatDate(item.createdAt),
      internalId: `${primaryRole === 'STUDENT' ? 'S' : primaryRole === 'TEACHER' ? 'T' : 'A'}-${String(item.userId).padStart(6, '0')}`,
      avatarKind:
        primaryRole === 'ADMIN'
          ? 'female'
          : primaryRole === 'TEACHER'
            ? item.fullName.toLowerCase().includes('siti')
              ? 'hijab'
              : item.fullName.toLowerCase().includes('li')
                ? 'female'
                : 'male'
            : 'student',
      classKey: primaryRole === 'ADMIN' ? 'ADMIN' : isStudent ? 'ENROLLED' : 'PUBLISHED',
      displayOrder: -new Date(item.createdAt).getTime(),
    }
  })
})

const filteredUsers = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()

  const result = combinedUsers.value.filter((item) => {
    if (activeTab.value === 'STUDENTS' && item.roleKey !== 'STUDENT') return false
    if (activeTab.value === 'TEACHERS' && item.roleKey !== 'TEACHER') return false
    if (activeTab.value === 'ADMINS' && item.roleKey !== 'ADMIN') return false

    if (roleFilter.value !== 'ALL' && item.roleKey !== roleFilter.value) return false
    if (statusFilter.value !== 'ALL' && item.statusLabel.toUpperCase() !== statusFilter.value) return false
    if (accountFilter.value !== 'ALL' && item.accountLabel.toUpperCase() !== accountFilter.value) return false
    if (classFilter.value !== 'ALL' && item.classKey !== classFilter.value) return false

    if (extraFilter.value === 'recent' && !item.lastActiveLabel.startsWith('Today')) return false
    if (extraFilter.value === 'verified' && item.accountLabel !== 'Verified') return false

    if (!keyword) return true
    return (
      item.fullName.toLowerCase().includes(keyword) ||
      item.email.toLowerCase().includes(keyword) ||
      item.internalId.toLowerCase().includes(keyword)
    )
  })

  result.sort((a, b) => {
    if (sortMode.value === 'name') return a.fullName.localeCompare(b.fullName)
    return a.displayOrder - b.displayOrder
  })

  return result
})

const hasActiveFilters = computed(
  () =>
    Boolean(searchTerm.value.trim()) ||
    activeTab.value !== 'ALL' ||
    roleFilter.value !== 'ALL' ||
    statusFilter.value !== 'ALL' ||
    accountFilter.value !== 'ALL' ||
    classFilter.value !== 'ALL' ||
    sortMode.value !== 'joined' ||
    extraFilter.value !== 'filters',
)
const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize)))
const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredUsers.value.slice(start, start + pageSize)
})

const selectedUserId = ref<number | null>(null)
const selectedUser = computed(() => {
  if (selectedUserId.value === null) return null
  return filteredUsers.value.find((item) => item.userId === selectedUserId.value) ?? null
})

watch(
  filteredUsers,
  (items, previousItems) => {
    if (currentPage.value > totalPages.value) currentPage.value = 1

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

const roleFilterLabel = computed(() => (roleFilter.value === 'ALL' ? 'All Roles' : roleFilter.value))
const statusFilterLabel = computed(() => (statusFilter.value === 'ALL' ? 'All Status' : statusFilter.value))
const accountFilterLabel = computed(() => (accountFilter.value === 'ALL' ? 'All Account States' : accountFilter.value))
const classFilterLabel = computed(() => {
  if (classFilter.value === 'ALL') return 'All Classes / Courses'
  if (classFilter.value === 'ENROLLED') return 'Enrolled'
  if (classFilter.value === 'PUBLISHED') return 'Published'
  return 'Admin Access'
})
const sortModeLabel = computed(() => (sortMode.value === 'joined' ? 'Filters' : 'A-Z'))
const extraFilterLabel = computed(() => {
  if (extraFilter.value === 'recent') return 'Recent'
  if (extraFilter.value === 'verified') return 'Verified'
  return 'More Filters'
})
const selectedUserMetaLabel = computed(() => {
  if (selectedUser.value) return `Focused on ${selectedUser.value.fullName}`
  if (!filteredUsers.value.length) {
    return hasActiveFilters.value
      ? 'No users match the current filters.'
      : 'User records will appear here once the directory loads.'
  }
  return 'Choose a user to inspect account context.'
})
const paginationLabel = computed(() => {
  const start = filteredUsers.value.length ? (currentPage.value - 1) * pageSize + 1 : 0
  const end = Math.min(filteredUsers.value.length, currentPage.value * pageSize)
  return `${start}-${end} of ${filteredUsers.value.length}`
})
const selectedFollowUpRoutes = computed(() => {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    return [
      {
        title: 'Teacher Verification',
        copy: 'Review status, documents, and approval notes.',
        icon: 'status',
        tone: 'green',
        path: '/admin/teacher-verification',
      },
      {
        title: 'Content Review',
        copy: 'Open submitted teacher content and moderation flow.',
        icon: 'content',
        tone: 'blue',
        path: '/admin/content',
      },
      {
        title: 'Audit Trail',
        copy: 'Review latest admin activity records.',
        icon: 'history',
        tone: 'purple',
        path: '/admin/settings',
      },
    ]
  }

  if (selectedUser.value?.roleKey === 'ADMIN') {
    return [
      {
        title: 'Audit Logs',
        copy: 'Inspect latest platform audit events.',
        icon: 'status',
        tone: 'green',
        path: '/admin/settings',
      },
      {
        title: 'Analytics',
        copy: 'Review platform performance and usage trends.',
        icon: 'analytics',
        tone: 'blue',
        path: '/admin/analytics',
      },
      {
        title: 'Moderation',
        copy: 'Open content and AI safety review queues.',
        icon: 'moderation',
        tone: 'purple',
        path: '/admin/moderation',
      },
    ]
  }

  return [
    {
      title: 'Analytics',
      copy: 'Review learner progress from aggregate analytics.',
      icon: 'analytics',
      tone: 'green',
      path: '/admin/analytics',
    },
    {
      title: 'Audit Logs',
      copy: 'Inspect account-related audit events.',
      icon: 'history',
      tone: 'blue',
      path: '/admin/settings',
    },
    {
      title: 'Support Settings',
      copy: 'Open support and operational settings.',
      icon: 'settings',
      tone: 'purple',
      path: '/admin/settings',
    },
  ]
})

const primaryActionLabel = computed(() => {
  if (selectedUser.value?.roleKey === 'TEACHER') return 'Open teacher review'
  if (selectedUser.value?.roleKey === 'STUDENT') return 'Open analytics'
  return 'Open audit logs'
})

const secondaryActionLabel = computed(() => {
  if (selectedUser.value?.roleKey === 'TEACHER') return 'Open content review'
  if (selectedUser.value?.roleKey === 'STUDENT') return 'Open support settings'
  return 'Open platform analytics'
})

const selectedAudit = computed(() =>
  auditLogs.value.slice(0, 5).map((item) => ({
    id: item.id,
    title: item.actionType.replaceAll('_', ' '),
    time: formatDateTime(item.createdAt),
    meta: item.actorEmail || 'System',
  })),
)

function cycleRoleFilter() {
  const order: RoleFilter[] = ['ALL', 'STUDENT', 'TEACHER', 'ADMIN']
  roleFilter.value = order[(order.indexOf(roleFilter.value) + 1) % order.length]
}

function cycleStatusFilter() {
  const order: StatusFilter[] = ['ALL', 'ACTIVE', 'PENDING', 'INACTIVE']
  statusFilter.value = order[(order.indexOf(statusFilter.value) + 1) % order.length]
}

function cycleAccountFilter() {
  const order: AccountFilter[] = ['ALL', 'VERIFIED', 'VERIFICATION']
  accountFilter.value = order[(order.indexOf(accountFilter.value) + 1) % order.length]
}

function cycleClassFilter() {
  const order: ClassFilter[] = ['ALL', 'ENROLLED', 'PUBLISHED', 'ADMIN']
  classFilter.value = order[(order.indexOf(classFilter.value) + 1) % order.length]
}

function cycleSortMode() {
  sortMode.value = sortMode.value === 'joined' ? 'name' : 'joined'
}

function cycleExtraFilter() {
  const order: ExtraFilter[] = ['filters', 'recent', 'verified']
  extraFilter.value = order[(order.indexOf(extraFilter.value) + 1) % order.length]
}

function clearFilters() {
  searchTerm.value = ''
  roleFilter.value = 'ALL'
  statusFilter.value = 'ALL'
  accountFilter.value = 'ALL'
  classFilter.value = 'ALL'
  sortMode.value = 'joined'
  extraFilter.value = 'filters'
  activeTab.value = 'ALL'
  currentPage.value = 1
}

function selectUser(userId: number) {
  selectedUserId.value = userId
}

function openAnalytics() {
  router.push('/admin/analytics')
}

function openProgressTarget() {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    router.push('/admin/teacher-verification')
    return
  }

  router.push('/admin/analytics')
}

function openActivityTarget() {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    router.push('/admin/teacher-verification')
    return
  }

  router.push('/admin/settings')
}

function openPrimaryAction() {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    router.push('/admin/teacher-verification')
    return
  }

  router.push('/admin/settings')
}

function openSecondaryAction() {
  if (selectedUser.value?.roleKey === 'TEACHER') {
    router.push('/admin/content')
    return
  }

  router.push('/admin/settings')
}

function openTeacherVerification() {
  router.push('/admin/teacher-verification')
}

function openRecoveryAction() {
  router.push('/admin/settings')
}

function clearSelection() {
  selectedUserId.value = null
}

async function toggleSelectedUserStatus() {
  if (submittingUserStatus.value) return
  if (!selectedUser.value) return

  if (usingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Account access cannot be changed while preview data is active.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingUserStatus.value = true
  const nextActive = !selectedUser.value.active

  try {
    await adminApi.updateUserStatus(selectedUser.value.userId, { active: nextActive })
    await usersResource.reload()
    await teachersResource.reload()
    await studentsResource.reload()
    actionSuccess.value = nextActive ? 'Account reactivated successfully.' : 'Account suspended successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to update account access right now.')
  } finally {
    submittingUserStatus.value = false
  }
}

function reloadAll() {
  void usersResource.reload()
  void teachersResource.reload()
  void studentsResource.reload()
  void auditResource.reload()
}
</script>

<style scoped>
.admin-users-page {
  position: relative;
}

.admin-users-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 354px;
  gap: 12px;
  align-items: start;
}

.admin-users-sidebar {
  min-width: 0;
  display: grid;
  gap: 16px;
}

.admin-users-profile-copy {
  gap: 6px;
}

.admin-users-table-card,
.admin-users-profile-card,
.admin-users-info-card,
.admin-users-progress-card,
.admin-users-activity-card,
.admin-users-audit-card,
.admin-users-actions-card {
  border-radius: 28px;
}

.teacher-outline-button {
  border: 1px solid rgba(223, 227, 243, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: var(--text);
  border-radius: 14px;
  padding: 10px 15px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.admin-users-tabs {
  display: flex;
  align-items: center;
  gap: 28px;
  padding-bottom: 11px;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-users-tab {
  padding: 0 2px 13px;
  border: 0;
  background: transparent;
  color: #6b7795;
  font-weight: 700;
}

.admin-users-tab.is-active {
  color: var(--primary);
  border-bottom: 3px solid var(--primary);
}

.admin-users-toolbar {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.admin-users-toolbar-top,
.admin-users-toolbar-bottom,
.admin-users-toolbar-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.admin-users-toolbar-top {
  justify-content: flex-end;
}

.admin-users-toolbar-bottom {
  justify-content: space-between;
}

.admin-users-toolbar-filters {
  flex: 1;
}

.admin-users-search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 300px;
  flex: 1 1 360px;
  padding: 0 15px;
  min-height: 44px;
  border-radius: 15px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #8a95b0;
}

.admin-users-search-input {
  min-height: 36px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
}

.admin-users-compact-button {
  min-height: 42px;
  padding-inline: 15px;
  border-radius: 14px;
  border-color: rgba(224, 228, 243, 0.96);
  box-shadow: none;
}

.admin-users-compact-button.is-icon {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.admin-users-clear {
  font-weight: 700;
}

.admin-users-table-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  color: #6e7a96;
  font-weight: 600;
  flex-wrap: wrap;
}

.admin-users-table-shell {
  margin-top: 16px;
  border-top: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-users-table-header,
.admin-users-table-row {
  display: grid;
  grid-template-columns: 36px minmax(232px, 1.34fr) 92px 110px 128px 112px 108px 54px;
  gap: 12px;
  align-items: center;
}

.admin-users-table-header {
  min-height: 48px;
  color: #73809c;
  font-size: 0.8rem;
  font-weight: 700;
}

.admin-users-table-row {
  min-height: 68px;
  padding: 8px 0;
  border-top: 1px solid rgba(236, 239, 247, 0.96);
  color: #4d5974;
  cursor: pointer;
}

.admin-users-table-row:focus-visible {
  outline: 3px solid rgba(111, 76, 255, 0.24);
  outline-offset: 4px;
}

.admin-users-table-row.is-active {
  margin: 0 -8px;
  padding: 8px 8px;
  border-radius: 20px;
  border: 1px solid rgba(126, 96, 255, 0.28);
  background: linear-gradient(135deg, rgba(248, 245, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.admin-users-check {
  display: grid;
  place-items: center;
}

.admin-users-check input {
  display: none;
}

.admin-users-check span {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  border: 1px solid rgba(211, 217, 235, 0.96);
  background: rgba(255, 255, 255, 0.98);
}

.admin-users-check input:checked + span {
  background: linear-gradient(135deg, #7d58ff, #6d49f8);
  border-color: transparent;
  box-shadow: 0 10px 18px rgba(106, 73, 255, 0.24);
}

.admin-users-user-cell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.admin-users-user-copy {
  display: grid;
  gap: 3px;
}

.admin-users-user-copy span,
.admin-users-user-copy small {
  color: #7b87a3;
}

.admin-users-role-pill,
.admin-users-status-pill,
.admin-users-account-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
}

.admin-users-status-pill i,
.admin-users-account-pill i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.92;
}

.admin-users-role-pill.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-users-role-pill.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-users-role-pill.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: var(--primary);
}

.admin-users-status-pill.is-active {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-users-status-pill.is-pending {
  background: rgba(255, 181, 68, 0.14);
  color: #e1901d;
}

.admin-users-status-pill.is-inactive {
  background: rgba(255, 124, 124, 0.14);
  color: #e25b51;
}

.admin-users-account-pill.is-verified {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-users-account-pill.is-warning {
  background: rgba(255, 181, 68, 0.14);
  color: #e1901d;
}

.admin-users-more {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #7b87a3;
}

.admin-users-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(236, 239, 247, 0.96);
  color: #75819b;
}

.admin-users-pagination-left,
.admin-users-pagination-right {
  align-items: center;
  gap: 8px;
}

.admin-users-empty-state {
  margin-top: 16px;
}

.admin-users-page-size {
  min-width: 56px;
  justify-content: center;
}

.admin-users-page-button {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  border: 1px solid rgba(228, 232, 244, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #7d89a4;
  font-weight: 700;
}

.admin-users-page-button.is-active {
  background: linear-gradient(135deg, #7d58ff, #6d49f8);
  color: white;
  border-color: transparent;
}

.admin-users-profile-head,
.admin-users-profile-main {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 12px;
}

.admin-users-profile-copy {
  min-width: 0;
}

.admin-users-profile-title,
.admin-users-profile-meta {
  flex-wrap: wrap;
}

.admin-users-profile-title strong,
.admin-users-profile-meta span,
.admin-users-profile-meta small {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.admin-users-profile-card span,
.admin-users-profile-card small,
.admin-users-info-grid span,
.admin-users-progress-copy span,
.admin-users-activity-copy span,
.admin-users-audit-item span,
.admin-users-actions-note {
  color: #74809c;
}

.admin-users-close {
  inline-size: 42px;
  block-size: 42px;
  min-inline-size: 42px;
  min-block-size: 42px;
  flex: 0 0 42px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 14px;
  background: transparent;
  color: #6f7b97;
}

.admin-users-info-grid {
  display: grid;
  gap: 10px;
}

.admin-users-info-grid div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.admin-users-progress-wrap {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.admin-users-progress-copy {
  display: grid;
  gap: 10px;
}

.admin-users-progress-copy div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.admin-users-activity-list,
.admin-users-audit-list {
  display: grid;
  gap: 8px;
}

.admin-users-activity-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.admin-users-activity-icon {
  width: 32px;
  height: 32px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.admin-users-activity-icon.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-users-activity-icon.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-users-activity-icon.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: var(--primary);
}

.admin-users-activity-copy,
.admin-users-audit-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.admin-users-audit-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-users-audit-item:last-child {
  border-bottom: 0;
}

.admin-users-audit-item small {
  max-width: 100%;
  color: #8b95af;
  overflow-wrap: anywhere;
}

.admin-users-audit-copy strong,
.admin-users-audit-copy span {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.admin-users-actions-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.admin-users-empty-sidebar {
  min-height: 220px;
  align-content: center;
}

.admin-users-action-safe {
  min-height: 48px;
  border-radius: 16px;
  border: 1px solid rgba(111, 210, 104, 0.16);
  background: rgba(111, 210, 104, 0.08);
  color: #49a347;
  font-weight: 800;
}

@media (max-width: 1520px) {
  .admin-users-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .admin-users-table-header,
  .admin-users-table-row {
    grid-template-columns: 34px minmax(220px, 1.3fr) 96px 112px 132px;
  }

  .admin-users-table-header span:nth-child(6),
  .admin-users-table-header span:nth-child(7),
  .admin-users-table-header span:nth-child(8),
  .admin-users-table-row > :nth-child(6),
  .admin-users-table-row > :nth-child(7),
  .admin-users-table-row > :nth-child(8) {
    display: none;
  }
}

@media (max-width: 760px) {
  .admin-users-search {
    min-width: 100%;
  }

  .admin-users-tabs {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    border-bottom: 0;
    padding-bottom: 0;
  }

  .admin-users-tab {
    padding: 10px 12px;
    border-radius: 14px;
    border: 1px solid rgba(233, 237, 247, 0.96);
    background: rgba(255, 255, 255, 0.98);
  }

  .admin-users-tab.is-active {
    border-bottom: 1px solid rgba(111, 76, 255, 0.22);
    background: rgba(244, 240, 255, 0.96);
  }

  .admin-users-toolbar,
  .admin-users-pagination,
  .admin-users-progress-wrap {
    grid-template-columns: 1fr;
    display: grid;
  }

  .admin-users-toolbar-top,
  .admin-users-toolbar-bottom {
    justify-content: stretch;
  }

  .admin-users-toolbar-filters,
  .admin-users-compact-button,
  .admin-users-clear {
    width: 100%;
  }

  .admin-users-table-card,
  .admin-users-profile-card,
  .admin-users-info-card,
  .admin-users-progress-card,
  .admin-users-activity-card,
  .admin-users-audit-card,
  .admin-users-actions-card {
    border-radius: 22px;
  }

  .admin-users-table-header {
    display: none;
  }

  .admin-users-table-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
    min-height: 0;
    padding: 12px;
    border: 1px solid rgba(229, 233, 248, 0.96);
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.92);
  }

  .admin-users-table-row > :nth-child(3),
  .admin-users-table-row > :nth-child(4),
  .admin-users-table-row > :nth-child(5),
  .admin-users-table-row > :nth-child(6),
  .admin-users-table-row > :nth-child(7),
  .admin-users-table-row > :nth-child(8) {
    display: none;
  }

  .admin-users-check {
    display: none;
  }

  .admin-users-user-cell {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
    gap: 10px;
  }

  .admin-users-user-copy {
    gap: 6px;
  }

  .admin-users-user-copy strong,
  .admin-users-user-copy span,
  .admin-users-user-copy small {
    max-width: 100%;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .admin-users-profile-head,
  .admin-users-profile-main,
  .admin-users-progress-copy div,
  .admin-users-info-grid div,
  .admin-users-audit-item {
    display: grid;
    grid-template-columns: 1fr;
    justify-items: start;
    text-align: left;
  }

  .admin-users-profile-main {
    gap: 12px;
  }

  .admin-users-close {
    justify-self: end;
  }

  .admin-users-actions-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 900px) and (max-width: 1180px) {
  .admin-users-layout {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1181px) {
  .admin-users-layout {
    grid-template-columns: minmax(0, 1.28fr) minmax(0, 0.84fr);
    gap: 12px;
  }

  .admin-users-sidebar {
    gap: 14px;
  }

  .admin-users-table-card,
  .admin-users-profile-card,
  .admin-users-info-card,
  .admin-users-progress-card,
  .admin-users-activity-card,
  .admin-users-audit-card,
  .admin-users-actions-card {
    padding: 18px;
    border-radius: 22px;
  }

  .admin-users-tabs {
    gap: 18px;
    padding-bottom: 10px;
  }

  .admin-users-tab {
    padding-bottom: 12px;
    font-size: 0.78rem;
  }

  .admin-users-toolbar {
    gap: 10px;
    margin-top: 12px;
  }

  .admin-users-search {
    min-width: 280px;
    min-height: 42px;
    padding: 0 14px;
    border-radius: 14px;
    font-size: 0.8rem;
  }

  .admin-users-compact-button {
    min-height: 40px;
    padding-inline: 12px;
    border-radius: 12px;
    font-size: 0.76rem;
  }

  .admin-users-table-meta,
  .admin-users-table-shell {
    margin-top: 12px;
  }

  .admin-users-table-header,
  .admin-users-table-row {
    grid-template-columns: 28px minmax(180px, 1.34fr) 70px 92px 108px 90px 90px 36px;
    gap: 8px;
  }

  .admin-users-table-header {
    min-height: 40px;
    font-size: 0.68rem;
  }

  .admin-users-table-row {
    min-height: 58px;
    padding: 7px 0;
    font-size: 0.72rem;
  }

  .admin-users-table-header span:nth-child(6),
  .admin-users-table-header span:nth-child(7),
  .admin-users-table-header span:nth-child(8),
  .admin-users-table-row > :nth-child(6),
  .admin-users-table-row > :nth-child(7),
  .admin-users-table-row > :nth-child(8) {
    display: block;
  }

  .admin-users-table-row.is-active {
    margin: 0 -6px;
    padding: 7px 6px;
    border-radius: 16px;
  }

  .admin-users-check span {
    width: 16px;
    height: 16px;
    border-radius: 5px;
  }

  .admin-users-user-cell {
    grid-template-columns: 40px minmax(0, 1fr);
    gap: 10px;
  }

  .admin-users-user-cell :deep(.portrait-avatar) {
    transform: scale(0.78);
    transform-origin: center;
  }

  .admin-users-user-copy strong,
  .admin-users-user-copy span,
  .admin-users-user-copy small {
    font-size: 0.72rem;
  }

  .admin-users-role-pill,
  .admin-users-status-pill,
  .admin-users-account-pill {
    gap: 5px;
    padding: 5px 9px;
    font-size: 0.64rem;
  }

  .admin-users-status-pill i,
  .admin-users-account-pill i {
    width: 6px;
    height: 6px;
  }

  .admin-users-more {
    width: 38px;
    height: 38px;
    border-radius: 10px;
  }

  .admin-users-pagination {
    gap: 12px;
    margin-top: 12px;
    padding-top: 12px;
    font-size: 0.74rem;
  }

  .admin-users-page-button {
    width: 30px;
    height: 30px;
    border-radius: 10px;
    font-size: 0.72rem;
  }

  .admin-users-profile-main {
    min-width: 0;
    gap: 12px;
  }

  .admin-users-profile-main :deep(.portrait-avatar) {
    transform: scale(0.84);
    transform-origin: center;
  }

  .admin-users-profile-title strong {
    font-size: 1rem;
  }

  .admin-users-profile-card span,
  .admin-users-profile-card small,
  .admin-users-info-grid,
  .admin-users-progress-copy,
  .admin-users-activity-copy,
  .admin-users-audit-item,
  .admin-users-actions-note {
    font-size: 0.74rem;
  }

  .admin-users-info-grid,
  .admin-users-progress-copy,
  .admin-users-activity-list,
  .admin-users-audit-list {
    gap: 9px;
  }

  .admin-users-progress-wrap {
    grid-template-columns: 86px minmax(0, 1fr);
    gap: 12px;
  }

  .admin-users-activity-item {
    gap: 10px;
  }

  .admin-users-activity-icon {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }

  .admin-users-actions-grid {
    gap: 10px;
  }

  .admin-users-action-safe,
  .teacher-outline-button {
    min-height: 40px;
    border-radius: 12px;
    padding: 9px 12px;
    font-size: 0.74rem;
  }
}
</style>
