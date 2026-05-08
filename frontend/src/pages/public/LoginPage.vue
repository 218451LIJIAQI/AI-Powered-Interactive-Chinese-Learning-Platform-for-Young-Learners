<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useAuthStore } from '../../stores/authStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t } = useI18n()

const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  email: '',
  password: '',
})

const redirectTarget = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : null
})

async function handleLogin() {
  if (!form.email.trim() || !form.password.trim()) {
    errorMessage.value = t('auth.validation.loginRequired')
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
  <div class="page-stack">
    <PageHero
      :eyebrow="t('auth.loginBadge')"
      :title="t('auth.loginTitle')"
      :description="t('auth.loginDescription')"
    />

    <div class="auth-panel">
      <el-card class="auth-card" shadow="hover">
        <template #header>
          <div class="auth-card__header">
            <div>
              <p class="section-label">{{ t('nav.login') }}</p>
              <h2>{{ t('auth.loginHeading') }}</h2>
            </div>
            <el-tag type="warning" effect="light">JWT</el-tag>
          </div>
        </template>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

        <el-form class="auth-form" label-position="top">
          <el-form-item :label="t('auth.fields.email')">
            <el-input v-model="form.email" type="email" autocomplete="email" />
          </el-form-item>

          <el-form-item :label="t('auth.fields.password')">
            <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
          </el-form-item>

          <el-button class="auth-submit" type="primary" :loading="loading" @click="handleLogin">
            {{ t('nav.login') }}
          </el-button>
        </el-form>

        <p class="auth-card__footer">
          {{ t('auth.noAccount') }}
          <router-link :to="{ name: 'register' }" class="auth-card__link">{{ t('nav.register') }}</router-link>
        </p>
      </el-card>
    </div>
  </div>
</template>
