<template>
  <div class="system-page">
    <main id="main-content" class="system-card" tabindex="-1" aria-labelledby="system-title">
      <div class="system-top">
        <AppBrand to="/" />
        <!-- FRLANG01: the language can be changed on every page, these included. -->
        <LanguageSwitcher />
      </div>

      <h1 id="system-title" class="system-title">
        {{ kind === 'not-found' ? t('system.notFoundTitle') : t('system.noAccessTitle') }}
      </h1>

      <div class="btn-group">
        <template v-if="kind === 'not-found'">
          <RouterLink class="btn btn-primary btn-lg" :to="homeRoute" data-testid="system-home">
            {{ t('system.returnHome') }}
          </RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" to="/classes">{{ t('system.exploreClasses') }}</RouterLink>
        </template>
        <template v-else-if="auth.isAuthenticated">
          <button
            class="btn btn-primary btn-lg"
            type="button"
            :disabled="signingOut"
            :aria-busy="signingOut"
            data-testid="system-switch-account"
            @click="useAnotherAccount"
          >
            {{ signingOut ? t('system.signingOut') : t('system.useAnotherAccount') }}
          </button>
          <RouterLink class="btn btn-secondary btn-lg" :to="homeRoute" data-testid="system-home">
            {{ t('system.myHome') }}
          </RouterLink>
        </template>
        <template v-else>
          <RouterLink class="btn btn-primary btn-lg" to="/auth">{{ t('system.signIn') }}</RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" to="/" data-testid="system-home">
            {{ t('system.returnHome') }}
          </RouterLink>
        </template>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { systemMessages } from '@/plugins/systemMessages'
import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'
import '@/styles/ops-public.css'

defineProps<{ kind: 'not-found' | 'unauthorized' }>()

const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ system: systemMessages })

const homeRoute = computed(() => defaultRouteForRole(auth.isAuthenticated ? auth.activeRole : null))

const signingOut = ref(false)

/** Signs the current account out (on the server too) and opens the sign-in page for the other one. */
async function useAnotherAccount(): Promise<void> {
  signingOut.value = true
  try {
    await auth.logout()
  } finally {
    signingOut.value = false
  }
  await router.push('/auth')
}
</script>
