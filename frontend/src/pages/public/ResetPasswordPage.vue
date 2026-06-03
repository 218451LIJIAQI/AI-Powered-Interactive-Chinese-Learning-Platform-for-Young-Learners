<template>
  <section class="public-page auth-reset-page">
    <div class="auth-reset-shell">
      <aside class="auth-reset-art">
        <AppBrand />

        <div class="auth-reset-art-copy">
          <span class="auth-reset-kicker">Password Help</span>
          <h1>
            Back to your Mandarin adventure,
            <span>safely.</span>
          </h1>
          <p>Choose a fresh password and keep your lessons, rewards, and progress protected.</p>
        </div>

        <div class="auth-reset-scene">
          <span class="auth-reset-cloud auth-reset-cloud--one" />
          <span class="auth-reset-cloud auth-reset-cloud--two" />
          <span class="auth-reset-bamboo" />
          <span class="auth-reset-pagoda" />
          <PandaIllustration class="auth-reset-panda" :size="190" outfit="#6f4cff" accent="#ffcc2f" />
        </div>

        <div class="auth-reset-safety-card">
          <strong><IconGlyph name="shield" :size="18" /> Safe account recovery</strong>
          <span>Use the reset token from your email and never share it with anyone.</span>
        </div>
      </aside>

      <div class="surface-card auth-reset-card">
        <div class="auth-reset-card-head">
          <span class="auth-reset-card-icon"><IconGlyph name="safe" :size="24" /></span>
          <div class="auth-form-title">
            <h2>Reset your password</h2>
            <p>Enter the reset token from your email and choose a new password.</p>
          </div>
        </div>

        <div v-if="error" class="soft-banner auth-alert auth-alert--error" role="alert" aria-live="assertive">
          <span>{{ error }}</span>
        </div>

        <div v-if="success" class="soft-banner auth-alert auth-alert--success" role="status" aria-live="polite">
          <span>{{ success }}</span>
        </div>

        <form class="auth-reset-form" @submit.prevent="submitReset">
          <div class="field">
            <label for="reset-token">Reset token</label>
            <input
              id="reset-token"
              v-model="token"
              class="input auth-input"
              autocomplete="one-time-code"
              placeholder="Paste your reset token"
            />
          </div>

          <div class="field">
            <label for="reset-password">New password</label>
            <input
              id="reset-password"
              v-model="password"
              class="input auth-input"
              autocomplete="new-password"
              type="password"
              placeholder="Enter a new password"
            />
          </div>

          <div class="auth-reset-strength">
            <div class="row-between">
              <span>Password readiness</span>
              <strong>{{ passwordStrengthLabel }}</strong>
            </div>
            <div class="auth-reset-strength-track">
              <span :class="passwordStrengthClass" :style="{ width: passwordStrengthWidth }" />
            </div>
            <div class="auth-reset-check-grid">
              <span v-for="check in passwordChecks" :key="check.label" :class="{ 'is-met': check.met }">
                <IconGlyph :name="check.met ? 'status' : 'close'" :size="14" /> {{ check.label }}
              </span>
            </div>
          </div>

          <div class="field">
            <label for="reset-confirm-password">Confirm new password</label>
            <input
              id="reset-confirm-password"
              v-model="confirmPassword"
              class="input auth-input"
              autocomplete="new-password"
              type="password"
              placeholder="Confirm your new password"
            />
          </div>

          <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmit" type="submit">
            <span>{{ submitting ? 'Resetting...' : 'Reset password' }}</span>
            <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="16" /></span>
          </button>
        </form>

        <div class="auth-reset-help-row">
          <span>Remember your password?</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/auth">Back to sign in</RouterLink>
        </div>

        <div class="auth-reset-help-row">
          <span>Need a new reset link?</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/forgot-password">Request one</RouterLink>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * Step 2 of the password-recovery flow: redeem the email-link token
 * and set a new password.
 *
 * <h2>Token handling</h2>
 * <p>The token arrives as a URL query parameter and is sent back as
 * an opaque string — the frontend never inspects or persists it.
 * Backend validates the SHA-256 hash, checks the 60-minute TTL,
 * and consumes the token atomically with the password change so
 * a single token cannot be redeemed twice.</p>
 *
 * <h2>Post-success behaviour</h2>
 * <p>On success the user is informed to sign in again — the auth
 * store is intentionally NOT auto-logged-in here so the existing
 * password is properly invalidated everywhere before the new one
 * goes live.</p>
 */
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { resolveApiError } from '@/utils/apiError'
import { firstQueryText, removeSensitiveRouteState } from '@/utils/sensitiveData'

const route = useRoute()
const router = useRouter()

const token = ref(firstQueryText(route.query.token))
const password = ref('')
const confirmPassword = ref('')
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const submitting = ref(false)

removeSensitiveQueryFromAddressBar()

const canSubmit = computed(
  () =>
    !submitting.value &&
    token.value.trim().length > 0 &&
    password.value.length >= 8 &&
    password.value === confirmPassword.value,
)
const passwordChecks = computed(() => [
  { label: 'At least 8 characters', met: password.value.length >= 8 },
  { label: 'Includes a number', met: /\d/.test(password.value) },
  { label: 'Passwords match', met: Boolean(confirmPassword.value && password.value === confirmPassword.value) },
])
const passwordStrengthScore = computed(() => passwordChecks.value.filter((check) => check.met).length)
const passwordStrengthLabel = computed(
  () => ['Start typing', 'Getting close', 'Almost ready', 'Ready'][passwordStrengthScore.value],
)
const passwordStrengthClass = computed(() => `is-${passwordStrengthScore.value}`)
const passwordStrengthWidth = computed(() => `${(passwordStrengthScore.value / passwordChecks.value.length) * 100}%`)

async function submitReset() {
  if (submitting.value) return

  error.value = null
  success.value = null

  if (password.value.length < 8) {
    error.value = 'Password must be at least 8 characters.'
    return
  }

  if (password.value !== confirmPassword.value) {
    error.value = 'Confirm password does not match.'
    return
  }

  submitting.value = true
  try {
    await authApi.resetPassword({
      token: token.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
    })
    token.value = ''
    password.value = ''
    confirmPassword.value = ''
    success.value = 'Password reset successfully. You can sign in with your new password now.'
  } catch (reason) {
    error.value = resolveApiError(
      reason,
      'Unable to reset the password. Please request a new reset link and try again.',
    )
  } finally {
    submitting.value = false
  }
}

function removeSensitiveQueryFromAddressBar() {
  const { changed, query, hash } = removeSensitiveRouteState(route.query, route.hash)

  if (changed) {
    void router.replace({ name: 'reset-password', query, hash })
  }
}
</script>
