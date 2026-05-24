<template>
  <section class="public-page auth-showcase-page">
    <div class="auth-showcase-shell">
      <aside class="auth-showcase-art">
        <AppBrand />

        <div class="auth-showcase-copy">
          <p class="auth-showcase-eyebrow">Mandarin for young learners</p>
          <h1>
            Speak Mandarin,<br />
            <span>spark confidence.</span>
          </h1>
          <p>Playful lessons, safe practice, and guided progress for students, families, and teachers.</p>
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
            <strong>Child-safe learning by design</strong>
            <span>Structured access, verified teachers, and age-aware practice tools.</span>
          </div>
        </div>
      </aside>

      <section class="auth-showcase-card">
        <div class="auth-showcase-topbar">
          <LanguageSwitcher show-label label="Language" size="compact" />
        </div>

        <div class="auth-showcase-headers" role="tablist" aria-label="Authentication views">
          <button
            class="auth-showcase-header"
            :class="{ 'auth-showcase-header--active': activeTab === 'signin' }"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'signin'"
            @click="activateSignIn"
          >
            <IconGlyph name="profile" :size="20" />
            <strong>Sign In</strong>
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
            <strong>Create Account</strong>
          </button>
        </div>

        <div class="auth-showcase-forms" :class="`is-${activeTab}`">
          <form v-show="activeTab === 'signin'" class="auth-form-panel" @submit.prevent="submitLogin">
            <div class="auth-form-title">
              <span class="auth-form-kicker">Secure workspace access</span>
              <h2>Welcome back</h2>
              <p>Use one sign-in for student lessons, teacher tools, and admin operations.</p>
            </div>

            <div v-if="loginError" id="login-error" class="soft-banner auth-alert auth-alert--error" role="alert" aria-live="assertive">
              <span>{{ loginError }}</span>
            </div>

            <div v-if="loginInfo" id="login-info" class="soft-banner auth-alert auth-alert--success" role="status" aria-live="polite">
              <span>{{ loginInfo }}</span>
            </div>

            <div class="field">
              <label for="login-email">Email Address</label>
              <input
                id="login-email"
                v-model="login.email"
                class="input auth-input"
                type="email"
                autocomplete="email"
                required
                placeholder="Enter your email"
                :aria-invalid="Boolean(loginError)"
                :aria-describedby="loginDescriptionId"
              />
            </div>

            <div class="field">
              <label for="login-password">Password</label>
              <input
                id="login-password"
                v-model="login.password"
                class="input auth-input"
                type="password"
                autocomplete="current-password"
                required
                placeholder="Enter your password"
                :aria-invalid="Boolean(loginError)"
                :aria-describedby="loginDescriptionId"
              />
            </div>

            <div class="auth-form-row">
              <p class="auth-session-note">Sign-in stays active for this browser session only.</p>
              <button class="auth-text-button" :disabled="!loginEmailValid" type="button" @click="openForgotPassword">
                Forgot password?
              </button>
            </div>

            <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmitLogin" type="submit">
              <span>{{ auth.loading ? 'Signing In...' : 'Sign In' }}</span>
              <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="18" /></span>
            </button>
          </form>

          <form v-show="activeTab === 'register'" class="auth-form-panel auth-form-panel--register" @submit.prevent="submitRegister">
            <div class="auth-form-title">
              <span class="auth-form-kicker">New account setup</span>
              <h2>Create account</h2>
              <p>Select the role first so the form only asks for what is needed.</p>
            </div>

            <div v-if="registerError" id="register-error" class="soft-banner auth-alert auth-alert--error" role="alert" aria-live="assertive">
              <span>{{ registerError }}</span>
            </div>

            <div v-if="registerInfo" id="register-info" class="soft-banner auth-alert auth-alert--success" role="status" aria-live="polite">
              <span>{{ registerInfo }}</span>
            </div>

            <div class="field">
              <label>Account Type</label>
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
                    <strong>Student</strong>
                    <small>Young learner</small>
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
                    <strong>Teacher</strong>
                    <small>Educator</small>
                  </span>
                </button>
              </div>
              <p class="helper-text auth-role-help">
                {{ register.role === 'STUDENT'
                  ? 'Student accounts open lessons, rewards, and AI practice for young learners.'
                  : 'Teacher accounts unlock classes and content after verification is reviewed.' }}
              </p>
            </div>

            <div class="field">
              <label for="register-full-name">Full Name</label>
              <input
                id="register-full-name"
                v-model="register.fullName"
                class="input auth-input"
                autocomplete="name"
                required
                maxlength="120"
                placeholder="Enter your full name"
                :aria-invalid="Boolean(registerError && !normalizedRegisterFullName)"
                :aria-describedby="registerDescriptionId"
              />
            </div>

            <div class="field">
              <label for="register-email">Email Address</label>
              <input
                id="register-email"
                v-model="register.email"
                class="input auth-input"
                type="email"
                autocomplete="email"
                required
                placeholder="Enter your email"
                :aria-invalid="Boolean(registerError && !registerEmailValid)"
                :aria-describedby="registerDescriptionId"
              />
            </div>

            <div class="field">
              <label for="register-password">Password</label>
              <input
                id="register-password"
                v-model="register.password"
                class="input auth-input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                placeholder="Create a password"
                :aria-invalid="Boolean(register.password && !registerPasswordReady)"
                :aria-describedby="registerDescriptionId"
              />
              <p class="helper-text auth-field-hint">Use at least 8 characters.</p>
            </div>

            <div class="field">
              <label for="register-confirm-password">Confirm Password</label>
              <input
                id="register-confirm-password"
                v-model="register.confirmPassword"
                class="input auth-input"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                placeholder="Confirm your password"
                :aria-invalid="Boolean(register.confirmPassword && !registerPasswordsMatch)"
                :aria-describedby="registerDescriptionId"
              />
              <p v-if="register.confirmPassword && !registerPasswordsMatch" class="error-text auth-field-hint">
                Passwords do not match yet.
              </p>
            </div>

            <div v-if="register.role === 'STUDENT'" class="field">
              <label for="register-preferred-language">Preferred Language</label>
              <select id="register-preferred-language" v-model="register.preferredLanguage" class="select auth-input">
                <option value="en">English</option>
                <option value="ms">Bahasa Melayu</option>
                <option value="zh">Simplified Chinese</option>
              </select>
            </div>

            <div v-else class="field">
              <label for="register-teacher-bio">Teacher Bio</label>
              <textarea
                id="register-teacher-bio"
                v-model="register.bio"
                class="textarea auth-input auth-textarea"
                maxlength="1000"
                placeholder="Share your teaching background."
              />
            </div>

            <div class="auth-checkbox-stack">
              <label class="auth-checkbox">
                <input v-model="register.agreedToTerms" type="checkbox" required />
                <span>
                  I agree to the
                  <a href="/terms" @click.prevent="router.push('/terms')">Terms of Service</a>
                  and
                  <a href="/privacy" @click.prevent="router.push('/privacy')">Privacy Policy</a>.
                </span>
              </label>
              <label class="auth-checkbox">
                <input v-model="register.guardianConfirmed" type="checkbox" required />
                <span>I confirm parent/guardian permission where required for child learners.</span>
              </label>
            </div>

            <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmitRegister" type="submit">
              <span>{{ registerSubmitting ? 'Creating...' : 'Create Account' }}</span>
              <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="18" /></span>
            </button>

            <div class="soft-banner auth-register-note">
              <span>Teacher accounts are verified before publishing courses.</span>
            </div>
          </form>
        </div>

        <div class="auth-bottom-strip">
          <div class="auth-bottom-item">
            <span class="auth-bottom-icon is-green"><IconGlyph name="shield" :size="20" /></span>
            <div>
              <strong>Safe for Young Learners</strong>
              <p>Age-aware access and guided practice keep the learning space focused.</p>
            </div>
          </div>
          <div class="auth-bottom-item">
            <span class="auth-bottom-icon is-blue"><IconGlyph name="verified" :size="20" /></span>
            <div>
              <strong>Teacher Verification</strong>
              <p>Educator accounts are reviewed before teaching and publishing access expands.</p>
            </div>
          </div>
          <div class="auth-bottom-item">
            <span class="auth-bottom-icon is-purple"><IconGlyph name="safe" :size="20" /></span>
            <div>
              <strong>One Secure Sign-In</strong>
              <p>Students, teachers, and admins return through a single clean entry point.</p>
            </div>
          </div>
        </div>

        <div v-if="previewEnabled" class="auth-preview-actions">
          <button class="btn btn-ghost" type="button" @click="previewStudent">Preview Student</button>
          <button class="btn btn-ghost" type="button" @click="previewTeacher">Preview Teacher</button>
          <button class="btn btn-ghost" type="button" @click="previewAdmin">Preview Admin</button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * Combined sign-in / register surface (one route, two tab modes).
 *
 * <h2>Why one page</h2>
 * <p>Keeping login + register together cuts a navigation step out
 * of the auth flow and lets the user toggle without losing typed
 * input. The active mode is reflected in the URL (`?mode=register`)
 * so direct links from marketing pages can land on the right tab.</p>
 *
 * <h2>Post-auth redirect</h2>
 * <p>After login we honour {@code ?redirect=} when it normalises
 * through {@link normalizeAppRoute} (defence against open-redirect
 * abuse); otherwise we use {@link defaultRouteForRole} so each
 * persona lands on their dashboard.</p>
 *
 * <h2>Teacher onboarding</h2>
 * <p>Teacher registration calls a different endpoint and the
 * resulting account starts in {@code PENDING_APPROVAL} — the
 * post-register message tells the teacher to wait for admin
 * verification before logging in.</p>
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import { appEnv } from '@/config/env'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { normalizeAppRoute } from '@/utils/routeCompat'
import { defaultRouteForRole } from '@/utils/role'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const previewEnabled = appEnv.useDemoData && appEnv.enablePreviewMode
const loginError = ref<string | null>(null)
const loginInfo = ref<string | null>(null)
const registerError = ref<string | null>(null)
const registerInfo = ref<string | null>(null)
const registerSubmitting = ref(false)
const activeTab = ref<'signin' | 'register'>(route.query.tab === 'register' ? 'register' : 'signin')

