<template>
  <div class="page-stack account-page">
    <PageHeaderBlock
      :title="t('account.pageTitle')"
      :description="settings ? t('account.signedInAs', { email: settings.email }) : ''"
    />
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
      <DataNotice
        :info="pendingEmail ? t('account.emailLinkSent', { email: pendingEmail }) : null"
        :info-title="t('account.emailPendingTitle')"
      />

      <div class="account-layout">
        <!-- The section index: one jump per section, in page order (desktop). -->
        <nav class="account-index" :aria-label="t('account.sectionsLabel')">
          <ul>
            <li v-for="section in sections" :key="section.id">
              <RouterLink :to="{ hash: `#${section.id}` }" :class="{ 'is-danger': section.id === 'account-danger' }">
                {{ section.label }}
              </RouterLink>
            </li>
          </ul>
        </nav>

        <div class="account-sections">
          <div class="panel account-sheet">
            <section id="account-profile" class="account-section" aria-labelledby="account-profile-title">
              <div class="account-section-head">
                <h2 id="account-profile-title" class="account-section-title">
                  {{ isStudent ? t('account.profileStudent') : t('account.profileOther') }}
                </h2>
                <p class="account-section-hint">{{ t('account.profileHint') }}</p>
              </div>

              <form id="account-profile-form" class="form-grid account-form" novalidate @submit.prevent="saveProfile">
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

                <fieldset v-if="isStudent" class="field-set account-goal" aria-describedby="account-goal-hint">
                  <legend class="field-label">{{ t('account.dailyGoal') }}</legend>
                  <SegmentedControl
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
                  <p id="account-goal-hint" class="field-hint">
                    {{ dailyGoalUnavailable ? t('account.dailyGoalUnavailable') : t('account.dailyGoalHint') }}
                  </p>
                </fieldset>

                <ul class="account-notes">
                  <li v-if="isTeacher">
                    <IconGlyph name="verified" :size="16" />
                    <span>
                      {{ t('account.teacherProfileHint') }}
                      <RouterLink class="account-notes-link" :to="{ name: 'teacher-profile' }">
                        {{ t('account.teachingProfile') }}<IconGlyph name="chevronRight" :size="14" />
                      </RouterLink>
                    </span>
                  </li>
                  <li>
                    <IconGlyph name="language" :size="16" />
                    <span>{{ t('account.languageHint') }}</span>
                  </li>
                </ul>

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

            <section id="account-notifications" class="account-section" aria-labelledby="account-notifications-title">
              <div class="account-section-head">
                <h2 id="account-notifications-title" class="account-section-title">
                  {{ t('account.notificationsTitle') }}
                </h2>
                <p class="account-section-hint">{{ t('account.notificationsHint') }}</p>
              </div>
              <div class="account-form">
                <ul class="account-preferences">
                  <li v-for="preference in preferenceRows" :key="preference.key" class="account-preference">
                    <label class="account-preference-label" :for="`account-pref-${preference.key}`">
                      <strong>{{ t(preference.title) }}</strong>
                      <span>{{ t(preference.hint) }}</span>
                    </label>
                    <span class="account-preference-control">
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
                  <li class="account-preference">
                    <span class="account-preference-label">
                      <strong>{{ t('account.securityTitle') }}</strong>
                      <span>{{ t('account.securityHint') }}</span>
                    </span>
                    <StatusPill :label="t('account.alwaysOn')" tone="neutral" icon="lock" />
                  </li>
                </ul>
                <p v-if="preferencesError" class="field-error" role="alert">{{ preferencesError }}</p>
              </div>
            </section>

            <section id="account-password" class="account-section" aria-labelledby="account-password-title">
              <div class="account-section-head">
                <h2 id="account-password-title" class="account-section-title">{{ t('account.passwordTitle') }}</h2>
                <p class="account-section-hint">{{ t('account.passwordSignOutNote') }}</p>
              </div>
              <form
                id="account-password-form"
                class="form-grid account-form"
                novalidate
                @submit.prevent="changePassword"
              >
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
                  <p
                    v-if="passwordErrors[field.key]"
                    :id="`account-${field.key}-error`"
                    class="field-error"
                    role="alert"
                  >
                    {{ t(passwordErrors[field.key] as string) }}
                  </p>
                </div>
                <p v-if="passwordError" class="alert alert-danger" role="alert">
                  <IconGlyph name="alert" :size="18" />
                  <span>{{ passwordError }}</span>
                </p>
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

            <section id="account-signout" class="account-section" aria-labelledby="account-signout-title">
              <div class="account-section-head">
                <h2 id="account-signout-title" class="account-section-title">{{ t('account.signOutTitle') }}</h2>
                <p class="account-section-hint">{{ t('account.signOutHint') }}</p>
                <!-- Admins cannot delete their own account; how an admin account ends is said here, not in a red zone. -->
                <p v-if="isAdmin" class="account-section-hint">{{ t('account.adminDeleteNote') }}</p>
              </div>
              <div class="account-form account-row">
                <button class="btn btn-secondary" type="button" data-testid="account-sign-out" @click="signOut">
                  <IconGlyph name="logout" :size="16" />
                  {{ t('account.signOut') }}
                </button>
              </div>
            </section>
          </div>

          <section
            v-if="!isAdmin"
            id="account-danger"
            class="account-section account-danger"
            aria-labelledby="account-danger-title"
          >
            <div class="account-section-head">
              <h2 id="account-danger-title" class="account-section-title">{{ t('account.dangerZone') }}</h2>
              <p class="account-section-hint">{{ t('account.dangerHint') }}</p>
            </div>
            <div class="account-form account-row">
              <button class="btn btn-danger-soft" type="button" data-testid="account-delete" @click="openDeleteDialog">
                <IconGlyph name="trash" :size="16" />
                {{ t('account.deleteAccount') }}
              </button>
            </div>
          </section>
        </div>
      </div>
    </template>

    <AppOverlay
      :open="emailDialogOpen"
      :title="t('account.emailDialogTitle')"
      :subtitle="t('account.emailDialogSubtitle')"
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
            :aria-invalid="Boolean(emailErrors.password)"
            :aria-describedby="emailErrors.password ? 'account-email-password-error' : undefined"
          />
          <p v-if="emailErrors.password" id="account-email-password-error" class="field-error">
            {{ emailErrors.password }}
          </p>
        </div>
        <p v-if="emailError" class="alert alert-danger" role="alert">
          <IconGlyph name="alert" :size="18" />
          <span>{{ emailError }}</span>
        </p>
      </form>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="emailDialogOpen = false">{{ t('account.cancel') }}</button>
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
      test-id="account-delete-dialog"
      @close="deleteDialogOpen = false"
    >
      <div class="callout is-danger">
        <IconGlyph name="alert" :size="18" />
        <p>{{ isTeacher ? t('account.deleteTeacherCopy') : t('account.deleteStudentCopy') }}</p>
      </div>
      <form id="account-delete-form" novalidate @submit.prevent="deleteAccount">
        <div class="field">
          <label for="account-delete-confirm">{{ t('account.typeToConfirm', { email: settings?.email ?? '' }) }}</label>
          <input
            id="account-delete-confirm"
            v-model="deleteConfirm"
            class="input"
            type="email"
            autocomplete="off"
            spellcheck="false"
          />
        </div>
      </form>
      <p v-if="deleteError" class="alert alert-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>{{ deleteError }}</span>
      </p>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="deleteDialogOpen = false">{{ t('account.cancel') }}</button>
        <button
          class="btn btn-danger"
          type="submit"
          form="account-delete-form"
          :disabled="!deleteConfirmed || deleting"
          :aria-busy="deleting"
          data-testid="account-delete-confirm"
        >
          {{ deleting ? t('account.deleting') : t('account.deleteForever') }}
        </button>
      </template>
    </AppOverlay>

    <AppOverlay
      :open="blockedDialogOpen"
      :title="t('account.blockedTitle')"
      :subtitle="t('account.blockedSubtitle')"
      test-id="account-blocked-dialog"
      @close="blockedDialogOpen = false"
    >
      <p class="card-subtitle">{{ t('account.blockedCopy') }}</p>
      <ul class="account-blocked-list">
        <li v-for="published in settings?.deletion.publishedClasses ?? []" :key="published.id">
          <IconGlyph name="classes" :size="18" />
          <span>{{ published.title }}</span>
          <RouterLink class="btn btn-link" :to="{ name: 'teacher-class', params: { classId: published.id } }">
            {{ t('account.openClass') }}
          </RouterLink>
        </li>
      </ul>
      <template #footer>
        <button class="btn btn-secondary" type="button" @click="blockedDialogOpen = false">
          {{ t('account.close') }}
        </button>
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
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
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

/** The section index, in page order. */
const sections = computed(() => [
  { id: 'account-profile', label: isStudent.value ? t('account.profileStudent') : t('account.profileOther') },
  { id: 'account-notifications', label: t('account.notificationsTitle') },
  { id: 'account-password', label: t('account.passwordTitle') },
  { id: 'account-signout', label: t('account.signOutTitle') },
  ...(isAdmin.value ? [] : [{ id: 'account-danger', label: t('account.dangerZone') }]),
])

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
