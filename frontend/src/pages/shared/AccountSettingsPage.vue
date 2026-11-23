<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('account.pageTitle')" hide-title />
    <AppToast :message="toast" :action="toastAction" />

    <StatePanel
      v-if="loading"
      heading-tag="h2"
      tone="loading"
      spacious
      :title="t('account.loadingTitle')"
      :message="t('account.loadingHint')"
    />
    <StatePanel
      v-else-if="!settings"
      heading-tag="h2"
      tone="error"
      icon="alert"
      spacious
      :title="t('account.loadErrorTitle')"
      :message="error ?? ''"
      @retry="reloadAll"
    />

    <template v-else>
      <div class="account-layout">
        <div class="account-column">
          <section class="panel account-card" aria-labelledby="account-profile-title">
            <h2 id="account-profile-title" class="card-title">
              {{ isLearner ? t('account.profileLearner') : t('account.profileOther') }}
            </h2>

            <form id="account-profile-form" class="form-grid" novalidate @submit.prevent="saveProfile">
              <div class="field">
                <label for="account-name">
                  {{ t('account.name') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="account-name"
                  v-model="profile.name"
                  class="input"
                  autocomplete="name"
                  required
                  maxlength="120"
                  :aria-invalid="Boolean(nameError)"
                  :aria-describedby="nameError ? 'account-name-error' : undefined"
                  @blur="checkName"
                />
                <p v-if="nameError" id="account-name-error" class="field-error" role="alert">
                  {{ t(nameError) }}
                </p>
              </div>

              <div class="field">
                <label for="account-email">{{ t('account.email') }}</label>
                <div class="account-email-row">
                  <input
                    id="account-email"
                    class="input"
                    type="email"
                    :value="settings.email"
                    readonly
                    :aria-describedby="pendingEmail ? 'account-email-pending' : undefined"
                  />
                  <button
                    ref="changeEmailButton"
                    class="btn btn-secondary"
                    type="button"
                    data-testid="account-change-email"
                    :disabled="pendingBusy"
                    @click="emailDialogOpen = true"
                  >
                    {{ t('account.changeEmail') }}
                  </button>
                </div>
                <!-- A change waits for its link: send it again, or cancel the change (its link stops working). -->
                <p v-if="pendingEmail" class="field-hint" data-testid="account-email-pending">
                  <span id="account-email-pending">{{ t('account.emailPending', { email: pendingEmail }) }}</span>
                  <span aria-hidden="true"> · </span>
                  <button
                    class="btn btn-link"
                    type="button"
                    :disabled="pendingBusy"
                    data-testid="account-email-resend"
                    @click="changePendingEmail('resend')"
                  >
                    {{ t('account.resendLink') }}
                  </button>
                  <span aria-hidden="true"> · </span>
                  <button
                    class="btn btn-link"
                    type="button"
                    :disabled="pendingBusy"
                    data-testid="account-email-cancel"
                    @click="changePendingEmail('cancel')"
                  >
                    {{ t('account.cancelChange') }}
                  </button>
                </p>
                <p v-if="pendingError" class="field-error" role="alert" data-testid="account-email-pending-error">
                  {{ pendingError }}
                </p>
              </div>

              <fieldset
                v-if="isLearner"
                class="field-set"
                :aria-describedby="dailyGoalResource.error.value ? 'account-goal-hint' : undefined"
              >
                <legend class="field-label">{{ t('common.dailyGoal') }}</legend>
                <FilterChips
                  v-model="profile.dailyGoal"
                  :options="dailyGoalOptions"
                  :disabled="!dailyGoalResource.data.value"
                />
                <p v-if="dailyGoalResource.error.value" id="account-goal-hint" class="field-hint">
                  {{ t('account.dailyGoalUnavailable') }}
                </p>
              </fieldset>

              <div v-if="profileError" class="alert alert-danger" role="alert" data-testid="account-save-error">
                <IconGlyph name="alert" :size="18" />
                <span>{{ profileError }}</span>
              </div>
              <div class="form-actions">
                <button
                  class="btn btn-primary"
                  type="submit"
                  :disabled="savingProfile || Boolean(nameError)"
                  :aria-busy="savingProfile"
                  data-testid="account-save"
                >
                  {{ savingProfile ? t('account.saving') : t('account.save') }}
                </button>
              </div>
            </form>
          </section>

          <HintSettingsCard v-if="isLearner" @saved="showToast" />

          <section class="panel account-card" aria-labelledby="account-notifications-title">
            <h2 id="account-notifications-title" class="card-title">{{ t('account.notificationsTitle') }}</h2>
            <ul class="row-list account-preferences">
              <li v-if="isAdmin">
                <label class="row-list-copy account-preference-label" for="account-pref-daily-summary">
                  <span class="row-list-title">{{ t('account.dailySummaryTitle') }}</span>
                  <span class="row-list-meta">{{ t('account.dailySummaryHint') }}</span>
                </label>
                <span class="row-list-actions">
                  <span class="type-caption" aria-hidden="true">
                    {{ dailySummary ? t('account.on') : t('account.off') }}
                  </span>
                  <input
                    id="account-pref-daily-summary"
                    class="switch"
                    type="checkbox"
                    role="switch"
                    :checked="dailySummary"
                    :disabled="savingSummary"
                    data-testid="account-pref-daily-summary"
                    @change="toggleDailySummary(($event.target as HTMLInputElement).checked)"
                  />
                </span>
              </li>
              <li>
                <span class="row-list-copy">
                  <span class="row-list-title">{{ t('account.securityTitle') }}</span>
                  <span class="row-list-meta">{{ t('account.securityHint') }}</span>
                </span>
                <span class="tag">{{ t('account.alwaysOn') }}</span>
              </li>
            </ul>
            <p v-if="preferencesError" class="field-error" role="alert">{{ preferencesError }}</p>
          </section>
        </div>

        <div class="account-column">
          <AccountPasswordCard @changed="onPasswordChanged" />

          <section class="panel account-card" aria-labelledby="account-device-title">
            <div class="account-device-copy">
              <h2 id="account-device-title" class="card-title">{{ t('account.deviceTitle') }}</h2>
              <p class="type-caption" data-testid="account-device">{{ deviceLine }}</p>
            </div>
            <!-- POST /auth/logout ends this browser's session only; a password change signs out the others. -->
            <p class="field-hint">{{ t('account.signOutHint') }}</p>
            <div class="form-actions">
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="signingOut"
                :aria-busy="signingOut"
                data-testid="account-sign-out"
                @click="signOut"
              >
                <IconGlyph name="logout" :size="16" />
                {{ t('account.signOut') }}
              </button>
            </div>
          </section>
        </div>
      </div>

      <section v-if="isLearner && !isAdmin" class="panel account-danger" aria-labelledby="account-danger-title">
        <div class="row-list-copy">
          <h2 id="account-danger-title" class="row-list-title">{{ t('account.dangerZone') }}</h2>
          <p class="row-list-meta">{{ t('account.dangerHint') }}</p>
        </div>
        <button class="btn btn-danger-soft" type="button" data-testid="account-delete" @click="openDeleteDialog">
          <IconGlyph name="trash" :size="16" />
          {{ t('account.deleteAccount') }}
        </button>
      </section>
      <!-- Admins cannot delete their own account; how an admin account ends is said here, not in a red zone. -->
      <p v-else-if="isAdmin" class="callout">
        <IconGlyph name="shield" :size="18" />
        <span>{{ t('account.adminDeleteNote') }}</span>
      </p>
    </template>

    <AccountEmailChangeDialog
      :open="emailDialogOpen"
      :current-email="settings?.email ?? null"
      :disabled="pendingBusy"
      @close="emailDialogOpen = false"
      @requested="onEmailChangeRequested"
    />

    <AccountDeleteDialog
      :open="deleteDialogOpen"
      :current-email="settings?.email ?? null"
      @close="deleteDialogOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, toRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { xpApi } from '@/api/learn'
import { accountOpsApi } from '@/api/ops'
import { authApi } from '@/api/services'
import AccountDeleteDialog from '@/components/account/AccountDeleteDialog.vue'
import AccountEmailChangeDialog from '@/components/account/AccountEmailChangeDialog.vue'
import AccountPasswordCard from '@/components/account/AccountPasswordCard.vue'
import AppToast from '@/components/common/AppToast.vue'
import FilterChips, { type FilterChipOption } from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import HintSettingsCard from '@/components/learn/HintSettingsCard.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { accountMessages } from '@/plugins/accountMessages'
import { appLocale } from '@/plugins/i18n'
import { useAuthStore } from '@/stores/auth'
import { hasServerMessage, resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import { SERVER_MESSAGE } from '@/utils/serverMessages'
import { DAILY_GOALS } from '@/utils/xpLevel'
import '@/styles/ops-account.css'

/** The server's answer when a pending email change was already retired or expired (AccountEmailService). */
const NO_PENDING_EMAIL_CHANGE = SERVER_MESSAGE.NO_PENDING_EMAIL_CHANGE

const BROWSERS = [
  [/Edg\//, 'Edge'],
  [/OPR\//, 'Opera'],
  [/Firefox\//, 'Firefox'],
  [/Chrome\//, 'Chrome'],
  [/Safari\//, 'Safari'],
] as const

// iOS also says "like Mac OS X", and Android also says "Linux".
const SYSTEMS = [
  [/Windows/, 'Windows'],
  [/iPhone|iPad|iPod/, 'iOS'],
  [/Mac OS X|Macintosh/, 'macOS'],
  [/Android/, 'Android'],
  [/CrOS/, 'ChromeOS'],
  [/Linux/, 'Linux'],
] as const

function detectBrowserDevice(agent: string): { browser?: string; os?: string } {
  return {
    browser: BROWSERS.find(([pattern]) => pattern.test(agent))?.[1],
    os: SYSTEMS.find(([pattern]) => pattern.test(agent))?.[1],
  }
}

const { t } = useLocalMessages({ account: accountMessages })
const auth = useAuthStore()
const router = useRouter()
const { toast, toastAction, showToast } = useToast()

const isUnmounted = useIsUnmounted()

function canUpdatePage(version: number): boolean {
  return !isUnmounted() && version === getAuthSessionVersion()
}

const isLearner = computed(() => auth.roles.includes('STUDENT'))
const isAdmin = computed(() => auth.roles.includes('ADMIN'))

const { data: settings, loading, error, reload } = useResource(accountOpsApi.settings)
// The learn module serves the daily goal (learners only). Its control waits, disabled, for the goal; when the goal
// cannot be loaded it says so and the rest of the page works.
const dailyGoalResource = useResource(xpApi.dailyGoal, isLearner.value)
const sessionResource = useResource(authApi.session)

const pendingEmail = ref<string | null>(null)
const profile = reactive<{ name: string; dailyGoal: number | null }>({ name: '', dailyGoal: null })
const dailySummary = ref(false)

const dailyGoalOptions = computed<FilterChipOption<number | null>[]>(() =>
  DAILY_GOALS.map((xp) => ({
    value: xp,
    label: t('account.dailyGoalOption', { xp }),
    testId: `account-goal-${xp}`,
  })),
)

watch(
  settings,
  (value) => {
    if (!value) return
    profile.name = value.fullName
    pendingEmail.value = value.pendingEmail
    dailySummary.value = Boolean(value.notifications.reviewQueues)
  },
  { once: true },
)
watch(
  dailyGoalResource.data,
  (value) => {
    if (value) profile.dailyGoal = value.dailyGoalXp
  },
  { once: true },
)

function reloadAll(): void {
  void reload()
  if (isLearner.value && dailyGoalResource.error.value) void dailyGoalResource.reload()
  if (sessionResource.error.value) void sessionResource.reload()
}

/** "Chrome on Windows · signed in since 12 May": read from the user agent and the current session. */
const deviceLine = computed(() => {
  const { browser, os } = detectBrowserDevice(navigator.userAgent)
  const device = browser && os ? t('account.deviceLine', { browser, os }) : (browser ?? t('account.deviceUnknown'))
  const signedInAt = sessionResource.data.value?.signedInAt
  return signedInAt ? t('account.deviceSince', { device, date: formatShortDate(signedInAt, appLocale.value) }) : device
})

// Profile

const savingProfile = ref(false)
const profileError = ref<string | null>(null)
const { error: nameError, validate: checkName } = useFieldCheck(toRef(profile, 'name'), (name) =>
  name.trim() ? null : 'account.nameRequired',
)

async function saveDisplayName(version: number): Promise<void> {
  const name = profile.name.trim()
  if (!settings.value || name === settings.value.fullName) return
  const user = await accountOpsApi.updateProfile({ displayName: name })
  if (version !== getAuthSessionVersion()) return
  // The header still needs the saved name if the user has left this page.
  auth.applySavedName(user, version)
  if (isUnmounted()) return
  settings.value = { ...settings.value, fullName: user.fullName }
  if (profile.name.trim() === name) profile.name = user.fullName
}

async function saveDailyGoal(version: number): Promise<void> {
  const requested = profile.dailyGoal
  const saved = dailyGoalResource.data.value
  if (!saved || requested === null || requested === saved.dailyGoalXp) return
  const goal = await xpApi.setDailyGoal(requested)
  if (version !== getAuthSessionVersion()) return
  notifyProgressChanged()
  if (isUnmounted()) return
  dailyGoalResource.replace(goal)
  if (profile.dailyGoal === requested) profile.dailyGoal = goal.dailyGoalXp
}

async function saveProfile(): Promise<void> {
  if (savingProfile.value) return
  profileError.value = null
  if (!checkName()) {
    await nextTick()
    document.getElementById('account-name')?.focus()
    return
  }

  savingProfile.value = true
  const version = getAuthSessionVersion()
  // A failed write must not unlock Save while the other write is still running.
  const results = await Promise.allSettled([saveDisplayName(version), saveDailyGoal(version)])
  if (!isUnmounted()) savingProfile.value = false
  if (!canUpdatePage(version)) return
  const failed = results.find((result) => result.status === 'rejected')
  if (failed) profileError.value = resolveApiError(failed.reason, t('account.saveError'))
  else showToast(t('account.changesSaved'))
}

// The admin's daily summary

const savingSummary = ref(false)
const preferencesError = ref<string | null>(null)

async function toggleDailySummary(enabled: boolean): Promise<void> {
  if (savingSummary.value) return
  const version = getAuthSessionVersion()
  const previous = dailySummary.value
  dailySummary.value = enabled
  savingSummary.value = true
  preferencesError.value = null
  try {
    const saved = await accountOpsApi.updatePreferences({ reviewQueues: enabled })
    if (!canUpdatePage(version)) return
    dailySummary.value = Boolean(saved.reviewQueues)
    showToast(t('account.changesSaved'))
  } catch (reason) {
    // Always restore the optimistic switch while still mounted, even if a concurrent password change rotated the token.
    if (!isUnmounted()) dailySummary.value = previous
    if (!canUpdatePage(version)) return
    preferencesError.value = resolveApiError(reason, t('account.preferencesError'))
  } finally {
    if (!isUnmounted()) savingSummary.value = false
  }
}

// Pending e-mail change & password update hooks

const emailDialogOpen = ref(false)
const changeEmailButton = ref<HTMLButtonElement | null>(null)
const pendingBusy = ref(false)
const pendingError = ref<string | null>(null)

function onEmailChangeRequested(nextPendingEmail: string): void {
  pendingEmail.value = nextPendingEmail
  pendingError.value = null
  emailDialogOpen.value = false
  showToast(t('account.emailLinkSent', { email: nextPendingEmail }))
}

function onPasswordChanged(): void {
  // Changing the password retires any open email-change confirmation on the server (AuthService.changePassword).
  pendingEmail.value = null
  pendingError.value = null
  showToast(t('account.passwordChanged'))
}

/** A pending change can get a new link or be cancelled; restoring focus when the pending row unmounts. */
async function changePendingEmail(action: 'resend' | 'cancel'): Promise<void> {
  if (pendingBusy.value) return
  const version = getAuthSessionVersion()
  pendingBusy.value = true
  pendingError.value = null
  let rowRemoved = false
  try {
    if (action === 'resend') {
      const result = await accountOpsApi.resendEmailChange()
      if (!canUpdatePage(version)) return
      pendingEmail.value = result.pendingEmail
      showToast(t('account.emailLinkSentAgain'))
      return
    }
    await accountOpsApi.cancelEmailChange()
    if (!canUpdatePage(version)) return
    pendingEmail.value = null
    rowRemoved = true
    showToast(t('account.emailChangeCancelled'))
  } catch (reason) {
    if (canUpdatePage(version)) {
      if (action === 'resend' && hasServerMessage(reason, NO_PENDING_EMAIL_CHANGE)) {
        pendingEmail.value = null
        rowRemoved = true
      }
      pendingError.value = resolveApiError(
        reason,
        t(action === 'resend' ? 'account.emailResendError' : 'account.emailCancelError'),
      )
    }
  } finally {
    if (!isUnmounted()) pendingBusy.value = false
  }
  if (!rowRemoved) return
  await nextTick()
  if (canUpdatePage(version) && document.activeElement === document.body) changeEmailButton.value?.focus()
}

// Sign out and deletion

const signingOut = ref(false)
async function signOut(): Promise<void> {
  if (signingOut.value) return
  signingOut.value = true
  try {
    const signedOut = await auth.logout()
    if (signedOut && (!isUnmounted() || Boolean(router.currentRoute.value.meta.roles?.length))) {
      await router.push('/')
    }
  } finally {
    if (!isUnmounted()) signingOut.value = false
  }
}

const deleteDialogOpen = ref(false)

function openDeleteDialog(): void {
  deleteDialogOpen.value = true
}
</script>
