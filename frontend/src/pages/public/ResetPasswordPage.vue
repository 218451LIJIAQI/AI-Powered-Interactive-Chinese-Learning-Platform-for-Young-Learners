<template>
  <AuthShell>
    <div v-if="state === 'checking'" class="auth-checking" role="status">
      <h1 class="sr-only">{{ isInvite ? t('recovery.inviteTitle') : t('recovery.resetTitle') }}</h1>
      <span class="spinner" aria-hidden="true" />
      <p>{{ t('recovery.checkingLink') }}</p>
    </div>

    <div v-else-if="state === 'invalid'" class="auth-form">
      <h1 class="auth-title">{{ isInvite ? t('recovery.inviteExpiredTitle') : t('recovery.expiredTitle') }}</h1>
      <p class="callout is-warning" role="alert" data-testid="link-invalid">
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{ invalidMessage }}</strong>
        </span>
      </p>
      <RouterLink v-if="!isInvite" class="btn btn-primary btn-lg btn-block" to="/forgot-password">
        {{ t('recovery.requestNewLink') }}
        <IconGlyph name="chevronRight" :size="18" />
      </RouterLink>
    </div>

    <!-- The check itself failed (offline, busy server): the link may well still work. -->
    <div v-else-if="state === 'unchecked'" class="auth-form">
      <h1 class="auth-title">{{ t('recovery.checkFailedTitle') }}</h1>
      <p class="callout is-danger" role="alert" data-testid="link-unchecked">
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{ resolveApiError(checkFailure, t('recovery.checkFailed')) }}</strong>
        </span>
      </p>
      <button class="btn btn-primary btn-lg btn-block" type="button" data-testid="link-retry" @click="checkLink">
        {{ t('common.retry') }}
      </button>
    </div>

    <form v-else class="auth-form" novalidate @submit.prevent="submit">
      <h1 class="auth-title">{{ isInvite ? t('recovery.inviteTitle') : t('recovery.resetTitle') }}</h1>
      <p class="field-hint auth-hint" data-testid="link-email">
        <IconGlyph name="mail" :size="14" />
        {{ isInvite ? t('recovery.inviteFor', { email: linkEmail }) : t('recovery.resetFor', { email: linkEmail }) }}
      </p>

      <p v-if="submitFailure" class="callout is-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{
            resolveApiError(submitFailure.reason, isInvite ? t('recovery.inviteFailed') : t('recovery.resetFailed'))
          }}</strong>
        </span>
      </p>

      <div v-if="isInvite" class="field">
        <label class="field-label" for="invite-full-name">
          {{ t('recovery.fullName') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="invite-full-name"
          v-model="fullName"
          class="input"
          autocomplete="name"
          required
          maxlength="120"
          :placeholder="t('recovery.fullNamePlaceholder')"
          :aria-invalid="Boolean(nameError)"
          :aria-describedby="nameError ? 'invite-name-error' : undefined"
        />
        <p v-if="nameError" id="invite-name-error" class="field-error" role="alert">
          <IconGlyph name="alert" :size="14" />{{ t(nameError) }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="reset-password">
          {{ t('recovery.newPassword') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <PasswordInput
          id="reset-password"
          v-model="password"
          autocomplete="new-password"
          required
          :placeholder="t('recovery.newPasswordPlaceholder')"
          :aria-invalid="tooLong"
          :aria-describedby="tooLong ? 'reset-too-long reset-readiness' : 'reset-readiness'"
        />
        <p v-if="tooLong" id="reset-too-long" class="field-error">
          <IconGlyph name="alert" :size="14" />{{ t('recovery.passwordTooLong') }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="reset-confirm">
          {{ t('recovery.confirmNewPassword') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <PasswordInput
          id="reset-confirm"
          v-model="confirmPassword"
          autocomplete="new-password"
          required
          :placeholder="t('recovery.confirmPlaceholder')"
          aria-describedby="reset-readiness"
        />
      </div>

      <!-- The two rules under the fields they judge; Reset password works once both are met. -->
      <ul id="reset-readiness" class="auth-checks" :aria-label="t('recovery.readiness')" aria-live="polite">
        <li v-for="check in checks" :key="check.id" :class="{ 'is-met': check.met }">
          <span class="auth-check-mark" aria-hidden="true">
            <IconGlyph v-if="check.met" name="check" :size="12" />
          </span>
          <span
            >{{ check.label
            }}<span class="sr-only"> — {{ t(check.met ? 'recovery.ruleMet' : 'recovery.rulePending') }}</span></span
          >
        </li>
      </ul>

      <button
        class="btn btn-primary btn-lg btn-block"
        type="submit"
        :disabled="!passwordReady || submitting"
        :aria-busy="submitting"
      >
        <span>{{ submitLabel }}</span>
        <IconGlyph name="chevronRight" :size="18" />
      </button>
    </form>

    <p v-if="state !== 'checking'" class="auth-switch">
      <span v-if="!isInvite">{{ t('recovery.rememberPassword') }}</span>
      <RouterLink class="btn btn-link auth-text-link" to="/auth">{{ t('recovery.backToSignIn') }}</RouterLink>
    </p>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { publicOpsApi } from '@/api/ops'
import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import AuthShell from '@/components/public/AuthShell.vue'
import PasswordInput from '@/components/public/PasswordInput.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { useAuthStore } from '@/stores/auth'
import { appLocale } from '@/plugins/i18n'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { isWithinPasswordByteLimit, meetsPasswordMinimum } from '@/utils/password'
import { firstQueryText, withoutQueryKeys } from '@/utils/queryText'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ recovery: recoveryMessages })

// An admin invite opens this page with ?invite=…, a password reset with ?token=…
const inviteToken = firstQueryText(route.query.invite)
const token = inviteToken || firstQueryText(route.query.token)
const isInvite = Boolean(inviteToken)

// The link token leaves the address bar and history once it has been read.
if (route.query.invite || route.query.token) {
  void router.replace({ name: 'reset-password', query: withoutQueryKeys(route.query, 'invite', 'token') })
}

const state = ref<'checking' | 'ready' | 'invalid' | 'unchecked'>('checking')
const linkEmail = ref('')
/** Why the link could not be checked (state `unchecked`): the server's or the connection's message. */
const checkFailure = shallowRef<unknown>()
const fullName = ref('')
// The name is checked when the form is sent; typing one clears the message.
const { error: nameError, validate: validateName } = useFieldCheck(fullName, (name) =>
  name.trim() ? null : 'recovery.nameRequired',
)
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const submitFailure = shallowRef<{ reason: unknown } | null>(null)
const isUnmounted = useIsUnmounted()
let checkVersion = 0

/** Why the link no longer works (state `invalid`). */
const invalidMessage = computed(() => {
  if (!token) return t('recovery.missingLink')
  // The server's refusal of a used or old link names its token; say it in the learner's words.
  return isInvite ? t('recovery.inviteExpiredCopy') : t('recovery.expiredHint')
})
const tooLong = computed(() => !isWithinPasswordByteLimit(password.value))
const meetsMinimum = computed(() => meetsPasswordMinimum(password.value))
const passwordsMatch = computed(() => Boolean(confirmPassword.value) && password.value === confirmPassword.value)
const checks = computed(() => [
  { id: 'length', label: t('recovery.ruleLength'), met: meetsMinimum.value },
  { id: 'match', label: t('recovery.ruleMatch'), met: passwordsMatch.value },
])
const passwordReady = computed(() => meetsMinimum.value && !tooLong.value && passwordsMatch.value)
const submitLabel = computed(() => {
  if (isInvite) return submitting.value ? t('recovery.inviteCreating') : t('recovery.inviteSubmit')
  return submitting.value ? t('recovery.resetting') : t('recovery.resetSubmit')
})

/**
 * Asks whether the link still works. Only the server's refusal (400) means it no longer does; a check that failed on
 * its way (offline, rate limit, server trouble) says so and can be tried again.
 */
async function checkLink(): Promise<void> {
  if (!token) {
    state.value = 'invalid'
    return
  }
  const version = ++checkVersion
  state.value = 'checking'
  try {
    const link = isInvite ? await publicOpsApi.checkAdminInvite(token) : await publicOpsApi.checkResetLink(token)
    if (isUnmounted() || version !== checkVersion) return
    linkEmail.value = link.email
    state.value = 'ready'
  } catch (reason) {
    if (isUnmounted() || version !== checkVersion) return
    if (apiErrorStatus(reason) === 400) {
      state.value = 'invalid'
    } else {
      state.value = 'unchecked'
      checkFailure.value = reason
    }
  }
}

onMounted(checkLink)

async function submit(): Promise<void> {
  if (submitting.value || !passwordReady.value) return
  submitFailure.value = null
  if (isInvite && !validateName()) {
    await nextTick()
    document.getElementById('invite-full-name')?.focus()
    return
  }
  submitting.value = true
  const sessionVersion = getAuthSessionVersion()
  const request = { token, password: password.value, confirmPassword: confirmPassword.value }
  try {
    if (isInvite) {
      // The new admin account takes the language of this page, as a sign-up does: its sign-in and e-mails use it.
      await publicOpsApi.acceptAdminInvite({
        ...request,
        fullName: fullName.value.trim(),
        preferredLanguage: appLocale.value,
      })
    } else {
      await authApi.resetPassword(request)
    }
    if (sessionVersion !== getAuthSessionVersion()) return
    if (!isInvite && auth.currentUser?.email.toLowerCase() === linkEmail.value.toLowerCase()) {
      // The server revoked this account's sessions with the password reset.
      auth.clearSessionInAllTabs()
    } else if (!isUnmounted() && auth.currentUser && !(await auth.logout())) return

    if (!isUnmounted()) await router.push({ name: 'auth', query: isInvite ? { invite: 'done' } : { reset: 'done' } })
  } catch (reason) {
    if (!isUnmounted() && sessionVersion === getAuthSessionVersion()) submitFailure.value = { reason }
  } finally {
    if (!isUnmounted()) submitting.value = false
  }
}
</script>
