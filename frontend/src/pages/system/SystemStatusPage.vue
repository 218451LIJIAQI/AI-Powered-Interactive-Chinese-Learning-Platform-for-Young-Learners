<template>
  <div class="system-page">
    <main id="main-content" class="system-card" tabindex="-1" aria-labelledby="system-title">
      <div class="system-top">
        <AppBrand :to="homeRoute" />
        <!-- FRLANG01: the language can be changed on every page, these included. -->
        <LanguageSwitcher />
      </div>

      <h1 id="system-title" class="system-title">{{ t(texts.title) }}</h1>
      <p class="system-copy" data-testid="system-copy">{{ t(texts.copy) }}</p>

      <div class="btn-group">
        <template v-if="kind === 'not-found'">
          <RouterLink class="btn btn-primary btn-lg" :to="homeRoute" data-testid="system-home">
            {{ homeLabel }}
          </RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" :to="classesRoute" data-testid="system-classes">
            {{ t('common.exploreClasses') }}
          </RouterLink>
        </template>
        <button
          v-else-if="kind === 'maintenance'"
          class="btn btn-primary btn-lg"
          type="button"
          :disabled="busy"
          :aria-busy="busy"
          data-testid="system-retry"
          @click="tryAgain"
        >
          {{ busy ? t('system.checking') : t('common.retry') }}
        </button>
        <!-- A lesson of a class the learner has not joined (P3.02): back to the class, or join it here and go on to the
             lesson. -->
        <template v-else-if="classRoute">
          <RouterLink class="btn btn-secondary btn-lg" :to="classRoute" data-testid="system-class">
            {{ t('system.backToClass') }}
          </RouterLink>
          <button
            class="btn btn-primary btn-lg"
            type="button"
            :disabled="busy"
            :aria-busy="busy"
            data-testid="system-join"
            @click="joinClass"
          >
            {{ busy ? t('system.joining') : t('system.joinClass') }}
          </button>
        </template>
        <!-- A page of the other role (a link opened where another account is signed in). -->
        <template v-else-if="auth.isAuthenticated">
          <RouterLink class="btn btn-primary btn-lg" :to="homeRoute" data-testid="system-home">
            {{ t('system.myHome') }}
          </RouterLink>
          <button
            class="btn btn-secondary btn-lg"
            type="button"
            :disabled="busy"
            :aria-busy="busy"
            data-testid="system-switch-account"
            @click="useAnotherAccount"
          >
            {{ busy ? t('system.signingOut') : t('system.useAnotherAccount') }}
          </button>
        </template>
        <template v-else>
          <RouterLink class="btn btn-primary btn-lg" :to="signInRoute" data-testid="system-sign-in">
            {{ t('common.signIn') }}
          </RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" :to="homeRoute" data-testid="system-home">
            {{ t('system.returnHome') }}
          </RouterLink>
        </template>
      </div>

      <p v-if="joinError" class="system-note is-error" role="alert" data-testid="system-join-error">
        <IconGlyph name="alert" :size="16" />
        <span>{{ joinError }}</span>
      </p>

      <!-- Always in the page, so the answer to "Try again" is announced when its text arrives. -->
      <p v-if="kind === 'maintenance'" class="system-note" role="status" data-testid="system-still-down">
        <template v-if="stillDown">
          <IconGlyph name="info" :size="16" />
          <span>{{ t('system.stillUnavailable') }}</span>
        </template>
      </p>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import { getAuthSessionVersion } from '@/api/http'
import { systemApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { systemMessages } from '@/plugins/systemMessages'
import { useAuthStore } from '@/stores/auth'
import { resolveApiError } from '@/utils/apiError'
import { normalizeAppRoute } from '@/utils/appRoute'
import { firstQueryText } from '@/utils/queryText'
import { defaultRouteForRole } from '@/utils/role'
import '@/styles/ops-public.css'

type SystemPageKind = 'not-found' | 'unauthorized' | 'maintenance'

/**
 * The pages outside every layout: 404, 403 and "we're updating" (/maintenance, opened by hand or when the API answers
 * 503 and its health check fails too: router/redirects.ts). They lead a signed-in user back into their own part of the
 * app (the logo too), and a visitor to the public site.
 */
const props = defineProps<{ kind: SystemPageKind }>()

/** The title and copy of each page; a refused lesson of a class not joined has its own. */
const TEXTS: Record<SystemPageKind | 'lesson-locked', { readonly title: string; readonly copy: string }> = {
  'not-found': { title: 'system.notFoundTitle', copy: 'system.notFoundCopy' },
  unauthorized: { title: 'system.noAccessTitle', copy: 'system.noAccessCopy' },
  maintenance: { title: 'system.maintenanceTitle', copy: 'system.maintenanceCopy' },
  'lesson-locked': { title: 'system.lessonLockedTitle', copy: 'system.lessonLockedCopy' },
}

/** A lesson of a learner's class (`/student/classes/7/lessons/12`): the refused page names the class to go back to. */
const CLASS_LESSON_PATH = /^\/student\/classes\/(\d+)\/lessons\//

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useLocalMessages({ system: systemMessages })

/** The role's home when signed in, else the landing page. */
const homeRoute = computed(() => defaultRouteForRole(auth.activeRole))
const homeLabel = computed(() => (auth.isAuthenticated ? t('system.myHome') : t('system.returnHome')))
/** A learner explores their own Find classes; everyone else the public catalogue. */
const classesRoute = computed<RouteLocationRaw>(() =>
  auth.roles.includes('STUDENT') ? { name: 'student-classes-find' } : { name: 'classes' },
)

/** The page the guard or a lesson refused (`?redirect=` on /unauthorized). */
const refusedPath = computed(() => normalizeAppRoute(firstQueryText(route.query.redirect)))
/** The class of a refused lesson, for a signed-in learner: lessons open once the class is joined (joining is free). */
const lockedClassId = computed(() => {
  if (props.kind !== 'unauthorized' || !auth.roles.includes('STUDENT') || !refusedPath.value) return null
  return CLASS_LESSON_PATH.exec(refusedPath.value)?.[1] ?? null
})
const classRoute = computed<RouteLocationRaw | null>(() =>
  lockedClassId.value ? { name: 'student-class', params: { classId: lockedClassId.value } } : null,
)
const texts = computed(() => TEXTS[classRoute.value ? 'lesson-locked' : props.kind])

/**
 * Sign in, keeping the page the guard refused so another account can open it; the sign-in page checks the target
 * before going there.
 */
const signInRoute = computed<RouteLocationRaw>(() =>
  refusedPath.value ? { path: '/auth', query: { redirect: refusedPath.value } } : '/auth',
)

const busy = ref(false)
const joinFailure = shallowRef<{ reason: unknown } | null>(null)
const joinError = computed(() =>
  joinFailure.value ? resolveApiError(joinFailure.value.reason, t('system.joinError')) : null,
)
const stillDown = ref(false)
let visit = 0

// Vue Router reuses this component between status pages and between different refused lessons.
watch([() => props.kind, refusedPath, lockedClassId], () => {
  visit++
  busy.value = false
  joinFailure.value = null
  stillDown.value = false
})
onBeforeUnmount(() => {
  visit++
})

/** Signs the current account out (on the server too) and opens the sign-in page for the other one. */
async function useAnotherAccount(): Promise<void> {
  if (busy.value) return
  const started = visit
  busy.value = true
  try {
    const signedOut = await auth.logout()
    if (started !== visit) return
    if (signedOut) await router.push(signInRoute.value)
  } finally {
    if (started === visit) busy.value = false
  }
}

/** "Join class": joins the refused lesson's class and opens the lesson; a refusal (an archived class) says why. */
async function joinClass(): Promise<void> {
  const classId = lockedClassId.value
  const target = refusedPath.value
  if (!classId || !target || busy.value) return
  const started = visit
  const version = getAuthSessionVersion()
  const stillHere = () => started === visit && lockedClassId.value === classId && version === getAuthSessionVersion()
  busy.value = true
  joinFailure.value = null
  try {
    await studentClassesApi.join(Number(classId))
    if (stillHere()) await router.replace(target)
  } catch (reason) {
    if (stillHere()) joinFailure.value = { reason }
  } finally {
    if (started === visit) busy.value = false
  }
}

/**
 * Maintenance: asks the health check. When the service is up again the user goes to their home (the landing page when
 * signed out); otherwise the page stays and says that it is still unavailable.
 */
async function tryAgain(): Promise<void> {
  if (busy.value) return
  const started = visit
  busy.value = true
  stillDown.value = false
  try {
    const health = await systemApi.health().catch(() => null)
    if (started !== visit) return
    const up = health?.status === 'UP'
    // A page that was loaded during the outage could not restore its session then: it can now.
    if (up && !auth.isAuthenticated) await auth.restoreSession()
    if (started !== visit) return
    if (up) {
      try {
        await router.replace(defaultRouteForRole(auth.activeRole))
      } catch {
        // Lazy route chunk load can fail right after recovery; announce unavailability instead of rejecting silently.
        if (started === visit) stillDown.value = true
      }
    } else {
      stillDown.value = true
    }
  } finally {
    if (started === visit) busy.value = false
  }
}
</script>
