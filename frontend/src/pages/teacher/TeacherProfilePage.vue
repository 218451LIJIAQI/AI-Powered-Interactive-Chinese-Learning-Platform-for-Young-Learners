<template>
  <div class="page-stack teacher-profile-page">
    <PageHeaderBlock
      title="Teacher Profile"
      description="Manage your public teaching profile and track verification status with real backend fields."
      icon="profile"
    />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <section class="teacher-profile-hero">
      <div class="teacher-profile-hero-copy">
        <span>Teacher identity</span>
        <h2>{{ profileNameLabel }}</h2>
        <p>{{ profileIntroLabel }}</p>
      </div>
      <div class="teacher-profile-hero-status">
        <StatusPill
          :label="verification.teacherStatus"
          :tone="verificationStatusTone"
        />
        <div class="teacher-profile-hero-stats">
          <article v-for="item in profileHighlights" :key="item.label">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </article>
        </div>
      </div>
    </section>

    <section class="three-col teacher-profile-layout">
      <div class="stack teacher-profile-main">
        <ActionFormCard
          title="Edit teacher profile"
          subtitle="Public-facing teacher information"
          icon="profile"
          submit-label="Save profile"
          submitting-label="Saving..."
          :submitting="savingProfile"
          :disabled="profileFormDisabled"
          @submit="saveProfile"
        >
          <div class="field">
            <label for="teacher-profile-full-name">Full name</label>
            <input
              id="teacher-profile-full-name"
              v-model="profileForm.fullName"
              class="input"
              autocomplete="name"
              maxlength="120"
              :disabled="profileFormDisabled"
            />
          </div>
          <div class="field">
            <label for="teacher-profile-bio">Bio</label>
            <textarea
              id="teacher-profile-bio"
              v-model="profileForm.bio"
              class="textarea"
              maxlength="1000"
              :disabled="profileFormDisabled"
            />
          </div>
          <div class="field">
            <label for="teacher-profile-short-introduction">Short introduction</label>
            <textarea
              id="teacher-profile-short-introduction"
              v-model="profileForm.shortIntroduction"
              class="textarea"
              maxlength="500"
              :disabled="profileFormDisabled"
            />
          </div>
          <div class="field">
            <label for="teacher-profile-categories">Teaching categories</label>
            <input
              id="teacher-profile-categories"
              v-model="categoriesText"
              class="input"
              maxlength="255"
              placeholder="Conversation, Pinyin, Listening"
              :disabled="profileFormDisabled"
            />
          </div>
        </ActionFormCard>

        <InfoCard title="Verification documents" subtitle="Current uploaded verification files." icon="verification">
          <div class="list">
            <ListInfoItem
              v-for="document in profile.verificationDocuments"
              :key="document.id"
              :title="document.originalFileName"
              :description="document.description || document.documentType"
              icon="file"
            >
              <template #aside>
                <div class="list-value-stack">
                  <span class="muted">{{ formatDateTime(document.submittedAt) }}</span>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    :disabled="profileUsingFallback || downloadingDocumentId === document.id"
                    @click="downloadVerificationDocument(document.id, document.originalFileName)"
                  >
                    {{ documentDownloadLabel(document.id) }}
                  </button>
                </div>
              </template>
            </ListInfoItem>
          </div>
        </InfoCard>
      </div>

      <div class="stack">
        <InfoCard title="Verification status" subtitle="Teacher identity protection." icon="verification">
          <StatusPill
            :label="verification.teacherStatus"
            :tone="verificationStatusTone"
          />
          <p class="muted u-m-0">
            {{ verification.latestReviewResult?.note || 'Your current verification state is shown here.' }}
          </p>
          <button
            v-if="verification.resubmissionAllowed"
            class="btn btn-secondary"
            type="button"
            @click="resubmitVerification"
          >
            Resubmit verification
          </button>
        </InfoCard>

        <InfoCard title="Recent notifications" subtitle="Open a notification to mark it as read." icon="bell">
          <template #header-end>
            <button
              class="teacher-outline-button"
              type="button"
              :disabled="markingNotificationsRead || !hasUnreadNotifications"
              @click="markAllNotificationsRead"
            >
              {{ markAllNotificationsLabel }}
            </button>
          </template>

          <div v-if="notifications.length" class="list">
            <NotificationLinkButton
              v-for="item in notifications"
              :key="item.id"
              :notification-id="item.id"
              :target-path="item.targetPath"
              fallback-to="/teacher/profile"
              @read="markTeacherNotificationRead"
            >
              <ListInfoItem :title="item.title" :description="item.message" icon="bell">
                <template #aside>
                  <div class="list-value-stack">
                    <StatusPill
                      :label="notificationStatusLabel(item.status)"
                      :tone="notificationStatusTone(item.status)"
                    />
                    <span class="muted">{{ formatDateTime(item.createdAt) }}</span>
                  </div>
                </template>
              </ListInfoItem>
            </NotificationLinkButton>
          </div>

          <p v-else class="muted u-m-0">Recent teacher notifications will appear here.</p>
        </InfoCard>

        <ActionFormCard
          title="Upload verification document"
          subtitle="Real backend multipart endpoint"
          icon="file"
          submit-label="Upload document"
          submitting-label="Uploading..."
          :submitting="uploading"
          :disabled="verificationUploadDisabled"
          @submit="uploadDocument"
        >
          <div class="field">
            <label for="teacher-verification-document-type">Document type</label>
            <select
              id="teacher-verification-document-type"
              v-model="uploadForm.documentType"
              class="input"
              :disabled="verificationUploadReadOnly"
            >
              <option v-for="option in verificationDocumentTypes" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="teacher-verification-description">Description</label>
            <input
              id="teacher-verification-description"
              v-model="uploadForm.description"
              class="input"
              maxlength="500"
              placeholder="Government ID, certificate, or qualification proof"
              :disabled="verificationUploadReadOnly"
            />
          </div>
          <div class="field">
            <label for="teacher-verification-file">File</label>
            <input
              id="teacher-verification-file"
              ref="fileInput"
              class="input"
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
              :disabled="verificationUploadReadOnly"
              @change="selectVerificationFile"
            />
            <small class="helper-text">Upload a PDF, JPG, JPEG, or PNG file under 5 MB.</small>
          </div>
        </ActionFormCard>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import { notificationsApi, teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import InfoCard from '@/components/dashboard/InfoCard.vue'
import ListInfoItem from '@/components/dashboard/ListInfoItem.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { teacherProfile, teacherVerification } from '@/demo/data'
import type { TeacherVerificationDocumentType } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDateTime } from '@/utils/format'

