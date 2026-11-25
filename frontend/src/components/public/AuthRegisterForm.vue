<template>
  <form class="auth-form" novalidate @submit.prevent="submit">
    <p
      v-if="summaryShown && errorCount"
      ref="summaryRef"
      class="callout is-danger"
      role="alert"
      tabindex="-1"
      data-testid="register-summary"
    >
      <IconGlyph name="alert" :size="18" />
      <span>{{ t('auth.errorSummary', errorCount) }}</span>
    </p>
    <p v-else-if="registerError" class="callout is-danger" role="alert" data-testid="register-error">
      <IconGlyph name="alert" :size="18" />
      <span>{{ registerError }}</span>
    </p>

    <div class="field">
      <label class="field-label" for="register-full-name">
        {{ t('auth.fullName') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <input
        id="register-full-name"
        v-model="form.fullName"
        class="input"
        autocomplete="name"
        required
        maxlength="120"
        :placeholder="t('auth.fullNamePlaceholder')"
        :aria-invalid="Boolean(errors.fullName)"
        :aria-describedby="errors.fullName ? 'register-full-name-error' : undefined"
        @blur="checkOnLeave('fullName')"
      />
      <p
        v-if="errors.fullName"
        id="register-full-name-error"
        class="field-error"
        :aria-live="summaryShown ? undefined : 'polite'"
      >
        <IconGlyph name="alert" :size="14" />{{ t(errors.fullName) }}
      </p>
    </div>

    <div class="field">
      <label class="field-label" for="register-email">
        {{ t('auth.email') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <input
        id="register-email"
        v-model="form.email"
        class="input"
        type="email"
        autocomplete="email"
        required
        maxlength="150"
        :placeholder="t('auth.emailPlaceholder')"
        :aria-invalid="Boolean(errors.email)"
        :aria-describedby="errors.email ? 'register-email-error' : undefined"
        @blur="checkOnLeave('email')"
      />
      <p
        v-if="errors.email"
        id="register-email-error"
        class="field-error"
        :aria-live="summaryShown ? undefined : 'polite'"
      >
        <IconGlyph name="alert" :size="14" />{{ t(errors.email) }}
      </p>
    </div>

    <div class="auth-passwords">
      <div class="form-grid two">
        <div class="field">
          <label class="field-label" for="register-password">
            {{ t('auth.password') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <PasswordInput
            id="register-password"
            v-model="form.password"
            autocomplete="new-password"
            required
            :placeholder="t('auth.createPassword')"
            :aria-invalid="Boolean(errors.password)"
            :aria-describedby="passwordDescribedBy"
            @blur="checkOnLeave('password')"
          />
          <p
            v-if="errors.password"
            id="register-password-error"
            class="field-error"
            :aria-live="summaryShown ? undefined : 'polite'"
          >
            <IconGlyph name="alert" :size="14" />{{ t(errors.password) }}
          </p>
        </div>
        <div class="field">
          <label class="field-label" for="register-confirm-password">
            {{ t('auth.confirmPassword') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <PasswordInput
            id="register-confirm-password"
            v-model="form.confirmPassword"
            autocomplete="new-password"
            required
            :placeholder="t('auth.confirmPasswordPlaceholder')"
            :aria-invalid="Boolean(errors.confirmPassword)"
            :aria-describedby="errors.confirmPassword ? 'register-confirm-error' : undefined"
            @blur="checkOnLeave('confirmPassword')"
          />
          <p
            v-if="errors.confirmPassword"
            id="register-confirm-error"
            class="field-error"
            :aria-live="summaryShown ? undefined : 'polite'"
          >
            <IconGlyph name="alert" :size="14" />{{ t(errors.confirmPassword) }}
          </p>
        </div>
      </div>
      <p v-if="showPasswordHint" id="register-password-hint" class="field-hint auth-hint">
        <IconGlyph name="info" :size="14" />{{ t('auth.passwordHint') }}
      </p>
    </div>

    <div class="auth-consent-field" @focusout="checkConsentOnLeave">
      <div class="choice auth-choice auth-consent">
        <input
          id="register-consent"
          v-model="form.consent"
          type="checkbox"
          required
          data-testid="register-consent"
          :aria-invalid="Boolean(errors.consent)"
          :aria-describedby="errors.consent ? 'register-consent-error' : undefined"
        />
        <label for="register-consent">
          <I18nT keypath="auth.consent" tag="span">
            <template #terms>
              <RouterLink to="/terms" target="_blank" rel="noopener"
                >{{ t('auth.terms') }}<span class="sr-only">{{ ` ${t('common.opensInNewTab')}` }}</span></RouterLink
              >
            </template>
            <template #privacy>
              <RouterLink to="/privacy" target="_blank" rel="noopener"
                >{{ t('auth.privacy') }}<span class="sr-only">{{ ` ${t('common.opensInNewTab')}` }}</span></RouterLink
              >
            </template>
          </I18nT>
        </label>
      </div>
      <p
        v-if="errors.consent"
        id="register-consent-error"
        class="field-error"
        :aria-live="summaryShown ? undefined : 'polite'"
      >
        <IconGlyph name="alert" :size="14" />{{ t(errors.consent) }}
      </p>
    </div>

    <button
      class="btn btn-primary btn-lg btn-block"
      type="submit"
      :disabled="submitting || errorCount > 0"
      :aria-busy="submitting"
      data-testid="register-submit"
      @mousedown.prevent
    >
      <span>{{ submitting ? t('auth.creating') : t('auth.createAccount') }}</span>
      <IconGlyph name="chevronRight" :size="18" />
    </button>
  </form>
</template>

<script setup lang="ts">
import { isCancel } from 'axios'
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, shallowRef, watch } from 'vue'
import { I18nT } from 'vue-i18n'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PasswordInput from '@/components/common/PasswordInput.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { authMessages } from '@/plugins/authMessages'
import { appLocale } from '@/plugins/i18n'
import { useAuthStore } from '@/stores/auth'
import { hasServerMessage, resolveApiError } from '@/utils/apiError'
import { signedInDestination } from '@/utils/appRoute'
import { isValidEmail } from '@/utils/email'
import { isBlankPassword, isWithinPasswordByteLimit, meetsPasswordMinimum } from '@/utils/password'
import { defaultRouteForRole } from '@/utils/role'
import { SERVER_MESSAGE } from '@/utils/serverMessages'

/** The fields of the create-account form (a learner account: the only kind anyone can create), in checking order. */
const REGISTER_FIELDS = ['fullName', 'email', 'password', 'confirmPassword', 'consent'] as const
type RegisterField = (typeof REGISTER_FIELDS)[number]

const emit = defineEmits<{
  /** Emitted when the account was created but automatic sign-in failed, handing off to the sign-in form. */
  fallbackSignIn: [email: string]
}>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ auth: authMessages })

const form = reactive({ fullName: '', email: '', password: '', confirmPassword: '', consent: false })
const address = computed(() => form.email.trim().toLowerCase())
const errors = reactive<Partial<Record<RegisterField, string>>>({})
const takenEmails = new Set<string>()
const registerFailure = shallowRef<{ reason: unknown; navigation?: boolean } | null>(null)
const registerError = computed(() =>
  registerFailure.value
    ? resolveApiError(
        registerFailure.value.reason,
        registerFailure.value.navigation ? t('common.errorTitle') : t('auth.registerFailed'),
      )
    : null,
)
/** Address of an account already created and signed in here if the post-login navigation failed. */
let signedInEmail: string | null = null

/**
 * `shown` tracks whether this KeepAlive tab is visible, while `disposed` tracks permanent unmount. A registration that
 * finishes after switching tabs skips starting auto-login and suppresses tab-local errors; once auto-login commits a
 * live session, however, navigation still runs unless the route itself has unmounted.
 */
let shown = true
let disposed = false
onActivated(() => {
  shown = true
})
onDeactivated(() => {
  shown = false
  registerFailure.value = null
})
onBeforeUnmount(() => {
  shown = false
  disposed = true
})

const submitting = ref(false)
const summaryRef = ref<HTMLElement | null>(null)
const summaryShown = ref(false)

const errorCount = computed(() => Object.keys(errors).length)
const showPasswordHint = computed(() => !errors.password && !errors.confirmPassword)
const passwordDescribedBy = computed(() => {
  if (errors.password) return 'register-password-error'
  return showPasswordHint.value ? 'register-password-hint' : undefined
})

function learnerStartAfterRegister(): RouteLocationRaw {
  const destination = signedInDestination(route.query.redirect, ['STUDENT'])
  return destination === defaultRouteForRole('STUDENT') ? { name: 'student-setup' } : destination
}

function fieldIssue(field: RegisterField): string | undefined {
  switch (field) {
    case 'fullName':
      return form.fullName.trim() ? undefined : 'auth.nameRequired'
    case 'email': {
      const email = address.value
      if (!isValidEmail(email)) return 'auth.emailInvalid'
      return takenEmails.has(email) ? 'auth.emailTaken' : undefined
    }
    case 'password':
      if (isBlankPassword(form.password)) return 'auth.passwordRequired'
      if (!isWithinPasswordByteLimit(form.password)) return 'auth.passwordLong'
      return meetsPasswordMinimum(form.password) ? undefined : 'auth.passwordShort'
    case 'confirmPassword':
      return form.confirmPassword && form.confirmPassword === form.password ? undefined : 'auth.passwordMismatch'
    case 'consent':
      return form.consent ? undefined : 'auth.consentRequired'
  }
}

function validateField(field: RegisterField): void {
  const issue = fieldIssue(field)
  if (issue) errors[field] = issue
  else delete errors[field]
}

function checkOnLeave(field: RegisterField): void {
  validateField(field)
  if (field === 'password' && form.confirmPassword) validateField('confirmPassword')
}

function checkConsentOnLeave(event: FocusEvent): void {
  const container = event.currentTarget as HTMLElement | null
  if (container && event.relatedTarget instanceof Node && container.contains(event.relatedTarget)) return
  // Opening the Terms or Privacy link in a new tab blurs the document without moving focus to another form control.
  if (!document.hasFocus()) return
  if (container && document.activeElement instanceof Node && container.contains(document.activeElement)) return
  validateField('consent')
}

watch(form, () => {
  for (const field of REGISTER_FIELDS) if (errors[field]) validateField(field)
})

watch(errorCount, (count) => {
  if (!count) summaryShown.value = false
})

async function showSummary(): Promise<void> {
  if (!errorCount.value) return
  summaryShown.value = true
  await nextTick()
  summaryRef.value?.focus()
}

async function registerAndSignIn(email: string, sessionVersion: number): Promise<boolean> {
  const account = {
    fullName: form.fullName.trim(),
    email,
    password: form.password,
    confirmPassword: form.confirmPassword,
    preferredLanguage: appLocale.value,
  }
  try {
    await authApi.registerStudent(account)
  } catch (reason) {
    if (!shown || isCancel(reason) || sessionVersion !== getAuthSessionVersion()) return false
    if (hasServerMessage(reason, SERVER_MESSAGE.EMAIL_ALREADY_REGISTERED)) {
      takenEmails.add(email)
      validateField('email')
      await showSummary()
    } else {
      registerFailure.value = { reason }
    }
    return false
  }

  if (!shown || sessionVersion !== getAuthSessionVersion()) return false
  try {
    // Like a sign-in with "Keep me signed in" left off: closing the browser ends the session on a shared computer.
    await auth.login(email, account.password, false)
    signedInEmail = email
    return true
  } catch (reason) {
    if (shown && !isCancel(reason)) emit('fallbackSignIn', email)
    return false
  }
}

async function submit(): Promise<void> {
  if (submitting.value) return
  registerFailure.value = null
  for (const field of REGISTER_FIELDS) validateField(field)
  if (errorCount.value) return showSummary()

  submitting.value = true
  const sessionVersion = getAuthSessionVersion()
  const email = address.value
  try {
    if ((signedInEmail !== email || !auth.isAuthenticated) && !(await registerAndSignIn(email, sessionVersion))) {
      return
    }
    if (!disposed) {
      try {
        await router.push(learnerStartAfterRegister())
      } catch (reason) {
        if (shown) registerFailure.value = { reason, navigation: true }
      }
    }
  } finally {
    submitting.value = false
  }
}
</script>
