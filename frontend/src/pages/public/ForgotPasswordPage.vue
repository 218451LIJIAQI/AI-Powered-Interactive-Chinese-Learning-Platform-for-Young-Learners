<template>
  <section class="public-page auth-showcase-page">
    <div class="auth-showcase-shell">
      <aside class="auth-showcase-art">
        <AppBrand />

        <div class="auth-showcase-copy">
          <p class="auth-showcase-eyebrow">{{ t('recovery.forgot.eyebrow') }}</p>
          <h1>
            {{ t('recovery.forgot.heroStart') }}
            <span>{{ t('recovery.forgot.heroHighlight') }}</span>
          </h1>
          <p class="auth-showcase-lead">{{ t('recovery.forgot.heroCopy') }}</p>
        </div>

        <div class="auth-showcase-scene" aria-hidden="true">
          <span class="auth-scene-bamboo" />
          <span class="auth-scene-card auth-scene-card--one" />
          <span class="auth-scene-card auth-scene-card--two" />
          <PandaIllustration class="auth-showcase-panda" :size="190" outfit="#ff8a3d" accent="#6f4cff" />
        </div>

        <div class="auth-showcase-safety">
          <span class="auth-showcase-safety-icon" aria-hidden="true"><IconGlyph name="shield" :size="20" /></span>
          <div>
            <strong>{{ t('recovery.forgot.safetyTitle') }}</strong>
            <span>{{ t('recovery.forgot.safetyCopy') }}</span>
          </div>
        </div>
      </aside>

      <section class="auth-showcase-card">
        <div class="auth-showcase-topbar"><LanguageSwitcher size="compact" /></div>

        <div class="auth-recovery-head">
          <span class="auth-recovery-icon" aria-hidden="true"><IconGlyph name="key" :size="22" /></span>
          <div class="auth-form-title">
            <h2>{{ t('recovery.forgot.title') }}</h2>
            <p>{{ t('recovery.forgot.cardCopy') }}</p>
          </div>
        </div>

        <div v-if="error" id="forgot-error" class="alert alert-danger" role="alert" aria-live="assertive">
          <IconGlyph name="alert" :size="18" />
          <span>{{ error }}</span>
        </div>

        <div v-if="success" class="alert alert-success" role="status" aria-live="polite">
          <IconGlyph name="status" :size="18" />
          <span>{{ success }}</span>
        </div>

        <form v-if="!success" class="auth-form-panel" @submit.prevent="submitForgotPassword">
          <div class="field">
            <label for="forgot-email">
              {{ t('recovery.forgot.accountEmail') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <input
              id="forgot-email"
              v-model="email"
              class="input"
              type="email"
              autocomplete="email"
              aria-required="true"
              :placeholder="t('recovery.forgot.emailPlaceholder')"
              :aria-invalid="Boolean(error)"
              :aria-describedby="error ? 'forgot-error' : 'forgot-steps'"
            />
          </div>

          <div id="forgot-steps" class="auth-steps">
            <p class="auth-steps-head">
              <span>{{ t('recovery.forgot.recoverySteps') }}</span>
              <strong>{{ t('recovery.forgot.emailLink') }}</strong>
            </p>
            <ol>
              <li>{{ t('recovery.forgot.enterEmail') }}</li>
              <li>{{ t('recovery.forgot.openLink') }}</li>
              <li>{{ t('recovery.forgot.choosePassword') }}</li>
            </ol>
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

        <div v-else class="auth-form-panel">
          <div class="auth-steps">
            <p class="auth-steps-head">
              <span>{{ t('recovery.forgot.nextStep') }}</span>
              <strong>{{ t('recovery.forgot.checkInbox') }}</strong>
            </p>
            <ol>
              <li>{{ t('recovery.forgot.checkAllMail') }}</li>
              <li>{{ t('recovery.forgot.openBrandLink') }}</li>
              <li>{{ t('recovery.forgot.secureReset') }}</li>
            </ol>
          </div>

          <RouterLink class="btn btn-primary btn-lg btn-block auth-submit-button" to="/auth">
            <span>{{ t('recovery.forgot.backSignIn') }}</span>
            <IconGlyph name="chevronRight" :size="18" />
          </RouterLink>

          <button class="btn btn-link auth-recovery-retry" type="button" @click="requestAnotherEmail">
            {{ t('recovery.forgot.anotherEmail') }}
          </button>
        </div>

        <div class="auth-recovery-links">
          <p>
            <span>{{ t('recovery.forgot.rememberPassword') }}</span>
            <RouterLink class="btn btn-link" to="/auth">{{ t('recovery.forgot.backSignIn') }}</RouterLink>
          </p>
          <p>
            <span>{{ t('recovery.forgot.haveToken') }}</span>
            <RouterLink class="btn btn-link" to="/reset-password">{{ t('recovery.forgot.resetPassword') }}</RouterLink>
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
import { firstQueryText, removeSensitiveRouteState } from '@/utils/sensitiveData'
import { getSessionStorage, safeGetItem, safeRemoveItem, storageKeys } from '@/utils/storage'
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

const email = ref(readInitialRecoveryEmail())
const error = ref<string | null>(null)
const success = ref<string | null>(null)
const submitting = ref(false)

removeSensitiveQueryFromAddressBar()

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const normalizedEmail = computed(() => email.value.trim().toLowerCase())
const emailValid = computed(() => emailPattern.test(normalizedEmail.value))
const canSubmit = computed(() => emailValid.value && !submitting.value)
const submitLabel = computed(() => (submitting.value ? t('recovery.forgot.sending') : t('recovery.forgot.send')))

async function submitForgotPassword(): Promise<void> {
  if (submitting.value) return

  error.value = null
  success.value = null

  if (!emailValid.value) {
    error.value = t('recovery.forgot.invalidEmail')
    return
  }

  submitting.value = true
  try {
    await authApi.forgotPassword(normalizedEmail.value)
    success.value = t('recovery.forgot.sent', { email: normalizedEmail.value })
  } catch (reason) {
    error.value = resolveApiError(reason, t('recovery.forgot.error'))
  } finally {
    submitting.value = false
  }
}

function requestAnotherEmail(): void {
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

function removeSensitiveQueryFromAddressBar(): void {
  const { changed, query, hash } = removeSensitiveRouteState(route.query, route.hash)

  if (changed) {
    void router.replace({ name: 'forgot-password', query, hash })
  }
}
</script>
