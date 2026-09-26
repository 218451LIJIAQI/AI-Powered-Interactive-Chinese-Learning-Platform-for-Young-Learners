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
          <p>{{ t('auth.heroCopy') }}</p>
        </div>

        <div class="auth-showcase-scene">
          <div class="auth-showcase-bamboo" />
          <div class="auth-showcase-pagoda" />
          <PandaIllustration class="auth-showcase-panda" :size="230" outfit="#ef6b40" accent="#f6c24d" />
          <div class="auth-showcase-river-fish"><IconGlyph name="fun" :size="30" /></div>
        </div>

        <div class="auth-showcase-lower-pattern" aria-hidden="true">
          <span class="auth-pattern-line" />
          <span class="auth-pattern-dot auth-pattern-dot--one" />
          <span class="auth-pattern-dot auth-pattern-dot--two" />
          <span class="auth-pattern-dot auth-pattern-dot--three" />
          <span class="auth-pattern-dot auth-pattern-dot--four" />
          <span class="auth-pattern-card auth-pattern-card--one" />
          <span class="auth-pattern-card auth-pattern-card--two" />
          <span class="auth-pattern-spark auth-pattern-spark--one"><IconGlyph name="star" :size="18" /></span>
          <span class="auth-pattern-spark auth-pattern-spark--two"><IconGlyph name="fun" :size="22" /></span>
        </div>

        <div class="auth-showcase-safety">
          <div class="auth-showcase-safety-icon"><IconGlyph name="shield" :size="22" /></div>
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

        <div class="auth-showcase-headers" role="tablist" :aria-label="t('auth.views')">
          <button
            class="auth-showcase-header"
            :class="{ 'auth-showcase-header--active': activeTab === 'signin' }"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'signin'"
            @click="activateSignIn"
          >
            <IconGlyph name="profile" :size="20" />
            <strong>{{ t('auth.signIn') }}</strong>
          </button>
          <button
            class="auth-showcase-header"
            :class="{ 'auth-showcase-header--active': activeTab === 'register' }"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'register'"
            @click="activateRegister()"
          >
            <IconGlyph name="status" :size="20" />
            <strong>{{ t('auth.createAccount') }}</strong>
          </button>
        </div>

        <div class="auth-showcase-forms" :class="`is-${activeTab}`">
          <form v-show="activeTab === 'signin'" class="auth-form-panel" @submit.prevent="submitLogin">
            <div class="auth-form-title">
              <span class="auth-form-kicker">{{ t('auth.signInKicker') }}</span>
              <h2>{{ t('auth.welcome') }}</h2>
              <p>{{ t('auth.signInCopy') }}</p>
            </div>

            <div
              v-if="loginError"
              id="login-error"
              class="soft-banner auth-alert auth-alert--error"
              role="alert"
              aria-live="assertive"
            >
              <span>{{ loginError }}</span>
            </div>

            <div
              v-if="loginInfo"
              id="login-info"
              class="soft-banner auth-alert auth-alert--success"
              role="status"
              aria-live="polite"
            >
              <span>{{ loginInfo }}</span>
            </div>

            <div class="field">
              <label for="login-email">{{ t('auth.email') }}</label>
              <input
                id="login-email"
                v-model="login.email"
                class="input auth-input"
                type="email"
                autocomplete="email"
                required
                :placeholder="t('auth.emailPlaceholder')"
                :aria-invalid="Boolean(loginError)"
                :aria-describedby="loginDescriptionId"
              />
            </div>

            <div class="field">
              <label for="login-password">{{ t('auth.password') }}</label>
              <input
                id="login-password"
                v-model="login.password"
                class="input auth-input"
                type="password"
                autocomplete="current-password"
                required
                :placeholder="t('auth.passwordPlaceholder')"
                :aria-invalid="Boolean(loginError)"
                :aria-describedby="loginDescriptionId"
              />
            </div>

            <div class="auth-form-row">
              <p class="auth-session-note">{{ t('auth.sessionNote') }}</p>
              <button class="auth-text-button" :disabled="!loginEmailValid" type="button" @click="openForgotPassword">
                {{ t('auth.forgotPassword') }}
              </button>
            </div>

            <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmitLogin" type="submit">
              <span>{{ loginSubmitLabel }}</span>
              <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="18" /></span>
            </button>
          </form>

          <form
            v-show="activeTab === 'register'"
            class="auth-form-panel auth-form-panel--register"
            @submit.prevent="submitRegister"
          >
            <div class="auth-form-title">
              <span class="auth-form-kicker">{{ t('auth.registerKicker') }}</span>
              <h2>{{ t('auth.registerTitle') }}</h2>
              <p>{{ t('auth.registerCopy') }}</p>
            </div>

            <div
              v-if="registerError"
              id="register-error"
              class="soft-banner auth-alert auth-alert--error"
              role="alert"
              aria-live="assertive"
            >
              <span>{{ registerError }}</span>
            </div>

            <div
              v-if="registerInfo"
              id="register-info"
              class="soft-banner auth-alert auth-alert--success"
              role="status"
              aria-live="polite"
            >
              <span>{{ registerInfo }}</span>
            </div>

            <div class="field">
              <label>{{ t('auth.accountType') }}</label>
              <div class="auth-role-grid">
                <button
                  class="auth-role-card"
                  :class="{ 'is-active': register.role === 'STUDENT' }"
                  type="button"
                  :aria-pressed="register.role === 'STUDENT'"
                  @click="selectRegisterRole('STUDENT')"
                >
                  <span class="auth-role-avatar"><IconGlyph name="students" :size="24" /></span>
                  <span>
                    <strong>{{ t('auth.student') }}</strong>
                    <small>{{ t('auth.youngLearner') }}</small>
                  </span>
                </button>
                <button
                  class="auth-role-card"
                  :class="{ 'is-active': register.role === 'TEACHER' }"
                  type="button"
                  :aria-pressed="register.role === 'TEACHER'"
                  @click="selectRegisterRole('TEACHER')"
                >
                  <span class="auth-role-avatar"><IconGlyph name="teacher" :size="24" /></span>
                  <span>
                    <strong>{{ t('auth.teacher') }}</strong>
                    <small>{{ t('auth.educator') }}</small>
                  </span>
                </button>
              </div>
              <p class="helper-text auth-role-help">
                {{ registerRoleHelp }}
              </p>
            </div>

            <div class="field">
              <label for="register-full-name">{{ t('auth.fullName') }}</label>
              <input
                id="register-full-name"
                v-model="register.fullName"
                class="input auth-input"
                autocomplete="name"
                required
                maxlength="120"
                :placeholder="t('auth.fullNamePlaceholder')"
                :aria-invalid="Boolean(registerError && !normalizedRegisterFullName)"
                :aria-describedby="registerDescriptionId"
              />
            </div>

            <div class="field">
              <label for="register-email">{{ t('auth.email') }}</label>
              <input
                id="register-email"
                v-model="register.email"
                class="input auth-input"
                type="email"
                autocomplete="email"
                required
                :placeholder="t('auth.emailPlaceholder')"
                :aria-invalid="Boolean(registerError && !registerEmailValid)"
                :aria-describedby="registerDescriptionId"
              />
            </div>

            <div class="field">
              <label for="register-password">{{ t('auth.password') }}</label>
              <input
                id="register-password"
                v-model="register.password"
                class="input auth-input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                :placeholder="t('auth.createPassword')"
                :aria-invalid="Boolean(register.password && !registerPasswordReady)"
                :aria-describedby="registerDescriptionId"
              />
              <p class="helper-text auth-field-hint">{{ t('auth.passwordHint') }}</p>
            </div>

            <div class="field">
              <label for="register-confirm-password">{{ t('auth.confirmPassword') }}</label>
              <input
                id="register-confirm-password"
                v-model="register.confirmPassword"
                class="input auth-input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                :placeholder="t('auth.confirmPasswordPlaceholder')"
                :aria-invalid="Boolean(register.confirmPassword && !registerPasswordsMatch)"
                :aria-describedby="registerDescriptionId"
              />
              <p v-if="register.confirmPassword && !registerPasswordsMatch" class="error-text auth-field-hint">
                {{ t('auth.passwordMismatch') }}
              </p>
            </div>

            <div v-if="register.role === 'STUDENT'" class="field">
              <label for="register-preferred-language">{{ t('auth.preferredLanguage') }}</label>
              <select id="register-preferred-language" v-model="register.preferredLanguage" class="select auth-input">
                <option value="en">{{ t('auth.english') }}</option>
                <option value="ms">{{ t('auth.malay') }}</option>
                <option value="zh">{{ t('auth.chinese') }}</option>
              </select>
            </div>

            <div v-else class="field">
              <label for="register-teacher-bio">{{ t('auth.teacherBio') }}</label>
              <textarea
                id="register-teacher-bio"
                v-model="register.bio"
                class="textarea auth-input auth-textarea"
                maxlength="1000"
                :placeholder="t('auth.teacherBioPlaceholder')"
              />
            </div>

            <div class="auth-checkbox-stack">
              <label class="auth-checkbox">
                <input v-model="register.agreedToTerms" type="checkbox" required />
                <span>
                  {{ t('auth.agreePrefix') }}
                  <a href="/terms" @click.prevent="openTerms">{{ t('auth.terms') }}</a>
                  {{ t('auth.and') }}
                  <a href="/privacy" @click.prevent="openPrivacy">{{ t('auth.privacy') }}</a
                  >{{ t('auth.agreementEnd') }}
                </span>
              </label>
              <label class="auth-checkbox">
                <input v-model="register.guardianConfirmed" type="checkbox" required />
                <span>{{ t('auth.guardian') }}</span>
              </label>
            </div>

            <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmitRegister" type="submit">
              <span>{{ registerSubmitLabel }}</span>
              <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="18" /></span>
            </button>

            <div class="soft-banner auth-register-note">
              <span>{{ t('auth.teacherNote') }}</span>
            </div>
          </form>
        </div>

        <div class="auth-bottom-strip">
          <div class="auth-bottom-item">
            <span class="auth-bottom-icon is-green"><IconGlyph name="shield" :size="20" /></span>
            <div>
              <strong>{{ t('auth.safetyFeatureTitle') }}</strong>
              <p>{{ t('auth.safetyFeatureCopy') }}</p>
            </div>
          </div>
          <div class="auth-bottom-item">
            <span class="auth-bottom-icon is-blue"><IconGlyph name="verified" :size="20" /></span>
            <div>
              <strong>{{ t('auth.teacherFeatureTitle') }}</strong>
              <p>{{ t('auth.teacherFeatureCopy') }}</p>
            </div>
          </div>
          <div class="auth-bottom-item">
            <span class="auth-bottom-icon is-purple"><IconGlyph name="safe" :size="20" /></span>
            <div>
              <strong>{{ t('auth.signInFeatureTitle') }}</strong>
              <p>{{ t('auth.signInFeatureCopy') }}</p>
            </div>
          </div>
        </div>

        <div v-if="previewEnabled" class="auth-preview-actions">
          <button class="btn btn-ghost" type="button" @click="previewStudent">{{ t('auth.previewStudent') }}</button>
          <button class="btn btn-ghost" type="button" @click="previewTeacher">{{ t('auth.previewTeacher') }}</button>
          <button class="btn btn-ghost" type="button" @click="previewAdmin">{{ t('auth.previewAdmin') }}</button>
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
import type { UserRole } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { isPasswordLengthValid } from '@/utils/password'
import { normalizeAppRoute } from '@/utils/routeCompat'
import { defaultRouteForRole } from '@/utils/role'
import { getSessionStorage, safeSetItem, storageKeys } from '@/utils/storage'
import '@/styles/auth.css'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { t } = useI18n({
  messages: {
    en: { auth: authMessages.en },
    ms: { auth: authMessages.ms },
    zh: { auth: authMessages.zh },
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
const registerInfo = ref<string | null>(null)
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
  preferredLanguage: 'en',
  bio: '',
  agreedToTerms: false,
  guardianConfirmed: false,
})

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const normalizedLoginEmail = computed(() => login.email.trim().toLowerCase())
const loginEmailValid = computed(() => emailPattern.test(normalizedLoginEmail.value))
const canSubmitLogin = computed(() => !auth.loading && loginEmailValid.value && login.password.trim().length > 0)
const normalizedRegisterFullName = computed(() => register.fullName.trim())
const normalizedRegisterEmail = computed(() => register.email.trim().toLowerCase())
const registerEmailValid = computed(() => emailPattern.test(normalizedRegisterEmail.value))
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
    register.agreedToTerms &&
    register.guardianConfirmed,
)
const loginDescriptionId = computed(() =>
  loginError.value ? 'login-error' : loginInfo.value ? 'login-info' : undefined,
)
const registerDescriptionId = computed(() =>
  registerError.value ? 'register-error' : registerInfo.value ? 'register-info' : undefined,
)

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = tab === 'register' ? 'register' : 'signin'
  },
  { immediate: true },
)

watch(
  () => (route.query.role === 'teacher' ? ('TEACHER' as const) : ('STUDENT' as const)),
  (nextRole) => {
    register.role = nextRole
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
    loginError.value = resolveApiError(reason, t('auth.loginFailed'))
  }
}

async function submitRegister(): Promise<void> {
  if (registerSubmitting.value) return

  registerError.value = null
  registerInfo.value = null
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

  if (!register.agreedToTerms || !register.guardianConfirmed) {
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
  registerInfo.value = null
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
