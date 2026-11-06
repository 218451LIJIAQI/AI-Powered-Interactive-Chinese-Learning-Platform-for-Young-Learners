<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminUsers.pageTitle')" hide-title />

    <AdminUsersTabs>
      <button class="btn btn-secondary" type="button" data-testid="admin-users-invite" @click="openInvite">
        <IconGlyph name="mail" :size="16" />
        <span>{{ t('adminUsers.inviteAdmin') }}</span>
      </button>
    </AdminUsersTabs>

    <DataNotice :error="actionError" :error-title="t('adminUsers.noticeFailed')" />

    <div class="admin-users" :class="{ 'has-detail': showDetail }">
      <section class="panel admin-table-card" aria-labelledby="admin-users-list-title">
        <h2 id="admin-users-list-title" class="sr-only">{{ t('adminUsers.tableLabel') }}</h2>
        <form class="admin-filters" role="search" @submit.prevent>
          <div class="field is-search">
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

        <div v-if="pendingInvites.length" class="callout admin-users-invites" data-testid="admin-users-invites">
          <IconGlyph name="mail" :size="16" />
          <div class="admin-users-invites-copy">
            <strong>{{
              t('adminUsers.invitesPending', { count: pendingInvites.length }, pendingInvites.length)
            }}</strong>
            <span v-for="invite in pendingInvites" :key="invite.id" class="admin-meta">
              {{ t('adminUsers.inviteRow', { email: invite.email, sent: formatDateTime(invite.sentAt) }) }}
            </span>
          </div>
        </div>

        <StatePanel
          v-if="users.loading.value"
          tone="loading"
          :title="t('adminUsers.loadingTitle')"
          :message="t('adminUsers.loadingHint')"
        />
        <StatePanel
          v-else-if="users.error.value && !items.length"
          tone="error"
          icon="alert"
          :title="t('adminUsers.loadErrorTitle')"
          :message="users.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="users.reload">{{ t('adminUsers.retry') }}</button>
          </template>
        </StatePanel>
        <template v-else-if="items.length">
          <div
            class="data-table-wrap admin-table-wrap"
            role="region"
            :aria-label="t('adminUsers.tableLabel')"
            tabindex="0"
          >
            <table class="data-table admin-table admin-users-table">
              <thead>
                <tr>
                  <th scope="col" class="admin-users-col-user">{{ t('adminUsers.colUser') }}</th>
                  <th scope="col" class="admin-users-col-role">{{ t('adminUsers.colRole') }}</th>
                  <th scope="col" class="admin-users-col-account">{{ t('adminUsers.colAccount') }}</th>
                  <th scope="col" class="admin-users-col-verification">{{ t('adminUsers.colVerification') }}</th>
                  <th scope="col" class="admin-users-col-joined">{{ t('adminUsers.colJoined') }}</th>
                  <th scope="col" class="col-actions admin-col-chevron">
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
                      <PortraitAvatar :size="36" :name="user.fullName" />
                      <span class="admin-cell-title">
                        <strong>{{ user.fullName }}</strong>
                        <span>{{ user.email }}</span>
                      </span>
                    </span>
                  </td>
                  <td>
                    <span class="tag is-outline">{{ t(roleKey(user.role)) }}</span>
                  </td>
                  <td><StatusPill v-bind="accountPill(user.active)" /></td>
                  <td>
                    <StatusPill
                      v-if="user.verificationState"
                      :label="t(verificationStateKey(user.verificationState))"
                      :tone="adminVerificationTone(user.verificationState)"
                    />
                    <template v-else>
                      <span aria-hidden="true">—</span>
                      <span class="sr-only">{{ t('adminUsers.notApplicable') }}</span>
                    </template>
                  </td>
                  <td class="admin-cell-date">{{ formatShortDate(user.joinedAt) }}</td>
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
            :current-page="page + 1"
            :total-pages="totalPages"
            :info="t('adminUsers.showing', showingRange)"
            :label="t('adminUsers.pagination')"
            icon-only
            test-id-prefix="admin-users"
            @update:current-page="goToPage($event - 1)"
          />
        </template>
        <StatePanel
          v-else
          tone="empty"
          icon="search"
          spacious
          :title="keyword ? t('adminUsers.emptyTitleSearch', { term: keyword }) : t('adminUsers.emptyTitle')"
          :message="keyword ? t('adminUsers.emptyHintSearch') : t('adminUsers.emptyHint')"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="clearFilters">
              {{ keyword ? t('adminUsers.clearSearch') : t('adminUsers.clearFilters') }}
            </button>
          </template>
        </StatePanel>
      </section>

      <div v-if="showDetail" ref="detailRef" class="admin-users-side" tabindex="-1">
        <StatePanel
          v-if="detail.loading.value || selectedId === null"
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
          <section
            class="panel admin-detail"
            aria-labelledby="admin-users-detail-name"
            data-testid="admin-users-detail"
          >
            <header class="admin-person">
              <PortraitAvatar :size="56" :name="selectedUser.fullName" />
              <div class="admin-detail-heading">
                <h2 id="admin-users-detail-name" class="admin-detail-title">{{ selectedUser.fullName }}</h2>
                <p class="admin-meta">{{ selectedUser.email }}</p>
                <div class="admin-tags">
                  <span class="tag is-outline">{{ t(roleKey(selectedUser.role)) }}</span>
                  <StatusPill v-bind="accountPill(selectedUser.active)" />
                </div>
              </div>
            </header>

            <section class="admin-section admin-ruled" aria-labelledby="admin-users-account-title">
              <h3 id="admin-users-account-title" class="admin-section-title">{{ t('adminUsers.detailHeading') }}</h3>
              <dl class="kv-list">
                <dt>{{ t('adminUsers.kvRole') }}</dt>
                <dd>{{ t(roleKey(selectedUser.role)) }}</dd>
                <dt>{{ t('adminUsers.kvAccount') }}</dt>
                <dd class="admin-users-account">
                  <StatusPill v-bind="accountPill(selectedUser.active)" />
                  <span v-if="selectedUser.deactivation" class="admin-meta" data-testid="admin-users-deactivation">
                    {{ deactivationLine }}
                    <template v-if="selectedUser.deactivation.reason">
                      {{ t('adminUsers.reasonLine', { reason: selectedUser.deactivation.reason }) }}
                    </template>
                  </span>
                </dd>
                <template v-if="selectedUser.verificationState">
                  <dt>{{ t('adminUsers.kvVerification') }}</dt>
                  <dd>
                    <StatusPill
                      :label="t(verificationStateKey(selectedUser.verificationState))"
                      :tone="adminVerificationTone(selectedUser.verificationState)"
                    />
                  </dd>
                </template>
                <dt>{{ t('adminUsers.kvJoined') }}</dt>
                <dd>{{ formatDate(selectedUser.joinedAt) }}</dd>
              </dl>
            </section>

            <div class="admin-section admin-ruled">
              <button
                v-if="selectedUser.self === false && selectedUser.active"
                class="btn btn-secondary admin-users-access"
                type="button"
                data-testid="admin-users-deactivate"
                @click="openStatusDialog('deactivate')"
              >
                <IconGlyph name="lock" :size="16" />
                <span>{{ t('adminUsers.deactivate') }}</span>
              </button>
              <button
                v-else-if="selectedUser.self === false"
                class="btn btn-primary admin-users-access"
                type="button"
                data-testid="admin-users-reactivate"
                @click="openStatusDialog('reactivate')"
              >
                <IconGlyph name="unlock" :size="16" />
                <span>{{ t('adminUsers.reactivate') }}</span>
              </button>
              <p class="admin-meta">
                {{
                  selectedUser.self
                    ? t('adminUsers.selfNote')
                    : selectedUser.active
                      ? t('adminUsers.deactivateHint')
                      : t('adminUsers.reactivateHint')
                }}
              </p>
            </div>
          </section>

          <section class="panel admin-detail" aria-labelledby="admin-users-audit-title">
            <div class="admin-section-head">
              <h2 id="admin-users-audit-title" class="card-title">{{ t('adminUsers.recentAudit') }}</h2>
              <RouterLink
                class="btn btn-ghost btn-sm admin-link-action"
                :to="{ name: 'admin-audit-log', query: { q: selectedUser.fullName } }"
                :aria-label="t('adminUsers.viewAllAudit', { name: selectedUser.fullName })"
              >
                {{ t('adminUsers.viewAll') }}
                <IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </div>
            <ol v-if="selectedUser.recentAudit.length" class="row-list">
              <li v-for="entry in selectedUser.recentAudit" :key="entry.id">
                <RouterLink class="admin-home-row" :to="{ name: 'admin-audit-log', query: { entry: entry.id } }">
                  <span class="row-list-copy">
                    <span class="row-list-title">{{ actionLabel(entry.actionType) }}</span>
                    <span class="row-list-meta">
                      {{ formatDateTime(entry.createdAt) }}
                      <template v-if="entry.actorEmail"> · {{ entry.actorEmail }}</template>
                    </span>
                    <span v-if="entry.targetLabel" class="row-list-meta">{{ entry.targetLabel }}</span>
                  </span>
                  <IconGlyph class="admin-row-chevron" name="chevronRight" :size="16" />
                </RouterLink>
              </li>
            </ol>
            <p v-else class="admin-meta">{{ t('adminUsers.noAudit') }}</p>
          </section>
        </template>
      </div>
    </div>

    <AdminDecisionDialog
      :open="statusDialog !== null"
      :title="statusDialogTitle"
      :subtitle="statusDialogSubtitle"
      :confirm-label="statusDialog === 'deactivate' ? t('adminUsers.deactivate') : t('adminUsers.reactivate')"
      :confirm-icon="statusDialog === 'deactivate' ? 'lock' : 'unlock'"
      :tone="statusDialog === 'deactivate' ? 'danger' : 'primary'"
      :busy="saving"
      :error="dialogError"
      test-id="admin-users-status-dialog"
      @cancel="closeStatusDialog"
      @confirm="confirmStatus"
    >
      <div v-if="statusDialog === 'deactivate' && selectedUser" class="callout" data-testid="admin-users-impact">
        <IconGlyph name="alert" :size="16" />
        <div class="admin-callout-copy">
          <strong>{{ t('adminUsers.impactSignedOut', { name: selectedUser.fullName }) }}</strong>
          <span v-for="line in impactLines" :key="line">{{ line }}</span>
        </div>
      </div>
      <p v-else-if="statusDialog === 'reactivate' && selectedUser" class="callout">
        <IconGlyph name="unlock" :size="16" />
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
          data-autofocus
          :aria-invalid="Boolean(reasonError)"
          :aria-describedby="reasonError ? 'admin-users-reason-error' : 'admin-users-reason-hint'"
          data-testid="admin-users-reason"
        />
        <p id="admin-users-reason-hint" class="field-hint">{{ t('adminUsers.reasonHint') }}</p>
        <p v-if="reasonError" id="admin-users-reason-error" class="field-error" role="alert">{{ reasonError }}</p>
      </div>
    </AdminDecisionDialog>

    <AppOverlay
      :open="inviteOpen"
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
            data-autofocus
            placeholder="name@example.com"
            :aria-invalid="Boolean(inviteError)"
            :aria-describedby="inviteError ? 'admin-users-invite-error' : 'admin-users-invite-hint'"
            data-testid="admin-users-invite-email"
          />
          <p id="admin-users-invite-hint" class="field-hint">{{ t('adminUsers.inviteEmailHint') }}</p>
          <p v-if="inviteError" id="admin-users-invite-error" class="field-error" role="alert">{{ inviteError }}</p>
        </div>
      </form>
      <template #footer>
        <button class="btn btn-secondary" type="button" @click="inviteOpen = false">
          {{ t('adminUsers.cancel') }}
        </button>
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

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { adminUsersApi } from '@/api/ops'
import AdminDecisionDialog from '@/components/admin/AdminDecisionDialog.vue'
import AdminUsersTabs from '@/components/admin/AdminUsersTabs.vue'
import { accountTone, adminVerificationTone } from '@/components/admin/adminDisplay'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useAuditLabels } from '@/composables/useAuditLabels'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { UserRole } from '@/types/api'
import type { AdminUserDetail, AdminUserQuery } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { formatDate, formatDateTime, formatShortDate } from '@/utils/format'
import { roleKey, verificationStateKey } from '@/utils/opsLabels'
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
const { actionError, clearFeedback } = useActionFeedback()
const { toast, showToast } = useToast()
const detailRef = ref<HTMLElement | null>(null)

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
  // A new search starts over: the first match opens beside the list.
  clearSelection()
  void users.reload()
})

