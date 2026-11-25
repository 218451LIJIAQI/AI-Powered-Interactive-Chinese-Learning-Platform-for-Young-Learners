<template>
  <form class="auth-form" novalidate @submit.prevent="submit">
    <!-- One line each: why the visitor is here (a notice), or what went wrong. A problem takes the notice's place. -->
    <p
      v-if="lockedMinutes"
      id="login-locked"
      class="callout is-warning"
      role="alert"
      :aria-live="lockTicked ? 'off' : 'assertive'"
      data-testid="auth-locked"
    >
      <IconGlyph name="lock" :size="18" />
      <span>{{ t('auth.locked', { minutes: lockedMinutes }, lockedMinutes) }}</span>
    </p>
    <!-- A deactivated account (an admin can reactivate it): the way out is the Help page form, open to visitors. -->
    <p
      v-else-if="deactivated"
      id="login-deactivated"
      class="callout is-danger"
      role="alert"
      data-testid="auth-deactivated"
    >
      <IconGlyph name="lock" :size="18" />
      <I18nT keypath="auth.deactivated" tag="span">
        <template #link>
          <RouterLink class="auth-notice-link" to="/help#contact" data-testid="auth-deactivated-contact">
            {{ t('auth.deactivatedLink') }}
          </RouterLink>
        </template>
      </I18nT>
    </p>
    <p v-else-if="loginError" id="login-error" class="callout is-danger" role="alert" data-testid="auth-error">
      <IconGlyph name="alert" :size="18" />
      <span>{{ loginError }}</span>
    </p>
    <p
      v-else-if="notice"
      class="callout"
      :class="{ 'is-success': notice.tone === 'success' }"
      role="status"
      data-testid="auth-notice"
    >
      <IconGlyph :name="notice.tone === 'success' ? 'correct' : 'info'" :size="18" />
      <span>{{ t(notice.message) }}</span>
    </p>

    <div class="field">
      <label class="field-label" for="login-email">
        {{ t('auth.email') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <input
        id="login-email"
        v-model="form.email"
        class="input"
        type="email"
        name="username"
        autocomplete="username"
        required
        maxlength="150"
        :placeholder="t('auth.emailPlaceholder')"
        :aria-invalid="loginFailure === 'fields' && !addressValid"
        :aria-describedby="describedBy"
      />
    </div>

    <div class="field">
      <label class="field-label" for="login-password">
        {{ t('auth.password') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <PasswordInput
        id="login-password"
        v-model="form.password"
        name="password"
        autocomplete="current-password"
        required
        :placeholder="t('auth.passwordPlaceholder')"
        :aria-invalid="loginFailure === 'fields' && isBlankPassword(form.password)"
        :aria-describedby="describedBy"
      />
    </div>

    <div class="auth-form-row">
      <label class="choice auth-choice">
        <input v-model="form.keepSignedIn" type="checkbox" data-testid="keep-signed-in" />
        <span>{{ t('auth.keepSignedIn') }}</span>
      </label>
      <RouterLink class="btn btn-link auth-text-link" to="/forgot-password" @click="rememberRecoveryEmail">
        {{ t('auth.forgotPassword') }}
      </RouterLink>
    </div>

    <!-- Paused after too many failed attempts, or refused for a deactivated account, until another address is typed:
       the ways on stay open (Forgot password?, Create account, the logo, contact us). -->
    <button
      class="btn btn-primary btn-lg btn-block"
      type="submit"
      :disabled="Boolean(blockedNoticeId) || auth.loading"
      :aria-busy="auth.loading"
      :aria-describedby="blockedNoticeId"
      data-testid="login-submit"
    >
      <span>{{ auth.loading ? t('auth.signingIn') : t('common.signIn') }}</span>
      <IconGlyph name="chevronRight" :size="18" />
    </button>
  </form>
</template>

<script setup lang="ts">
import { isCancel } from 'axios'
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, shallowRef, watch } from 'vue'
import { I18nT } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PasswordInput from '@/components/common/PasswordInput.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { authMessages } from '@/plugins/authMessages'
import { useAuthStore } from '@/stores/auth'
import { apiErrorStatus, hasServerMessage, resolveApiError } from '@/utils/apiError'
import { signedInDestination } from '@/utils/appRoute'
import { isValidEmail } from '@/utils/email'
import { isBlankPassword, isWithinPasswordByteLimit } from '@/utils/password'
import { getSessionStorage, safeSetItem, storageKeys } from '@/utils/storage'
import { SERVER_MESSAGE } from '@/utils/serverMessages'

/** The sign-in lockout lasts 15 minutes; the server's Retry-After header says how much of it is left. */
const DEFAULT_LOCK_SECONDS = 15 * 60

const NOTICES = {
  created: { message: 'auth.createdSignIn', tone: 'success' },
  reset: { message: 'auth.noticeReset', tone: 'success' },
  email: { message: 'auth.noticeEmail', tone: 'success' },
  invite: { message: 'auth.noticeInvite', tone: 'success' },
  expired: { message: 'auth.noticeExpired', tone: 'info' },
} as const

const props = withDefaults(
  defineProps<{
    /** Pre-filled address from registration fallback or an ended session. */
    initialEmail?: string
    /** True when arriving directly from registration after automatic sign-in failed. */
    accountCreated?: boolean
  }>(),
  { initialEmail: '', accountCreated: false },
)

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ auth: authMessages })

const form = reactive({
  email: props.initialEmail,
  password: '',
  keepSignedIn: false,
})
const address = computed(() => form.email.trim().toLowerCase())
const addressValid = computed(() => isValidEmail(address.value))
const loginFailure = shallowRef<'fields' | 'credentials' | { reason: unknown } | null>(null)
const loginError = computed(() => {
  const failure = loginFailure.value
  if (!failure) return null
  if (failure === 'credentials') return t('auth.wrongCredentials')
  if (failure === 'fields') {
    if (!addressValid.value) return t('auth.emailInvalid')
    return isBlankPassword(form.password) ? t('auth.passwordRequired') : null
  }
  return resolveApiError(failure.reason, t('auth.signInFailed'))
})
const lockedMinutes = ref<number | null>(null)
const lockTicked = ref(false)
const deactivated = ref(false)
let unlockTimer: ReturnType<typeof setTimeout> | undefined
let shown = true
let disposed = false

const notice = computed(() => {
  if (props.accountCreated) return NOTICES.created
  if (route.query.reset === 'done') return NOTICES.reset
  if (route.query.email === 'changed') return NOTICES.email
  if (route.query.invite === 'done') return NOTICES.invite
  if (route.query.reason === 'expired') return NOTICES.expired
  return null
})

const blockedNoticeId = computed(() => {
  if (lockedMinutes.value) return 'login-locked'
  return deactivated.value ? 'login-deactivated' : undefined
})
const describedBy = computed(() => blockedNoticeId.value ?? (loginError.value ? 'login-error' : undefined))

// The lockout and the refusal belong to one e-mail address; another address may be tried.
watch(address, () => {
  clearTimeout(unlockTimer)
  lockedMinutes.value = null
  deactivated.value = false
})

// The page keeps this form while Create account is open: an address handed over later (an account just created) fills
// it in, and leaving the tab drops the last error, as the typed address and a sign-in pause stay.
watch(
  () => props.initialEmail,
  (email) => {
    if (email && email !== form.email) {
      form.email = email
      form.password = ''
    }
  },
)
onActivated(() => {
  shown = true
})
onDeactivated(() => {
  shown = false
  loginFailure.value = null
})

/** "Forgot password?" takes the address typed here along (session storage, read once). */
function rememberRecoveryEmail(): void {
  if (addressValid.value) safeSetItem(storageKeys.recoveryEmail, address.value, getSessionStorage())
}

function retryAfterSeconds(reason: unknown): number {
  const header = (reason as { response?: { headers?: Record<string, unknown> } })?.response?.headers?.['retry-after']
  const seconds = Number(header)
  return Number.isFinite(seconds) && seconds > 0 ? seconds : DEFAULT_LOCK_SECONDS
}

/** Refresh the displayed minutes at each boundary and unlock at the server's deadline. */
function pauseSignIn(seconds: number): void {
  clearTimeout(unlockTimer)
  lockTicked.value = false
  const deadline = Date.now() + seconds * 1000
  function updateRemaining(ticked: boolean): void {
    const remaining = deadline - Date.now()
    if (remaining <= 0) {
      lockedMinutes.value = null
      lockTicked.value = false
      return
    }
    lockTicked.value = ticked
    lockedMinutes.value = Math.ceil(remaining / 60_000)
    unlockTimer = setTimeout(() => updateRemaining(true), remaining % 60_000 || 60_000)
  }
  updateRemaining(false)
}

onBeforeUnmount(() => {
  shown = false
  disposed = true
  clearTimeout(unlockTimer)
})

function focusField(id: 'login-email' | 'login-password'): void {
  void nextTick(() => document.getElementById(id)?.focus())
}

async function submit(): Promise<void> {
  if (blockedNoticeId.value || auth.loading) return
  loginFailure.value = null
  const currentAddress = address.value
  const password = form.password
  if (!addressValid.value || isBlankPassword(password)) {
    loginFailure.value = 'fields'
    focusField(addressValid.value ? 'login-password' : 'login-email')
    return
  }
  if (!isWithinPasswordByteLimit(password)) {
    form.password = ''
    loginFailure.value = 'credentials'
    focusField('login-password')
    return
  }
  try {
    await auth.login(currentAddress, password, form.keepSignedIn)
  } catch (reason) {
    if (disposed || currentAddress !== address.value || isCancel(reason)) return
    const status = apiErrorStatus(reason)
    // Clear the typed password only when credentials/account/rate-limit rejected it; preserve it on transient 5xx/network hiccups.
    if ((status === 401 || status === 403 || status === 429) && form.password === password) form.password = ''
    // The server sends 403 ACCOUNT_UNAVAILABLE only after the password matched, avoiding account-state enumeration.
    if (status === 403 && hasServerMessage(reason, SERVER_MESSAGE.ACCOUNT_UNAVAILABLE)) {
      deactivated.value = true
      if (shown) focusField('login-email')
      return
    }
    if (status === 429) {
      pauseSignIn(retryAfterSeconds(reason))
      if (shown) focusField('login-email')
      return
    }
    if (!shown) return
    loginFailure.value = status === 401 ? 'credentials' : { reason }
    focusField('login-password')
    return
  }
  if (disposed) return
  try {
    await router.push(signedInDestination(route.query.redirect, auth.roles))
  } catch (reason) {
    if (shown) loginFailure.value = { reason }
  }
}
</script>
