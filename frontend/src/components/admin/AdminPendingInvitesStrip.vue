<template>
  <DataNotice :error="invites.error.value" :error-title="t('adminUsers.invitesLoadFailed')" @retry="retryInvites" />
  <div
    v-if="!invites.error.value && pendingInvites.length"
    ref="stripRef"
    class="callout admin-users-invites"
    data-testid="admin-users-invites"
  >
    <IconGlyph name="mail" :size="16" />
    <div class="admin-list-head">
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
  <p
    v-if="actionError"
    ref="errorRef"
    class="field-error"
    role="alert"
    tabindex="-1"
    data-testid="admin-users-invite-error"
  >
    {{ actionError }}
  </p>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

import { adminUsersApi } from '@/api/ops'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import type { AdminInvite } from '@/types/ops'
import { apiErrorStatus, hasServerMessage, resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import { SERVER_MESSAGE } from '@/utils/serverMessages'

interface InviteChange {
  id: number
  action: 'resend' | 'cancel' | 'restore'
}

const emit = defineEmits<{
  toast: [message: string, action?: { readonly label: string; readonly run: () => void }]
  hideToast: []
  /** Emitted when a 409 (already accepted) or 400 (already registered) shows the invitee already has an account so the parent can refresh the accounts list. */
  accepted: []
}>()

const { t } = useLocalMessages({ adminUsers: adminUsersMessages })

const isUnmounted = useIsUnmounted()

const invites = useResource(adminUsersApi.invites)
const pendingInvites = computed(() => invites.data.value ?? [])
const busy = ref<InviteChange | null>(null)
const actionError = ref<string | null>(null)
const stripRef = ref<HTMLElement | null>(null)
const errorRef = ref<HTMLElement | null>(null)

function retryInvites(): void {
  invites.data.value = null
  void invites.reload()
}

/** Called by the parent after Invite admin sends a fresh invitation. */
function reloadAfterInvite(): void {
  actionError.value = null
  void invites.reload()
}

defineExpose({ reloadAfterInvite })

async function restoreFocusIfDropped(): Promise<void> {
  await nextTick()
  if (isUnmounted()) return
  const active = document.activeElement
  if (active && active !== document.body && !(active instanceof HTMLElement && active.matches(':disabled'))) return
  const firstAction = stripRef.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')
  if (firstAction) firstAction.focus({ preventScroll: true })
  else errorRef.value?.focus({ preventScroll: true })
}

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
    // Gone (404), conflict (409: accepted or expired), or already registered (400): refresh the strip.
    const status = apiErrorStatus(failure)
    const alreadyRegistered = status === 400 && hasServerMessage(failure, SERVER_MESSAGE.EMAIL_ALREADY_REGISTERED)
    const alreadyAccepted = status === 409 && hasServerMessage(failure, SERVER_MESSAGE.INVITE_ACCEPTED)
    if (status === 404 || status === 409 || alreadyRegistered) void invites.reload()
    // Only an accepted/registered invite adds an account to the users table; an expired invite does not.
    if (alreadyAccepted || alreadyRegistered) emit('accepted')
  } finally {
    if (!isUnmounted()) {
      busy.value = null
      void restoreFocusIfDropped()
    }
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