type StatusTone = 'success' | 'warning' | 'info'

const profileResource = useResource(() => teacherApi.profile(), teacherProfile)
const verificationResource = useResource(() => teacherApi.verificationStatus(), teacherVerification)
const notificationsResource = useResource(() => notificationsApi.list({ size: 6 }), [])

const emptyProfile = {
  userId: 0,
  fullName: '',
  email: '',
  teacherProfileId: 0,
  bio: null,
  shortIntroduction: null,
  teachingCategories: [],
  teacherStatus: 'PENDING',
  rejectionReason: null,
  verificationDocuments: [],
  createdAt: '',
  updatedAt: '',
}
const emptyVerification = {
  teacherStatus: 'PENDING',
  latestSubmittedDocuments: [],
  latestReviewResult: null,
  rejectionReason: null,
  resubmissionAllowed: false,
}

const profile = computed(
  () => profileResource.data.value ?? (profileResource.usingFallback.value ? teacherProfile : emptyProfile),
)
const profileUsingFallback = computed(() => profileResource.usingFallback.value)
const profileFormDisabled = computed(() => profileUsingFallback.value || savingProfile.value)
const verification = computed(
  () =>
    verificationResource.data.value ??
    (verificationResource.usingFallback.value ? teacherVerification : emptyVerification),
)
const notifications = computed(() => notificationsResource.data.value ?? [])
const usingFallback = computed(
  () =>
    profileResource.usingFallback.value ||
    verificationResource.usingFallback.value ||
    notificationsResource.usingFallback.value,
)
const pageError = computed(
  () =>
    profileResource.error.value ||
    verificationResource.error.value ||
    notificationsResource.error.value,
)
const noticeError = computed(() => actionError.value || pageError.value)
const profileNameLabel = computed(() => profile.value.fullName || 'Teacher profile')
const profileIntroLabel = computed(
  () => profile.value.shortIntroduction || profile.value.bio || 'Build a trusted teacher profile for MandarinSpark families.',
)
const verificationStatusTone = computed(() => (verification.value.teacherStatus === 'APPROVED' ? 'success' : 'warning'))
const profileHighlights = computed(() => [
  { label: 'Categories', value: String(profile.value.teachingCategories.length) },
  { label: 'Documents', value: String(profile.value.verificationDocuments.length) },
  { label: 'Status', value: verification.value.teacherStatus },
])
const hasUnreadNotifications = computed(() => notifications.value.some((item) => item.status !== 'READ'))
const markAllNotificationsLabel = computed(() => (markingNotificationsRead.value ? 'Marking...' : 'Mark all read'))
const verificationDocumentTypes: Array<{ value: TeacherVerificationDocumentType; label: string }> = [
  { value: 'IDENTITY', label: 'Identity document' },
  { value: 'QUALIFICATION', label: 'Teaching qualification' },
  { value: 'CERTIFICATE', label: 'Certificate' },
  { value: 'OTHER', label: 'Other supporting document' },
]

const profileForm = reactive({
  fullName: '',
  bio: '',
  shortIntroduction: '',
})
const categoriesText = ref('')
const uploadForm = reactive({
  documentType: 'IDENTITY' as TeacherVerificationDocumentType,
  description: '',
})
const fileInput = ref<HTMLInputElement | null>(null)
const verificationFile = ref<File | null>(null)
const savingProfile = ref(false)
const uploading = ref(false)
const downloadingDocumentId = ref<number | null>(null)
const markingNotificationsRead = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const maxVerificationFileSize = 5 * 1024 * 1024
const verificationFileExtensions = ['.pdf', '.jpg', '.jpeg', '.png']
const verificationContentTypes = ['application/pdf', 'image/jpeg', 'image/png']
const verificationUploadReadOnly = computed(() => uploading.value || profileUsingFallback.value)
const verificationUploadDisabled = computed(() => verificationUploadReadOnly.value || verificationFile.value === null)

