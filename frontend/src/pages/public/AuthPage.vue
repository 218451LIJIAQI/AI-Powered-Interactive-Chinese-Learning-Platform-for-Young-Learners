<template>
  <AuthShell>
    <h1 class="sr-only">{{ activeTab === 'register' ? t('auth.createAccount') : t('auth.signInTitle') }}</h1>

    <div class="tabs" role="tablist" :aria-label="t('auth.views')" @keydown="onTabKeydown">
      <button
        v-for="tab in TABS"
        :id="`auth-tab-${tab.id}`"
        :key="tab.id"
        class="tab-button"
        type="button"
        role="tab"
        :aria-controls="`auth-panel-${tab.id}`"
        :aria-selected="activeTab === tab.id"
        :tabindex="activeTab === tab.id ? 0 : -1"
        @click="showTab(tab.id)"
      >
        <IconGlyph :name="tab.icon" :size="16" />
        <span>{{ t(tab.label) }}</span>
      </button>
    </div>

    <!-- Each form is kept while the other tab is open: what was typed, and a sign-in pause, survive a look at it. -->
    <div id="auth-panel-signin" role="tabpanel" aria-labelledby="auth-tab-signin" :hidden="activeTab !== 'signin'">
      <KeepAlive>
        <AuthSignInForm
          v-if="activeTab === 'signin'"
          :initial-email="signInEmail"
          :account-created="createdEmail !== null"
        />
      </KeepAlive>
    </div>

    <div
      id="auth-panel-register"
      role="tabpanel"
      aria-labelledby="auth-tab-register"
      :hidden="activeTab !== 'register'"
    >
      <KeepAlive>
        <AuthRegisterForm v-if="activeTab === 'register'" @fallback-sign-in="onFallbackSignIn" />
      </KeepAlive>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import AuthRegisterForm from '@/components/public/AuthRegisterForm.vue'
import AuthShell from '@/components/public/AuthShell.vue'
import AuthSignInForm from '@/components/public/AuthSignInForm.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { authMessages } from '@/plugins/authMessages'
import { useAuthStore } from '@/stores/auth'
import { withoutQueryKeys } from '@/utils/routeQuery'

const TABS = [
  { id: 'signin', icon: 'profile', label: 'common.signIn' },
  { id: 'register', icon: 'userPlus', label: 'auth.createAccount' },
] as const
type AuthTab = (typeof TABS)[number]['id']

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ auth: authMessages })

const activeTab = ref<AuthTab>('signin')
/** Set when a new account was created but automatic sign-in failed, pre-filling Sign in with a confirmation notice. */
const createdEmail = ref<string | null>(null)
const signInEmail = computed(
  () => createdEmail.value ?? (route.query.reason === 'expired' ? (auth.endedSessionEmail ?? '') : ''),
)

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = tab === 'register' ? 'register' : 'signin'
    if (tab === 'register') createdEmail.value = null
  },
  { immediate: true },
)

function showTab(tab: AuthTab): void {
  if (tab === activeTab.value) return
  activeTab.value = tab
  createdEmail.value = null
  // The address keeps the open tab (a reload or a shared link opens it again) and drops a one-time notice. Older links
  // also carry a role: every new account is a learner.
  const query = withoutQueryKeys(route.query, 'reset', 'email', 'invite', 'reason', 'role', 'tab')
  if (tab === 'register') query.tab = 'register'
  void router.replace({ path: route.path, query })
}

function onFallbackSignIn(email: string): void {
  showTab('signin')
  createdEmail.value = email
}

/** Arrow keys, Home and End move between the two tabs (only the selected tab is in the Tab order). */
function onTabKeydown(event: KeyboardEvent): void {
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
  const other: AuthTab = activeTab.value === 'signin' ? 'register' : 'signin'
  const target: Partial<Record<string, AuthTab>> = {
    ArrowLeft: other,
    ArrowRight: other,
    Home: 'signin',
    End: 'register',
  }
  const tab = target[event.key]
  if (!tab) return
  event.preventDefault()
  if (tab !== activeTab.value) showTab(tab)
  void nextTick(() => document.getElementById(`auth-tab-${tab}`)?.focus())
}
</script>
