<template>
  <AppOverlay
    :title="t('adminUsers.inviteAdmin')"
    :subtitle="t('adminUsers.inviteIntro')"
    :busy="inviting"
    test-id="admin-users-invite-drawer"
    @close="emit('close')"
  >
    <form id="admin-users-invite-form" class="form-grid" novalidate @submit.prevent="sendInvite">
      <div class="field">
        <label class="field-label" for="admin-users-invite-email">
          {{ t('adminUsers.inviteEmail') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="admin-users-invite-email"
          ref="inviteField"
          v-model="inviteEmail"
          class="input"
          type="email"
          autocomplete="off"
          maxlength="150"
          required
          data-autofocus
          :disabled="inviting"
          :placeholder="EMAIL_EXAMPLE"
          :aria-invalid="Boolean(inviteProblem)"
          :aria-describedby="
            inviteProblem ? 'admin-users-invite-error admin-users-invite-hint' : 'admin-users-invite-hint'
          "
          data-testid="admin-users-invite-email"
          @blur="checkInviteOnBlur"
        />
        <p id="admin-users-invite-hint" class="field-hint">{{ t('adminUsers.inviteEmailHint') }}</p>
        <p v-if="inviteProblem" id="admin-users-invite-error" class="field-error" role="alert">
          {{ inviteProblem }}
        </p>
      </div>
    </form>
    <template #footer>
      <!-- A press on a footer button leaves focus in the field: its check on blur would move the footer mid-click. -->
      <button class="btn btn-secondary" type="button" :disabled="inviting" @mousedown.prevent @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-primary"
        type="submit"
        form="admin-users-invite-form"
        :disabled="inviting || Boolean(inviteError)"
        :aria-busy="inviting"
        data-testid="admin-users-send-invite"
        @mousedown.prevent
      >
        <IconGlyph name="send" :size="16" />
        <span>{{ t('adminUsers.sendInvite') }}</span>
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { adminUsersApi } from '@/api/ops'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminUsersMessages } from '@/plugins/adminUsersMessages'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'

/**
 * Invite admin (A2.07): the e-mail address to invite, checked when the field is left and when the invite is sent;
 * `sent` names the address the invite went to. The page mounts it for one opening, so each opening starts empty.
 */
const emit = defineEmits<{ close: []; sent: [email: string] }>()

/** The field's placeholder, and the shape its error asks for ("@" cannot be written in a message). */
const EMAIL_EXAMPLE = 'name@example.com'

const { t } = useLocalMessages({ adminUsers: adminUsersMessages })

const inviteEmail = ref('')
const inviteField = ref<HTMLInputElement | null>(null)
/** What is wrong with the address as typed: shown under the field, and Send invite waits until it is fixed. */
const {
  error: inviteError,
  validate: validateInvite,
  validateOnBlur: checkInviteOnBlur,
} = useFieldCheck(inviteEmail, (value) => {
  const email = value.trim()
  if (!email) return t('adminUsers.inviteEmailMissing')
  return isValidEmail(email) ? null : t('adminUsers.inviteEmailInvalid', { example: EMAIL_EXAMPLE })
})
/** Why the server did not send the invite; the same address can be sent again. */
const inviteFailure = ref<string | null>(null)
const inviteProblem = computed(() => inviteError.value ?? inviteFailure.value)
const inviting = ref(false)
const isUnmounted = useIsUnmounted()

watch(inviteEmail, () => {
  inviteFailure.value = null
})

async function sendInvite(): Promise<void> {
  if (inviting.value) return
  inviteFailure.value = null
  if (!validateInvite()) {
    inviteField.value?.focus()
    return
  }
  const email = inviteEmail.value.trim().toLowerCase()
  const sessionVersion = getAuthSessionVersion()
  inviting.value = true
  try {
    await adminUsersApi.invite(email)
    if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
    emit('sent', email)
  } catch (failure) {
    if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
    inviteFailure.value = resolveApiError(failure, t('adminUsers.inviteFailed'))
  } finally {
    if (!isUnmounted()) inviting.value = false
  }
}
</script>