watch(
  profile,
  (value) => {
    profileForm.fullName = value.fullName || ''
    profileForm.bio = value.bio || ''
    profileForm.shortIntroduction = value.shortIntroduction || ''
    categoriesText.value = value.teachingCategories.join(', ')
  },
  { immediate: true },
)

function hasAllowedVerificationFileType(file: File): boolean {
  const fileName = file.name.toLowerCase()
  const contentType = file.type.toLowerCase()
  return (
    verificationFileExtensions.some((extension) => fileName.endsWith(extension)) &&
    verificationContentTypes.includes(contentType)
  )
}

function resetVerificationFileInput(): void {
  verificationFile.value = null

  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

function selectVerificationFile(event: Event): void {
  actionError.value = null
  actionSuccess.value = null

  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0] ?? null

  if (!file) {
    verificationFile.value = null
    return
  }

  if (!hasAllowedVerificationFileType(file) || file.size > maxVerificationFileSize) {
    resetVerificationFileInput()
    actionError.value = 'Choose a PDF, JPG, JPEG, or PNG file under 5 MB.'
    return
  }

  verificationFile.value = file
}

function documentDownloadLabel(documentId: number): string {
  return downloadingDocumentId.value === documentId ? 'Downloading...' : 'Download'
}

function notificationStatusLabel(status: string): string {
  return status === 'READ' ? 'Read' : 'Unread'
}

function notificationStatusTone(status: string): StatusTone {
  return status === 'READ' ? 'info' : 'warning'
}

async function saveProfile(): Promise<void> {
  if (savingProfile.value) return
  if (profileUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Teacher profile cannot be changed while preview data is active.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  savingProfile.value = true
  try {
    await teacherApi.updateProfile({
      fullName: profileForm.fullName,
      bio: profileForm.bio,
      shortIntroduction: profileForm.shortIntroduction,
      teachingCategories: categoriesText.value
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
    })
    await profileResource.reload()
    actionSuccess.value = 'Teacher profile saved and refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save your teacher profile.')
  } finally {
    savingProfile.value = false
  }
}

async function downloadVerificationDocument(documentId: number, originalFileName: string): Promise<void> {
  if (downloadingDocumentId.value) return
  if (profileUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Verification document downloads are unavailable while using preview data.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  downloadingDocumentId.value = documentId

  try {
    const download = await teacherApi.downloadVerificationDocument(documentId)
    saveBlobDownload(download, originalFileName || `verification-document-${documentId}`)
    actionSuccess.value = 'Verification document downloaded.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to download this verification document.')
  } finally {
    downloadingDocumentId.value = null
  }
}

function markTeacherNotificationRead(notificationId: number): void {
  notificationsResource.data.value = notifications.value.map((item) =>
    item.id === notificationId ? { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() } : item,
  )
}

async function markAllNotificationsRead(): Promise<void> {
  if (markingNotificationsRead.value) return
  if (notificationsResource.usingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Notifications cannot be changed while using preview data.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  markingNotificationsRead.value = true
  try {
    const result = await notificationsApi.markAllRead()
    notificationsResource.data.value = notifications.value.map((item) =>
      item.status === 'READ' ? item : { ...item, status: 'READ', readAt: item.readAt ?? new Date().toISOString() },
    )
    window.dispatchEvent(new CustomEvent('mandarin-spark:notification-read', { detail: { all: true } }))
    actionSuccess.value =
      result.updatedCount > 0
        ? `Marked ${result.updatedCount} notification(s) as read.`
        : 'All notifications are already read.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to mark notifications as read.')
  } finally {
    markingNotificationsRead.value = false
  }
}

async function resubmitVerification(): Promise<void> {
  if (profileUsingFallback.value || verificationResource.usingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Verification status cannot be resubmitted while preview data is active.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  try {
    await teacherApi.resubmitVerification()
    await verificationResource.reload()
    actionSuccess.value = 'Verification resubmitted and status refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to resubmit verification.')
  }
}

async function uploadDocument(): Promise<void> {
  if (uploading.value) return
  if (profileUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Verification document upload is unavailable while using preview data.'
    return
  }

  const file = verificationFile.value
  if (!file) {
    actionSuccess.value = null
    actionError.value = 'Choose a verification file before uploading.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('documentType', uploadForm.documentType)
    formData.append('description', uploadForm.description.trim())
    await teacherApi.uploadVerificationDocument(formData)
    await verificationResource.reload()
    await profileResource.reload()
    resetVerificationFileInput()
    uploadForm.documentType = 'IDENTITY'
    uploadForm.description = ''
    actionSuccess.value = 'Verification document uploaded and profile refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to upload this verification document.')
  } finally {
    uploading.value = false
  }
}
</script>
