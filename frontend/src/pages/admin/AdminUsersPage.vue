<template>
  <div class="admin-users page-stack">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminUsers.pageTitle')" :description="t('adminUsers.lede')">
        <template #actions>
          <button class="btn btn-primary" type="button" data-testid="admin-users-invite" @click="openInvite">
            <IconGlyph name="mail" :size="16" />
            <span>{{ t('adminUsers.inviteAdmin') }}</span>
          </button>
        </template>
      </PageHeaderBlock>

      <AdminUsersTabs />
    </div>

    <DataNotice
      :error="actionError"
      :success="actionSuccess"
      :detail="noticeDetail"
      :success-title="t('adminUsers.noticeDone')"
      :error-title="t('adminUsers.noticeFailed')"
    />

    <section class="admin-sheet" aria-labelledby="admin-users-list-title">
      <h2 id="admin-users-list-title" class="sr-only">{{ t('adminUsers.tableLabel') }}</h2>
      <form class="admin-toolbar" role="search" @submit.prevent>
        <div class="field admin-toolbar-search">
          <label class="field-label" for="admin-users-search">{{ t('adminUsers.searchLabel') }}</label>
          <span class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="admin-users-search"
              v-model="searchTerm"
              class="input"
              type="search"
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
            <option v-for="option in ROLE_OPTIONS" :key="option" :value="option">{{ t(roleKey(option)) }}</option>
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

      <div v-if="users.loading.value" class="admin-sheet-body">
        <StatePanel tone="loading" :title="t('adminUsers.loadingTitle')" :message="t('adminUsers.loadingHint')" />
      </div>
      <div v-else-if="users.error.value && !items.length" class="admin-sheet-body">
        <StatePanel tone="error" icon="alert" :title="t('adminUsers.loadErrorTitle')" :message="users.error.value">
          <template #actions>
            <button class="btn btn-primary" type="button" @click="users.reload">{{ t('adminUsers.retry') }}</button>
          </template>
        </StatePanel>
      </div>
      <template v-else-if="items.length">
        <div class="data-table-wrap" role="region" :aria-label="t('adminUsers.tableLabel')" tabindex="0">
          <table class="data-table admin-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminUsers.colUser') }}</th>
                <th scope="col">{{ t('adminUsers.colRole') }}</th>
                <th scope="col">{{ t('adminUsers.colAccount') }}</th>
                <th scope="col">{{ t('adminUsers.colVerification') }}</th>
                <th scope="col" class="col-numeric">{{ t('adminUsers.colJoined') }}</th>
                <th scope="col" class="col-actions">
                  <span class="sr-only">{{ t('adminUsers.detailHeading') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in items"
                :key="user.id"
                :class="{ 'is-selected': user.id === selectedId }"
                data-testid="admin-users-row"
                @click="selectUser(user.id)"
              >
                <td>
                  <span class="admin-cell-person">
                    <PortraitAvatar :size="32" :name="user.fullName" />
                    <span class="admin-cell-title">
                      <strong>{{ user.fullName }}</strong>
                      <span>{{ user.email }}</span>
                    </span>
                  </span>
                </td>
                <td>
                  <span class="tag">{{ t(roleKey(user.role)) }}</span>
                </td>
                <!-- Active is the norm and reads as plain text; only a deactivated account is flagged. -->
                <td class="admin-users-account">
                  <span v-if="user.active">{{ t('adminUsers.accountActive') }}</span>
                  <StatusPill v-else v-bind="accountPill(false)" />
                </td>
                <td>
                  <StatusPill
                    v-if="user.verificationState"
                    :label="t(verificationStateKey(user.verificationState))"
                    :tone="verificationTone(user.verificationState)"
                  />
                  <template v-else>
                    <span aria-hidden="true">—</span>
                    <span class="sr-only">{{ t('adminUsers.notApplicable') }}</span>
                  </template>
                </td>
                <td class="col-numeric admin-cell-date">
                  {{ formatDate(user.joinedAt) }}
                </td>
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
        <div class="admin-sheet-foot">
          <PaginationBar
            :current-page="page + 1"
            :total-pages="totalPages"
            :info="t('adminUsers.showing', showingRange)"
            :label="t('adminUsers.pagination')"
            icon-only
            test-id-prefix="admin-users"
            @update:current-page="goToPage($event - 1)"
          />
        </div>
      </template>
      <div v-else class="admin-sheet-body">
        <StatePanel tone="empty" icon="search" :title="t('adminUsers.emptyTitle')" :message="t('adminUsers.emptyHint')">
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="clearFilters">
              {{ t('adminUsers.clearFilters') }}
            </button>
          </template>
        </StatePanel>
      </div>
    </section>

    <section
      v-if="pendingInvites.length"
      class="admin-users-invites"
      aria-labelledby="admin-users-invites-title"
      data-testid="admin-users-invites"
    >
      <h2 id="admin-users-invites-title" class="admin-users-invites-title">
        {{ t('adminUsers.invitesPending') }}
        <span class="chip">{{ t('adminUsers.invitesPendingCount', { count: pendingInvites.length }) }}</span>
      </h2>
      <ul class="admin-users-invite-list">
        <li v-for="invite in pendingInvites" :key="invite.id">
          <IconGlyph name="mail" :size="16" />
          <strong>{{ invite.email }}</strong>
          <span>
            {{
              t('adminUsers.inviteRow', { sent: formatDateTime(invite.sentAt), expires: formatDate(invite.expiresAt) })
            }}
          </span>
        </li>
      </ul>
    </section>

    <AppOverlay
      :open="selectedId !== null"
      variant="drawer"
      :title="selectedUser?.fullName ?? t('adminUsers.detailHeading')"
      :subtitle="selectedUser?.email ?? ''"
      test-id="admin-users-detail"
      @close="closeUser"
    >
      <StatePanel
        v-if="detail.loading.value"
        tone="loading"
        :title="t('adminUsers.detailLoading')"
        :message="t('adminUsers.detailLoadingHint')"
      />
      <StatePanel
        v-else-if="detail.error.value || !selectedUser"
        tone="error"
        icon="alert"
        :title="t('adminUsers.detailError')"
        :message="detail.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('adminUsers.retry') }}</button>
        </template>
      </StatePanel>
      <template v-else>
        <div class="admin-users-profile">
          <PortraitAvatar :size="56" :name="selectedUser.fullName" />
          <div class="admin-users-profile-tags">
            <span class="tag">{{ t(roleKey(selectedUser.role)) }}</span>
            <StatusPill v-bind="accountPill(selectedUser.active)" />
            <StatusPill
              v-if="selectedUser.verificationState"
              :label="t(verificationStateKey(selectedUser.verificationState))"
              :tone="verificationTone(selectedUser.verificationState)"
            />
          </div>
        </div>

        <dl class="kv-list">
          <dt>{{ t('adminUsers.kvJoined') }}</dt>
          <dd>{{ formatDate(selectedUser.joinedAt) }}</dd>
          <template v-if="selectedUser.approvedAt">
            <dt>{{ t('adminUsers.kvApproved') }}</dt>
            <dd>{{ formatDate(selectedUser.approvedAt) }}</dd>
          </template>
        </dl>

        <div v-if="selectedUser.deactivation" class="callout is-warning" data-testid="admin-users-deactivation">
          <IconGlyph name="lock" :size="18" />
          <div>
            <p>
              {{
                selectedUser.deactivation.actorName
                  ? t('adminUsers.deactivatedBy', {
                      date: formatDateTime(selectedUser.deactivation.at),
                      actor: selectedUser.deactivation.actorName,
                    })
                  : t('adminUsers.deactivatedOn', { date: formatDateTime(selectedUser.deactivation.at) })
              }}
            </p>
            <p v-if="selectedUser.deactivation.reason">
              {{ t('adminUsers.reasonLine', { reason: selectedUser.deactivation.reason }) }}
            </p>
          </div>
        </div>

        <section class="admin-drawer-section" aria-labelledby="admin-users-audit-title">
          <div class="admin-drawer-head">
            <h3 id="admin-users-audit-title" class="admin-reader-label">{{ t('adminUsers.recentAudit') }}</h3>
            <RouterLink
              class="btn btn-link"
              :to="{ name: 'admin-audit-log', query: { q: selectedUser.fullName } }"
              :aria-label="t('adminUsers.viewAllAudit', { name: selectedUser.fullName })"
            >
              {{ t('adminUsers.viewAll') }}
              <IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
          </div>
          <ol v-if="selectedUser.recentAudit.length" class="admin-users-audit">
            <li v-for="entry in selectedUser.recentAudit" :key="entry.id">
              <RouterLink class="admin-users-audit-entry" :to="{ name: 'admin-audit-log', query: { entry: entry.id } }">
                <strong>{{ actionLabel(entry.actionType) }}</strong>
                <span v-if="entry.targetLabel">{{ entry.targetLabel }}</span>
                <span
                  >{{ formatDateTime(entry.createdAt)
                  }}<template v-if="entry.actorEmail"> · {{ entry.actorEmail }}</template></span
                >
              </RouterLink>
            </li>
          </ol>
          <p v-else class="type-caption">{{ t('adminUsers.noAudit') }}</p>
        </section>

        <section class="admin-drawer-section" aria-labelledby="admin-users-access-title">
          <h3 id="admin-users-access-title" class="admin-reader-label">{{ t('adminUsers.access') }}</h3>
          <p class="type-caption">
            {{
              selectedUser.self
                ? t('adminUsers.selfNote')
                : selectedUser.active
                  ? t('adminUsers.deactivateHint')
                  : t('adminUsers.reactivateHint')
            }}
          </p>
        </section>
      </template>
      <template v-if="selectedUser && !selectedUser.self && !detail.loading.value" #footer>
        <button
          v-if="selectedUser.active"
          class="btn btn-danger-soft"
          type="button"
          data-testid="admin-users-deactivate"
          @click="openStatusDialog('deactivate')"
        >
          <IconGlyph name="lock" :size="16" />
          <span>{{ t('adminUsers.deactivate') }}</span>
        </button>
        <button
          v-else
          class="btn btn-secondary"
          type="button"
          data-testid="admin-users-reactivate"
          @click="openStatusDialog('reactivate')"
        >
          <IconGlyph name="unlock" :size="16" />
          <span>{{ t('adminUsers.reactivate') }}</span>
        </button>
      </template>
    </AppOverlay>

    <ConfirmDialog
      :open="statusDialog !== null"
      :title="statusDialogTitle"
      :message="statusDialogMessage"
      :confirm-label="statusDialog === 'deactivate' ? t('adminUsers.deactivate') : t('adminUsers.reactivate')"
      :tone="statusDialog === 'deactivate' ? 'danger' : 'primary'"
      :busy="saving"
      :error="dialogError"
      test-id="admin-users-status-dialog"
      @cancel="closeStatusDialog"
      @confirm="confirmStatus"
    >
      <div v-if="statusDialog === 'deactivate' && selectedUser" class="callout is-warning">
        <IconGlyph name="alert" :size="18" />
        <ul data-testid="admin-users-impact">
          <li>
            <strong>{{ t('adminUsers.impactSignedOut', { name: selectedUser.fullName }) }}</strong>
          </li>
          <li v-for="line in impactLines" :key="line">{{ line }}</li>
        </ul>
      </div>
      <p v-else-if="statusDialog === 'reactivate' && selectedUser" class="callout">
        <IconGlyph name="unlock" :size="18" />
        <span>{{ t('adminUsers.reactivateCopy', { name: selectedUser.fullName }) }}</span>
      </p>
      <div class="field">
        <label class="field-label" for="admin-users-reason">
          {{ t('adminUsers.reasonLabel') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          id="admin-users-reason"
          v-model="reason"
          class="textarea"
          rows="3"
          maxlength="500"
          required
          :aria-invalid="Boolean(reasonError)"
          :aria-describedby="reasonError ? 'admin-users-reason-error' : 'admin-users-reason-hint'"
          data-testid="admin-users-reason"
        />
        <p id="admin-users-reason-hint" class="field-hint">{{ t('adminUsers.reasonHint') }}</p>
        <p v-if="reasonError" id="admin-users-reason-error" class="field-error" role="alert">{{ reasonError }}</p>
      </div>
    </ConfirmDialog>

    <AppOverlay
      :open="inviteOpen"
      variant="drawer"
      :title="t('adminUsers.inviteTitle')"
      :subtitle="t('adminUsers.inviteIntro')"
      test-id="admin-users-invite-drawer"
      @close="inviteOpen = false"
    >
      <form id="admin-users-invite-form" class="form-grid" novalidate @submit.prevent="sendInvite">
        <div class="field">
          <label class="field-label" for="admin-users-invite-email">
            {{ t('adminUsers.inviteEmail') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="admin-users-invite-email"
            v-model="inviteEmail"
            class="input"
            type="email"
            autocomplete="off"
            required
            placeholder="name@example.com"
            :aria-invalid="Boolean(inviteError)"
            :aria-describedby="inviteError ? 'admin-users-invite-error' : 'admin-users-invite-hint'"
            data-testid="admin-users-invite-email"
          />
          <p id="admin-users-invite-hint" class="field-hint">{{ t('adminUsers.inviteEmailHint') }}</p>
          <p v-if="inviteError" id="admin-users-invite-error" class="field-error" role="alert">{{ inviteError }}</p>
        </div>
        <p class="callout">
          <IconGlyph name="info" :size="18" />
          <span>{{ t('adminUsers.inviteListNote') }}</span>
        </p>
      </form>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="inviteOpen = false">{{ t('adminUsers.cancel') }}</button>
        <button
          class="btn btn-primary"
          type="submit"
          form="admin-users-invite-form"
          :disabled="inviting"
          :aria-busy="inviting"
          data-testid="admin-users-send-invite"
        >
          <IconGlyph name="send" :size="16" />
          <span>{{ t('adminUsers.sendInvite') }}</span>
        </button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { adminUsersApi } from '@/api/ops'
import AdminUsersTabs from '@/components/admin/AdminUsersTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useAuditLabels } from '@/composables/useAuditLabels'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { UserRole } from '@/types/api'
import type { AdminUserDetail, AdminUserQuery } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { formatDate, formatDateTime } from '@/utils/format'
import { roleKey, verificationStateKey, verificationTone } from '@/utils/opsLabels'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 10
const ROLE_OPTIONS: readonly UserRole[] = ['STUDENT', 'TEACHER', 'ADMIN']

type AccountFilter = '' | 'ACTIVE' | 'DEACTIVATED'
type StatusDialog = 'deactivate' | 'reactivate'

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({
  adminUsers: adminUsersMessages,
  opsLabels: opsLabelMessages,
  auditActions: auditActionMessages,
})
const { actionLabel } = useAuditLabels(t)
const { actionError, actionSuccess, clearFeedback, showSuccess } = useActionFeedback()
const noticeDetail = ref<string | null>(null)

// ----- list -----

const { searchTerm, keyword, clear: clearSearch } = useDebouncedSearch()
const role = ref<UserRole | ''>('')
const account = ref<AccountFilter>('')
const page = ref(0)

function usersQuery(): AdminUserQuery {
  const query: AdminUserQuery = {}
  if (keyword.value) query.keyword = keyword.value
  if (role.value) query.role = role.value
  if (account.value) query.active = account.value === 'ACTIVE'
  return { ...query, page: page.value, size: PAGE_SIZE }
}

const users = useResource(() => adminUsersApi.list(usersQuery()))
const { items, totalPages, range: showingRange, goToPage } = useServerPage(users, page)

watch([keyword, role, account], () => {
  page.value = 0
  void users.reload()
})

function clearFilters(): void {
  clearSearch()
  role.value = ''
  account.value = ''
}

function accountPill(active: boolean) {
  return active
    ? { label: t('adminUsers.accountActive'), tone: 'success' as const }
    : { label: t('adminUsers.accountDeactivated'), tone: 'neutral' as const, icon: 'lock' }
}

// ----- selected user (`?user=<id>`) -----

const selectedId = computed<number | null>(() => {
  const id = Number(route.query.user)
  return Number.isInteger(id) && id > 0 ? id : null
})

const detail = useResource<AdminUserDetail | null>(
  () => (selectedId.value ? adminUsersApi.get(selectedId.value) : Promise.resolve(null)),
  false,
)
const selectedUser = computed(() => detail.data.value)

watch(
  selectedId,
  (id, previous) => {
    if (id !== previous) detail.data.value = null
    void detail.reload()
  },
  { immediate: true },
)

function selectUser(id: number): void {
  void router.replace({ query: { ...route.query, user: String(id) } })
}

function closeUser(): void {
  if (statusDialog.value) return
  const query = { ...route.query }
  delete query.user
  void router.replace({ query })
}

// ----- deactivate / reactivate -----

const statusDialog = ref<StatusDialog | null>(null)
const reason = ref('')
const reasonError = ref<string | null>(null)
const dialogError = ref<string | null>(null)
const saving = ref(false)

const statusDialogTitle = computed(() => {
  const name = selectedUser.value?.fullName ?? ''
  return statusDialog.value === 'reactivate'
    ? t('adminUsers.reactivateTitle', { name })
    : t('adminUsers.deactivateTitle', { name })
})
const statusDialogMessage = computed(() =>
  selectedUser.value
    ? t('adminUsers.dialogSubtitle', { email: selectedUser.value.email, role: t(roleKey(selectedUser.value.role)) })
    : '',
)

/** The consequences listed in the Deactivate dialog; lines with nothing to say are left out. */
const impactLines = computed<string[]>(() => {
  const impact = selectedUser.value?.deactivationImpact
  if (!impact) return []
  const lines: string[] = []
  if (impact.publishedClasses.length) {
    lines.push(
      t('adminUsers.impactClasses', { titles: impact.publishedClasses.map((title) => `“${title}”`).join(', ') }),
    )
  }
  if (impact.upcomingSessions > 0) {
    lines.push(t('adminUsers.impactSessions', { sessions: impact.upcomingSessions, bookings: impact.bookings }))
  }
  if (impact.reviewItems > 0) lines.push(t('adminUsers.impactReview', { count: impact.reviewItems }))
  return lines
})

function openStatusDialog(kind: StatusDialog): void {
  reason.value = ''
  reasonError.value = null
  dialogError.value = null
  statusDialog.value = kind
}

function closeStatusDialog(): void {
  if (!saving.value) statusDialog.value = null
}

async function confirmStatus(): Promise<void> {
  const user = selectedUser.value
  const kind = statusDialog.value
  if (!user || !kind) return
  const text = reason.value.trim()
  reasonError.value = text ? null : t('adminUsers.reasonMissing')
  if (!text) return

  saving.value = true
  dialogError.value = null
  try {
    if (kind === 'deactivate') {
      const result = await adminUsersApi.deactivate(user.id, text)
      detail.data.value = result.user
      showSuccess(
        t('adminUsers.deactivated', {
          name: user.fullName,
          sessions: result.cancelledSessions,
          bookings: result.endedBookings,
          items: result.itemsBackToDraft,
        }),
      )
    } else {
      detail.data.value = await adminUsersApi.reactivate(user.id, text)
      showSuccess(t('adminUsers.reactivated', { name: user.fullName }))
    }
    noticeDetail.value = t('adminUsers.reasonLine', { reason: text })
    statusDialog.value = null
    void users.reload()
  } catch (failure) {
    dialogError.value = resolveApiError(
      failure,
      kind === 'deactivate' ? t('adminUsers.deactivateFailed') : t('adminUsers.reactivateFailed'),
    )
  } finally {
    saving.value = false
  }
}

// ----- invite admin -----

const invites = useResource(() => adminUsersApi.invites())
const pendingInvites = computed(() => invites.data.value ?? [])
const inviteOpen = ref(false)
const inviteEmail = ref('')
const inviteError = ref<string | null>(null)
const inviting = ref(false)

function openInvite(): void {
  inviteEmail.value = ''
  inviteError.value = null
  inviteOpen.value = true
}

async function sendInvite(): Promise<void> {
  const email = inviteEmail.value.trim().toLowerCase()
  if (!isValidEmail(email)) {
    inviteError.value = t('adminUsers.inviteEmailInvalid')
    return
  }
  inviting.value = true
  inviteError.value = null
  try {
    await adminUsersApi.invite(email)
    clearFeedback()
    noticeDetail.value = null
    showSuccess(t('adminUsers.inviteSent', { email }))
    inviteOpen.value = false
    void invites.reload()
  } catch (failure) {
    inviteError.value = resolveApiError(failure, t('adminUsers.inviteFailed'))
  } finally {
    inviting.value = false
  }
}
</script>
