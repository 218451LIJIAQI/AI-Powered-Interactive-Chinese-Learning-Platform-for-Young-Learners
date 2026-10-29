<template>
  <AuthShell variant="recovery">
    <div class="auth-flow">
      <div v-if="state === 'checking'" class="auth-checking" role="status">
        <h1 class="sr-only">{{ isInvite ? t('recovery.inviteTitle') : t('recovery.resetTitle') }}</h1>
        <span class="spinner" aria-hidden="true" />
        <p>{{ t('recovery.checkingLink') }}</p>
      </div>

      <template v-else-if="state === 'invalid'">
        <header class="auth-form-head">
          <h1>{{ isInvite ? t('recovery.inviteExpiredTitle') : t('recovery.expiredTitle') }}</h1>
          <p v-if="!isInvite">{{ t('recovery.expiredCopy') }}</p>
        </header>
        <p class="alert alert-warning" role="alert" data-testid="link-invalid">
          <IconGlyph name="info" :size="18" />
          <span>{{ invalidMessage }}</span>
        </p>
        <RouterLink v-if="!isInvite" class="btn btn-primary btn-lg btn-block" to="/forgot-password">
          {{ t('recovery.requestNewLink') }}
          <IconGlyph name="chevronRight" :size="18" />
        </RouterLink>
      </template>

      <form v-else class="auth-form" novalidate @submit.prevent="submit">
        <header class="auth-form-head">
          <h1>{{ isInvite ? t('recovery.inviteTitle') : t('recovery.resetTitle') }}</h1>
          <p data-testid="link-email">
            {{
              isInvite ? t('recovery.inviteFor', { email: linkEmail }) : t('recovery.resetFor', { email: linkEmail })
            }}
          </p>
        </header>

        <p v-if="error" id="reset-error" class="alert alert-danger" role="alert">
          <IconGlyph name="alert" :size="18" />
          <span>{{ error }}</span>
        </p>

        <div v-if="isInvite" class="field">
          <label class="field-label" for="invite-full-name">
            {{ t('recovery.fullName') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="invite-full-name"
            v-model="fullName"
            class="input"
            autocomplete="name"
            required
            maxlength="120"
            :placeholder="t('recovery.fullNamePlaceholder')"
            :aria-invalid="Boolean(nameError)"
            :aria-describedby="nameError ? 'invite-name-error' : undefined"
          />
          <p v-if="nameError" id="invite-name-error" class="field-error">{{ nameError }}</p>
        </div>

        <div class="field">
          <label class="field-label" for="reset-password">
            {{ t('recovery.newPassword') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="reset-password"
            v-model="password"
            class="input"
            :type="showPasswords ? 'text' : 'password'"
            autocomplete="new-password"
            required
            :placeholder="t('recovery.newPasswordPlaceholder')"
            aria-describedby="reset-readiness"
          />
        </div>
        <div class="field">
          <label class="field-label" for="reset-confirm">
            {{ t('recovery.confirmNewPassword') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="reset-confirm"
            v-model="confirmPassword"
            class="input"
            :type="showPasswords ? 'text' : 'password'"
            autocomplete="new-password"
            required
            :placeholder="t('recovery.confirmPlaceholder')"
            aria-describedby="reset-readiness"
          />
        </div>
        <label class="choice">
          <input v-model="showPasswords" type="checkbox" />
          <span>{{ t('recovery.showPasswords') }}</span>
        </label>

        <section id="reset-readiness" class="auth-readiness" :aria-label="t('recovery.readiness')">
          <p class="auth-readiness-head">
            <span>{{ t('recovery.readiness') }}</span>
            <StatusPill
              :label="passwordReady ? t('recovery.ready') : t('recovery.notReady')"
              :tone="passwordReady ? 'success' : 'neutral'"
            />
          </p>
          <ul class="auth-checks">
            <li v-for="check in checks" :key="check.label" :class="{ 'is-met': check.met }">
              <IconGlyph :name="check.met ? 'check' : 'info'" :size="14" />
              <span>{{ check.label }}</span>
            </li>
          </ul>
        </section>

        <button
          class="btn btn-primary btn-lg btn-block"
          type="submit"
          :disabled="!passwordReady || submitting"
          :aria-busy="submitting"
        >
          <span>{{ submitLabel }}</span>
          <IconGlyph name="chevronRight" :size="18" />
        </button>
      </form>

      <p class="auth-switch">
        <span>{{ t('recovery.rememberPassword') }}</span>
        <RouterLink class="btn btn-link" to="/auth">{{ t('recovery.backToSignIn') }}</RouterLink>
      </p>
      <p v-if="!isInvite && state === 'ready'" class="auth-switch">
        <span>{{ t('recovery.needNewLink') }}</span>
        <RouterLink class="btn btn-link" to="/forgot-password">{{ t('recovery.requestNewLink') }}</RouterLink>
      </p>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { publicOpsApi } from '@/api/ops'
import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AuthShell from '@/components/public/AuthShell.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { resolveApiError } from '@/utils/apiError'
import { isPasswordLengthValid, isWithinPasswordByteLimit } from '@/utils/password'
import { firstQueryText } from '@/utils/sensitiveData'

type LinkState = 'checking' | 'ready' | 'invalid'

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ recovery: recoveryMessages })

// An admin invite opens this page with ?invite=…, a password reset with ?token=…
const inviteToken = firstQueryText(route.query.invite)
const token = inviteToken || firstQueryText(route.query.token)
const isInvite = Boolean(inviteToken)

// The link token leaves the address bar and history once it has been read.
if (route.query.invite || route.query.token) {
  const query = { ...route.query }
  delete query.invite
  delete query.token
  void router.replace({ name: 'reset-password', query })
}

const state = ref<LinkState>('checking')
const linkEmail = ref('')
const invalidMessage = ref('')
const fullName = ref('')
const nameError = ref<string | null>(null)
const password = ref('')
const confirmPassword = ref('')
const showPasswords = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)

const checks = computed(() => [
  { label: t('recovery.ruleLength'), met: password.value.length >= 8 },
  { label: t('recovery.ruleLimit'), met: password.value.length > 0 && isWithinPasswordByteLimit(password.value) },
  { label: t('recovery.ruleMatch'), met: Boolean(confirmPassword.value) && password.value === confirmPassword.value },
])
const passwordReady = computed(() => isPasswordLengthValid(password.value) && password.value === confirmPassword.value)
const submitLabel = computed(() => {
  if (isInvite) return submitting.value ? t('recovery.inviteCreating') : t('recovery.inviteSubmit')
  return submitting.value ? t('recovery.resetting') : t('recovery.resetSubmit')
})

onMounted(async () => {
  if (!token) {
    state.value = 'invalid'
    invalidMessage.value = isInvite ? t('recovery.inviteExpiredCopy') : t('recovery.missingLink')
    return
  }
  try {
    const link = isInvite ? await publicOpsApi.checkAdminInvite(token) : await publicOpsApi.checkResetLink(token)
    linkEmail.value = link.email
    state.value = 'ready'
  } catch (reason) {
    state.value = 'invalid'
    invalidMessage.value = isInvite
      ? t('recovery.inviteExpiredCopy')
      : resolveApiError(reason, t('recovery.expiredHint'))
  }
})

async function submit(): Promise<void> {
  if (submitting.value || !passwordReady.value) return
  error.value = null
  nameError.value = isInvite && !fullName.value.trim() ? t('recovery.nameRequired') : null
  if (nameError.value) return
  submitting.value = true
  try {
    if (isInvite) {
      await publicOpsApi.acceptAdminInvite({
        token,
        fullName: fullName.value.trim(),
        password: password.value,
        confirmPassword: confirmPassword.value,
      })
      await router.push({ name: 'auth', query: { invite: 'done' } })
    } else {
      await authApi.resetPassword({ token, password: password.value, confirmPassword: confirmPassword.value })
      await router.push({ name: 'auth', query: { reset: 'done' } })
    }
  } catch (reason) {
    error.value = resolveApiError(reason, isInvite ? t('recovery.inviteFailed') : t('recovery.resetFailed'))
  } finally {
    submitting.value = false
  }
}
</script>
