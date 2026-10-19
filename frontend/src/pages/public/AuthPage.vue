<template>
  <section class="public-page auth-showcase-page">
    <div class="auth-showcase-shell">
      <aside class="auth-showcase-art">
        <AppBrand />

        <div class="auth-showcase-copy">
          <p class="auth-showcase-eyebrow">{{ t('auth.eyebrow') }}</p>
          <h1>
            {{ t('auth.heroLineOne') }}<br />
            <span>{{ t('auth.heroLineTwo') }}</span>
          </h1>
          <p class="auth-showcase-lead">{{ t('auth.heroCopy') }}</p>
        </div>

        <div class="auth-showcase-scene" aria-hidden="true">
          <span class="auth-scene-bamboo" />
          <span class="auth-scene-card auth-scene-card--one" />
          <span class="auth-scene-card auth-scene-card--two" />
          <span class="auth-scene-spark"><IconGlyph name="star" :size="18" /></span>
          <PandaIllustration class="auth-showcase-panda" :size="200" outfit="#ef6b40" accent="#f6c24d" />
        </div>

        <div class="auth-showcase-safety">
          <span class="auth-showcase-safety-icon" aria-hidden="true"><IconGlyph name="shield" :size="20" /></span>
          <div>
            <strong>{{ t('auth.safetyTitle') }}</strong>
            <span>{{ t('auth.safetyCopy') }}</span>
          </div>
        </div>
      </aside>

      <section class="auth-showcase-card">
        <div class="auth-showcase-topbar">
          <LanguageSwitcher show-label :label="t('auth.language')" size="compact" />
        </div>

        <div class="tabs tabs--block" role="tablist" :aria-label="t('auth.views')">
          <button
            id="auth-tab-signin"
            class="tab-button auth-tab"
            type="button"
            role="tab"
            aria-controls="auth-panel-signin"
            :aria-selected="activeTab === 'signin'"
            @click="activateSignIn"
          >
            <IconGlyph name="profile" :size="18" />
            <span>{{ t('auth.signIn') }}</span>
          </button>
          <button
            id="auth-tab-register"
            class="tab-button auth-tab"
            type="button"
            role="tab"
            aria-controls="auth-panel-register"
            :aria-selected="activeTab === 'register'"
            @click="activateRegister()"
          >
            <IconGlyph name="status" :size="18" />
            <span>{{ t('auth.createAccount') }}</span>
          </button>
        </div>

        <form
          v-show="activeTab === 'signin'"
          id="auth-panel-signin"
          class="auth-form-panel"
          role="tabpanel"
          aria-labelledby="auth-tab-signin"
          @submit.prevent="submitLogin"
        >
          <div class="auth-form-title">
            <span class="page-eyebrow">{{ t('auth.signInKicker') }}</span>
            <h2>{{ t('auth.welcome') }}</h2>
            <p>{{ t('auth.signInCopy') }}</p>
          </div>

          <div v-if="loginError" id="login-error" class="alert alert-danger" role="alert" aria-live="assertive">
            <IconGlyph name="alert" :size="18" />
            <span>{{ loginError }}</span>
          </div>

          <div v-if="loginInfo" id="login-info" class="alert alert-success" role="status" aria-live="polite">
            <IconGlyph name="status" :size="18" />
            <span>{{ loginInfo }}</span>
          </div>

          <div class="field">
            <label for="login-email">
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
              :aria-invalid="Boolean(loginError)"
              :aria-describedby="loginDescriptionId"
            />
          </div>

          <div class="field">
            <label for="login-password">
              {{ t('auth.password') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="login-password"
              v-model="login.password"
              class="input"
              type="password"
              autocomplete="current-password"
              required
              :placeholder="t('auth.passwordPlaceholder')"
              :aria-invalid="Boolean(loginError)"
              :aria-describedby="loginDescriptionId"
            />
          </div>

          <div class="auth-form-row">
            <p class="field-hint auth-session-note">
              <IconGlyph name="safe" :size="14" />
              <span>{{ t('auth.sessionNote') }}</span>
            </p>
            <button
              class="btn btn-link auth-forgot-link"
              :disabled="!loginEmailValid"
              type="button"
              @click="openForgotPassword"
            >
              {{ t('auth.forgotPassword') }}
            </button>
          </div>

          <button
            class="btn btn-primary btn-lg btn-block auth-submit-button"
            :disabled="!canSubmitLogin"
            :aria-busy="auth.loading"
            type="submit"
          >
            <span>{{ loginSubmitLabel }}</span>
            <IconGlyph name="chevronRight" :size="18" />
          </button>
        </form>

        <form
          v-show="activeTab === 'register'"
          id="auth-panel-register"
          class="auth-form-panel"
          role="tabpanel"
          aria-labelledby="auth-tab-register"
          @submit.prevent="submitRegister"
        >
          <div class="auth-form-title">
            <span class="page-eyebrow">{{ t('auth.registerKicker') }}</span>
            <h2>{{ t('auth.registerTitle') }}</h2>
            <p>{{ t('auth.registerCopy') }}</p>
          </div>

          <div v-if="registerError" id="register-error" class="alert alert-danger" role="alert" aria-live="assertive">
            <IconGlyph name="alert" :size="18" />
            <span>{{ registerError }}</span>
          </div>

          <fieldset class="auth-role-fieldset">
            <legend class="field-label">{{ t('auth.accountType') }}</legend>
            <div class="auth-role-grid">
              <button
                class="auth-role-card"
                :class="{ 'is-active': register.role === 'STUDENT' }"
                type="button"
                :aria-pressed="register.role === 'STUDENT'"
                aria-describedby="register-role-help"
                @click="selectRegisterRole('STUDENT')"
              >
                <span class="auth-role-avatar" aria-hidden="true"><IconGlyph name="students" :size="22" /></span>
                <span class="auth-role-copy">
                  <strong>{{ t('auth.student') }}</strong>
                  <small>{{ t('auth.learnerRole') }}</small>
                </span>
                <span class="auth-role-check" aria-hidden="true"><IconGlyph name="status" :size="18" /></span>
              </button>
              <button
                class="auth-role-card"
                :class="{ 'is-active': register.role === 'TEACHER' }"
                type="button"
                :aria-pressed="register.role === 'TEACHER'"
                aria-describedby="register-role-help"
                @click="selectRegisterRole('TEACHER')"
              >
                <span class="auth-role-avatar" aria-hidden="true"><IconGlyph name="teacher" :size="22" /></span>
                <span class="auth-role-copy">
                  <strong>{{ t('auth.teacher') }}</strong>
                  <small>{{ t('auth.educator') }}</small>
                </span>
                <span class="auth-role-check" aria-hidden="true"><IconGlyph name="status" :size="18" /></span>
              </button>
            </div>
            <p id="register-role-help" class="field-hint">{{ registerRoleHelp }}</p>
          </fieldset>

          <div class="field">
            <label for="register-full-name">
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
              :aria-invalid="Boolean(registerError && !normalizedRegisterFullName)"
              :aria-describedby="registerDescriptionId"
            />
          </div>

          <div class="field">
            <label for="register-email">
              {{ t('auth.email') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="register-email"
              v-model="register.email"
              class="input"
              type="email"
              autocomplete="email"
              required
              :placeholder="t('auth.emailPlaceholder')"
              :aria-invalid="Boolean(registerError && !registerEmailValid)"
              :aria-describedby="registerDescriptionId"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label for="register-password">
                {{ t('auth.password') }}<span class="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="register-password"
                v-model="register.password"
                class="input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                :placeholder="t('auth.createPassword')"
                :aria-invalid="Boolean(register.password && !registerPasswordReady)"
                :aria-describedby="describedBy('register-password-hint', registerDescriptionId)"
              />
              <p
                id="register-password-hint"
                class="field-hint auth-field-check"
                :class="{ 'is-met': registerPasswordReady }"
              >
                <IconGlyph :name="registerPasswordReady ? 'status' : 'info'" :size="14" />
                <span>{{ t('auth.passwordHint') }}</span>
              </p>
            </div>

            <div class="field">
              <label for="register-confirm-password">
                {{ t('auth.confirmPassword') }}<span class="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="register-confirm-password"
                v-model="register.confirmPassword"
                class="input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                :placeholder="t('auth.confirmPasswordPlaceholder')"
                :aria-invalid="Boolean(register.confirmPassword && !registerPasswordsMatch)"
                :aria-describedby="describedBy(confirmPasswordErrorId, registerDescriptionId)"
              />
              <p v-if="confirmPasswordErrorId" :id="confirmPasswordErrorId" class="field-error">
                <IconGlyph name="alert" :size="14" />
                <span>{{ t('auth.passwordMismatch') }}</span>
              </p>
            </div>
          </div>

          <div v-if="register.role === 'STUDENT'" class="field">
            <label for="register-preferred-language">{{ t('auth.preferredLanguage') }}</label>
            <select id="register-preferred-language" v-model="register.preferredLanguage" class="select">
              <option value="en">{{ t('auth.english') }}</option>
              <option value="ms">{{ t('auth.malay') }}</option>
              <option value="zh-CN">{{ t('auth.chinese') }}</option>
            </select>
          </div>

          <div v-else class="field">
            <label for="register-teacher-bio">
              {{ t('auth.teacherBio') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <textarea
              id="register-teacher-bio"
              v-model="register.bio"
              class="textarea auth-textarea"
              maxlength="1000"
              aria-required="true"
              :placeholder="t('auth.teacherBioPlaceholder')"
              :aria-describedby="describedBy('register-bio-hint', registerDescriptionId)"
            />
            <p id="register-bio-hint" class="field-hint auth-field-check" :class="{ 'is-met': teacherBioReady }">
              <IconGlyph :name="teacherBioReady ? 'status' : 'info'" :size="14" />
              <span>{{ t('auth.teacherBioHint') }}</span>
            </p>
          </div>

          <label class="choice auth-checkbox">
            <input v-model="register.agreedToTerms" data-testid="register-consent" type="checkbox" required />
            <span>
              {{ t('auth.consentPrefix') }}
              <a href="/terms" @click.prevent="openTerms">{{ t('auth.terms') }}</a>
              {{ t('auth.and') }}
              <a href="/privacy" @click.prevent="openPrivacy">{{ t('auth.privacy') }}</a
              >{{ t('auth.agreementEnd') }}
            </span>
          </label>

          <button
            class="btn btn-primary btn-lg btn-block auth-submit-button"
            :disabled="!canSubmitRegister"
            :aria-busy="registerSubmitting"
            type="submit"
          >
            <span>{{ registerSubmitLabel }}</span>
            <IconGlyph name="chevronRight" :size="18" />
          </button>

          <p class="alert">
            <IconGlyph name="info" :size="18" />
            <span>{{ t('auth.teacherNote') }}</span>
          </p>
        </form>

        <ul class="auth-trust-list">
          <li class="auth-trust-item is-green">
            <span class="auth-trust-icon" aria-hidden="true"><IconGlyph name="shield" :size="18" /></span>
            <div>
              <strong>{{ t('auth.safetyFeatureTitle') }}</strong>
              <p>{{ t('auth.safetyFeatureCopy') }}</p>
            </div>
          </li>
          <li class="auth-trust-item is-blue">
            <span class="auth-trust-icon" aria-hidden="true"><IconGlyph name="verified" :size="18" /></span>
            <div>
              <strong>{{ t('auth.teacherFeatureTitle') }}</strong>
              <p>{{ t('auth.teacherFeatureCopy') }}</p>
            </div>
          </li>
          <li class="auth-trust-item is-purple">
            <span class="auth-trust-icon" aria-hidden="true"><IconGlyph name="safe" :size="18" /></span>
            <div>
              <strong>{{ t('auth.signInFeatureTitle') }}</strong>
              <p>{{ t('auth.signInFeatureCopy') }}</p>
            </div>
          </li>
        </ul>

        <div v-if="previewEnabled" class="auth-preview" role="group" aria-labelledby="auth-preview-label">
          <p id="auth-preview-label" class="auth-preview-label">{{ t('auth.previewLabel') }}</p>
          <div class="auth-preview-actions">
            <button class="btn btn-secondary btn-sm" type="button" @click="previewStudent">
              <IconGlyph name="students" :size="16" />
              {{ t('auth.previewStudent') }}
            </button>
            <button class="btn btn-secondary btn-sm" type="button" @click="previewTeacher">
              <IconGlyph name="teacher" :size="16" />
              {{ t('auth.previewTeacher') }}
            </button>
            <button class="btn btn-secondary btn-sm" type="button" @click="previewAdmin">
              <IconGlyph name="admin" :size="16" />
              {{ t('auth.previewAdmin') }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import { appEnv } from '@/config/env'
import { authMessages } from '@/plugins/authMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { AppLanguage, UserRole } from '@/types/api'
import { isForbiddenApiError, resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { isPasswordLengthValid } from '@/utils/password'
import { normalizeAppRoute } from '@/utils/routeCompat'
import { defaultRouteForRole } from '@/utils/role'
import { getSessionStorage, safeSetItem, storageKeys } from '@/utils/storage'
import '@/styles/auth.css'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()
const { t } = useI18n({
  messages: {
    en: { auth: authMessages.en },
    ms: { auth: authMessages.ms },
    'zh-CN': { auth: authMessages['zh-CN'] },
  },
})

type AuthTab = 'signin' | 'register'
type RegisterRole = 'STUDENT' | 'TEACHER'
type PublicAuthRoute = '/privacy' | '/terms'
type PreviewDestination = '/admin/dashboard' | '/student/home' | '/teacher/dashboard'

const previewEnabled = appEnv.useDemoData && appEnv.enablePreviewMode
const loginError = ref<string | null>(null)
const loginInfo = ref<string | null>(null)
const registerError = ref<string | null>(null)
const registerSubmitting = ref(false)
const activeTab = ref<AuthTab>('signin')

const login = reactive({
  email: '',
  password: '',
})

const register = reactive({
  role: 'STUDENT' as RegisterRole,
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  preferredLanguage: ui.locale as AppLanguage,
  bio: '',
  agreedToTerms: false,
})

const normalizedLoginEmail = computed(() => login.email.trim().toLowerCase())
const loginEmailValid = computed(() => isValidEmail(normalizedLoginEmail.value))
const canSubmitLogin = computed(() => !auth.loading && loginEmailValid.value && login.password.trim().length > 0)
const normalizedRegisterFullName = computed(() => register.fullName.trim())
const normalizedRegisterEmail = computed(() => register.email.trim().toLowerCase())
const registerEmailValid = computed(() => isValidEmail(normalizedRegisterEmail.value))
const registerPasswordReady = computed(() => isPasswordLengthValid(register.password))
const registerPasswordsMatch = computed(
  () => register.password === register.confirmPassword && register.confirmPassword.length > 0,
)
const teacherBioReady = computed(() => register.role !== 'TEACHER' || register.bio.trim().length >= 20)
const loginSubmitLabel = computed(() => (auth.loading ? t('auth.signingIn') : t('auth.signIn')))
const registerSubmitLabel = computed(() => (registerSubmitting.value ? t('auth.creating') : t('auth.createAccount')))
const registerRoleHelp = computed(() => (register.role === 'STUDENT' ? t('auth.studentHelp') : t('auth.teacherHelp')))
const canSubmitRegister = computed(
  () =>
    !registerSubmitting.value &&
    normalizedRegisterFullName.value.length >= 2 &&
    registerEmailValid.value &&
    registerPasswordReady.value &&
    registerPasswordsMatch.value &&
    teacherBioReady.value &&
    register.agreedToTerms,
)
const loginDescriptionId = computed(() =>
  loginError.value ? 'login-error' : loginInfo.value ? 'login-info' : undefined,
)
const registerDescriptionId = computed(() => (registerError.value ? 'register-error' : undefined))
const confirmPasswordErrorId = computed(() =>
  register.confirmPassword && !registerPasswordsMatch.value ? 'register-confirm-error' : undefined,
)

function describedBy(...ids: Array<string | undefined>): string | undefined {
  const joined = ids.filter(Boolean).join(' ')
  return joined || undefined
}

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = tab === 'register' ? 'register' : 'signin'
  },
  { immediate: true },
)

// Only an explicit ?role changes the account type: switching to the sign-in tab drops the query but keeps the choice.
watch(
  () => route.query.role,
  (role) => {
    if (role === 'teacher') register.role = 'TEACHER'
    else if (role === 'student') register.role = 'STUDENT'
  },
  { immediate: true },
)

function resolveRedirect(): string {
  const redirect =
    typeof route.query.redirect === 'string' ? route.query.redirect : defaultRouteForRole(auth.activeRole)
  return normalizeAppRoute(redirect) ?? defaultRouteForRole(auth.activeRole)
}

async function submitLogin(): Promise<void> {
  if (auth.loading) return

  loginError.value = null
  loginInfo.value = null

  if (!loginEmailValid.value) {
    loginError.value = t('auth.invalidLoginEmail')
    return
  }

  if (!login.password.trim()) {
    loginError.value = t('auth.missingLoginPassword')
    return
  }

  try {
    await auth.login(normalizedLoginEmail.value, login.password)
    await router.push(resolveRedirect())
  } catch (reason) {
    // D12: an inactive account with the correct password gets a distinct 403 (wrong passwords stay 401).
    // The translated text is the server's message ("This account is currently unavailable...") in the UI language.
    loginError.value = isForbiddenApiError(reason)
      ? t('auth.accountUnavailable')
      : resolveApiError(reason, t('auth.loginFailed'))
  }
}

async function submitRegister(): Promise<void> {
  if (registerSubmitting.value) return

  registerError.value = null
  loginInfo.value = null

  if (!normalizedRegisterFullName.value) {
    registerError.value = t('auth.missingName')
    return
  }

  if (!registerEmailValid.value) {
    registerError.value = t('auth.invalidRegisterEmail')
    return
  }

  if (!registerPasswordReady.value) {
    registerError.value = t('auth.invalidRegisterPassword')
    return
  }

  if (!registerPasswordsMatch.value) {
    registerError.value = t('auth.registerPasswordMismatch')
    return
  }

  if (!teacherBioReady.value) {
    registerError.value = t('auth.shortTeacherBio')
    return
  }

  if (!register.agreedToTerms) {
    registerError.value = t('auth.missingConfirmations')
    return
  }

  registerSubmitting.value = true

  try {
    const response =
      register.role === 'STUDENT'
        ? await authApi.registerStudent({
            fullName: normalizedRegisterFullName.value,
            email: normalizedRegisterEmail.value,
            password: register.password,
            confirmPassword: register.confirmPassword,
            preferredLanguage: register.preferredLanguage,
          })
        : await authApi.registerTeacher({
            fullName: normalizedRegisterFullName.value,
            email: normalizedRegisterEmail.value,
            password: register.password,
            confirmPassword: register.confirmPassword,
            bio: register.bio.trim(),
          })

    login.email = response?.email ?? normalizedRegisterEmail.value
    login.password = ''
    register.password = ''
    register.confirmPassword = ''
    loginInfo.value = register.role === 'TEACHER' ? t('auth.teacherCreated') : t('auth.studentCreated')
    activateSignIn()
  } catch (reason) {
    registerError.value = resolveApiError(reason, t('auth.registerFailed'))
  } finally {
    registerSubmitting.value = false
  }
}

function replaceAuthQuery(tab: AuthTab, role?: RegisterRole): void {
  const nextQuery = { ...route.query }

  if (tab === 'register') {
    nextQuery.tab = 'register'
    nextQuery.role = (role ?? register.role) === 'TEACHER' ? 'teacher' : 'student'
  } else {
    delete nextQuery.tab
    delete nextQuery.role
  }

  void router.replace({
    path: route.path,
    query: nextQuery,
  })
}

function activateSignIn(): void {
  activeTab.value = 'signin'
  registerError.value = null
  replaceAuthQuery('signin')
}

function activateRegister(role: RegisterRole = register.role): void {
  activeTab.value = 'register'
  register.role = role
  loginError.value = null
  loginInfo.value = null
  replaceAuthQuery('register', role)
}

function selectRegisterRole(role: RegisterRole): void {
  register.role = role

  if (activeTab.value === 'register') {
    replaceAuthQuery('register', role)
  }
}

function openPublicRoute(path: PublicAuthRoute): void {
  void router.push(path)
}

function openTerms(): void {
  openPublicRoute('/terms')
}

function openPrivacy(): void {
  openPublicRoute('/privacy')
}

function openForgotPassword(): void {
  if (loginEmailValid.value) {
    safeSetItem(storageKeys.recoveryEmail, normalizedLoginEmail.value, getSessionStorage())
  }
  void router.push({ name: 'forgot-password' })
}

function previewRole(role: UserRole, destination: PreviewDestination): void {
  if (!previewEnabled) {
    return
  }

  auth.setPreviewRole(role)
  void router.push(destination)
}

function previewStudent(): void {
  previewRole('STUDENT', '/student/home')
}

function previewTeacher(): void {
  previewRole('TEACHER', '/teacher/dashboard')
}

function previewAdmin(): void {
  previewRole('ADMIN', '/admin/dashboard')
}
</script>
