<template>
  <div class="page-stack account-page" data-testid="account-page">
    <PageHeaderBlock :title="t('account.title')" :description="t('account.description')" icon="account" />

    <DataNotice v-if="isPreview" using-fallback :fallback-message="t('account.previewNotice')" />

    <section class="account-grid">
      <form class="panel account-card" novalidate data-testid="account-profile-form" @submit.prevent="saveProfile">
        <div class="account-card-head">
          <span class="account-card-icon" aria-hidden="true"><IconGlyph name="profile" :size="20" /></span>
          <div>
            <h2 class="card-title">{{ t('account.profileHeading') }}</h2>
            <p class="card-subtitle">{{ t('account.profileHint') }}</p>
          </div>
        </div>

        <div class="field">
          <label class="field-label" for="account-email">{{ t('account.email') }}</label>
          <input
            id="account-email"
            class="input"
            type="email"
            :value="email"
            readonly
            aria-describedby="account-email-hint"
          />
          <small id="account-email-hint" class="field-hint">{{ t('account.emailHint') }}</small>
        </div>

        <div class="field">
          <label class="field-label" for="account-display-name">
            {{ t('account.displayName') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="account-display-name"
            v-model="profileForm.displayName"
            class="input"
            type="text"
            maxlength="120"
            autocomplete="name"
            required
            :aria-invalid="Boolean(profileError)"
            :aria-describedby="profileError ? 'account-display-name-error' : undefined"
            data-testid="account-display-name"
          />
          <small v-if="profileError" id="account-display-name-error" class="field-error">
            <IconGlyph name="alert" :size="14" />
            {{ profileError }}
          </small>
        </div>

        <div class="field">
          <label class="field-label" for="account-language">{{ t('account.preferredLanguage') }}</label>
          <select
            id="account-language"
            v-model="profileForm.preferredLanguage"
            class="select"
            aria-describedby="account-language-hint"
            data-testid="account-language"
          >
            <option value="en">{{ t('account.languageEn') }}</option>
            <option value="ms">{{ t('account.languageMs') }}</option>
            <option value="zh-CN">{{ t('account.languageZh') }}</option>
          </select>
          <small id="account-language-hint" class="field-hint">{{ t('account.languageHint') }}</small>
        </div>

        <div class="form-actions account-actions">
          <p
            v-if="feedbackForm === 'profile' && (noticeSuccess || noticeError)"
            class="inline-feedback"
            :class="{ 'is-error': noticeError }"
            role="status"
          >
            <IconGlyph :name="noticeError ? 'alert' : 'status'" :size="16" />
            {{ noticeError || noticeSuccess }}
          </p>
          <button
            class="btn btn-primary"
            type="submit"
            :disabled="savingProfile"
            :aria-busy="savingProfile"
            data-testid="account-save-profile"
          >
            {{ savingProfile ? t('account.savingProfile') : t('account.saveProfile') }}
          </button>
        </div>
      </form>

      <form class="panel account-card" novalidate data-testid="account-password-form" @submit.prevent="changePassword">
        <div class="account-card-head">
          <span class="account-card-icon" aria-hidden="true"><IconGlyph name="key" :size="20" /></span>
          <div>
            <h2 class="card-title">{{ t('account.passwordHeading') }}</h2>
            <p class="card-subtitle">{{ t('account.passwordHint') }}</p>
          </div>
        </div>

        <div v-for="field in passwordFields" :key="field.name" class="field">
          <label class="field-label" :for="`account-${field.name}`">
            {{ field.label }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            :id="`account-${field.name}`"
            v-model="passwordForm[field.name]"
            class="input"
            :type="showPasswords ? 'text' : 'password'"
            :autocomplete="field.autocomplete"
            maxlength="200"
            required
            :aria-invalid="Boolean(passwordErrors[field.name])"
            :aria-describedby="passwordErrors[field.name] ? `account-${field.name}-error` : undefined"
            :data-testid="`account-${field.name}`"
          />
          <small v-if="passwordErrors[field.name]" :id="`account-${field.name}-error`" class="field-error">
            <IconGlyph name="alert" :size="14" />
            {{ t(`account.${passwordErrors[field.name]}`) }}
          </small>
        </div>

        <label class="choice account-show-passwords">
          <input v-model="showPasswords" type="checkbox" />
          <span>{{ t('account.showPasswords') }}</span>
        </label>

        <div class="form-actions account-actions">
          <p
            v-if="feedbackForm === 'password' && (noticeSuccess || noticeError)"
            class="inline-feedback"
            :class="{ 'is-error': noticeError }"
            role="status"
          >
            <IconGlyph :name="noticeError ? 'alert' : 'status'" :size="16" />
            {{ noticeError || noticeSuccess }}
          </p>
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
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { accountApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { accountMessages } from '@/plugins/accountMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { AppLanguage } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { normalizeAppLanguage } from '@/utils/locale'
import {
  hasPasswordChangeErrors,
  validatePasswordChange,
  type PasswordChangeErrors,
  type PasswordChangeForm,
} from '@/utils/password'

const DISPLAY_NAME_MAX_LENGTH = 120

const { t } = useI18n({
  messages: {
    en: { account: accountMessages.en },
    ms: { account: accountMessages.ms },
    'zh-CN': { account: accountMessages['zh-CN'] },
  },
})

const auth = useAuthStore()
const ui = useUiStore()

const isPreview = computed(() => !auth.isAuthenticated)
const email = computed(() => auth.currentUser?.email ?? '')

function currentDisplayName(): string {
  const user = auth.currentUser
  return user?.studentProfile?.displayName?.trim() || user?.fullName || ''
}

function currentLanguage(): AppLanguage {
  return normalizeAppLanguage(auth.currentUser?.preferredLanguage) ?? ui.locale
}

const profileForm = reactive<{ displayName: string; preferredLanguage: AppLanguage }>({
  displayName: currentDisplayName(),
  preferredLanguage: currentLanguage(),
})
const passwordForm = reactive<PasswordChangeForm>({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordErrors = ref<PasswordChangeErrors>({})
const profileError = ref<string | null>(null)
const savingProfile = ref(false)
const changingPassword = ref(false)
const showPasswords = ref(false)
const noticeError = ref<string | null>(null)
const noticeSuccess = ref<string | null>(null)
/** Which form the current success or error message belongs to; it is shown next to that form's button. */
const feedbackForm = ref<'profile' | 'password' | null>(null)

const passwordFields = computed(
  () =>
    [
      { name: 'currentPassword', label: t('account.currentPassword'), autocomplete: 'current-password' },
      { name: 'newPassword', label: t('account.newPassword'), autocomplete: 'new-password' },
      { name: 'confirmPassword', label: t('account.confirmPassword'), autocomplete: 'new-password' },
    ] as const,
)

watch(
  () => auth.currentUser,
  () => {
    profileForm.displayName = currentDisplayName()
    profileForm.preferredLanguage = currentLanguage()
  },
)

function clearNotices(): void {
  noticeError.value = null
  noticeSuccess.value = null
}

async function saveProfile(): Promise<void> {
  clearNotices()
  feedbackForm.value = 'profile'
  const displayName = profileForm.displayName.trim()
  profileError.value = !displayName
    ? t('account.displayNameRequired')
    : displayName.length > DISPLAY_NAME_MAX_LENGTH
      ? t('account.displayNameTooLong')
      : null
  if (profileError.value) return
  if (isPreview.value) {
    noticeError.value = t('account.previewNotice')
    return
  }

  savingProfile.value = true
  try {
    const user = await accountApi.updateProfile({ displayName, preferredLanguage: profileForm.preferredLanguage })
    auth.applyProfileUpdate(user)
    if (!normalizeAppLanguage(user.preferredLanguage)) ui.setLocale(profileForm.preferredLanguage)
    noticeSuccess.value = t('account.profileSaved')
  } catch (reason) {
    noticeError.value = resolveApiError(reason, t('account.profileError'))
  } finally {
    savingProfile.value = false
  }
}

async function changePassword(): Promise<void> {
  clearNotices()
  feedbackForm.value = 'password'
  passwordErrors.value = validatePasswordChange(passwordForm)
  if (hasPasswordChangeErrors(passwordErrors.value)) return
  if (isPreview.value) {
    noticeError.value = t('account.previewNotice')
    return
  }

  changingPassword.value = true
  try {
    const response = await accountApi.changePassword({ ...passwordForm })
    // Other sessions are revoked server-side; keep this tab working with the fresh access token.
    auth.replaceAccessToken(response.token)
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    noticeSuccess.value = t('account.passwordChanged')
  } catch (reason) {
    noticeError.value = resolveApiError(reason, t('account.passwordError'))
  } finally {
    changingPassword.value = false
  }
}
</script>

<style scoped>
.account-page {
  display: grid;
  gap: var(--page-gap);
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.account-card {
  display: grid;
  gap: var(--space-4);
}

.account-card-head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
}

.account-card-head > div {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.account-card-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.account-card .input[readonly] {
  background: var(--surface-sunken);
  color: var(--text-secondary);
}

.account-show-passwords {
  font-size: var(--font-size-md);
}

.account-actions {
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.account-actions .inline-feedback {
  flex: 1 1 200px;
  align-items: flex-start;
  margin: 0;
  line-height: var(--line-height-normal);
}

.account-actions .inline-feedback .icon-glyph {
  flex: 0 0 auto;
  margin-top: var(--space-0-5);
}

/* On phones the actions stack (button first); the message keeps its own height. */
@media (max-width: 640px) {
  .account-actions .inline-feedback {
    flex: 0 0 auto;
  }
}
</style>
