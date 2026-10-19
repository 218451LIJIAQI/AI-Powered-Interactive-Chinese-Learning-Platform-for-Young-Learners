<template>
  <div class="page-stack teacher-profile-page">
    <PageHeaderBlock :title="t('teacherProfile.title')" :description="t('teacherProfile.description')" icon="profile" />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <ResourceSection :loading="profileLoading" :error="profileResource.error.value" @retry="reloadAll">
      <section class="panel teacher-profile-hero" aria-labelledby="teacher-profile-name">
        <div class="teacher-profile-identity">
          <PortraitAvatar :size="72" rounded="soft" :name="profileNameLabel" aria-hidden="true" />
          <div class="teacher-profile-identity-copy">
            <span class="page-eyebrow">{{ t('teacherProfile.identity') }}</span>
            <h2 id="teacher-profile-name" class="teacher-profile-name">{{ profileNameLabel }}</h2>
            <p class="teacher-profile-intro">{{ profileIntroLabel }}</p>
            <ul v-if="profile.teachingCategories.length" class="teacher-profile-categories">
              <li v-for="category in profile.teachingCategories" :key="category" class="tag">{{ category }}</li>
            </ul>
          </div>
        </div>

        <dl class="teacher-profile-stats">
          <div v-for="item in profileHighlights" :key="item.key" class="teacher-profile-stat">
            <dt>{{ item.label }}</dt>
            <dd v-if="item.key === 'status'">
              <StatusPill :label="item.value" :tone="verificationStatusTone" :icon="verificationStatusIcon" />
            </dd>
            <dd v-else class="teacher-profile-stat-value">{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <div class="teacher-profile-layout">
        <div class="teacher-profile-column">
          <ActionFormCard
            :title="t('teacherProfile.edit')"
            :subtitle="t('teacherProfile.editHint')"
            icon="profile"
            :submit-label="t('teacherProfile.save')"
            :submitting-label="t('teacherProfile.saving')"
            :submitting="savingProfile"
            :disabled="profileFormDisabled"
            @submit="saveProfile"
          >
            <p class="field-hint">{{ t('teacherProfile.requiredNote') }}</p>
            <div class="field">
              <label class="field-label" for="teacher-profile-full-name">
                {{ t('teacherProfile.fullName') }}<span class="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="teacher-profile-full-name"
                v-model="profileForm.fullName"
                class="input"
                autocomplete="name"
                maxlength="120"
                required
                :disabled="profileFormDisabled"
              />
            </div>
            <div class="field">
              <label class="field-label" for="teacher-profile-short-introduction">
                {{ t('teacherProfile.shortIntro') }}
              </label>
              <textarea
                id="teacher-profile-short-introduction"
                v-model="profileForm.shortIntroduction"
                class="textarea teacher-profile-textarea-short"
                maxlength="1000"
                aria-describedby="teacher-profile-short-introduction-hint"
                :disabled="profileFormDisabled"
              />
              <p id="teacher-profile-short-introduction-hint" class="field-hint">
                {{ t('teacherProfile.shortIntroHint') }}
              </p>
            </div>
            <div class="field">
              <label class="field-label" for="teacher-profile-bio">{{ t('teacherProfile.bio') }}</label>
              <textarea
                id="teacher-profile-bio"
                v-model="profileForm.bio"
                class="textarea"
                maxlength="2000"
                aria-describedby="teacher-profile-bio-hint"
                :disabled="profileFormDisabled"
              />
              <p id="teacher-profile-bio-hint" class="field-hint">{{ t('teacherProfile.bioHint') }}</p>
            </div>
            <div class="field">
              <label class="field-label" for="teacher-profile-categories">
                {{ t('teacherProfile.teachingCategories') }}
              </label>
              <input
                id="teacher-profile-categories"
                v-model="categoriesText"
                class="input"
                maxlength="255"
                aria-describedby="teacher-profile-categories-hint"
                :placeholder="t('teacherProfile.categoriesPlaceholder')"
                :disabled="profileFormDisabled"
              />
              <p id="teacher-profile-categories-hint" class="field-hint">{{ t('teacherProfile.categoriesHint') }}</p>
            </div>
          </ActionFormCard>

          <InfoCard
            :title="t('teacherProfile.verificationDocuments')"
            :subtitle="t('teacherProfile.verificationDocumentsHint')"
            icon="verification"
          >
            <ul v-if="profile.verificationDocuments.length" class="teacher-profile-list">
              <li v-for="document in profile.verificationDocuments" :key="document.id">
                <ListInfoItem
                  :title="document.originalFileName"
                  :description="document.description || document.documentType"
                  icon="file"
                >
                  <template #meta>
                    <span class="type-caption">{{ formatDateTime(document.submittedAt) }}</span>
                  </template>
                  <template #aside>
                    <button
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :aria-label="t('teacherProfile.downloadAria', { name: document.originalFileName })"
                      :aria-busy="downloadingDocumentId === document.id"
                      :disabled="profileUsingFallback || downloadingDocumentId === document.id"
                      @click="downloadVerificationDocument(document.id, document.originalFileName)"
                    >
                      <IconGlyph name="download" :size="16" aria-hidden="true" />
                      {{ documentDownloadLabel(document.id) }}
                    </button>
                  </template>
                </ListInfoItem>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="file"
              :title="t('teacherProfile.noDocuments')"
              :message="t('teacherProfile.noDocumentsHint')"
            />
          </InfoCard>
        </div>

        <div class="teacher-profile-column">
          <InfoCard
            :title="t('teacherProfile.verificationStatus')"
            :subtitle="t('teacherProfile.verificationStatusHint')"
            icon="verification"
          >
            <div class="teacher-verification-status">
              <StatusPill
                :label="verificationStatusLabel"
                :tone="verificationStatusTone"
                :icon="verificationStatusIcon"
              />
              <p class="teacher-verification-note">
                {{ verificationNote || t('teacherProfile.currentStatus') }}
              </p>
            </div>
            <div v-if="verification.resubmissionAllowed" class="card-footer">
              <button
                class="btn btn-secondary"
                type="button"
                :aria-busy="resubmitting"
                :disabled="resubmitting"
                @click="resubmitVerification"
              >
                <IconGlyph name="refresh" :size="16" aria-hidden="true" />
                {{ t('teacherProfile.resubmit') }}
              </button>
            </div>
          </InfoCard>

          <ActionFormCard
            :title="t('teacherProfile.upload')"
            :subtitle="t('teacherProfile.uploadHint')"
            icon="upload"
            :submit-label="t('teacherProfile.uploadDocument')"
            :submitting-label="t('teacherProfile.uploading')"
            :submitting="uploading"
            :disabled="verificationUploadDisabled"
            @submit="uploadDocument"
          >
            <div class="field">
              <label class="field-label" for="teacher-verification-document-type">
                {{ t('teacherProfile.documentType') }}
              </label>
              <select
                id="teacher-verification-document-type"
                v-model="uploadForm.documentType"
                class="select"
                :disabled="verificationUploadReadOnly"
              >
                <option v-for="option in verificationDocumentTypes" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div class="field">
              <label class="field-label" for="teacher-verification-description">
                {{ t('teacherProfile.documentDescription') }}
              </label>
              <input
                id="teacher-verification-description"
                v-model="uploadForm.description"
                class="input"
                maxlength="500"
                :placeholder="t('teacherProfile.documentPlaceholder')"
                :disabled="verificationUploadReadOnly"
              />
            </div>
            <div class="field">
              <label class="field-label" for="teacher-verification-file">
                {{ t('teacherProfile.file') }}<span class="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="teacher-verification-file"
                ref="fileInput"
                class="input"
                type="file"
                required
                accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                :aria-invalid="Boolean(fileError) || undefined"
                :aria-describedby="fileDescribedBy"
                :disabled="verificationUploadReadOnly"
                @change="selectVerificationFile"
              />
              <p id="teacher-verification-file-hint" class="field-hint">{{ t('teacherProfile.fileHint') }}</p>
              <p v-if="fileError" id="teacher-verification-file-error" class="field-error" role="alert">
                {{ fileError }}
              </p>
            </div>
          </ActionFormCard>

          <InfoCard
            :title="t('teacherProfile.recentNotifications')"
            :subtitle="t('teacherProfile.recentNotificationsHint')"
            icon="bell"
          >
            <template #header-end>
              <button
                class="btn btn-ghost btn-sm teacher-profile-mark-read"
                type="button"
                :aria-busy="markingNotificationsRead"
                :disabled="markingNotificationsRead || !hasUnreadNotifications"
                @click="markAllNotificationsRead"
              >
                {{ markAllNotificationsLabel }}
              </button>
            </template>

            <div v-if="notifications.length" class="teacher-profile-list">
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
                      <span class="type-caption">{{ formatDateTime(item.createdAt) }}</span>
                    </div>
                  </template>
                </ListInfoItem>
              </NotificationLinkButton>
            </div>

            <p v-else class="teacher-profile-empty">{{ t('teacherProfile.noNotifications') }}</p>
          </InfoCard>
        </div>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { notificationsApi, teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import NotificationLinkButton from '@/components/common/NotificationLinkButton.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import InfoCard from '@/components/dashboard/InfoCard.vue'
import ListInfoItem from '@/components/dashboard/ListInfoItem.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { teacherProfile, teacherVerification } from '@/demo/data'
import { teacherProfileMessages } from '@/plugins/teacherProfileMessages'
import type { TeacherVerificationDocumentType } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDateTime } from '@/utils/format'
import { announceNotificationsRead, markAllLoadedRead, markNotificationRead } from '@/utils/notifications'

const { t } = useI18n({
  messages: {
    en: { teacherProfile: teacherProfileMessages.en },
    ms: { teacherProfile: teacherProfileMessages.ms },
    'zh-CN': { teacherProfile: teacherProfileMessages['zh-CN'] },
  },
})

type StatusTone = 'success' | 'warning' | 'danger' | 'info'

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
// A profile load error replaces the page with the ResourceSection error (its retry reloads every request), so the
// notice then carries only action errors; otherwise it reports the verification or notification request errors.
const noticeError = computed(
  () =>
    actionError.value ||
    (profileResource.error.value ? null : verificationResource.error.value || notificationsResource.error.value),
)
// Only the first load of the profile and verification status blocks the page; notification errors stay in the notice.
const profileLoading = computed(
  () =>
    (profileResource.loading.value && !profileResource.data.value) ||
    (verificationResource.loading.value && !verificationResource.data.value),
)
const profileNameLabel = computed(() => profile.value.fullName || t('teacherProfile.fallbackName'))
const profileIntroLabel = computed(
  () => profile.value.shortIntroduction || profile.value.bio || t('teacherProfile.fallbackIntro'),
)
const verificationStatusTone = computed<StatusTone>(() => {
  const status = verification.value.teacherStatus
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
})
const verificationStatusIcon = computed(() => (verification.value.teacherStatus === 'APPROVED' ? 'status' : 'alert'))
const verificationStatusLabel = computed(() => {
  const keys: Record<string, string> = {
    PENDING: 'statusPending',
    APPROVED: 'statusApproved',
    REJECTED: 'statusRejected',
  }
  const key = keys[verification.value.teacherStatus]
  return key ? t(`teacherProfile.${key}`) : verification.value.teacherStatus
})
const profileHighlights = computed(() => [
  { key: 'categories', label: t('teacherProfile.categories'), value: String(profile.value.teachingCategories.length) },
  { key: 'documents', label: t('teacherProfile.documents'), value: String(profile.value.verificationDocuments.length) },
  { key: 'status', label: t('teacherProfile.status'), value: verificationStatusLabel.value },
])
const hasUnreadNotifications = computed(() => notifications.value.some((item) => item.status !== 'READ'))
const markAllNotificationsLabel = computed(() =>
  markingNotificationsRead.value ? t('teacherProfile.marking') : t('teacherProfile.markAllRead'),
)
const verificationDocumentTypes = computed<Array<{ value: TeacherVerificationDocumentType; label: string }>>(() => [
  { value: 'IDENTITY', label: t('teacherProfile.identityDocument') },
  { value: 'QUALIFICATION', label: t('teacherProfile.qualification') },
  { value: 'CERTIFICATE', label: t('teacherProfile.certificate') },
  { value: 'OTHER', label: t('teacherProfile.otherDocument') },
])

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
/** File problems are also shown next to the file field (the page notice can be far away). */
const fileError = ref<string | null>(null)
const savingProfile = ref(false)
const uploading = ref(false)
const downloadingDocumentId = ref<number | null>(null)
const markingNotificationsRead = ref(false)
const resubmitting = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const maxVerificationFileSize = 5 * 1024 * 1024
const verificationFileExtensions = ['.pdf', '.jpg', '.jpeg', '.png']
const verificationContentTypes = ['application/pdf', 'image/jpeg', 'image/png']
const verificationUploadReadOnly = computed(() => uploading.value || profileUsingFallback.value)
const verificationUploadDisabled = computed(() => verificationUploadReadOnly.value || verificationFile.value === null)
const fileDescribedBy = computed(() =>
  fileError.value ? 'teacher-verification-file-error teacher-verification-file-hint' : 'teacher-verification-file-hint',
)

/**
 * The latest review note only describes the current status when it decided it: after a resubmission the teacher is
 * PENDING again while the latest review is still the old rejection.
 */
const verificationNote = computed(() => {
  const value = verification.value
  if (value.teacherStatus === 'REJECTED') return value.rejectionReason || value.latestReviewResult?.note || null
  const review = value.latestReviewResult
  return review && review.decisionStatus === value.teacherStatus ? review.note || null : null
})

// Refill the form on first load and after a profile save only, so unsaved edits survive a document upload.
watch(
  () => `${profile.value.userId}|${profile.value.updatedAt}`,
  () => {
    const value = profile.value
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
  fileError.value = null

  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0] ?? null

  if (!file) {
    verificationFile.value = null
    return
  }

  if (!hasAllowedVerificationFileType(file) || file.size > maxVerificationFileSize) {
    resetVerificationFileInput()
    actionError.value = t('teacherProfile.invalidFile')
    fileError.value = actionError.value
    return
  }

  verificationFile.value = file
}

function documentDownloadLabel(documentId: number): string {
  return downloadingDocumentId.value === documentId ? t('teacherProfile.downloading') : t('teacherProfile.download')
}

function notificationStatusLabel(status: string): string {
  return status === 'READ' ? t('teacherProfile.read') : t('teacherProfile.unread')
}

function notificationStatusTone(status: string): StatusTone {
  return status === 'READ' ? 'info' : 'warning'
}

async function saveProfile(): Promise<void> {
  if (savingProfile.value) return
  if (profileUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('teacherProfile.previewProfile')
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
    actionSuccess.value = t('teacherProfile.profileSaved')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherProfile.profileSaveError'))
  } finally {
    savingProfile.value = false
  }
}

async function downloadVerificationDocument(documentId: number, originalFileName: string): Promise<void> {
  if (downloadingDocumentId.value) return
  if (profileUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('teacherProfile.previewDownload')
    return
  }

  actionError.value = null
  actionSuccess.value = null
  downloadingDocumentId.value = documentId

  try {
    const download = await teacherApi.downloadVerificationDocument(documentId)
    saveBlobDownload(download, originalFileName || `verification-document-${documentId}`)
    actionSuccess.value = t('teacherProfile.downloaded')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherProfile.downloadError'))
  } finally {
    downloadingDocumentId.value = null
  }
}

function markTeacherNotificationRead(notificationId: number): void {
  notificationsResource.data.value = markNotificationRead(notifications.value, notificationId)
}

async function markAllNotificationsRead(): Promise<void> {
  if (markingNotificationsRead.value) return
  if (notificationsResource.usingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('teacherProfile.previewNotifications')
    return
  }

  actionError.value = null
  actionSuccess.value = null
  markingNotificationsRead.value = true
  try {
    const result = await notificationsApi.markAllRead()
    notificationsResource.data.value = markAllLoadedRead(notifications.value)
    announceNotificationsRead()
    actionSuccess.value =
      result.updatedCount > 0
        ? t('teacherProfile.markedCount', { count: result.updatedCount })
        : t('teacherProfile.alreadyRead')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherProfile.markReadError'))
  } finally {
    markingNotificationsRead.value = false
  }
}

async function resubmitVerification(): Promise<void> {
  if (resubmitting.value) return
  if (profileUsingFallback.value || verificationResource.usingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('teacherProfile.previewResubmit')
    return
  }

  actionError.value = null
  actionSuccess.value = null
  resubmitting.value = true
  try {
    verificationResource.data.value = await teacherApi.resubmitVerification()
    actionSuccess.value = t('teacherProfile.resubmitted')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherProfile.resubmitError'))
  } finally {
    resubmitting.value = false
  }
}

function reloadAll(): void {
  void profileResource.reload()
  void verificationResource.reload()
  void notificationsResource.reload()
}

async function uploadDocument(): Promise<void> {
  if (uploading.value) return
  if (profileUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('teacherProfile.previewUpload')
    return
  }

  const file = verificationFile.value
  if (!file) {
    actionSuccess.value = null
    actionError.value = t('teacherProfile.chooseFile')
    fileError.value = actionError.value
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
    verificationResource.data.value = await teacherApi.uploadVerificationDocument(formData)
    // The documents list comes from the profile.
    await profileResource.reload()
    resetVerificationFileInput()
    uploadForm.documentType = 'IDENTITY'
    uploadForm.description = ''
    actionSuccess.value = t('teacherProfile.uploaded')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherProfile.uploadError'))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.teacher-profile-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, auto);
  gap: var(--space-6);
  align-items: center;
  background: var(--gradient-brand-soft);
}

.teacher-profile-identity {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  min-width: 0;
}

.teacher-profile-identity-copy {
  display: grid;
  gap: var(--space-1-5);
  min-width: 0;
}

.teacher-profile-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
  overflow-wrap: anywhere;
}

.teacher-profile-intro {
  margin: 0;
  max-width: var(--reading-max);
  color: var(--text-secondary);
  line-height: var(--line-height-normal);
}

.teacher-profile-categories {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: var(--space-1) 0 0;
  padding: 0;
  list-style: none;
}

.teacher-profile-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(112px, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.teacher-profile-stat {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.teacher-profile-stat dt {
  color: var(--text-secondary);
  font-size: var(--type-caption-size);
  font-weight: var(--font-weight-bold);
}

.teacher-profile-stat dd {
  margin: 0;
  min-width: 0;
}

.teacher-profile-stat-value {
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 1fr);
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-profile-column {
  display: grid;
  gap: var(--grid-gap);
  min-width: 0;
}

.teacher-profile-mark-read {
  flex-shrink: 0;
  white-space: nowrap;
}

.teacher-profile-textarea-short {
  min-height: 88px;
}

.teacher-profile-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-verification-status {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
}

.teacher-verification-note,
.teacher-profile-empty {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.teacher-profile-empty {
  padding: var(--space-4);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
  text-align: center;
}

@media (max-width: 1200px) {
  .teacher-profile-hero {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .teacher-profile-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .teacher-profile-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--space-2);
  }

  .teacher-profile-stat {
    padding: var(--space-2-5) var(--space-3);
  }

  .teacher-profile-name {
    font-size: var(--font-size-2xl);
  }
}
</style>
