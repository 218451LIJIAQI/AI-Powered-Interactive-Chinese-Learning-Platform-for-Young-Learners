<template>
  <section class="panel account-card" aria-labelledby="account-password-title">
    <h2 id="account-password-title" class="card-title">{{ t('account.passwordTitle') }}</h2>
    <form id="account-password-form" class="form-grid" novalidate @submit.prevent="changePassword">
      <div v-for="field in PASSWORD_FIELDS" :key="field.key" class="field">
        <label :for="`account-${field.key}`">
          {{ t(field.label) }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`account-${field.key}`"
          v-model="password[field.key]"
          class="input"
          :type="showPasswords ? 'text' : 'password'"
          :readonly="changingPassword"
          :autocomplete="field.autocomplete"
          spellcheck="false"
          autocapitalize="off"
          autocorrect="off"
          required
          :aria-invalid="Boolean(passwordErrors[field.key])"
          :aria-describedby="describedBy(field.key)"
        />
        <p v-if="field.key === 'newPassword'" id="account-newPassword-hint" class="field-hint">
          {{ t('account.newPasswordHint') }}
        </p>
        <p v-if="passwordErrors[field.key]" :id="`account-${field.key}-error`" class="field-error" role="alert">
          {{ t(passwordErrors[field.key]!) }}
        </p>
      </div>
      <p class="callout">
        <IconGlyph name="info" :size="18" />
        <span>{{ t('account.passwordSignOutNote') }}</span>
      </p>
      <div v-if="passwordError" class="callout is-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>{{ passwordError }}</span>
      </div>
      <div class="account-password-actions">
        <label class="choice">
          <input v-model="showPasswords" type="checkbox" />
          <span>{{ t('account.showPasswords') }}</span>
        </label>
        <button
          class="btn btn-primary"
          type="submit"
          :disabled="changingPassword || !canChangePassword"
          :aria-busy="changingPassword"
          data-testid="account-change-password"
        >
          {{ changingPassword ? t('account.changingPassword') : t('account.changePassword') }}
        </button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { accountOpsApi } from '@/api/ops'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { accountMessages } from '@/plugins/accountMessages'
import { useAuthStore } from '@/stores/auth'
import { hasServerMessage, resolveApiError } from '@/utils/apiError'
import { SERVER_MESSAGE } from '@/utils/serverMessages'
import {
  isBlankPassword,
  validatePasswordChange,
  type PasswordChangeErrors,
  type PasswordChangeForm,
} from '@/utils/password'

const PASSWORD_FIELDS = [
  { key: 'currentPassword', label: 'account.currentPassword', autocomplete: 'current-password' },
  { key: 'newPassword', label: 'account.newPassword', autocomplete: 'new-password' },
  { key: 'confirmPassword', label: 'account.confirmPassword', autocomplete: 'new-password' },
] as const satisfies readonly { key: keyof PasswordChangeForm; label: string; autocomplete: string }[]

const emit = defineEmits<{ changed: [] }>()

const { t } = useLocalMessages({ account: accountMessages })
const auth = useAuthStore()

const isUnmounted = useIsUnmounted()

const password = reactive<PasswordChangeForm>({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordErrors = ref<PasswordChangeErrors>({})
const passwordError = ref<string | null>(null)
const showPasswords = ref(false)
const changingPassword = ref(false)

/** Change password waits for all three fields, and for another current password after a wrong one. */
const canChangePassword = computed(
  () =>
    passwordErrors.value.currentPassword !== 'account.currentPasswordWrong' &&
    PASSWORD_FIELDS.every((field) => !isBlankPassword(password[field.key])),
)

watch([() => password.currentPassword, () => password.newPassword, () => password.confirmPassword], (next, prev) => {
  if (passwordError.value === null && !Object.values(passwordErrors.value).some(Boolean)) return
  passwordError.value = null
  const updated = { ...passwordErrors.value }
  if (next[0] !== prev[0]) {
    updated.currentPassword = undefined
    if (updated.newPassword === 'account.sameAsCurrent' && password.newPassword !== password.currentPassword) {
      updated.newPassword = undefined
    }
  }
  if (next[1] !== prev[1]) {
    updated.newPassword = undefined
    if (password.confirmPassword === password.newPassword) updated.confirmPassword = undefined
  }
  if (next[2] !== prev[2]) updated.confirmPassword = undefined
  passwordErrors.value = updated
})

function describedBy(key: keyof PasswordChangeForm): string | undefined {
  const ids = [
    key === 'newPassword' ? 'account-newPassword-hint' : '',
    passwordErrors.value[key] ? `account-${key}-error` : '',
  ]
  return ids.filter(Boolean).join(' ') || undefined
}

async function changePassword(): Promise<void> {
  if (changingPassword.value || !canChangePassword.value) return
  passwordErrors.value = validatePasswordChange(password)
  passwordError.value = null
  const invalidField = PASSWORD_FIELDS.find(({ key }) => passwordErrors.value[key])
  if (invalidField) {
    await nextTick()
    document.getElementById(`account-${invalidField.key}`)?.focus()
    return
  }

  changingPassword.value = true
  const version = getAuthSessionVersion()
  try {
    const response = await accountOpsApi.changePassword({ ...password })
    // The rotated token is needed by the session even if this page has been left.
    if (version !== getAuthSessionVersion() || !auth.replaceAccessToken(response.token, version) || isUnmounted())
      return
    Object.assign(password, { currentPassword: '', newPassword: '', confirmPassword: '' })
    showPasswords.value = false
    emit('changed')
  } catch (reason) {
    if (isUnmounted() || version !== getAuthSessionVersion()) return
    if (hasServerMessage(reason, SERVER_MESSAGE.WRONG_CURRENT_PASSWORD)) {
      passwordErrors.value.currentPassword = 'account.currentPasswordWrong'
      await nextTick()
      document.getElementById('account-currentPassword')?.focus()
    } else {
      passwordError.value = resolveApiError(reason, t('account.passwordError'))
    }
  } finally {
    if (!isUnmounted()) changingPassword.value = false
  }
}
</script>
