<template>
  <div class="page-stack account-page">
    <PageHeaderBlock :title="t('account.pageTitle')" hide-title />
    <AppToast :message="toast" />

    <StatePanel
      v-if="settingsResource.loading.value"
      tone="loading"
      spacious
      :title="t('account.loadingTitle')"
      :message="t('account.loadingHint')"
    />
    <StatePanel
      v-else-if="settingsResource.error.value || !settings"
      tone="error"
      icon="alert"
      spacious
      :title="t('account.loadErrorTitle')"
      :message="settingsResource.error.value ?? ''"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="settingsResource.reload">{{ t('account.retry') }}</button>
      </template>
    </StatePanel>

    <template v-else>
      <div v-if="pendingEmail" class="alert alert-success" role="status" data-testid="account-email-pending">
        <IconGlyph name="status" :size="18" />
        <span>{{ t('account.emailLinkSent', { email: pendingEmail }) }}</span>
      </div>

      <div class="account-layout">
        <div class="account-column">
          <section class="panel account-card" aria-labelledby="account-profile-title">
            <h2 id="account-profile-title" class="card-title">
              {{ isStudent ? t('account.profileStudent') : t('account.profileOther') }}
            </h2>

            <form id="account-profile-form" class="form-grid" novalidate @submit.prevent="saveProfile">
              <div class="field">
                <label for="account-name">{{ t('account.name') }}</label>
                <input
                  id="account-name"
                  v-model="profile.name"
                  class="input"
                  autocomplete="name"
                  maxlength="120"
                  :aria-invalid="Boolean(profileError)"
                  :aria-describedby="profileError ? 'account-name-error' : undefined"
                />
                <p v-if="profileError" id="account-name-error" class="field-error" role="alert">{{ profileError }}</p>
              </div>

              <div class="field">
                <label for="account-email">{{ t('account.email') }}</label>
                <div class="account-email-row">
                  <input id="account-email" class="input" type="email" :value="settings.email" disabled />
                  <button
                    class="btn btn-secondary"
                    type="button"
                    data-testid="account-change-email"
                    @click="openEmailDialog"
                  >
                    {{ t('account.changeEmail') }}
                  </button>
                </div>
              </div>

              <fieldset
                v-if="isStudent"
                class="field-set"
                :aria-describedby="dailyGoalUnavailable ? 'account-goal-hint' : undefined"
              >
                <legend class="field-label">{{ t('account.dailyGoal') }}</legend>
                <FilterChips
                  v-model="profile.dailyGoal"
                  :options="
                    DAILY_GOALS.map((xp) => ({
                      value: xp,
                      label: t('account.dailyGoalOption', { xp }),
                      testId: `account-goal-${xp}`,
                    }))
                  "
                  :disabled="dailyGoalUnavailable"
                />
                <p v-if="dailyGoalUnavailable" id="account-goal-hint" class="field-hint">
                  {{ t('account.dailyGoalUnavailable') }}
                </p>
              </fieldset>

              <div class="form-actions">
                <button
                  class="btn btn-primary"
                  type="submit"
                  :disabled="savingProfile"
                  :aria-busy="savingProfile"
                  data-testid="account-save"
                >
                  {{ savingProfile ? t('account.saving') : t('account.save') }}
                </button>
              </div>
            </form>
          </section>

          <section class="panel account-card" aria-labelledby="account-notifications-title">
            <h2 id="account-notifications-title" class="card-title">{{ t('account.notificationsTitle') }}</h2>
            <ul class="row-list account-preferences">
              <li v-for="preference in preferenceRows" :key="preference.key">
                <label class="row-list-copy account-preference-label" :for="`account-pref-${preference.key}`">
                  <span class="row-list-title">{{ t(preference.title) }}</span>
                  <span class="row-list-meta">{{ t(preference.hint) }}</span>
                </label>
                <span class="row-list-actions">
                  <span class="type-caption" aria-hidden="true">
                    {{ preferences[preference.key] ? t('account.on') : t('account.off') }}
                  </span>
                  <input
                    :id="`account-pref-${preference.key}`"
                    class="switch"
                    type="checkbox"
                    role="switch"
                    :checked="Boolean(preferences[preference.key])"
                    :disabled="savingPreference === preference.key"
                    :data-testid="`account-pref-${preference.key}`"
                    @change="togglePreference(preference.key, ($event.target as HTMLInputElement).checked)"
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
          <section class="panel account-card" aria-labelledby="account-password-title">
            <h2 id="account-password-title" class="card-title">{{ t('account.passwordTitle') }}</h2>
            <form id="account-password-form" class="form-grid" novalidate @submit.prevent="changePassword">
              <div v-for="field in PASSWORD_FIELDS" :key="field.key" class="field">
                <label :for="`account-${field.key}`">
                  {{ t(field.label) }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  :id="`account-${field.key}`"
                  v-model="password[field.key]"
                  class="input"
                  :type="showPasswords ? 'text' : 'password'"
                  :autocomplete="field.autocomplete"
                  aria-required="true"
                  :aria-invalid="Boolean(passwordErrors[field.key])"
                  :aria-describedby="describedBy(field.key)"
                />
                <p v-if="field.key === 'newPassword'" id="account-newPassword-hint" class="field-hint">
                  {{ t('account.newPasswordHint') }}
                </p>
                <p v-if="passwordErrors[field.key]" :id="`account-${field.key}-error`" class="field-error" role="alert">
                  {{ t(passwordErrors[field.key] as string) }}
                </p>
              </div>
              <p class="callout">
                <IconGlyph name="info" :size="18" />
                <span>{{ t('account.passwordSignOutNote') }}</span>
              </p>
              <div v-if="passwordError" class="alert alert-danger" role="alert">
                <IconGlyph name="alert" :size="18" />
                <span>{{ passwordError }}</span>
              </div>
              <div class="account-password-actions">
                <label class="choice">
                  <input v-model="showPasswords" type="checkbox" data-testid="account-show-passwords" />
                  <span>{{ t('account.showPasswords') }}</span>
                </label>
                <button
                  class="btn btn-primary"
                  type="submit"
                  :disabled="changingPassword"
                  :aria-busy="changingPassword"
                  data-testid="account-change-password"
                >
                  {{ changingPassword ? t('account.changingPassword') : t('account.changePassword') }}
                </button>
              </div>
            </form>
          </section>

          <section class="panel account-card" aria-labelledby="account-signout-title">
            <h2 id="account-signout-title" class="card-title">{{ t('account.signOutTitle') }}</h2>
            <div class="form-actions">
              <button class="btn btn-secondary" type="button" data-testid="account-sign-out" @click="signOut">
                <IconGlyph name="logout" :size="16" />
                {{ t('account.signOut') }}
              </button>
            </div>
          </section>
        </div>
      </div>

      <section v-if="!isAdmin" class="panel account-danger" aria-labelledby="account-danger-title">
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
      <p v-else class="callout">
        <IconGlyph name="shield" :size="18" />
        <span>{{ t('account.adminDeleteNote') }}</span>
      </p>
    </template>

    <AppOverlay
      :open="emailDialogOpen"
      :title="t('account.emailDialogTitle')"
      :subtitle="t('account.emailDialogSubtitle')"
      :busy="sendingEmail"
      test-id="account-email-dialog"
      @close="emailDialogOpen = false"
    >
      <form id="account-email-form" class="form-grid" novalidate @submit.prevent="requestEmailChange">
        <div class="field">
          <label for="account-current-email">{{ t('account.currentEmail') }}</label>
          <input id="account-current-email" class="input" type="email" :value="settings?.email" disabled />
        </div>
        <div class="field">
          <label for="account-new-email">
            {{ t('account.newEmail') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="account-new-email"
            data-autofocus
            v-model="emailForm.newEmail"
            class="input"
            type="email"
            autocomplete="email"
            aria-required="true"
            :aria-invalid="Boolean(emailErrors.newEmail)"
            :aria-describedby="emailErrors.newEmail ? 'account-new-email-error' : undefined"
          />
          <p v-if="emailErrors.newEmail" id="account-new-email-error" class="field-error">{{ emailErrors.newEmail }}</p>
        </div>
        <div class="field">
          <label for="account-email-password">
            {{ t('account.passwordToConfirm') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="account-email-password"
            v-model="emailForm.password"
            class="input"
            type="password"
            autocomplete="current-password"
            aria-required="true"
            :placeholder="t('account.passwordPlaceholder')"
            :aria-invalid="Boolean(emailErrors.password)"
            :aria-describedby="emailErrors.password ? 'account-email-password-error' : undefined"
          />
          <p v-if="emailErrors.password" id="account-email-password-error" class="field-error">
            {{ emailErrors.password }}
          </p>
        </div>
        <div v-if="emailError" class="alert alert-danger" role="alert">
          <IconGlyph name="alert" :size="18" />
          <span>{{ emailError }}</span>
        </div>
      </form>
      <template #footer>
        <button class="btn btn-secondary" type="button" :disabled="sendingEmail" @click="emailDialogOpen = false">
          {{ t('account.cancel') }}
        </button>
        <button
          class="btn btn-primary"
          type="submit"
          form="account-email-form"
          :disabled="sendingEmail"
          :aria-busy="sendingEmail"
          data-testid="account-send-link"
        >
          {{ sendingEmail ? t('account.sendingLink') : t('account.sendLink') }}
        </button>
      </template>
    </AppOverlay>

    <AppOverlay
      :open="deleteDialogOpen"
      :title="t('account.deleteDialogTitle')"
      :subtitle="t('account.deleteDialogSubtitle')"
      :busy="deleting"
      test-id="account-delete-dialog"
      @close="deleteDialogOpen = false"
    >
      <p class="callout is-danger">
        <IconGlyph name="alert" :size="18" />
        <span>{{ deleteCopy }}</span>
      </p>
      <form id="account-delete-form" novalidate @submit.prevent="deleteAccount">
        <div class="field">
          <label for="account-delete-confirm">
            {{ t('account.typeToConfirm', { email: settings?.email ?? '' })
            }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="account-delete-confirm"
            data-autofocus
            v-model="deleteConfirm"
            class="input"
            type="email"
            autocomplete="off"
            spellcheck="false"
            aria-required="true"
          />
        </div>
      </form>
      <div v-if="deleteError" class="alert alert-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>{{ deleteError }}</span>
      </div>
      <template #footer>
        <button class="btn btn-secondary" type="button" :disabled="deleting" @click="deleteDialogOpen = false">
          {{ t('account.cancel') }}
        </button>
        <button
          class="btn btn-danger"
          type="submit"
          form="account-delete-form"
          :disabled="!deleteConfirmed || deleting"
          :aria-busy="deleting"
          data-testid="account-delete-confirm"
        >
          <IconGlyph name="trash" :size="16" />
          {{ deleting ? t('account.deleting') : t('account.deleteForever') }}
        </button>
      </template>
    </AppOverlay>

    <!-- A teacher with a published class: the same dialog, but the way forward is the class page. -->
    <AppOverlay
      :open="blockedDialogOpen"
      :title="t('account.deleteDialogTitle')"
      :subtitle="t('account.blockedSubtitle', { count: publishedClasses.length }, publishedClasses.length)"
      test-id="account-blocked-dialog"
      @close="blockedDialogOpen = false"
    >
      <div class="callout is-warning">
        <IconGlyph name="alert" :size="18" />
        <span>
          <strong>{{ t('account.blockedHeading', {}, publishedClasses.length) }}</strong>
          <span>
            {{
              onlyPublishedClass
                ? t('account.blockedCopyOne', { title: onlyPublishedClass.title })
                : t('account.blockedCopyMany')
            }}
          </span>
        </span>
      </div>
      <ul v-if="!onlyPublishedClass" class="row-list">
        <li v-for="published in publishedClasses" :key="published.id">
          <span class="row-list-title account-blocked-title">{{ published.title }}</span>
          <RouterLink
            class="btn btn-secondary btn-sm"
            :to="{ name: 'teacher-class', params: { classId: published.id } }"
          >
            {{ t('account.openClass') }}
            <IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </li>
      </ul>
      <template #footer>
        <button class="btn btn-secondary" type="button" @click="blockedDialogOpen = false">
          {{ t('account.close') }}
        </button>
        <RouterLink
          v-if="onlyPublishedClass"
          class="btn btn-primary"
          :to="{ name: 'teacher-class', params: { classId: onlyPublishedClass.id } }"
        >
          {{ t('account.openClass') }}
        </RouterLink>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { xpApi } from '@/api/learn'
import { accountOpsApi } from '@/api/ops'
import { accountApi } from '@/api/services'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { accountMessages } from '@/plugins/accountMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { NotificationPreferences } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { isValidEmail } from '@/utils/email'
import {
  hasPasswordChangeErrors,
  validatePasswordChange,
  type PasswordChangeErrors,
  type PasswordChangeForm,
} from '@/utils/password'
import '@/styles/ops-account.css'

type DailyGoal = 10 | 20 | 30
type PreferenceKey = keyof NotificationPreferences

const DAILY_GOALS: readonly DailyGoal[] = [10, 20, 30]
const PASSWORD_FIELDS = [
  { key: 'currentPassword', label: 'account.currentPassword', autocomplete: 'current-password' },
  { key: 'newPassword', label: 'account.newPassword', autocomplete: 'new-password' },
  { key: 'confirmPassword', label: 'account.confirmPassword', autocomplete: 'new-password' },
] as const satisfies readonly { key: keyof PasswordChangeForm; label: string; autocomplete: string }[]

const { t } = useLocalMessages({ account: accountMessages })
const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const { toast, showToast } = useToast()

const isStudent = computed(() => auth.activeRole === 'STUDENT')
const isTeacher = computed(() => auth.activeRole === 'TEACHER')
const isAdmin = computed(() => auth.activeRole === 'ADMIN')

const settingsResource = useResource(() => accountOpsApi.settings())
const settings = computed(() => settingsResource.data.value)
// The learn module serves the daily goal; without it the control is shown disabled and the page still works.
const dailyGoalResource = useResource(() => (isStudent.value ? xpApi.dailyGoal() : Promise.resolve(null)))
const dailyGoalUnavailable = computed(() => Boolean(dailyGoalResource.error.value) || !dailyGoalResource.data.value)

const pendingEmail = ref<string | null>(null)
const profile = reactive({ name: '', dailyGoal: 20 as DailyGoal })
const preferences = reactive<NotificationPreferences>({
  liveSessionReminders: null,
  feedback: null,
  contentReviews: null,
  reviewQueues: null,
})

watch(
  settings,
  (value) => {
    if (!value) return
    profile.name = value.fullName
    pendingEmail.value = value.pendingEmail
    Object.assign(preferences, value.notifications)
  },
  { immediate: true },
)
watch(
  () => dailyGoalResource.data.value,
  (value) => {
    if (value && DAILY_GOALS.includes(value.dailyGoalXp as DailyGoal))
      profile.dailyGoal = value.dailyGoalXp as DailyGoal
  },
  { immediate: true },
)

const PREFERENCE_ROWS: Record<'STUDENT' | 'TEACHER' | 'ADMIN', { key: PreferenceKey; title: string; hint: string }[]> =
  {
    STUDENT: [
      { key: 'liveSessionReminders', title: 'account.liveRemindersTitle', hint: 'account.liveRemindersStudent' },
      { key: 'feedback', title: 'account.feedbackTitle', hint: 'account.feedbackHint' },
    ],
    TEACHER: [
      { key: 'liveSessionReminders', title: 'account.liveRemindersTitle', hint: 'account.liveRemindersTeacher' },
      { key: 'contentReviews', title: 'account.contentReviewsTitle', hint: 'account.contentReviewsHint' },
    ],
    ADMIN: [{ key: 'reviewQueues', title: 'account.reviewQueuesTitle', hint: 'account.reviewQueuesHint' }],
  }
const preferenceRows = computed(() => PREFERENCE_ROWS[auth.activeRole ?? 'STUDENT'])

// ----- profile -----

const savingProfile = ref(false)
const profileError = ref<string | null>(null)

async function saveProfile(): Promise<void> {
  if (savingProfile.value || !settings.value) return
  const name = profile.name.trim()
  profileError.value = name ? null : t('account.nameRequired')
  if (!name) return

  savingProfile.value = true
  try {
    const writes: Promise<unknown>[] = [
      accountOpsApi.updateProfile({ displayName: name, preferredLanguage: ui.locale }).then((user) => {
        auth.applyProfileUpdate(user)
      }),
    ]
    const savedGoal = dailyGoalResource.data.value?.dailyGoalXp
    if (isStudent.value && !dailyGoalUnavailable.value && savedGoal !== profile.dailyGoal) {
      writes.push(xpApi.setDailyGoal(profile.dailyGoal).then((goal) => (dailyGoalResource.data.value = goal)))
    }
    await Promise.all(writes)
    showToast(t('account.changesSaved'))
  } catch (reason) {
    profileError.value = resolveApiError(reason, t('account.saveError'))
  } finally {
    savingProfile.value = false
  }
}

// ----- notification preferences -----

const savingPreference = ref<PreferenceKey | null>(null)
const preferencesError = ref<string | null>(null)

async function togglePreference(key: PreferenceKey, enabled: boolean): Promise<void> {
  const previous = preferences[key]
  preferences[key] = enabled
  savingPreference.value = key
  preferencesError.value = null
  try {
    Object.assign(preferences, await accountOpsApi.updatePreferences({ [key]: enabled }))
    showToast(t('account.changesSaved'))
  } catch (reason) {
    preferences[key] = previous
    preferencesError.value = resolveApiError(reason, t('account.preferencesError'))
  } finally {
    savingPreference.value = null
  }
}

// ----- e-mail change -----

const emailDialogOpen = ref(false)
const sendingEmail = ref(false)
const emailError = ref<string | null>(null)
const emailForm = reactive({ newEmail: '', password: '' })
const emailErrors = reactive<{ newEmail: string | null; password: string | null }>({ newEmail: null, password: null })

function openEmailDialog(): void {
  emailForm.newEmail = ''
  emailForm.password = ''
  emailErrors.newEmail = null
  emailErrors.password = null
  emailError.value = null
  emailDialogOpen.value = true
}

async function requestEmailChange(): Promise<void> {
  if (sendingEmail.value || !settings.value) return
  const newEmail = emailForm.newEmail.trim().toLowerCase()
  emailErrors.newEmail = !isValidEmail(newEmail)
    ? t('account.newEmailInvalid')
    : newEmail === settings.value.email
      ? t('account.newEmailSame')
      : null
  emailErrors.password = emailForm.password ? null : t('account.passwordToConfirmRequired')
  if (emailErrors.newEmail || emailErrors.password) return

  sendingEmail.value = true
  emailError.value = null
  try {
    const result = await accountOpsApi.requestEmailChange({ newEmail, currentPassword: emailForm.password })
    pendingEmail.value = result.pendingEmail
    emailDialogOpen.value = false
  } catch (reason) {
    emailError.value = resolveApiError(reason, t('account.emailError'))
  } finally {
    sendingEmail.value = false
  }
}

// ----- password -----

const password = reactive<PasswordChangeForm>({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordErrors = ref<PasswordChangeErrors>({})
const passwordError = ref<string | null>(null)
const showPasswords = ref(false)
const changingPassword = ref(false)

function describedBy(key: keyof PasswordChangeForm): string | undefined {
  const ids = [
    key === 'newPassword' ? 'account-newPassword-hint' : '',
    passwordErrors.value[key] ? `account-${key}-error` : '',
  ].filter(Boolean)
  return ids.length ? ids.join(' ') : undefined
}

async function changePassword(): Promise<void> {
  if (changingPassword.value) return
  passwordErrors.value = validatePasswordChange(password)
  passwordError.value = null
  if (hasPasswordChangeErrors(passwordErrors.value)) return

  changingPassword.value = true
  try {
    const response = await accountApi.changePassword({ ...password })
    auth.replaceAccessToken(response.token)
    Object.assign(password, { currentPassword: '', newPassword: '', confirmPassword: '' })
    showToast(t('account.passwordChanged'))
  } catch (reason) {
    passwordError.value = resolveApiError(reason, t('account.passwordError'))
  } finally {
    changingPassword.value = false
  }
}

// ----- sign out and deletion -----

async function signOut(): Promise<void> {
  await auth.logout()
  await router.push('/')
}

const deleteDialogOpen = ref(false)
const blockedDialogOpen = ref(false)
const deleteConfirm = ref('')
const deleteError = ref<string | null>(null)
const deleting = ref(false)
const deleteConfirmed = computed(
  () => Boolean(settings.value) && deleteConfirm.value.trim().toLowerCase() === settings.value?.email.toLowerCase(),
)
const deleteCopy = computed(() => {
  if (!isTeacher.value) return t('account.deleteStudentCopy')
  return auth.isApprovedTeacher ? t('account.deleteTeacherCopy') : t('account.deleteApplicantCopy')
})
const publishedClasses = computed(() => settings.value?.deletion.publishedClasses ?? [])
const onlyPublishedClass = computed(() => (publishedClasses.value.length === 1 ? publishedClasses.value[0] : null))

function openDeleteDialog(): void {
  if (settings.value?.deletion.blockedBy === 'PUBLISHED_CLASSES') {
    blockedDialogOpen.value = true
    return
  }
  deleteConfirm.value = ''
  deleteError.value = null
  deleteDialogOpen.value = true
}

async function deleteAccount(): Promise<void> {
  if (!deleteConfirmed.value || deleting.value) return
  deleting.value = true
  deleteError.value = null
  try {
    await accountOpsApi.deleteAccount(deleteConfirm.value.trim())
    deleteDialogOpen.value = false
    auth.clearSession()
    await router.replace({ name: 'landing', query: { notice: 'account-deleted' } })
  } catch (reason) {
    deleteError.value = resolveApiError(reason, t('account.deleteError'))
  } finally {
    deleting.value = false
  }
}
</script>
