<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useAuthStore } from '../../stores/authStore'

type RegisterRole = 'STUDENT' | 'TEACHER'

const router = useRouter()
const authStore = useAuthStore()
const { locale, t } = useI18n()

const studentFormRef = ref<FormInstance | null>(null)
const teacherFormRef = ref<FormInstance | null>(null)
const activeRole = ref<RegisterRole>('STUDENT')
const loading = ref(false)
const errorMessage = ref('')

const studentForm = reactive({
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  preferredLanguage: 'en',
})

const teacherForm = reactive({
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  bio: '',
})

const languageOptions = computed(() => [
  { label: t('ai.languages.en'), value: 'en' },
  { label: t('ai.languages.ms'), value: 'ms' },
  { label: t('ai.languages.zh'), value: 'zh' },
])

/**
 * Builds a fresh confirm-password validator each render. The validator closes
 * over the *current* value of `form.password`, so we recreate it whenever the
 * rules computed re-runs. This avoids a stale-password bug where the user
 * types a new password but the rule still compares against the old one.
 */
function makeConfirmPasswordRule(form: { password: string }): FormItemRule {
  return {
    validator: (_rule, value: string, callback) => {
      if (!value) {
        callback(new Error(t('auth.validation.confirmPasswordRequired')))
        return
      }
      if (value !== form.password) {
        callback(new Error(t('auth.validation.passwordMismatch')))
        return
      }
      callback()
    },
    trigger: ['blur', 'change'],
  }
}

const studentRules = computed<FormRules<typeof studentForm>>(() => ({
  fullName: [{ required: true, message: t('auth.validation.fullNameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: t('auth.validation.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('auth.validation.emailInvalid'), trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: t('auth.validation.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('auth.validation.passwordMin'), trigger: ['blur', 'change'] },
  ],
  confirmPassword: [makeConfirmPasswordRule(studentForm)],
}))

const teacherRules = computed<FormRules<typeof teacherForm>>(() => ({
  fullName: [{ required: true, message: t('auth.validation.fullNameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: t('auth.validation.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('auth.validation.emailInvalid'), trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: t('auth.validation.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('auth.validation.passwordMin'), trigger: ['blur', 'change'] },
  ],
  confirmPassword: [makeConfirmPasswordRule(teacherForm)],
}))

async function handleRegister() {
  loading.value = true
  errorMessage.value = ''

  try {
    if (activeRole.value === 'STUDENT') {
      if (!studentFormRef.value) return
      try {
        await studentFormRef.value.validate()
      } catch {
        return
      }

      await authStore.registerStudent({
        fullName: studentForm.fullName.trim(),
        email: studentForm.email.trim(),
        password: studentForm.password,
        confirmPassword: studentForm.confirmPassword,
        preferredLanguage: studentForm.preferredLanguage || locale.value,
      })
      ElMessage.success(t('auth.messages.studentRegisterSuccess'))
    } else {
      if (!teacherFormRef.value) return
      try {
        await teacherFormRef.value.validate()
      } catch {
        return
      }

      await authStore.registerTeacher({
        fullName: teacherForm.fullName.trim(),
        email: teacherForm.email.trim(),
        password: teacherForm.password,
        confirmPassword: teacherForm.confirmPassword,
        bio: teacherForm.bio.trim(),
      })
      ElMessage.success(t('auth.messages.teacherRegisterSuccess'))
    }

    await router.push('/login')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('auth.messages.registerFailed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-stack">
    <PageHero
      :eyebrow="t('auth.registerBadge')"
      :title="t('auth.registerTitle')"
      :description="t('auth.registerDescription')"
    />

    <div class="auth-panel">
      <el-card class="auth-card auth-card--wide" shadow="hover">
        <template #header>
          <div class="auth-card__header">
            <div>
              <p class="section-label">{{ t('nav.register') }}</p>
              <h2>{{ t('auth.registerHeading') }}</h2>
            </div>
            <el-radio-group v-model="activeRole" size="large">
              <el-radio-button value="STUDENT">{{ t('auth.studentTab') }}</el-radio-button>
              <el-radio-button value="TEACHER">{{ t('auth.teacherTab') }}</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

        <el-form
          v-if="activeRole === 'STUDENT'"
          ref="studentFormRef"
          class="auth-form auth-form--grid"
          label-position="top"
          :model="studentForm"
          :rules="studentRules"
          @submit.prevent="handleRegister"
        >
          <el-form-item :label="t('auth.fields.fullName')" prop="fullName">
            <el-input v-model="studentForm.fullName" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.email')" prop="email">
            <el-input v-model="studentForm.email" type="email" autocomplete="email" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.password')" prop="password">
            <el-input v-model="studentForm.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.confirmPassword')" prop="confirmPassword">
            <el-input
              v-model="studentForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item :label="t('auth.fields.preferredLanguage')" prop="preferredLanguage">
            <el-select v-model="studentForm.preferredLanguage">
              <el-option
                v-for="option in languageOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <el-form
          v-else
          ref="teacherFormRef"
          class="auth-form auth-form--grid"
          label-position="top"
          :model="teacherForm"
          :rules="teacherRules"
          @submit.prevent="handleRegister"
        >
          <el-form-item :label="t('auth.fields.fullName')" prop="fullName">
            <el-input v-model="teacherForm.fullName" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.email')" prop="email">
            <el-input v-model="teacherForm.email" type="email" autocomplete="email" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.password')" prop="password">
            <el-input v-model="teacherForm.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.confirmPassword')" prop="confirmPassword">
            <el-input
              v-model="teacherForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item class="auth-form__full" :label="t('auth.fields.bio')" prop="bio">
            <el-input v-model="teacherForm.bio" type="textarea" :rows="4" maxlength="2000" show-word-limit />
          </el-form-item>
        </el-form>

        <div v-if="activeRole === 'TEACHER'" class="auth-card__note">
          {{ t('auth.teacherPendingNotice') }}
        </div>

        <el-button class="auth-submit" type="primary" :loading="loading" @click="handleRegister">
          {{ t('nav.register') }}
        </el-button>

        <p class="auth-card__footer">
          {{ t('auth.haveAccount') }}
          <router-link :to="{ name: 'login' }" class="auth-card__link">{{ t('nav.login') }}</router-link>
        </p>
      </el-card>
    </div>
  </div>
</template>
