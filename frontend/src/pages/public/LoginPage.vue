<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { useAuthStore } from '../../stores/authStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t } = useI18n()

const formRef = ref<FormInstance | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  email: '',
  password: '',
})

// Element Plus form rules. Using a `computed` so the messages re-evaluate when
// the user switches languages.
const rules = computed<FormRules<typeof form>>(() => ({
  email: [
    { required: true, message: t('auth.validation.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('auth.validation.emailInvalid'), trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: t('auth.validation.passwordRequired'), trigger: 'blur' },
  ],
}))

const redirectTarget = computed(() => {
  // Validate that the redirect target is a same-origin path:
  //   - must be a string
  //   - must start with a single `/` (relative path)
  //   - must NOT start with `//` or `/\` (protocol-relative URLs that the
  //     browser would interpret as off-site, e.g. `//evil.com/login` → open
  //     redirect attack vector)
  const redirect = route.query.redirect
  if (typeof redirect !== 'string') return null
  if (!redirect.startsWith('/')) return null
  if (redirect.startsWith('//') || redirect.startsWith('/\\')) return null
  return redirect
})

async function handleLogin() {
  if (!formRef.value) return

  // `validate` rejects with an inline error map when invalid; we treat that as
  // "user must fix highlighted fields" and avoid hitting the API.
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const user = await authStore.login({
      email: form.email.trim(),
      password: form.password,
    })

    ElMessage.success(t('auth.messages.loginSuccess'))

    if (redirectTarget.value) {
      await router.push(redirectTarget.value)
      return
    }

    if (user?.roles.includes('STUDENT')) {
      await router.push('/student/dashboard')
      return
    }

    if (user?.roles.includes('TEACHER')) {
      await router.push('/teacher/dashboard')
      return
    }

    await router.push('/admin/dashboard')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : t('auth.messages.loginFailed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-page__panel">
      <header class="login-page__header">
        <p class="login-page__eyebrow">{{ t('auth.loginBadge') }}</p>
        <h1 class="login-page__title">{{ t('auth.loginHeading') }}</h1>
        <p class="login-page__subtitle">{{ t('auth.loginDescription') }}</p>
      </header>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="login-page__alert"
      />

      <el-form
        ref="formRef"
        class="login-page__form"
        label-position="top"
        :model="form"
        :rules="rules"
        @submit.prevent="handleLogin"
      >
        <el-form-item :label="t('auth.fields.email')" prop="email">
          <el-input v-model="form.email" type="email" autocomplete="email" size="large" />
        </el-form-item>

        <el-form-item :label="t('auth.fields.password')" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            size="large"
            @keydown.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          class="login-page__submit"
          type="primary"
          size="large"
          :loading="loading"
          native-type="submit"
          @click="handleLogin"
        >
          {{ t('nav.login') }}
        </el-button>
      </el-form>

      <p class="login-page__footer">
        {{ t('auth.noAccount') }}
        <router-link :to="{ name: 'register' }" class="login-page__link">{{ t('nav.register') }}</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: calc(100vh - 240px);
  padding: 48px 16px;
}

.login-page__panel {
  width: min(100%, 420px);
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 36px 32px;
  border-radius: var(--radius-lg);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.login-page__header {
  text-align: left;
}

.login-page__eyebrow {
  margin: 0 0 12px;
  color: var(--text-tertiary);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-page__title {
  margin: 0 0 8px;
  font-size: 1.6rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.login-page__subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.95rem;
  line-height: 1.5;
}

.login-page__alert {
  margin: 0;
}

.login-page__form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.login-page__submit {
  width: 100%;
  margin-top: 12px;
  font-weight: 600;
  letter-spacing: -0.005em;
}

.login-page__footer {
  margin: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 0.92rem;
  text-align: center;
}

.login-page__link {
  color: var(--primary);
  font-weight: 600;
  margin-left: 4px;
}

.login-page__link:hover {
  color: var(--primary-deep);
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
