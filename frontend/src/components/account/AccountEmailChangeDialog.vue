<template>
  <AppOverlay
    :open="open"
    :title="t('account.changeEmail')"
    :busy="sending"
    test-id="account-email-dialog"
    @close="emit('close')"
  >
    <form id="account-email-form" class="form-grid" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="account-new-email">
          {{ t('account.newEmail') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="account-new-email"
          v-model="form.newEmail"
          data-autofocus
          :readonly="sending"
          class="input"
          type="email"
          autocomplete="email"
          maxlength="150"
          required
          :aria-invalid="Boolean(newEmailError)"
          :aria-describedby="newEmailError ? 'account-new-email-error' : undefined"
          @blur="normalizedEmail && checkNewEmail()"
        />
        <p v-if="newEmailError" id="account-new-email-error" class="field-error" role="alert">
          {{ t(newEmailError) }}
        </p>
      </div>
      <div class="field">
        <label for="account-email-password">
          {{ t('account.passwordToConfirm') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="account-email-password"
          v-model="form.password"
          :readonly="sending"
          class="input"
          type="password"
          autocomplete="current-password"
          required
          :placeholder="t('account.passwordPlaceholder')"
        />
      </div>
      <div v-if="error" class="alert alert-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>{{ error }}</span>
      </div>
    </form>
    <template #footer>
      <button class="btn btn-secondary" type="button" :disabled="sending" @mousedown.prevent @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-primary"
        type="submit"
        form="account-email-form"
        :disabled="sending || !canSend"
        :aria-busy="sending"
        data-testid="account-email-send"
      >
        {{ sending ? t('common.sending') : t('account.sendLink') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { accountOpsApi } from '@/api/ops'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { accountMessages } from '@/plugins/accountMessages'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { isBlankPassword } from '@/utils/password'

const props = defineProps<{
  open: boolean
  currentEmail: string | null
  disabled: boolean
}>()
const emit = defineEmits<{ close: []; requested: [pendingEmail: string] }>()

const { t } = useLocalMessages({ account: accountMessages })

const isUnmounted = useIsUnmounted()

const sending = ref(false)
const error = ref<string | null>(null)
const form = reactive({ newEmail: '', password: '' })
const normalizedEmail = computed(() => form.newEmail.trim().toLowerCase())

function newEmailIssue(address: string): string | null {
  if (!isValidEmail(address)) return 'account.newEmailInvalid'
  return address === props.currentEmail?.toLowerCase() ? 'account.newEmailSame' : null
}

const {
  error: newEmailError,
  validate: checkNewEmail,
  reset: resetNewEmail,
} = useFieldCheck(normalizedEmail, newEmailIssue)
const canSend = computed(
  () =>
    !props.disabled &&
    Boolean(props.currentEmail) &&
    !newEmailIssue(normalizedEmail.value) &&
    !isBlankPassword(form.password),
)

watch(
  () => props.open,
  (opened) => {
    if (!opened) return
    Object.assign(form, { newEmail: '', password: '' })
    resetNewEmail()
    error.value = null
  },
)

async function submit(): Promise<void> {
  if (sending.value || !canSend.value) return
  const version = getAuthSessionVersion()
  sending.value = true
  error.value = null
  try {
    const result = await accountOpsApi.requestEmailChange({
      newEmail: normalizedEmail.value,
      currentPassword: form.password,
    })
    if (isUnmounted() || version !== getAuthSessionVersion()) return
    emit('requested', result.pendingEmail)
  } catch (reason) {
    if (!isUnmounted() && version === getAuthSessionVersion())
      error.value = resolveApiError(reason, t('account.emailError'))
  } finally {
    if (!isUnmounted()) sending.value = false
  }
}
</script>
