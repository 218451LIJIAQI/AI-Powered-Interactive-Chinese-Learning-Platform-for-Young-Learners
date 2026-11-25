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
          maxlength="150"
          :placeholder="t('recovery.emailPlaceholder')"
          :aria-invalid="Boolean(emailError)"
          :aria-describedby="emailError ? 'forgot-email-error' : undefined"
          @blur="validateEmail"
        />
        <p v-if="emailError" id="forgot-email-error" class="field-error">
          <IconGlyph name="alert" :size="14" />{{ t(emailError) }}
        </p>
      </div>
      <p v-if="sendFailure" id="forgot-send-error" class="callout is-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>{{ resolveApiError(sendFailure.reason, t('recovery.sendFailed')) }}</span>
      </p>

      <!-- What the product says after sending: the link is in the inbox of this address, if it has an account. -->
      <div v-if="sent" id="forgot-sent" class="callout" role="status" data-testid="forgot-sent">
        <IconGlyph name="mail" :size="18" />
        <span>
          <strong>{{ t('recovery.sentTitle') }}</strong>
          <span>{{ t('recovery.sentCopy') }}</span>
          <span>{{ t('recovery.sentNewest') }}</span>
        </span>
      </div>

      <!-- One button: after sending it sends the link again, so focus returns to it once re-enabled. -->
      <button
        ref="submitButton"
        class="btn btn-lg btn-block"
        :class="sent ? 'btn-secondary' : 'btn-primary'"
        type="submit"
        :disabled="submitting || Boolean(emailError)"
        :aria-busy="submitting"
        :aria-describedby="sendFailure ? 'forgot-send-error' : sent ? 'forgot-sent' : undefined"
        data-testid="forgot-submit"
        @mousedown.prevent
      >
        <span>{{ submitLabel }}</span>
        <IconGlyph v-if="!sent" name="chevronRight" :size="18" />
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
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import AppToast from '@/components/common/AppToast.vue'
import AuthShell from '@/components/public/AuthShell.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useToast } from '@/composables/useToast'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import { restoreFocusIfDropped } from '@/utils/focus'
import { getSessionStorage, safeGetItem, safeRemoveItem, storageKeys } from '@/utils/storage'

const { t } = useLocalMessages({ recovery: recoveryMessages })
const { toast, showToast } = useToast()

const email = ref(readRememberedEmail())
const submitButton = ref<HTMLButtonElement | null>(null)
const normalizedEmail = computed(() => email.value.trim().toLowerCase())
/** The link went to the address in the field (the confirmation speaks of "this email"). */
const sent = ref(false)
const submitting = ref(false)
const sendFailure = shallowRef<{ reason: unknown } | null>(null)

const { error: emailError, validate: validateEmail } = useFieldCheck(email, (value) =>
  isValidEmail(value.trim()) ? null : 'recovery.emailInvalid',
)

const submitLabel = computed(() => {
  if (submitting.value) return t('common.sending')
  return sent.value ? t('recovery.sendAgain') : t('recovery.sendLink')
})

/** The sign-in page hands over the address typed there (session storage, read once). */
function readRememberedEmail(): string {
  const storage = getSessionStorage()
  const remembered = safeGetItem(storageKeys.recoveryEmail, storage) ?? ''
  safeRemoveItem(storageKeys.recoveryEmail, storage)
  return remembered
}

// Another address is a new request: the confirmation belonged to the address it was sent for.
watch(normalizedEmail, () => {
  sent.value = false
  sendFailure.value = null
})

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!validateEmail()) {
    void nextTick(() => document.getElementById('forgot-email')?.focus())
    return
  }
  const hadButtonFocus = document.activeElement === submitButton.value
  submitting.value = true
  sendFailure.value = null
  const address = normalizedEmail.value
  try {
    await authApi.forgotPassword(address)
    // The confirmation speaks of the address in the field: not when it was changed while the link went out.
    if (normalizedEmail.value !== address) return
    if (sent.value) showToast(t('recovery.sentAgain'))
    sent.value = true
  } catch (reason) {
    if (normalizedEmail.value === address) sendFailure.value = { reason }
  } finally {
    submitting.value = false
    if (hadButtonFocus) await restoreFocusIfDropped(() => submitButton.value)
  }
}
</script>