const login = reactive({
  email: '',
  password: '',
})

const register = reactive({
  role: 'STUDENT' as 'STUDENT' | 'TEACHER',
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
const registerPasswordReady = computed(() => register.password.length >= 8)
const registerPasswordsMatch = computed(() => register.password === register.confirmPassword && register.confirmPassword.length > 0)
const teacherBioReady = computed(() => register.role !== 'TEACHER' || register.bio.trim().length >= 20)
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
const loginDescriptionId = computed(() => (loginError.value ? 'login-error' : loginInfo.value ? 'login-info' : undefined))
const registerDescriptionId = computed(() =>
  registerError.value ? 'register-error' : registerInfo.value ? 'register-info' : undefined,
)

const requestedRole = computed(() => (route.query.role === 'teacher' ? 'TEACHER' : 'STUDENT'))
watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = tab === 'register' ? 'register' : 'signin'
  },
  { immediate: true },
)

watch(
  requestedRole,
  (nextRole) => {
    register.role = nextRole
  },
  { immediate: true },
)

function resolveRedirect() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : defaultRouteForRole(auth.activeRole)
  return normalizeAppRoute(redirect) ?? defaultRouteForRole(auth.activeRole)
}

async function submitLogin() {
  loginError.value = null
  loginInfo.value = null

  if (!loginEmailValid.value) {
    loginError.value = 'Enter a valid email address before signing in.'
    return
  }

  if (!login.password.trim()) {
    loginError.value = 'Enter your password before signing in.'
    return
  }

  try {
    await auth.login(normalizedLoginEmail.value, login.password)
    await router.push(resolveRedirect())
  } catch (reason) {
    loginError.value = resolveApiError(reason, 'Unable to sign in. Please check your email and password, then try again.')
  }
}

