<template>
  <section class="public-page auth-reset-page">
    <div class="auth-reset-shell">
      <aside class="auth-reset-art">
        <AppBrand />

        <div class="auth-reset-art-copy">
          <span class="auth-reset-kicker">Account Recovery</span>
          <h1>
            Find your way back,
            <span>safely.</span>
          </h1>
          <p>Enter your account email and we will send a secure password reset link if the account exists.</p>
        </div>

        <div class="auth-reset-scene">
          <span class="auth-reset-cloud auth-reset-cloud--one" />
          <span class="auth-reset-cloud auth-reset-cloud--two" />
          <span class="auth-reset-bamboo" />
          <span class="auth-reset-pagoda" />
          <PandaIllustration class="auth-reset-panda" :size="190" outfit="#ff8a3d" accent="#6f4cff" />
        </div>

        <div class="auth-reset-safety-card">
          <strong><IconGlyph name="shield" :size="18" /> Secure reset flow</strong>
          <span>Only use reset links from your own inbox and never share your reset token with anyone.</span>
        </div>
      </aside>

      <div class="surface-card auth-reset-card">
        <div class="auth-reset-card-head">
          <span class="auth-reset-card-icon"><IconGlyph name="bell" :size="24" /></span>
          <div class="auth-form-title">
            <h2>Forgot password?</h2>
            <p>We will email you the next step for resetting your MandarinSpark password.</p>
          </div>
        </div>

        <div v-if="error" class="soft-banner auth-alert auth-alert--error" role="alert" aria-live="assertive">
          <span>{{ error }}</span>
        </div>

        <div v-if="success" class="soft-banner auth-alert auth-alert--success" role="status" aria-live="polite">
          <span>{{ success }}</span>
        </div>

        <form v-if="!success" class="auth-reset-form" @submit.prevent="submitForgotPassword">
          <div class="field">
            <label for="forgot-email">Account email</label>
            <input
              id="forgot-email"
              v-model="email"
              class="input auth-input"
              type="email"
              autocomplete="email"
              placeholder="Enter your email address"
              :aria-invalid="Boolean(error)"
            />
          </div>

          <div class="auth-reset-strength">
            <div class="row-between">
              <span>Recovery steps</span>
              <strong>Email link</strong>
            </div>
            <div class="auth-reset-check-grid">
              <span class="is-met"><IconGlyph name="status" :size="14" /> Enter your registered email</span>
              <span class="is-met"><IconGlyph name="status" :size="14" /> Open the reset link in your inbox</span>
              <span class="is-met"><IconGlyph name="status" :size="14" /> Choose a new password</span>
            </div>
          </div>

          <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmit" type="submit">
            <span>{{ submitting ? 'Sending reset link...' : 'Send reset link' }}</span>
            <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="16" /></span>
          </button>
        </form>

        <div v-else class="auth-reset-form">
          <div class="auth-reset-strength">
            <div class="row-between">
              <span>Next step</span>
              <strong>Check your inbox</strong>
            </div>
            <div class="auth-reset-check-grid">
              <span class="is-met"><IconGlyph name="status" :size="14" /> Check inbox, spam, and all mail</span>
              <span class="is-met"><IconGlyph name="status" :size="14" /> Open the link from MandarinSpark</span>
              <span class="is-met"><IconGlyph name="status" :size="14" /> Reset your password on the secure page</span>
            </div>
          </div>

          <RouterLink class="btn btn-accent btn-block auth-submit-button" to="/auth">
            <span>Back to sign in</span>
            <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="16" /></span>
          </RouterLink>

          <button class="auth-text-button auth-reset-link" type="button" @click="requestAnotherEmail">
            Use another email
          </button>
        </div>

        <div class="auth-reset-help-row">
          <span>Remember your password?</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/auth">Back to sign in</RouterLink>
        </div>

        <div class="auth-reset-help-row">
          <span>Already have a reset token?</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/reset-password">Reset password</RouterLink>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * Step 1 of the password-recovery flow: collect the user's email
 * and ask the backend to send a reset link.
 *
 * <h2>Uniform response</h2>
 * <p>The success message is identical whether or not the email
 * is registered — this is paired with the backend's user-enumeration
 * defence so an attacker cannot use this page to probe for valid
 * accounts.</p>
 *
 * <h2>Rate limit</h2>
 * <p>The backend's {@code PublicAuthRequestLimiter} caps requests
 * per IP+email pair. A 429 is surfaced as a friendly "please wait
 * before trying again" message via {@link resolveApiError}.</p>
 */
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { resolveApiError } from '@/utils/apiError'
import { firstQueryText, removeSensitiveRouteState } from '@/utils/sensitiveData'
import { getSessionStorage, safeGetItem, safeRemoveItem, storageKeys } from '@/utils/storage'

const route = useRoute()
const router = useRouter()

const email = ref(readInitialRecoveryEmail())
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const submitting = ref(false)

removeSensitiveQueryFromAddressBar()

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const normalizedEmail = computed(() => email.value.trim().toLowerCase())
const emailValid = computed(() => emailPattern.test(normalizedEmail.value))
const canSubmit = computed(() => emailValid.value && !submitting.value)

async function submitForgotPassword() {
  if (submitting.value) return

  error.value = null
  success.value = null

  if (!emailValid.value) {
    error.value = 'Enter a valid email address before requesting a reset link.'
    return
  }

  submitting.value = true
  try {
    await authApi.forgotPassword(normalizedEmail.value)
    success.value = `If ${normalizedEmail.value} is registered, reset instructions will be sent shortly.`
  } catch (reason) {
    error.value = resolveApiError(reason, 'Unable to request a password reset right now. Please try again later.')
  } finally {
    submitting.value = false
  }
}

function requestAnotherEmail() {
  success.value = null
  error.value = null
  email.value = ''
}

function readInitialRecoveryEmail(): string {
  const queryEmail = firstQueryText(route.query.email)
  const storage = getSessionStorage()
  const storedEmail = safeGetItem(storageKeys.recoveryEmail, storage)?.trim() ?? ''
  safeRemoveItem(storageKeys.recoveryEmail, storage)
  return queryEmail || storedEmail
}

function removeSensitiveQueryFromAddressBar() {
  const { changed, query, hash } = removeSensitiveRouteState(route.query, route.hash)

  if (changed) {
    void router.replace({ name: 'forgot-password', query, hash })
  }
}
</script>
