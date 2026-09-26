<template>
  <section class="public-page auth-reset-page">
    <div class="auth-reset-shell">
      <aside class="auth-reset-art">
        <AppBrand />

        <div class="auth-reset-art-copy">
          <span class="auth-reset-kicker">{{ t('recovery.forgot.eyebrow') }}</span>
          <h1>
            {{ t('recovery.forgot.heroStart') }}
            <span>{{ t('recovery.forgot.heroHighlight') }}</span>
          </h1>
          <p>{{ t('recovery.forgot.heroCopy') }}</p>
        </div>

        <div class="auth-reset-scene">
          <span class="auth-reset-cloud auth-reset-cloud--one" />
          <span class="auth-reset-cloud auth-reset-cloud--two" />
          <span class="auth-reset-bamboo" />
          <span class="auth-reset-pagoda" />
          <PandaIllustration class="auth-reset-panda" :size="190" outfit="#ff8a3d" accent="#6f4cff" />
        </div>

        <div class="auth-reset-safety-card">
          <strong><IconGlyph name="shield" :size="18" /> {{ t('recovery.forgot.safetyTitle') }}</strong>
          <span>{{ t('recovery.forgot.safetyCopy') }}</span>
        </div>
      </aside>

      <div class="surface-card auth-reset-card">
        <div class="auth-reset-language"><LanguageSwitcher size="compact" /></div>
        <div class="auth-reset-card-head">
          <span class="auth-reset-card-icon"><IconGlyph name="bell" :size="24" /></span>
          <div class="auth-form-title">
            <h2>{{ t('recovery.forgot.title') }}</h2>
            <p>{{ t('recovery.forgot.cardCopy') }}</p>
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
            <label for="forgot-email">{{ t('recovery.forgot.accountEmail') }}</label>
            <input
              id="forgot-email"
              v-model="email"
              class="input auth-input"
              type="email"
              autocomplete="email"
              :placeholder="t('recovery.forgot.emailPlaceholder')"
              :aria-invalid="Boolean(error)"
            />
          </div>

          <div class="auth-reset-strength">
            <div class="row-between">
              <span>{{ t('recovery.forgot.recoverySteps') }}</span>
              <strong>{{ t('recovery.forgot.emailLink') }}</strong>
            </div>
            <div class="auth-reset-check-grid">
              <span class="is-met"><IconGlyph name="status" :size="14" /> {{ t('recovery.forgot.enterEmail') }}</span>
              <span class="is-met"><IconGlyph name="status" :size="14" /> {{ t('recovery.forgot.openLink') }}</span>
              <span class="is-met"
                ><IconGlyph name="status" :size="14" /> {{ t('recovery.forgot.choosePassword') }}</span
              >
            </div>
          </div>

          <button class="btn btn-accent btn-block auth-submit-button" :disabled="!canSubmit" type="submit">
            <span>{{ submitLabel }}</span>
            <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="16" /></span>
          </button>
        </form>

        <div v-else class="auth-reset-form">
          <div class="auth-reset-strength">
            <div class="row-between">
              <span>{{ t('recovery.forgot.nextStep') }}</span>
              <strong>{{ t('recovery.forgot.checkInbox') }}</strong>
            </div>
            <div class="auth-reset-check-grid">
              <span class="is-met"><IconGlyph name="status" :size="14" /> {{ t('recovery.forgot.checkAllMail') }}</span>
              <span class="is-met"
                ><IconGlyph name="status" :size="14" /> {{ t('recovery.forgot.openBrandLink') }}</span
              >
              <span class="is-met"><IconGlyph name="status" :size="14" /> {{ t('recovery.forgot.secureReset') }}</span>
            </div>
          </div>

          <RouterLink class="btn btn-accent btn-block auth-submit-button" to="/auth">
            <span>{{ t('recovery.forgot.backSignIn') }}</span>
            <span class="auth-submit-arrow"><IconGlyph name="chevronRight" :size="16" /></span>
          </RouterLink>

          <button class="auth-text-button auth-reset-link" type="button" @click="requestAnotherEmail">
            {{ t('recovery.forgot.anotherEmail') }}
          </button>
        </div>

        <div class="auth-reset-help-row">
          <span>{{ t('recovery.forgot.rememberPassword') }}</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/auth">{{
            t('recovery.forgot.backSignIn')
          }}</RouterLink>
        </div>

        <div class="auth-reset-help-row">
          <span>{{ t('recovery.forgot.haveToken') }}</span>
          <RouterLink class="auth-text-button auth-reset-link" to="/reset-password">{{
            t('recovery.forgot.resetPassword')
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
import { firstQueryText, removeSensitiveRouteState } from '@/utils/sensitiveData'
import { getSessionStorage, safeGetItem, safeRemoveItem, storageKeys } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { recovery: recoveryMessages.en },
    ms: { recovery: recoveryMessages.ms },
    zh: { recovery: recoveryMessages.zh },
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

<style scoped>
.auth-reset-language {
  display: flex;
  justify-content: flex-end;
}
</style>
