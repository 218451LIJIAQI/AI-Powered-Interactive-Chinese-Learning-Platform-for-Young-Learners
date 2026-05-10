<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useAuthStore } from '../../stores/authStore'

type RegisterRole = 'STUDENT' | 'TEACHER'

const router = useRouter()
const authStore = useAuthStore()
const { locale, t } = useI18n()

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

function validateCommonFields(fullName: string, email: string, password: string, confirmPassword: string) {
  if (!fullName.trim() || !email.trim() || !password.trim() || !confirmPassword.trim()) {
    return t('auth.validation.registerRequired')
  }

  if (password.length < 8) {
    return t('auth.validation.passwordMin')
  }

  if (password !== confirmPassword) {
    return t('auth.validation.passwordMismatch')
  }

  return ''
}

async function handleRegister() {
  loading.value = true
  errorMessage.value = ''

  try {
    if (activeRole.value === 'STUDENT') {
      const validationMessage = validateCommonFields(
        studentForm.fullName,
        studentForm.email,
        studentForm.password,
        studentForm.confirmPassword,
      )

      if (validationMessage) {
        errorMessage.value = validationMessage
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
      const validationMessage = validateCommonFields(
        teacherForm.fullName,
        teacherForm.email,
        teacherForm.password,
        teacherForm.confirmPassword,
      )

      if (validationMessage) {
        errorMessage.value = validationMessage
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
              <el-radio-button label="STUDENT">{{ t('auth.studentTab') }}</el-radio-button>
              <el-radio-button label="TEACHER">{{ t('auth.teacherTab') }}</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

        <el-form v-if="activeRole === 'STUDENT'" class="auth-form auth-form--grid" label-position="top">
          <el-form-item :label="t('auth.fields.fullName')">
            <el-input v-model="studentForm.fullName" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.email')">
            <el-input v-model="studentForm.email" type="email" autocomplete="email" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.password')">
            <el-input v-model="studentForm.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.confirmPassword')">
            <el-input
              v-model="studentForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item :label="t('auth.fields.preferredLanguage')">
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

        <el-form v-else class="auth-form auth-form--grid" label-position="top">
          <el-form-item :label="t('auth.fields.fullName')">
            <el-input v-model="teacherForm.fullName" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.email')">
            <el-input v-model="teacherForm.email" type="email" autocomplete="email" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.password')">
            <el-input v-model="teacherForm.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.confirmPassword')">
            <el-input
              v-model="teacherForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>

          <el-form-item class="auth-form__full" :label="t('auth.fields.bio')">
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
