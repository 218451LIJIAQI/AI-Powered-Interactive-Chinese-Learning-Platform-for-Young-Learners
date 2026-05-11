<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherService } from '../../services/teacherService'
import { useAuthStore } from '../../stores/authStore'
import type { TeacherProfileResponse } from '../../types/teacherManagement'

const authStore = useAuthStore()
const { t } = useI18n()

const loading = ref(true)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)
const profile = ref<TeacherProfileResponse | null>(null)

const form = reactive({
  fullName: '',
  bio: '',
  shortIntroduction: '',
  teachingCategoriesText: '',
})

const rules = computed<FormRules>(() => ({
  fullName: [
    { required: true, message: t('teacherVerification.validation.fullNameRequired'), trigger: 'blur' },
  ],
}))

const teacherStatus = computed(() => profile.value?.teacherStatus ?? authStore.teacherStatus ?? 'PENDING')

async function loadProfile() {
  loading.value = true
  try {
    const response = await teacherService.getMyProfile()
    profile.value = response
    form.fullName = response.fullName
    form.bio = response.bio ?? ''
    form.shortIntroduction = response.shortIntroduction ?? ''
    form.teachingCategoriesText = response.teachingCategories.join(', ')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    profile.value = await teacherService.updateMyProfile({
      fullName: form.fullName.trim(),
      bio: form.bio.trim() || undefined,
      shortIntroduction: form.shortIntroduction.trim() || undefined,
      teachingCategories: form.teachingCategoriesText
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean),
    })
    ElMessage.success(t('teacherVerification.messages.profileSaved'))
    await authStore.fetchCurrentUser()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.profileSaveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('teacherVerification.profileEyebrow') }}</p>
              <h1>{{ t('teacherVerification.profileTitle') }}</h1>
              <p>{{ t('teacherVerification.profileDescription') }}</p>
            </div>

            <div class="dashboard-hero__aside">
              <el-tag :type="teacherStatus === 'APPROVED' ? 'success' : teacherStatus === 'REJECTED' ? 'danger' : 'warning'" size="large" round>
                {{ teacherStatus }}
              </el-tag>
            </div>
          </div>
        </el-card>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.profileFormLabel') }}</p>
                <h2>{{ t('teacherVerification.profileFormTitle') }}</h2>
              </div>
            </div>

            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              label-position="top"
              class="teacher-profile-form"
              @submit.prevent="saveProfile"
            >
              <el-form-item :label="t('auth.fields.fullName')" prop="fullName">
                <el-input v-model="form.fullName" maxlength="120" />
              </el-form-item>

              <el-form-item :label="t('auth.fields.bio')">
                <el-input v-model="form.bio" type="textarea" :rows="5" maxlength="2000" show-word-limit />
              </el-form-item>

              <el-form-item :label="t('teacherVerification.shortIntroduction')">
                <el-input v-model="form.shortIntroduction" type="textarea" :rows="4" maxlength="1000" show-word-limit />
              </el-form-item>

              <el-form-item :label="t('teacherVerification.teachingCategories')">
                <el-input
                  v-model="form.teachingCategoriesText"
                  :placeholder="t('teacherVerification.teachingCategoriesPlaceholder')"
                />
              </el-form-item>

              <el-button type="primary" :loading="saving" native-type="submit" @click="saveProfile">
                {{ t('teacherVerification.saveProfile') }}
              </el-button>
            </el-form>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.statusCardLabel') }}</p>
                <h2>{{ t('teacherVerification.statusCardTitle') }}</h2>
              </div>
            </div>

            <div class="profile-summary">
              <p><strong>{{ t('auth.fields.email') }}:</strong> {{ profile?.email }}</p>
              <p><strong>{{ t('dashboard.teacherStatus') }}:</strong> {{ teacherStatus }}</p>
              <p v-if="profile?.teachingCategories.length">
                <strong>{{ t('teacherVerification.teachingCategories') }}:</strong>
                {{ profile?.teachingCategories.join(', ') }}
              </p>
              <p v-if="teacherStatus === 'REJECTED' && profile?.rejectionReason">
                <strong>{{ t('dashboard.rejectionReason') }}:</strong> {{ profile.rejectionReason }}
              </p>
            </div>

            <el-alert
              v-if="teacherStatus === 'PENDING'"
              :title="t('teacherVerification.pendingMessage')"
              type="warning"
              show-icon
              :closable="false"
            />
            <el-alert
              v-else-if="teacherStatus === 'APPROVED'"
              :title="t('teacherVerification.approvedMessage')"
              type="success"
              show-icon
              :closable="false"
            />
            <el-alert
              v-else
              :title="t('teacherVerification.rejectedMessage')"
              type="error"
              show-icon
              :closable="false"
            />
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
