<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherService } from '../../services/teacherService'
import type { TeacherVerificationStatusResponse } from '../../types/teacherManagement'
import { downloadProtectedFile } from '../../utils/fileDownload'

const { t } = useI18n()

const loading = ref(true)
const uploading = ref(false)
const resubmitting = ref(false)
const selectedFile = ref<File | null>(null)
const status = ref<TeacherVerificationStatusResponse | null>(null)

const form = reactive({
  documentType: 'CERTIFICATE',
  description: '',
})

const canResubmit = computed(() => status.value?.resubmissionAllowed === true)

async function loadStatus() {
  loading.value = true
  try {
    status.value = await teacherService.getVerificationStatus()
  } finally {
    loading.value = false
  }
}

function handleFileChange(file: { raw?: File }) {
  selectedFile.value = file.raw ?? null
}

async function uploadDocument() {
  if (!selectedFile.value) {
    ElMessage.error(t('teacherVerification.validation.fileRequired'))
    return
  }

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('documentType', form.documentType)
  formData.append('description', form.description)

  uploading.value = true
  try {
    status.value = await teacherService.uploadVerificationDocument(formData)
    selectedFile.value = null
    form.description = ''
    ElMessage.success(t('teacherVerification.messages.uploadSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

async function resubmitVerification() {
  resubmitting.value = true
  try {
    status.value = await teacherService.resubmitVerification()
    ElMessage.success(t('teacherVerification.messages.resubmitted'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.resubmitFailed'))
  } finally {
    resubmitting.value = false
  }
}

async function openDocument(documentId: number, originalFileName: string) {
  try {
    await downloadProtectedFile(teacherService.getVerificationDocumentUrl(documentId), originalFileName)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('teacherVerification.messages.documentOpenFailed'))
  }
}

onMounted(() => {
  loadStatus()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-skeleton :loading="loading" animated :rows="8">
      <template #default>
        <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
          <div class="dashboard-hero__split">
            <div>
              <p class="section-label">{{ t('teacherVerification.uploadEyebrow') }}</p>
              <h1>{{ t('teacherVerification.uploadTitle') }}</h1>
              <p>{{ t('teacherVerification.uploadDescription') }}</p>
            </div>
            <div class="dashboard-hero__aside">
              <el-tag :type="status?.teacherStatus === 'APPROVED' ? 'success' : status?.teacherStatus === 'REJECTED' ? 'danger' : 'warning'" size="large" round>
                {{ status?.teacherStatus ?? 'PENDING' }}
              </el-tag>
            </div>
          </div>
        </el-card>

        <section class="dashboard-grid dashboard-grid--two-thirds">
          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.uploadPanelLabel') }}</p>
                <h2>{{ t('teacherVerification.uploadPanelTitle') }}</h2>
              </div>
            </div>

            <el-alert :title="t('teacherVerification.allowedFiles')" type="info" show-icon :closable="false" />

            <el-form label-position="top" class="teacher-profile-form">
              <el-form-item :label="t('teacherVerification.documentType')">
                <el-select v-model="form.documentType">
                  <el-option label="Certificate" value="CERTIFICATE" />
                  <el-option label="Qualification" value="QUALIFICATION" />
                  <el-option label="Identity" value="IDENTITY" />
                  <el-option label="Other" value="OTHER" />
                </el-select>
              </el-form-item>

              <el-form-item :label="t('teacherVerification.documentDescription')">
                <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
              </el-form-item>

              <el-form-item :label="t('teacherVerification.chooseFile')">
                <el-upload
                  :auto-upload="false"
                  :limit="1"
                  :show-file-list="true"
                  accept=".pdf,.jpg,.jpeg,.png"
                  :on-change="handleFileChange"
                >
                  <template #trigger>
                    <el-button type="primary" plain>{{ t('teacherVerification.selectFile') }}</el-button>
                  </template>
                </el-upload>
              </el-form-item>

              <div class="teacher-profile-actions">
                <el-button type="primary" :loading="uploading" @click="uploadDocument">
                  {{ t('teacherVerification.uploadButton') }}
                </el-button>
                <el-button v-if="canResubmit" type="warning" :loading="resubmitting" @click="resubmitVerification">
                  {{ t('teacherVerification.resubmitButton') }}
                </el-button>
              </div>
            </el-form>
          </el-card>

          <el-card class="dashboard-panel" shadow="hover">
            <div class="panel-header">
              <div>
                <p class="section-label">{{ t('teacherVerification.documentListLabel') }}</p>
                <h2>{{ t('teacherVerification.documentListTitle') }}</h2>
              </div>
            </div>

            <el-empty
              v-if="!status?.latestSubmittedDocuments.length"
              :description="t('teacherVerification.noDocuments')"
            />

            <div v-else class="stacked-summary-list">
              <article
                v-for="document in status?.latestSubmittedDocuments"
                :key="document.id"
                class="summary-row"
              >
                <div>
                  <strong>{{ document.documentType }}</strong>
                  <p>{{ document.originalFileName }} · {{ Math.ceil(document.fileSize / 1024) }} KB</p>
                  <p>{{ document.description || t('teacherVerification.noDescription') }}</p>
                </div>
                <div class="teacher-profile-actions">
                  <el-tag v-if="document.currentVersion" type="success" round>{{ t('teacherVerification.currentVersion') }}</el-tag>
                  <el-button type="primary" plain @click="openDocument(document.id, document.originalFileName)">
                    {{ t('teacherVerification.viewDocument') }}
                  </el-button>
                </div>
              </article>
            </div>
          </el-card>
        </section>
      </template>
    </el-skeleton>
  </div>
</template>
