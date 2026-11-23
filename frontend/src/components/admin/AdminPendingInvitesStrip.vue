<template>
  <DataNotice :error="invites.error.value" :error-title="t('adminUsers.invitesLoadFailed')">
    <template #actions>
      <button type="button" class="btn btn-secondary btn-sm" @click="invites.reload">
        {{ t('common.retry') }}
      </button>
    </template>
  </DataNotice>
  <div
    v-if="!invites.error.value && pendingInvites.length"
    class="callout admin-users-invites"
    data-testid="admin-users-invites"
  >
    <IconGlyph name="mail" :size="16" />
    <div class="admin-users-invites-copy">
      <strong>{{ t('adminUsers.invitesPending', { count: pendingInvites.length }, pendingInvites.length) }}</strong>
      <span v-for="invite in pendingInvites" :key="invite.id" class="admin-users-invite">
        <span class="admin-meta">
          {{ t('adminUsers.inviteRow', { email: invite.email, sent: formatDateTime(invite.sentAt) }) }}
        </span>
        <span class="btn-group">
          <button
            class="btn btn-ghost btn-sm"
            type="button"
            :disabled="busy !== null"
            :aria-busy="busy?.id === invite.id && busy.action === 'resend'"
            :aria-label="t('adminUsers.resendInviteLabel', { email: invite.email })"
            data-testid="admin-users-resend-invite"
            @click="resendInvite(invite)"
          >
            {{ t('adminUsers.resendInvite') }}
          </button>
          <button
            class="btn btn-ghost btn-sm"
            type="button"
            :disabled="busy !== null"
            :aria-busy="busy?.id === invite.id && busy.action === 'cancel'"
            :aria-label="t('adminUsers.cancelInviteLabel', { email: invite.email })"
            data-testid="admin-users-cancel-invite"
            @click="cancelInvite(invite)"
          >
            {{ t('adminUsers.cancelInvite') }}
          </button>
        </span>
      </span>
    </div>
  </div>
  <!-- Under the strip, not in it: an invite accepted meanwhile leaves the strip, and the reason stays. -->
  <p v-if="actionError" class="field-error" role="alert" data-testid="admin-users-invite-error">
    {{ actionError }}
  </p>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { adminUsersApi } from '@/api/ops'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import type { AdminInvite } from '@/types/ops'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'

interface InviteChange {
  id: number
  action: 'resend' | 'cancel' | 'restore'
}

const emit = defineEmits<{
  toast: [message: string, action?: { readonly label: string; readonly run: () => void }]
  hideToast: []
  /** Emitted when a 409 shows an invite was already accepted so the parent can refresh the accounts list. */
  accepted: []
}>()

const { t } = useLocalMessages({ adminUsers: adminUsersMessages })

const isUnmounted = useIsUnmounted()

const invites = useResource(adminUsersApi.invites)
const pendingInvites = computed(() => invites.data.value ?? [])
const busy = ref<InviteChange | null>(null)
const actionError = ref<string | null>(null)

/** Called by the parent after Invite admin sends a fresh invitation. */
function reloadAfterInvite(): void {
  actionError.value = null
  void invites.reload()
}

defineExpose({ reloadAfterInvite })

async function changeInvite(
  pending: InviteChange,
  change: () => Promise<unknown>,
  done: () => void,
  failed: string,
): Promise<void> {
  if (busy.value !== null) return
  emit('hideToast')
  busy.value = pending
  actionError.value = null
  try {
    await change()
    if (isUnmounted()) return
    await invites.reload()
    if (isUnmounted()) return
    done()
  } catch (failure) {
    if (isUnmounted()) return
    actionError.value = resolveApiError(failure, failed)
    // Gone (404) or already accepted (409): the strip was out of date. An accepted invite is a new admin in the list.
    const status = apiErrorStatus(failure)
    if (status === 404 || status === 409) void invites.reload()
    if (status === 409) emit('accepted')
  } finally {
    if (!isUnmounted()) busy.value = null
  }
}

function resendInvite(invite: AdminInvite): Promise<void> {
  return changeInvite(
    { id: invite.id, action: 'resend' },
    () => adminUsersApi.invite(invite.email),
    () => emit('toast', t('adminUsers.inviteResent')),
    t('adminUsers.resendInviteFailed'),
  )
}

/** Cancelling runs at once: its link stops working, and Undo in the toast brings the same link back (X1.19). */
function cancelInvite(invite: AdminInvite): Promise<void> {
  return changeInvite(
    { id: invite.id, action: 'cancel' },
    () => adminUsersApi.cancelInvite(invite.id),
    () =>
      emit('toast', t('adminUsers.inviteCancelled'), {
        label: t('common.undo'),
        run: () => void restoreInvite(invite.id),
      }),
    t('adminUsers.cancelInviteFailed'),
  )
}

function restoreInvite(id: number): Promise<void> {
  return changeInvite(
    { id, action: 'restore' },
    () => adminUsersApi.restoreInvite(id),
    () => emit('toast', t('adminUsers.inviteRestored')),
    t('adminUsers.restoreInviteFailed'),
  )
}
</script>
