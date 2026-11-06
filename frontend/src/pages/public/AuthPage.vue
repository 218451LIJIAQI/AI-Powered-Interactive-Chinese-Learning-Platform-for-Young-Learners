<template>
  <AuthShell>
    <h1 class="sr-only">{{ activeTab === 'register' ? t('auth.registerTitle') : t('auth.signInTitle') }}</h1>

    <div class="tabs auth-tabs" role="tablist" :aria-label="t('auth.views')" @keydown="onTabKeydown">
      <button
        id="auth-tab-signin"
        class="tab-button"
        type="button"
        role="tab"
        aria-controls="auth-panel-signin"
        :aria-selected="activeTab === 'signin'"
        :tabindex="activeTab === 'signin' ? 0 : -1"
        @click="showTab('signin')"
      >
        <IconGlyph name="profile" :size="16" />
        <span>{{ t('auth.signInTab') }}</span>
      </button>
      <button
        id="auth-tab-register"
        class="tab-button"
        type="button"
        role="tab"
        aria-controls="auth-panel-register"
        :aria-selected="activeTab === 'register'"
        :tabindex="activeTab === 'register' ? 0 : -1"
        @click="showTab('register')"
      >
        <IconGlyph name="userPlus" :size="16" />
        <span>{{ t('auth.registerTab') }}</span>
      </button>
    </div>

    <form
      v-if="activeTab === 'signin'"
      id="auth-panel-signin"
      class="auth-form"
      role="tabpanel"
      aria-labelledby="auth-tab-signin"
      novalidate
      @submit.prevent="submitLogin"
    >
      <div v-if="notice" class="callout is-success auth-notice" role="status" data-testid="auth-notice">
        <IconGlyph name="correct" :size="18" />
        <span>
          <strong>{{ t(notice.title) }}</strong>
          <span>{{ t(notice.copy) }}</span>
        </span>
      </div>
      <div v-else-if="accountCreated" class="callout is-success auth-notice" role="status">
        <IconGlyph name="correct" :size="18" />
        <span>
          <strong>{{ t('auth.createdSignIn') }}</strong>
        </span>
      </div>
      <div
        v-if="lockedMinutes"
        id="login-locked"
        class="callout is-warning auth-notice"
        role="alert"
        data-testid="auth-locked"
      >
        <IconGlyph name="lock" :size="18" />
        <span>
          <strong>{{ t('auth.lockedTitle', { minutes: lockedMinutes }) }}</strong>
          <span>{{ t('auth.lockedCopy') }}</span>
        </span>
      </div>
      <div
        v-else-if="loginError"
        id="login-error"
        class="callout is-danger auth-notice"
        role="alert"
        data-testid="auth-error"
      >
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{ loginError }}</strong>
        </span>
      </div>

      <div class="field">
        <label class="field-label" for="login-email">
          {{ t('auth.email') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="login-email"
          v-model="login.email"
          class="input"
          type="email"
          autocomplete="email"
          required
          :placeholder="t('auth.emailPlaceholder')"
          :aria-invalid="Boolean(loginError) && !isValidEmail(login.email.trim())"
          :aria-describedby="loginDescribedBy"
        />
      </div>

      <div class="field">
        <label class="field-label" for="login-password">
          {{ t('auth.password') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <div class="auth-password">
          <input
            id="login-password"
            v-model="login.password"
            class="input"
            :type="showLoginPassword ? 'text' : 'password'"
            autocomplete="current-password"
            required
            :placeholder="t('auth.passwordPlaceholder')"
            :aria-invalid="Boolean(loginError) && !login.password"
            :aria-describedby="loginDescribedBy"
          />
          <button
            class="btn btn-ghost btn-icon btn-sm auth-password-toggle"
            type="button"
            :aria-label="showLoginPassword ? t('auth.hidePassword') : t('auth.showPassword')"
            :aria-pressed="showLoginPassword"
            @click="showLoginPassword = !showLoginPassword"
          >
            <IconGlyph :name="showLoginPassword ? 'eyeOff' : 'eye'" :size="16" />
          </button>
        </div>
      </div>

      <div class="auth-form-row">
        <label class="choice auth-choice">
          <input v-model="login.keepSignedIn" type="checkbox" data-testid="keep-signed-in" />
          <span>{{ t('auth.keepSignedIn') }}</span>
        </label>
        <RouterLink class="btn btn-link auth-text-link" to="/forgot-password" @click="rememberRecoveryEmail">
          {{ t('auth.forgotPassword') }}
        </RouterLink>
      </div>

      <button
        class="btn btn-primary btn-lg btn-block"
        type="submit"
        :disabled="Boolean(lockedMinutes) || auth.loading"
        :aria-busy="auth.loading"
        data-testid="login-submit"
      >
        <IconGlyph v-if="lockedMinutes" name="lock" :size="18" />
        <span>{{ loginSubmitLabel }}</span>
        <IconGlyph v-if="!lockedMinutes" name="chevronRight" :size="18" />
      </button>
    </form>

    <form
      v-else
      id="auth-panel-register"
      class="auth-form"
      role="tabpanel"
      aria-labelledby="auth-tab-register"
      novalidate
      @submit.prevent="submitRegister"
    >
      <div
        v-if="errorCount"
        id="register-summary"
        ref="summaryRef"
        class="callout is-danger auth-notice"
        role="alert"
        tabindex="-1"
        data-testid="register-summary"
      >
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{ t('auth.errorSummaryTitle') }}</strong>
          <span>{{
            errorCount === 1 ? t('auth.errorSummaryOne') : t('auth.errorSummary', { count: errorCount })
          }}</span>
        </span>
      </div>
      <div v-else-if="registerError" class="callout is-danger auth-notice" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{ registerError }}</strong>
        </span>
      </div>

      <fieldset class="field-set">
        <legend class="field-label">{{ t('auth.accountType') }}</legend>
        <div class="auth-role-grid">
          <label v-for="card in ROLE_CARDS" :key="card.role" class="auth-role-card">
            <input
              v-model="register.role"
              class="auth-role-input"
              type="radio"
              name="account-type"
              :value="card.role"
              :data-testid="`auth-role-${card.role.toLowerCase()}`"
            />
            <span class="auth-role-icon" aria-hidden="true"><IconGlyph :name="card.icon" :size="20" /></span>
            <span class="auth-role-copy">
              <strong>{{ t(card.title) }}</strong>
              <small>{{ t(card.caption) }}</small>
            </span>
            <IconGlyph name="correct" :size="20" class="auth-role-check" />
          </label>
        </div>
      </fieldset>

      <div class="field">
        <label class="field-label" for="register-full-name">
          {{ t('auth.fullName') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="register-full-name"
          v-model="register.fullName"
          class="input"
          autocomplete="name"
          required
          maxlength="120"
          :placeholder="t('auth.fullNamePlaceholder')"
          :aria-invalid="Boolean(errors.fullName)"
          :aria-describedby="errors.fullName ? 'register-full-name-error' : undefined"
        />
        <p v-if="errors.fullName" id="register-full-name-error" class="field-error">
          <IconGlyph name="alert" :size="14" />{{ errors.fullName }}
        </p>
      </div>

      <div class="field">
        <label class="field-label" for="register-email">
          {{ t('auth.email') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="register-email"
          v-model="register.email"
          class="input"
          type="email"
          autocomplete="email"
          required
          maxlength="150"
          :placeholder="t('auth.emailPlaceholder')"
          :aria-invalid="Boolean(errors.email)"
          :aria-describedby="errors.email ? 'register-email-error' : undefined"
        />
        <p v-if="errors.email" id="register-email-error" class="field-error">
          <IconGlyph name="alert" :size="14" />{{ errors.email }}
        </p>
      </div>

      <div class="auth-passwords">
        <div class="form-grid two">
          <div class="field">
            <label class="field-label" for="register-password">
              {{ t('auth.password') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="register-password"
              v-model="register.password"
              class="input"
              type="password"
              autocomplete="new-password"
              required
              :placeholder="t('auth.createPassword')"
              :aria-invalid="Boolean(errors.password)"
              :aria-describedby="passwordDescribedBy"
            />
            <p v-if="errors.password" id="register-password-error" class="field-error">
              <IconGlyph name="alert" :size="14" />{{ errors.password }}
            </p>
          </div>
          <div class="field">
            <label class="field-label" for="register-confirm-password">
              {{ t('auth.confirmPassword') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="register-confirm-password"
              v-model="register.confirmPassword"
              class="input"
              type="password"
              autocomplete="new-password"
              required
              :placeholder="t('auth.confirmPasswordPlaceholder')"
              :aria-invalid="Boolean(errors.confirmPassword)"
              :aria-describedby="errors.confirmPassword ? 'register-confirm-error' : undefined"
            />
            <p v-if="errors.confirmPassword" id="register-confirm-error" class="field-error">
              <IconGlyph name="alert" :size="14" />{{ errors.confirmPassword }}
            </p>
          </div>
        </div>
        <p v-if="showPasswordHint" id="register-password-hint" class="field-hint auth-hint">
          <IconGlyph name="info" :size="14" />{{ t('auth.passwordHint') }}
        </p>
      </div>

      <div v-if="register.role === 'TEACHER'" class="field">
        <label class="field-label" for="register-teacher-bio">
          {{ t('auth.teacherBio') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          id="register-teacher-bio"
          v-model="register.bio"
          class="textarea"
          rows="4"
          :maxlength="TEACHER_BIO_MAX_LENGTH"
          aria-required="true"
          :placeholder="t('auth.teacherBioPlaceholder')"
          :aria-invalid="Boolean(errors.bio)"
          :aria-describedby="errors.bio ? 'register-bio-error register-bio-hint' : 'register-bio-hint'"
        />
        <div class="field-foot">
          <p id="register-bio-hint" class="field-hint">{{ t('auth.teacherBioHint') }}</p>
          <p class="field-hint field-counter">{{ t('auth.bioCount', { count: register.bio.trim().length }) }}</p>
        </div>
        <p v-if="errors.bio" id="register-bio-error" class="field-error">
          <IconGlyph name="alert" :size="14" />{{ errors.bio }}
        </p>
      </div>

      <div class="auth-consent-field">
        <label class="choice auth-choice auth-consent">
          <input
            v-model="register.consent"
            type="checkbox"
            data-testid="register-consent"
            :aria-invalid="Boolean(errors.consent)"
            :aria-describedby="errors.consent ? 'register-consent-error' : undefined"
          />
          <span>
            {{ t('auth.consentStart') }}
            <a href="/terms" target="_blank" rel="noopener">
              {{ t('auth.terms') }}<span class="sr-only"> {{ t('auth.newTab') }}</span>
            </a>
            {{ t('auth.and') }}
            <a href="/privacy" target="_blank" rel="noopener">
              {{ t('auth.privacy') }}<span class="sr-only"> {{ t('auth.newTab') }}</span> </a
            >{{ t('auth.consentEnd') }}
          </span>
        </label>
        <p v-if="errors.consent" id="register-consent-error" class="field-error">
          <IconGlyph name="alert" :size="14" />{{ errors.consent }}
        </p>
      </div>

      <button
        class="btn btn-primary btn-lg btn-block"
        type="submit"
        :disabled="registerSubmitting"
        :aria-busy="registerSubmitting"
        data-testid="register-submit"
      >
        <span>{{ registerSubmitting ? t('auth.creating') : t('auth.createAccount') }}</span>
        <IconGlyph name="chevronRight" :size="18" />
      </button>

      <p v-if="register.role === 'TEACHER'" class="callout auth-notice auth-teacher-note">
        <IconGlyph name="info" :size="18" />
        <span>{{ t('auth.teacherNote') }}</span>
      </p>
    </form>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import AuthShell from '@/components/public/AuthShell.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { authMessages } from '@/plugins/authMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { isPasswordLengthValid, isWithinPasswordByteLimit } from '@/utils/password'
import { normalizeAppRoute } from '@/utils/routeCompat'
import { defaultRouteForRole } from '@/utils/role'
import { getSessionStorage, safeSetItem, storageKeys } from '@/utils/storage'
import { TEACHER_BIO_MAX_LENGTH, teacherBioIssue } from '@/utils/teacherBio'

type AuthTab = 'signin' | 'register'
type RegisterRole = 'STUDENT' | 'TEACHER'
type RegisterField = 'fullName' | 'email' | 'password' | 'confirmPassword' | 'bio' | 'consent'

/** The sign-in lockout lasts 15 minutes; the server's Retry-After header says how much of it is left. */
const DEFAULT_LOCK_MINUTES = 15
/** The registration error the email field explains itself. */
const EMAIL_TAKEN_MESSAGE = 'Email is already registered.'

const ROLE_CARDS = [
  { role: 'STUDENT', icon: 'learn', title: 'auth.student', caption: 'auth.studentCaption' },
  { role: 'TEACHER', icon: 'teacher', title: 'auth.teacher', caption: 'auth.teacherCaption' },
] as const

/** Notices the other pages send here after a reset, an e-mail change or an accepted admin invite. */
const NOTICES = {
  reset: { title: 'auth.resetDoneTitle', copy: 'auth.resetDone' },
  email: { title: 'auth.emailChangedTitle', copy: 'auth.emailChanged' },
  invite: { title: 'auth.inviteDoneTitle', copy: 'auth.inviteDone' },
} as const

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ auth: authMessages })

const activeTab = ref<AuthTab>('signin')
const login = reactive({ email: '', password: '', keepSignedIn: false })
const showLoginPassword = ref(false)
const loginError = ref<string | null>(null)
const lockedMinutes = ref<number | null>(null)
const accountCreated = ref(false)

const register = reactive({
  role: 'STUDENT' as RegisterRole,
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  bio: '',
  consent: false,
})
const errors = reactive<Partial<Record<RegisterField, string>>>({})
const registerError = ref<string | null>(null)
const registerSubmitting = ref(false)
const summaryRef = ref<HTMLElement | null>(null)

const notice = computed(() => {
  if (route.query.reset === 'done') return NOTICES.reset
  if (route.query.email === 'changed') return NOTICES.email
  if (route.query.invite === 'done') return NOTICES.invite
  return null
})
const errorCount = computed(() => Object.values(errors).filter(Boolean).length)
const loginDescribedBy = computed(() =>
  lockedMinutes.value ? 'login-locked' : loginError.value ? 'login-error' : undefined,
)
const loginSubmitLabel = computed(() => {
  if (lockedMinutes.value) return t('auth.lockedButton', { minutes: lockedMinutes.value })
  return auth.loading ? t('auth.signingIn') : t('auth.signIn')
})
/** One hint under both password fields; a field's own error takes its place. */
const showPasswordHint = computed(() => !errors.password && !errors.confirmPassword)
const passwordDescribedBy = computed(() => {
  if (errors.password) return 'register-password-error'
  return showPasswordHint.value ? 'register-password-hint' : undefined
})

watch(
  () => [route.query.tab, route.query.role],
  ([tab, role]) => {
    activeTab.value = tab === 'register' ? 'register' : 'signin'
    if (role === 'teacher') register.role = 'TEACHER'
    else if (role === 'student') register.role = 'STUDENT'
  },
  { immediate: true },
)

// The lockout belongs to one e-mail address; another address may be tried.
watch(
  () => login.email,
  () => {
    lockedMinutes.value = null
  },
)

watch(
  () => register.role,
  (role) => {
    if (role !== 'TEACHER') delete errors.bio
    if (activeTab.value === 'register') replaceQuery('register')
  },
)

function replaceQuery(tab: AuthTab): void {
  const query = { ...route.query }
  delete query.reset
  delete query.email
  delete query.invite
  if (tab === 'register') {
    query.tab = 'register'
    query.role = register.role === 'TEACHER' ? 'teacher' : 'student'
  } else {
    delete query.tab
    delete query.role
  }
  void router.replace({ path: route.path, query })
}

function showTab(tab: AuthTab): void {
  activeTab.value = tab
  loginError.value = null
  accountCreated.value = false
  registerError.value = null
  replaceQuery(tab)
}

/** Arrow keys, Home and End move between the two tabs (only the selected tab is in the Tab order). */
function onTabKeydown(event: KeyboardEvent): void {
  const target: Partial<Record<string, AuthTab>> = {
    ArrowLeft: activeTab.value === 'signin' ? 'register' : 'signin',
    ArrowRight: activeTab.value === 'signin' ? 'register' : 'signin',
    Home: 'signin',
    End: 'register',
  }
  const tab = target[event.key]
  if (!tab) return
  event.preventDefault()
  if (tab !== activeTab.value) showTab(tab)
  void nextTick(() => document.getElementById(`auth-tab-${tab}`)?.focus())
}

function redirectAfterSignIn(): RouteLocationRaw {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
  return normalizeAppRoute(redirect) ?? defaultRouteForRole(auth.activeRole)
}

function rememberRecoveryEmail(): void {
  const email = login.email.trim().toLowerCase()
  if (isValidEmail(email)) safeSetItem(storageKeys.recoveryEmail, email, getSessionStorage())
}

function retryAfterMinutes(reason: unknown): number {
  const header = (reason as { response?: { headers?: Record<string, unknown> } })?.response?.headers?.['retry-after']
  const seconds = Number(header)
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds / 60) : DEFAULT_LOCK_MINUTES
}

/** After a failed sign-in the password is empty again and the cursor waits in the field to fix. */
function focusLoginField(id: 'login-email' | 'login-password'): void {
  void nextTick(() => document.getElementById(id)?.focus())
}

async function submitLogin(): Promise<void> {
  if (lockedMinutes.value || auth.loading) return
  loginError.value = null
  const email = login.email.trim().toLowerCase()
  if (!isValidEmail(email) || !login.password) {
    loginError.value = t('auth.wrongCredentials')
    focusLoginField(isValidEmail(email) ? 'login-password' : 'login-email')
    return
  }
  try {
    await auth.login(email, login.password, login.keepSignedIn)
    await router.push(redirectAfterSignIn())
  } catch (reason) {
    const status = apiErrorStatus(reason)
    if (status === 429) {
      lockedMinutes.value = retryAfterMinutes(reason)
    } else if (status === 403) {
      loginError.value = t('auth.accountUnavailable')
    } else if (status === 401) {
      loginError.value = t('auth.wrongCredentials')
    } else {
      loginError.value = resolveApiError(reason, t('auth.signInFailed'))
    }
    login.password = ''
    focusLoginField('login-password')
  }
}

function validateRegistration(): void {
  for (const field of Object.keys(errors) as RegisterField[]) delete errors[field]
  if (!register.fullName.trim()) errors.fullName = t('auth.nameRequired')
  if (!isValidEmail(register.email.trim().toLowerCase())) errors.email = t('auth.emailInvalid')
  if (!isWithinPasswordByteLimit(register.password)) errors.password = t('auth.passwordLong')
  else if (!isPasswordLengthValid(register.password)) errors.password = t('auth.passwordShort')
  if (!register.confirmPassword || register.confirmPassword !== register.password) {
    errors.confirmPassword = t('auth.passwordMismatch')
  }
  if (register.role === 'TEACHER') {
    const issue = teacherBioIssue(register.bio)
    if (issue) errors.bio = issue === 'required' ? t('auth.bioRequired') : t('auth.bioLength')
  }
  if (!register.consent) errors.consent = t('auth.consentRequired')
}

function isEmailTaken(reason: unknown): boolean {
  const message = (reason as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  return apiErrorStatus(reason) === 400 && message === EMAIL_TAKEN_MESSAGE
}

async function submitRegister(): Promise<void> {
  if (registerSubmitting.value) return
  registerError.value = null
  validateRegistration()
  if (errorCount.value) {
    await nextTick()
    summaryRef.value?.focus()
    return
  }

  registerSubmitting.value = true
  const email = register.email.trim().toLowerCase()
  const account = {
    fullName: register.fullName.trim(),
    email,
    password: register.password,
    confirmPassword: register.confirmPassword,
    preferredLanguage: ui.locale,
  }
  try {
    if (register.role === 'TEACHER') {
      await authApi.registerTeacher({ ...account, bio: register.bio.trim() })
    } else {
      await authApi.registerStudent(account)
    }
  } catch (reason) {
    registerSubmitting.value = false
    if (isEmailTaken(reason)) {
      errors.email = t('auth.emailTaken')
      await nextTick()
      summaryRef.value?.focus()
    } else {
      registerError.value = resolveApiError(reason, t('auth.registerFailed'))
    }
    return
  }

  try {
    await auth.login(email, register.password, true)
    await router.push(register.role === 'TEACHER' ? { name: 'teacher-dashboard' } : { name: 'student-setup' })
  } catch {
    // The account exists; signing in by hand is the way on.
    login.email = email
    showTab('signin')
    accountCreated.value = true
  } finally {
    registerSubmitting.value = false
  }
}
</script>