function clearFilters(): void {
  clearSearch()
  role.value = ''
  account.value = ''
}

function accountPill(active: boolean) {
  return {
    label: active ? t('adminUsers.accountActive') : t('adminUsers.accountDeactivated'),
    tone: accountTone(active),
  }
}

// ----- selected user (`?user=<id>`), shown in the side column -----

const selectedId = computed<number | null>(() => {
  const id = Number(route.query.user)
  return Number.isInteger(id) && id > 0 ? id : null
})

const detail = useResource<AdminUserDetail | null>(
  () => (selectedId.value ? adminUsersApi.get(selectedId.value) : Promise.resolve(null)),
  false,
)
const selectedUser = computed(() => detail.data.value)
const showDetail = computed(() => items.value.length > 0 || users.loading.value)

watch(
  selectedId,
  (id, previous) => {
    if (id !== previous) detail.data.value = null
    void detail.reload()
  },
  { immediate: true },
)

// Without a choice in the URL, the first user of the list opens beside it.
watch([items, selectedId], ([list, id]) => {
  const first = list[0]
  if (id === null && first) void router.replace({ query: { ...route.query, user: String(first.id) } })
})

async function selectUser(id: number): Promise<void> {
  await router.replace({ query: { ...route.query, user: String(id) } })
  await nextTick()
  // Under 1200 the person sits below the table: focusing them there scrolls them into view.
  const side = detailRef.value
  side?.focus({ preventScroll: side.getBoundingClientRect().top < window.innerHeight })
}

function clearSelection(): void {
  const query = { ...route.query }
  delete query.user
  void router.replace({ query })
}

const deactivationLine = computed(() => {
  const deactivation = selectedUser.value?.deactivation
  if (!deactivation) return ''
  return deactivation.actorName
    ? t('adminUsers.deactivatedBy', { date: formatDateTime(deactivation.at), actor: deactivation.actorName })
    : t('adminUsers.deactivatedOn', { date: formatDateTime(deactivation.at) })
})

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
const statusDialogSubtitle = computed(() =>
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
      showToast(
        t('adminUsers.deactivated', {
          name: user.fullName,
          sessions: result.cancelledSessions,
          items: result.itemsBackToDraft,
        }),
      )
    } else {
      detail.data.value = await adminUsersApi.reactivate(user.id, text)
      showToast(t('adminUsers.reactivated', { name: user.fullName }))
    }
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
    showToast(t('adminUsers.inviteSent', { email }))
    inviteOpen.value = false
    void invites.reload()
  } catch (failure) {
    inviteError.value = resolveApiError(failure, t('adminUsers.inviteFailed'))
  } finally {
    inviting.value = false
  }
}
</script>