async function submitRegister() {
  registerError.value = null
  registerInfo.value = null
  loginInfo.value = null

  if (!normalizedRegisterFullName.value) {
    registerError.value = 'Enter the account holder name before creating an account.'
    return
  }

  if (!registerEmailValid.value) {
    registerError.value = 'Enter a valid email address before creating an account.'
    return
  }

  if (!registerPasswordReady.value) {
    registerError.value = 'Use at least 8 characters for the password.'
    return
  }

  if (!registerPasswordsMatch.value) {
    registerError.value = 'Make sure both password fields match.'
    return
  }

  if (!teacherBioReady.value) {
    registerError.value = 'Teacher bio must be at least 20 characters so families and admins have useful context.'
    return
  }

  if (!register.agreedToTerms || !register.guardianConfirmed) {
    registerError.value = 'Please accept the required confirmations before creating an account.'
    return
  }

  registerSubmitting.value = true

  try {
    const response = register.role === 'STUDENT'
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

    login.email = response.email
    login.password = ''
    register.password = ''
    register.confirmPassword = ''
    loginInfo.value = register.role === 'TEACHER'
      ? 'Teacher account created. Please sign in and complete verification if required.'
      : 'Student account created. You can sign in now.'
    activateSignIn()
  } catch (reason) {
    registerError.value = resolveApiError(reason, 'Unable to create the account. Please check the form and try again.')
  } finally {
    registerSubmitting.value = false
  }
}

function replaceAuthQuery(tab: 'signin' | 'register', role?: 'STUDENT' | 'TEACHER') {
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

function activateSignIn() {
  activeTab.value = 'signin'
  replaceAuthQuery('signin')
}

function activateRegister(role: 'STUDENT' | 'TEACHER' = register.role) {
  activeTab.value = 'register'
  register.role = role
  replaceAuthQuery('register', role)
}

function selectRegisterRole(role: 'STUDENT' | 'TEACHER') {
  register.role = role

  if (activeTab.value === 'register') {
    replaceAuthQuery('register', role)
  }
}

function openForgotPassword() {
  const query = loginEmailValid.value ? { email: normalizedLoginEmail.value } : undefined
  void router.push({ name: 'forgot-password', query })
}

function previewRole(role: UserRole, destination: string) {
  if (!previewEnabled) {
    return
  }

  auth.setPreviewRole(role)
  void router.push(destination)
}

function previewStudent() {
  previewRole('STUDENT', '/student/home')
}

function previewTeacher() {
  previewRole('TEACHER', '/teacher/dashboard')
}

function previewAdmin() {
  previewRole('ADMIN', '/admin/dashboard')
}
</script>



