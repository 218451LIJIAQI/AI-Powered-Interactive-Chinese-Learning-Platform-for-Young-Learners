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
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { resolveApiError } from '@/utils/apiError'

const route = useRoute()
const initialEmail = typeof route.query.email === 'string' ? route.query.email : ''

const email = ref(initialEmail)
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const submitting = ref(false)

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const normalizedEmail = computed(() => email.value.trim().toLowerCase())
const emailValid = computed(() => emailPattern.test(normalizedEmail.value))
const canSubmit = computed(() => emailValid.value && !submitting.value)

async function submitForgotPassword() {
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
</script>
