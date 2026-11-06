<template>
  <AuthShell>
    <form class="auth-form" novalidate @submit.prevent="submit">
      <h1 class="auth-title">{{ t('recovery.forgotTitle') }}</h1>

      <div class="field">
        <label class="field-label" for="forgot-email">
          {{ t('recovery.email') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          id="forgot-email"
          v-model="email"
          class="input"
          type="email"
          autocomplete="email"
          required
          :placeholder="t('recovery.emailPlaceholder')"
          :aria-invalid="Boolean(error)"
          :aria-describedby="error ? 'forgot-error' : undefined"
        />
        <p v-if="error" id="forgot-error" class="field-error" role="alert">
          <IconGlyph name="alert" :size="14" />{{ error }}
        </p>
      </div>

      <div v-if="sentTo" class="callout auth-notice" role="status" data-testid="forgot-sent">
        <IconGlyph name="mail" :size="18" />
        <span>
          <strong>{{ t('recovery.sentTitle') }}</strong>
          <span>{{ t('recovery.sentCopy', { email: sentTo }) }}</span>
        </span>
      </div>

      <button
        v-if="sentTo"
        class="btn btn-secondary btn-lg btn-block"
        type="submit"
        :disabled="submitting"
        :aria-busy="submitting"
        data-testid="send-again"
      >
        {{ t('recovery.sendAgain') }}
      </button>
      <button
        v-else
        class="btn btn-primary btn-lg btn-block"
        type="submit"
        :disabled="submitting"
        :aria-busy="submitting"
      >
        <span>{{ submitting ? t('recovery.sending') : t('recovery.sendLink') }}</span>
        <IconGlyph name="chevronRight" :size="18" />
      </button>

      <p class="auth-switch">
        <span>{{ t('recovery.rememberPassword') }}</span>
        <RouterLink class="btn btn-link auth-text-link" to="/auth">{{ t('recovery.backToSignIn') }}</RouterLink>
      </p>
    </form>
    <AppToast :message="toast" />
  </AuthShell>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import AppToast from '@/components/common/AppToast.vue'
import AuthShell from '@/components/public/AuthShell.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useToast } from '@/composables/useToast'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { replaceSensitiveRouteState } from '@/utils/sensitiveData'
import { getSessionStorage, safeGetItem, safeRemoveItem, storageKeys } from '@/utils/storage'

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ recovery: recoveryMessages })
const { toast, showToast } = useToast()

replaceSensitiveRouteState(router, route, 'forgot-password')

const email = ref(readRememberedEmail())
const sentTo = ref<string | null>(null)
const submitting = ref(false)
const error = ref<string | null>(null)

/** The sign-in page hands over the address typed there (session storage, read once). */
function readRememberedEmail(): string {
  const storage = getSessionStorage()
  const remembered = safeGetItem(storageKeys.recoveryEmail, storage)?.trim() ?? ''
  safeRemoveItem(storageKeys.recoveryEmail, storage)
  return remembered
}

/** The field stays editable after sending, so "Send the link again" also serves a corrected address. */
async function submit(): Promise<void> {
  if (submitting.value) return
  const address = email.value.trim().toLowerCase()
  if (!isValidEmail(address)) {
    error.value = t('recovery.emailInvalid')
    void nextTick(() => document.getElementById('forgot-email')?.focus())
    return
  }
  submitting.value = true
  error.value = null
  try {
    await authApi.forgotPassword(address)
    if (sentTo.value) showToast(t('recovery.sentAgain'))
    sentTo.value = address
  } catch (reason) {
    error.value = resolveApiError(reason, t('recovery.sendFailed'))
  } finally {
    submitting.value = false
  }
}
</script>
