<template>
  <section class="public-page auth-reset-page">
    <div class="auth-reset-shell">
      <aside class="auth-reset-art">
        <AppBrand />

        <div class="auth-reset-art-copy">
          <span class="auth-reset-kicker">{{ t('recovery.reset.eyebrow') }}</span>
          <h1>
            {{ t('recovery.reset.heroStart') }}
            <span>{{ t('recovery.reset.heroHighlight') }}</span>
          </h1>
          <p>{{ t('recovery.reset.heroCopy') }}</p>
        </div>

        <div class="auth-reset-scene">
          <span class="auth-reset-cloud auth-reset-cloud--one" />
          <span class="auth-reset-cloud auth-reset-cloud--two" />
          <span class="auth-reset-bamboo" />
          <span class="auth-reset-pagoda" />
          <PandaIllustration class="auth-reset-panda" :size="190" outfit="#6f4cff" accent="#ffcc2f" />
        </div>

        <div class="auth-reset-safety-card">
          <strong><IconGlyph name="shield" :size="18" /> {{ t('recovery.reset.safetyTitle') }}</strong>
          <span>{{ t('recovery.reset.safetyCopy') }}</span>
        </div>
      </aside>

      <div class="surface-card auth-reset-card">
        <div class="auth-reset-language"><LanguageSwitcher size="compact" /></div>
        <div class="auth-reset-card-head">
          <span class="auth-reset-card-icon"><IconGlyph name="safe" :size="24" /></span>
          <div class="auth-form-title">
            <h2>{{ t('recovery.reset.title') }}</h2>
            <p>{{ t('recovery.reset.cardCopy') }}</p>
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
            <label for="reset-token">{{ t('recovery.reset.token') }}</label>
            <input
              id="reset-token"
              v-model="token"
              class="input auth-input"
              autocomplete="one-time-code"
              :placeholder="t('recovery.reset.tokenPlaceholder')"
            />
          </div>

          <div class="field">
            <label for="reset-password">{{ t('recovery.reset.newPassword') }}</label>
            <input
              id="reset-password"
              v-model="password"
              class="input auth-input"
              autocomplete="new-password"
              type="password"
              :placeholder="t('recovery.reset.newPasswordPlaceholder')"
            />
          </div>

          <div class="auth-reset-strength">
            <div class="row-between">
              <span>{{ t('recovery.reset.readiness') }}</span>
              <strong>{{ passwordStrengthLabel }}</strong>
            </div>
            <div class="auth-reset-strength-track">
              <span :class="passwordStrengthClass" :style="{ width: passwordStrengthWidth }" />
            </div>
            <div class="auth-reset-check-grid">
              <span v-for="check in passwordChecks" :key="check.label" :class="{ 'is-met': check.met }">
                <IconGlyph :name="passwordCheckIcon(check.met)" :size="14" /> {{ check.label }}
              </span>
            </div>
          </div>

          <div class="field">
            <label for="reset-confirm-password">{{ t('recovery.reset.confirmPassword') }}</label>
            <input
              id="reset-confirm-password"
              v-model="confirmPassword"
              class="input auth-input"
              autocomplete="new-password"
              type="password"
              :placeholder="t('recovery.reset.confirmPlaceholder')"
            />
          </div>

          <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmit" type="submit">
            <span>{{ submitLabel }}</span>
            <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="16" /></span>
          </button>
        </form>

        <div class="auth-reset-help-row">
          <span>{{ t('recovery.reset.rememberPassword') }}</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/auth">{{
            t('recovery.reset.backSignIn')
          }}</RouterLink>
        </div>

        <div class="auth-reset-help-row">
          <span>{{ t('recovery.reset.needLink') }}</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/forgot-password">{{
            t('recovery.reset.requestOne')
          }}</RouterLink>
        </div>
      </div>
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

const route = useRoute()
const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { recovery: recoveryMessages.en },
    ms: { recovery: recoveryMessages.ms },
    zh: { recovery: recoveryMessages.zh },
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

<style scoped>
.auth-reset-language {
  display: flex;
  justify-content: flex-end;
}
</style>
