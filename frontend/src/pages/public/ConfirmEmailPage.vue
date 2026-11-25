<template>
  <section ref="cardRef" class="panel confirm-email" aria-labelledby="confirm-email-title">
    <span
      class="state-icon confirm-email-icon"
      :class="state === 'retryable' ? 'is-failed' : `is-${state}`"
      aria-hidden="true"
    >
      <span v-if="state === 'checking'" class="spinner" />
      <IconGlyph v-else :name="state === 'done' ? 'check' : 'alert'" :size="24" />
    </span>

    <template v-if="state === 'checking'">
      <h1 id="confirm-email-title">{{ signup ? t('recovery.signupConfirmTitle') : t('recovery.confirmTitle') }}</h1>
      <p role="status">{{ signup ? t('recovery.signupConfirming') : t('recovery.confirming') }}</p>
    </template>

    <template v-else-if="state === 'done'">
      <h1 id="confirm-email-title">{{ t(signup ? 'recovery.signupConfirmedTitle' : 'recovery.confirmedTitle') }}</h1>
      <p role="status" data-testid="confirm-email-done">
        {{ t(signup ? 'recovery.signupConfirmedCopy' : 'recovery.confirmedCopy', { email: newEmail }) }}
      </p>
      <RouterLink
        v-if="homePath && (signup || (auth.isAuthenticated && !unconfirmedSession))"
        class="btn btn-primary btn-lg"
        :to="homePath"
      >
        {{ homeCtaLabel }}
        <IconGlyph name="chevronRight" :size="18" />
      </RouterLink>
      <button
        v-else-if="!signup && auth.isAuthenticated && unconfirmedSession"
        type="button"
        class="btn btn-primary btn-lg"
        data-testid="confirm-email-sign-in"
        @click="signInAfterChange"
      >
        {{ t('common.signIn') }}
        <IconGlyph name="chevronRight" :size="18" />
      </button>
      <RouterLink
        v-else
        class="btn btn-primary btn-lg"
        :to="signup ? '/auth' : { name: 'auth', query: { email: 'changed' } }"
      >
        {{ t('common.signIn') }}
        <IconGlyph v-if="!signup" name="chevronRight" :size="18" />
      </RouterLink>
    </template>

    <template v-else-if="state === 'retryable'">
      <h1 id="confirm-email-title">{{ t('recovery.checkFailedTitle') }}</h1>
      <p role="alert" data-testid="confirm-email-error">{{ errorMessage }}</p>
      <button
        ref="retryButton"
        class="btn btn-primary btn-lg"
        type="button"
        data-testid="confirm-email-retry"
        @click="confirmLink"
      >
        {{ t('common.retry') }}
      </button>
    </template>

    <template v-else>
      <h1 id="confirm-email-title">{{ t('recovery.confirmFailedTitle') }}</h1>
      <p role="alert" data-testid="confirm-email-error">{{ errorMessage }}</p>
      <p>{{ failedHint }}</p>
      <RouterLink v-if="signup && studentHomePath" class="btn btn-primary btn-lg" :to="studentHomePath">{{
        homeCtaLabel
      }}</RouterLink>
      <RouterLink v-else-if="!signup && accountPath" class="btn btn-primary btn-lg" :to="accountPath">{{
        t('recovery.openAccount')
      }}</RouterLink>
      <button
        v-else-if="auth.isAuthenticated"
        type="button"
        class="btn btn-primary btn-lg"
        :disabled="signingOut"
        :aria-busy="signingOut"
        data-testid="confirm-email-switch-account"
        @click="switchAccount"
      >
        {{ t('common.signIn') }}
      </button>
      <RouterLink v-else class="btn btn-primary btn-lg" to="/auth">{{ t('common.signIn') }}</RouterLink>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { authApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { finishSessionEndNavigation } from '@/composables/usePageNavigation'
import { recoveryMessages } from '@/plugins/recoveryMessages'
import { useAuthStore } from '@/stores/auth'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { restoreFocusIfDropped } from '@/utils/focus'
import { defaultRouteForRole, workspacePathForRole } from '@/utils/role'
import { firstQueryText, withoutQueryKeys } from '@/utils/routeQuery'

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

// The link token leaves the address bar and history once it has been read.
if (route.query.token) {
  void router.replace({ name: 'confirm-email', query: withoutQueryKeys(route.query, 'token') })
}

const state = ref<'checking' | 'done' | 'failed' | 'retryable'>('checking')
const newEmail = ref('')
const unconfirmedSession = ref(false)
const signingOut = ref(false)
const failure = shallowRef<unknown>()
const cardRef = ref<HTMLElement | null>(null)
const retryButton = ref<HTMLButtonElement | null>(null)

watch([() => auth.isAuthenticated, () => auth.activeRole], () => {
  unconfirmedSession.value = false
})
const errorMessage = computed(() =>
  token
    ? resolveApiError(failure.value, t('recovery.confirmFailedFallback'))
    : t(signup ? 'recovery.missingSignupConfirmLink' : 'recovery.missingConfirmLink'),
)

const accountPath = computed(() => (auth.activeRole ? workspacePathForRole(auth.activeRole, 'account') : null))
const homePath = computed(() => (auth.activeRole ? defaultRouteForRole(auth.activeRole) : null))
const studentHomePath = computed(() => (auth.activeRole === 'STUDENT' ? defaultRouteForRole('STUDENT') : null))
const homeCtaLabel = computed(() => (auth.activeRole === 'ADMIN' ? t('nav.dashboard') : t('recovery.continueLearning')))
const failedHint = computed(() => {
  if (!signup) return t('recovery.confirmFailedHint')
  return studentHomePath.value ? t('recovery.signupFailedHintSignedIn') : t('recovery.signupFailedHint')
})

const isUnmounted = useIsUnmounted()
// Single-use confirmation tokens must never fire overlapping POST requests on rapid Retry clicks.
let confirming = false

async function confirmLink(): Promise<void> {
  if (!token) {
    state.value = 'failed'
    return
  }
  if (confirming) return
  confirming = true
  const version = getAuthSessionVersion()
  const hadRetryFocus = Boolean(retryButton.value && document.activeElement === retryButton.value)
  state.value = 'checking'
  unconfirmedSession.value = false
  failure.value = undefined
  try {
    const confirmed = await (signup ? authApi.confirmSignupEmail(token) : authApi.confirmEmail(token))
    // The server signed the changed account out everywhere. A session here that still refreshes is another
    // account's and stays; otherwise this browser is signed out in every tab before we expose the Sign in CTA.
    if (!signup && auth.isAuthenticated && version === getAuthSessionVersion()) {
      // A connection failure leaves the identity of the session unconfirmed.
      const live = await auth.hasLiveSession().catch(() => undefined)
      if (version === getAuthSessionVersion()) {
        if (live === false) {
          auth.clearSessionInAllTabs()
          if (isUnmounted()) {
            await finishSessionEndNavigation(router, { name: 'auth', query: { email: 'changed' } }, false)
            return
          }
        } else if (live === undefined) {
          unconfirmedSession.value = true
        }
      }
    }
    if (isUnmounted()) return
    newEmail.value = confirmed.email
    state.value = 'done'
  } catch (reason) {
    if (isUnmounted()) return
    failure.value = reason
    state.value = apiErrorStatus(reason) === 400 ? 'failed' : 'retryable'
  } finally {
    confirming = false
  }
  if (!isUnmounted() && hadRetryFocus) {
    await restoreFocusIfDropped(
      () => cardRef.value?.querySelector<HTMLElement>('button, a'),
      () => !isUnmounted(),
    )
  }
}

function signInAfterChange(): void {
  auth.clearSessionInAllTabs()
  void router.push({ name: 'auth', query: { email: 'changed' } })
}

async function switchAccount(): Promise<void> {
  if (signingOut.value) return
  signingOut.value = true
  try {
    const signedOut = await auth.logout()
    if (signedOut) await finishSessionEndNavigation(router, '/auth', !isUnmounted()).catch(() => undefined)
  } finally {
    if (!isUnmounted()) signingOut.value = false
  }
}

onMounted(confirmLink)
</script>
