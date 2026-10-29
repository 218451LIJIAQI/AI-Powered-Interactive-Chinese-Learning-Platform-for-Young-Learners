<template>
  <AuthShell variant="recovery">
    <div class="auth-flow">
      <header class="auth-form-head">
        <h1>{{ sentTo ? t('recovery.sentTitle') : t('recovery.forgotTitle') }}</h1>
        <p v-if="!sentTo">{{ t('recovery.forgotCopy') }}</p>
      </header>

      <div v-if="sentTo" class="auth-sent" data-testid="forgot-sent">
        <p class="alert alert-success" role="status">
          <IconGlyph name="check" :size="18" />
          <span>{{ t('recovery.sentCopy', { email: sentTo }) }}</span>
        </p>
        <p class="field-hint">{{ t('recovery.sentSpam') }}</p>
        <p v-if="error" class="alert alert-danger" role="alert">
          <IconGlyph name="alert" :size="18" />
          <span>{{ error }}</span>
        </p>
        <div class="auth-sent-actions">
          <button
            class="btn btn-secondary"
            type="button"
            :disabled="submitting"
            :aria-busy="submitting"
            data-testid="send-again"
            @click="sendAgain"
          >
            <IconGlyph name="refresh" :size="16" />
            {{ t('recovery.sendAgain') }}
          </button>
          <button class="btn btn-ghost" type="button" @click="useAnotherEmail">
            {{ t('recovery.useAnotherEmail') }}
          </button>
        </div>
      </div>

      <form v-else class="auth-form" novalidate @submit.prevent="submit">
        <p v-if="error" id="forgot-error" class="alert alert-danger" role="alert">
          <IconGlyph name="alert" :size="18" />
          <span>{{ error }}</span>
        </p>
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
            :aria-describedby="error ? 'forgot-error' : 'forgot-steps'"
          />
        </div>
        <button class="btn btn-primary btn-lg btn-block" type="submit" :disabled="submitting" :aria-busy="submitting">
          <span>{{ submitting ? t('recovery.sending') : t('recovery.sendLink') }}</span>
          <IconGlyph name="chevronRight" :size="18" />
        </button>
        <ol id="forgot-steps" class="auth-steps" :aria-label="t('recovery.steps')">
          <li>{{ t('recovery.stepEnter') }}</li>
          <li>{{ t('recovery.stepOpen') }}</li>
          <li>{{ t('recovery.stepChoose') }}</li>
        </ol>
      </form>

      <p class="auth-switch">
        <span>{{ t('recovery.rememberPassword') }}</span>
        <RouterLink class="btn btn-link" to="/auth">{{ t('recovery.backToSignIn') }}</RouterLink>
      </p>
    </div>
    <AppToast :message="toast" />
  </AuthShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
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

async function requestLink(address: string): Promise<boolean> {
  submitting.value = true
  error.value = null
  try {
    await authApi.forgotPassword(address)
    return true
  } catch (reason) {
    error.value = resolveApiError(reason, t('recovery.sendFailed'))
    return false
  } finally {
    submitting.value = false
  }
}

async function submit(): Promise<void> {
  if (submitting.value) return
  const address = email.value.trim().toLowerCase()
  if (!isValidEmail(address)) {
    error.value = t('recovery.emailInvalid')
    return
  }
  if (await requestLink(address)) sentTo.value = address
}

async function sendAgain(): Promise<void> {
  if (sentTo.value && !submitting.value && (await requestLink(sentTo.value))) {
    showToast(t('recovery.sentAgain'))
  }
}

function useAnotherEmail(): void {
  sentTo.value = null
  error.value = null
  email.value = ''
}
</script>
