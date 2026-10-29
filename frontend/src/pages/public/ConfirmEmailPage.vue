<template>
  <section class="confirm-email" aria-labelledby="confirm-email-title">
    <span class="state-icon confirm-email-icon" :class="`is-${state}`" aria-hidden="true">
      <span v-if="state === 'checking'" class="spinner" />
      <IconGlyph v-else :name="state === 'done' ? 'check' : 'alert'" :size="26" />
    </span>

    <template v-if="state === 'checking'">
      <h1 id="confirm-email-title">{{ signup ? t('recovery.signupConfirmTitle') : t('recovery.confirmTitle') }}</h1>
      <p role="status">{{ signup ? t('recovery.signupConfirming') : t('recovery.confirming') }}</p>
    </template>

    <template v-else-if="state === 'done' && signup">
      <h1 id="confirm-email-title">{{ t('recovery.signupConfirmedTitle') }}</h1>
      <p role="status" data-testid="confirm-email-done">{{ t('recovery.signupConfirmedCopy', { email: newEmail }) }}</p>
      <RouterLink v-if="homePath" class="btn btn-primary btn-lg" :to="homePath">
        {{ t('recovery.continueLearning') }}
        <IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
      <RouterLink v-else class="btn btn-primary btn-lg" to="/auth">{{ t('recovery.signIn') }}</RouterLink>
    </template>

    <template v-else-if="state === 'done'">
      <h1 id="confirm-email-title">{{ t('recovery.confirmedTitle') }}</h1>
      <p role="status" data-testid="confirm-email-done">{{ t('recovery.confirmedCopy', { email: newEmail }) }}</p>
      <RouterLink class="btn btn-primary btn-lg" :to="{ name: 'auth', query: { email: 'changed' } }">
        {{ t('recovery.signIn') }}
        <IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
    </template>

    <template v-else>
      <h1 id="confirm-email-title">{{ t('recovery.confirmFailedTitle') }}</h1>
      <p role="alert" data-testid="confirm-email-error">{{ errorMessage }}</p>
      <p class="confirm-email-hint">{{ signup ? t('recovery.signupFailedHint') : t('recovery.confirmFailedHint') }}</p>
      <RouterLink v-if="signup && homePath" class="btn btn-primary btn-lg" :to="homePath">{{
        t('recovery.continueLearning')
      }}</RouterLink>
      <RouterLink v-else-if="!signup && accountPath" class="btn btn-primary btn-lg" :to="accountPath">{{
        t('recovery.openAccount')
      }}</RouterLink>
      <RouterLink v-else class="btn btn-primary btn-lg" to="/auth">{{ t('recovery.signIn') }}</RouterLink>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { publicOpsApi } from '@/api/ops'
import { emailConfirmationApi } from '@/api/plan'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { useAuthStore } from '@/stores/auth'
import { resolveApiError } from '@/utils/apiError'
import { defaultRouteForRole } from '@/utils/role'
import { firstQueryText, replaceSensitiveRouteState } from '@/utils/sensitiveData'

/**
 * Opens an e-mailed confirmation link: a changed address (Account; the server signs the account out everywhere) or,
 * with `kind=signup`, a new account's address (nothing else changes and learning goes on).
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ recovery: recoveryMessages })

const token = firstQueryText(route.query.token)
const signup = firstQueryText(route.query.kind) === 'signup'
replaceSensitiveRouteState(router, route, 'confirm-email')

const state = ref<'checking' | 'done' | 'failed'>('checking')
const newEmail = ref('')
const errorMessage = ref('')

const accountPath = computed(() =>
  auth.isAuthenticated && auth.activeRole ? `/${auth.activeRole.toLowerCase()}/account` : null,
)
const homePath = computed(() => (auth.isAuthenticated && auth.activeRole ? defaultRouteForRole(auth.activeRole) : null))

onMounted(async () => {
  if (!token) {
    state.value = 'failed'
    errorMessage.value = t('recovery.missingConfirmLink')
    return
  }
  try {
    if (signup) {
      newEmail.value = (await emailConfirmationApi.confirm(token)).email
    } else {
      newEmail.value = (await publicOpsApi.confirmEmail(token)).email
    }
    state.value = 'done'
    // The server signed the account out everywhere when the address changed.
    if (!signup && auth.isAuthenticated) auth.clearSession()
  } catch (reason) {
    state.value = 'failed'
    errorMessage.value = resolveApiError(reason, t('recovery.confirmFailedTitle'))
  }
})
</script>
