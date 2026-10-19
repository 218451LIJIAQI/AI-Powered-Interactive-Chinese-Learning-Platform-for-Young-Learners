<template>
  <section class="public-page auth-showcase-page">
    <div class="auth-showcase-shell">
      <aside class="auth-showcase-art">
        <AppBrand />

        <div class="auth-showcase-copy">
          <p class="auth-showcase-eyebrow">{{ t('recovery.reset.eyebrow') }}</p>
          <h1>
            {{ t('recovery.reset.heroStart') }}
            <span>{{ t('recovery.reset.heroHighlight') }}</span>
          </h1>
          <p class="auth-showcase-lead">{{ t('recovery.reset.heroCopy') }}</p>
        </div>

        <div class="auth-showcase-scene" aria-hidden="true">
          <span class="auth-scene-bamboo" />
          <span class="auth-scene-card auth-scene-card--one" />
          <span class="auth-scene-card auth-scene-card--two" />
          <PandaIllustration class="auth-showcase-panda" :size="190" outfit="#6f4cff" accent="#ffcc2f" />
        </div>

        <div class="auth-showcase-safety">
          <span class="auth-showcase-safety-icon" aria-hidden="true"><IconGlyph name="shield" :size="20" /></span>
          <div>
            <strong>{{ t('recovery.reset.safetyTitle') }}</strong>
            <span>{{ t('recovery.reset.safetyCopy') }}</span>
          </div>
        </div>
      </aside>

      <section class="auth-showcase-card">
        <div class="auth-showcase-topbar"><LanguageSwitcher size="compact" /></div>

        <div class="auth-recovery-head">
          <span class="auth-recovery-icon" aria-hidden="true"><IconGlyph name="safe" :size="22" /></span>
          <div class="auth-form-title">
            <h2>{{ t('recovery.reset.title') }}</h2>
            <p>{{ t('recovery.reset.cardCopy') }}</p>
          </div>
        </div>

        <div v-if="error" id="reset-error" class="alert alert-danger" role="alert" aria-live="assertive">
          <IconGlyph name="alert" :size="18" />
          <span>{{ error }}</span>
        </div>

        <div v-if="success" class="alert alert-success" role="status" aria-live="polite">
          <IconGlyph name="status" :size="18" />
          <span>{{ success }}</span>
        </div>

        <form class="auth-form-panel" @submit.prevent="submitReset">
          <div class="field">
            <label for="reset-token">
              {{ t('recovery.reset.token') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="reset-token"
              v-model="token"
              class="input"
              autocomplete="one-time-code"
              aria-required="true"
              :placeholder="t('recovery.reset.tokenPlaceholder')"
            />
          </div>

          <div class="field">
            <label for="reset-password">
              {{ t('recovery.reset.newPassword') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="reset-password"
              v-model="password"
              class="input"
              autocomplete="new-password"
              type="password"
              aria-required="true"
              :placeholder="t('recovery.reset.newPasswordPlaceholder')"
              :aria-invalid="Boolean(error)"
              :aria-describedby="error ? 'reset-error reset-password-strength' : 'reset-password-strength'"
            />
          </div>

          <div id="reset-password-strength" class="auth-strength">
            <p class="auth-steps-head">
              <span>{{ t('recovery.reset.readiness') }}</span>
              <strong>{{ passwordStrengthLabel }}</strong>
            </p>
            <div class="auth-reset-strength-track" aria-hidden="true">
              <span :class="passwordStrengthClass" :style="{ width: passwordStrengthWidth }" />
            </div>
            <ul class="auth-check-list">
              <li v-for="check in passwordChecks" :key="check.label" :class="{ 'is-met': check.met }">
                <IconGlyph :name="passwordCheckIcon(check.met)" :size="16" />
                <span>{{ check.label }}</span>
              </li>
            </ul>
          </div>

          <div class="field">
            <label for="reset-confirm-password">
              {{ t('recovery.reset.confirmPassword') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="reset-confirm-password"
              v-model="confirmPassword"
              class="input"
              autocomplete="new-password"
              type="password"
              aria-required="true"
              :placeholder="t('recovery.reset.confirmPlaceholder')"
              :aria-invalid="confirmMismatch"
              :aria-describedby="confirmMismatch ? 'reset-confirm-error' : undefined"
            />
            <p v-if="confirmMismatch" id="reset-confirm-error" class="field-error">
              {{ t('recovery.reset.mismatch') }}
            </p>
          </div>

          <button
            class="btn btn-primary btn-lg btn-block auth-submit-button"
            :disabled="!canSubmit"
            :aria-busy="submitting"
            type="submit"
          >
            <span>{{ submitLabel }}</span>
            <IconGlyph name="chevronRight" :size="18" />
          </button>
        </form>

        <div class="auth-recovery-links">
          <p>
            <span>{{ t('recovery.reset.rememberPassword') }}</span>
            <RouterLink class="btn btn-link" to="/auth">{{ t('recovery.reset.backSignIn') }}</RouterLink>
          </p>
          <p>
            <span>{{ t('recovery.reset.needLink') }}</span>
            <RouterLink class="btn btn-link" to="/forgot-password">{{ t('recovery.reset.requestOne') }}</RouterLink>
          </p>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { resolveApiError } from '@/utils/apiError'
import { isPasswordLengthValid } from '@/utils/password'
import { firstQueryText, removeSensitiveRouteState } from '@/utils/sensitiveData'
import '@/styles/auth.css'

const route = useRoute()
const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { recovery: recoveryMessages.en },
    ms: { recovery: recoveryMessages.ms },
    'zh-CN': { recovery: recoveryMessages['zh-CN'] },
  },
})

const token = ref(firstQueryText(route.query.token))
const password = ref('')
const confirmPassword = ref('')
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const submitting = ref(false)

type PasswordCheckIcon = 'close' | 'status'
const passwordStrengthKeys = ['strengthStart', 'strengthClose', 'strengthAlmost', 'strengthReady'] as const

removeSensitiveQueryFromAddressBar()

/** Shown once the learner has typed a confirmation that differs from the new password. */
const confirmMismatch = computed(() => Boolean(confirmPassword.value) && password.value !== confirmPassword.value)
const canSubmit = computed(
  () =>
    !submitting.value &&
    token.value.trim().length > 0 &&
    isPasswordLengthValid(password.value) &&
    password.value === confirmPassword.value,
)
const passwordChecks = computed(() => [
  { label: t('recovery.reset.atLeastEight'), met: password.value.length >= 8 },
  { label: t('recovery.reset.withinLimit'), met: isPasswordLengthValid(password.value) },
  { label: t('recovery.reset.includesNumber'), met: /\d/.test(password.value) },
  {
    label: t('recovery.reset.passwordsMatch'),
    met: Boolean(confirmPassword.value && password.value === confirmPassword.value),
  },
])
const passwordStrengthScore = computed(() => passwordChecks.value.filter((check) => check.met).length)
const passwordStrengthLabel = computed(() =>
  t(`recovery.reset.${passwordStrengthKeys[Math.min(passwordStrengthScore.value, 3)]}`),
)
const passwordStrengthClass = computed(() => `is-${Math.min(passwordStrengthScore.value, 3)}`)
const passwordStrengthWidth = computed(() => `${(passwordStrengthScore.value / passwordChecks.value.length) * 100}%`)
const submitLabel = computed(() =>
  submitting.value ? t('recovery.reset.resetting') : t('recovery.reset.resetPassword'),
)

async function submitReset(): Promise<void> {
  if (submitting.value) return

  error.value = null
  success.value = null

  if (!isPasswordLengthValid(password.value)) {
    error.value = t('recovery.reset.invalidPassword')
    return
  }

  if (password.value !== confirmPassword.value) {
    error.value = t('recovery.reset.mismatch')
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
    success.value = t('recovery.reset.success')
  } catch (reason) {
    error.value = resolveApiError(reason, t('recovery.reset.error'))
  } finally {
    submitting.value = false
  }
}

function removeSensitiveQueryFromAddressBar(): void {
  const { changed, query, hash } = removeSensitiveRouteState(route.query, route.hash)

  if (changed) {
    void router.replace({ name: 'reset-password', query, hash })
  }
}

function passwordCheckIcon(met: boolean): PasswordCheckIcon {
  return met ? 'status' : 'close'
}
</script>
