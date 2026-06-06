<template>
  <div class="page-stack admin-verification-page">
    <section class="admin-verification-header">
      <PageHeaderBlock
        title="Teacher Verification"
        description="Review real teacher applications, inspect uploaded documents, and complete approval or rejection actions."
        icon="verification"
      />

      <div class="admin-verification-toolbar">
        <label class="admin-verification-search">
          <IconGlyph name="search" :size="16" />
          <input
            v-model="searchTerm"
            class="admin-verification-search-input"
            placeholder="Search applicants by name or email"
          />
        </label>

        <button class="teacher-outline-button admin-verification-tool-button" type="button" @click="cycleSortMode">
          Sort: <strong>{{ sortModeLabel }}</strong>
        </button>

        <div class="teacher-outline-button admin-verification-tool-button is-date" role="status" aria-live="polite">
          <IconGlyph name="calendar" :size="16" />
          {{ todayLabel }}
        </div>
      </div>
    </section>

    <DataNotice
      :using-fallback="pendingResource.usingFallback.value"
      :error="noticeError"
      :success="actionSuccess"
    />

    <section class="admin-verification-stats">
      <article v-for="card in statCards" :key="card.label" class="admin-verification-stat">
        <div class="admin-verification-stat-icon" :class="`is-${card.tone}`">
          <IconGlyph :name="card.icon" :size="22" />
        </div>
        <div class="stack u-gap-4">
          <span class="admin-verification-stat-label">{{ card.label }}</span>
          <strong class="admin-verification-stat-value">{{ card.value }}</strong>
          <span class="admin-verification-stat-copy">{{ card.copy }}</span>
        </div>
      </article>
    </section>

    <section class="admin-verification-layout">
      <article class="panel admin-verification-list-panel">
        <div class="admin-verification-list-head">
          <div class="student-section-heading">
            <strong>Pending Applications ({{ filteredApplicants.length }})</strong>
          </div>
        </div>

        <div v-if="pagedApplicants.length" class="admin-verification-list">
          <button
            v-for="item in pagedApplicants"
            :key="item.teacherId"
            type="button"
            class="admin-verification-row"
            :class="{ 'is-active': item.teacherId === selectedTeacherId }"
            @click="selectTeacher(item.teacherId)"
          >
            <PortraitAvatar :size="58" :kind="item.avatarKind" />
            <div class="admin-verification-row-copy">
              <strong>{{ item.fullName }}</strong>
              <span>{{ item.email }}</span>
              <small>{{ item.summary }}</small>
            </div>
            <div class="admin-verification-row-side">
              <span>{{ item.appliedLabel }}</span>
              <small>{{ item.documentCountLabel }}</small>
            </div>
          </button>
        </div>
        <StatePanel
          v-else
          icon="verification"
          title="No pending applications"
          message="Teachers waiting for review will appear here."
        />

        <div v-if="totalPages > 1" class="admin-verification-pagination">
          <div class="row admin-verification-pagination-pages">
            <button
              v-for="page in totalPages"
              :key="page"
              class="admin-verification-page-button"
              :class="{ 'is-active': currentPage === page }"
              type="button"
              @click="goToPage(page)"
            >
              {{ page }}
            </button>
          </div>
          <span>Showing {{ paginationCountLabel }} of {{ filteredApplicants.length }}</span>
        </div>
      </article>

      <article class="panel admin-verification-detail-panel">
        <template v-if="selectedApplicant">
          <div class="admin-verification-detail-top">
            <div class="admin-verification-detail-profile">
              <PortraitAvatar :size="76" :kind="selectedApplicant.avatarKind" />
              <div class="stack u-gap-6">
                <h2>{{ selectedApplicant.fullName }}</h2>
                <span>{{ selectedApplicant.email }}</span>
                <small>Submitted {{ selectedApplicant.appliedLabel }}</small>
              </div>
            </div>

            <div class="admin-verification-detail-top-side">
              <StatusPill label="Pending" tone="warning" />
              <small>{{ selectedApplicant.documentCountLabel }}</small>
            </div>
          </div>

          <div class="tabs admin-review-tabs">
            <button
              class="tab-button"
              type="button"
              :class="{ 'is-active': activeDetailTab === 'overview' }"
              @click="showDetailTab('overview')"
            >
              Overview
            </button>
            <button
              class="tab-button"
              type="button"
              :class="{ 'is-active': activeDetailTab === 'documents' }"
              @click="showDetailTab('documents')"
            >
              Documents
            </button>
            <button
              class="tab-button"
              type="button"
              :class="{ 'is-active': activeDetailTab === 'notes' }"
              @click="showDetailTab('notes')"
            >
              Decision
            </button>
          </div>

          <template v-if="activeDetailTab === 'overview'">
            <div class="admin-verification-detail-grid">
              <section class="panel admin-verification-info-card">
                <div class="admin-verification-section-head">
                  <strong>Application Summary</strong>
                </div>

                <div class="admin-verification-basic-info">
                  <div>
                    <span>Teacher ID</span><strong>#{{ selectedApplicant.teacherId }}</strong>
                  </div>
                  <div>
                    <span>User ID</span><strong>#{{ selectedApplicant.userId }}</strong>
                  </div>
                  <div>
                    <span>Status</span><strong>{{ formatStatus(selectedApplicant.teacherStatus) }}</strong>
                  </div>
                  <div>
                    <span>Submitted</span><strong>{{ selectedApplicant.appliedLabel }}</strong>
                  </div>
                  <div>
                    <span>Teaching Categories</span><strong>{{ selectedApplicant.categoriesLabel }}</strong>
                  </div>
                  <div class="admin-verification-block-row">
                    <span>Short Introduction</span>
                    <div class="stack u-gap-4">
                      <strong>{{ selectedApplicant.shortIntroductionLabel }}</strong>
                      <small>{{ selectedApplicant.bioLabel }}</small>
                    </div>
                  </div>
                </div>
              </section>

              <section class="panel admin-verification-doc-card">
                <div class="admin-verification-section-head">
                  <strong>Review History</strong>
                </div>

                <div v-if="reviewHistory.length" class="admin-verification-audit-list">
                  <article v-for="entry in reviewHistory" :key="entry.id" class="admin-verification-audit-item">
                    <span class="admin-verification-audit-dot" />
                    <div class="stack u-gap-4">
                      <strong>{{ entry.title }}</strong>
                      <span>{{ entry.copy }}</span>
                      <small>{{ entry.time }}</small>
                    </div>
                  </article>
                </div>
                <p v-else class="admin-verification-empty-copy">No review history has been recorded yet.</p>

                <div class="admin-verification-section-head admin-verification-audit-head">
                  <strong>Recent Audit Logs</strong>
                </div>

                <div v-if="auditTrail.length" class="admin-verification-audit-list">
                  <article
                    v-for="entry in auditTrail"
                    :key="entry.title + entry.time"
                    class="admin-verification-audit-item"
                  >
                    <span class="admin-verification-audit-dot" />
                    <div class="stack u-gap-4">
                      <strong>{{ entry.title }}</strong>
                      <span>{{ entry.copy }}</span>
                      <small>{{ entry.time }}</small>
                    </div>
                  </article>
                </div>
                <p v-else class="admin-verification-empty-copy">No audit trail is available for this teacher yet.</p>
              </section>
            </div>
          </template>

          <section
            v-else-if="activeDetailTab === 'documents'"
            class="panel admin-verification-doc-card admin-verification-doc-card--solo"
          >
            <div class="admin-verification-section-head">
              <strong>Verification Documents</strong>
            </div>

            <div v-if="reviewDocuments.length" class="admin-verification-document-list">
              <article v-for="document in reviewDocuments" :key="document.id" class="admin-verification-document-row">
                <div class="row">
                  <span class="admin-verification-document-icon">
                    <IconGlyph name="file" :size="16" />
                  </span>
                  <div class="stack u-gap-3">
                    <strong>{{ document.title }}</strong>
                    <span>{{ document.fileName }}</span>
                    <small>{{ document.meta }}</small>
                  </div>
                </div>
                <div class="stack u-gap-8 admin-verification-document-actions">
                  <StatusPill :label="document.status" :tone="document.tone" />
                  <button
                    class="teacher-outline-button"
                    type="button"
                    :disabled="detailUsingFallback || downloadingDocumentId === document.id"
                    @click="downloadTeacherDocument(document.id, document.fileName)"
                  >
                    {{ getDocumentDownloadLabel(document.id) }}
                  </button>
                </div>
              </article>
            </div>
            <StatePanel
              v-else
              icon="file"
              title="No uploaded documents"
              message="This teacher has not uploaded verification documents yet."
            />
          </section>

          <section v-else class="panel admin-verification-notes-card">
            <div class="admin-verification-section-head">
              <strong>Decision Notes</strong>
            </div>

            <div class="stack u-gap-14">
              <article class="admin-verification-note-preview">
                <strong>Current rejection reason</strong>
                <p>{{ selectedApplicant.rejectionReasonLabel }}</p>
              </article>

              <div class="field">
                <label for="admin-verification-approval-note">Approval note</label>
                <textarea
                  id="admin-verification-approval-note"
                  v-model="approvalNote"
                  class="textarea"
                  maxlength="1000"
                  placeholder="Add a concise approval note."
                  :disabled="verificationDecisionReadOnly"
                />
              </div>

              <div class="field">
                <label for="admin-verification-rejection-reason">Rejection reason</label>
                <textarea
                  id="admin-verification-rejection-reason"
                  v-model="rejectionReason"
                  class="textarea"
                  maxlength="1000"
                  placeholder="Explain what needs correction before resubmission."
                  :disabled="verificationDecisionReadOnly"
                />
              </div>
            </div>
          </section>

          <div class="admin-verification-footer-actions">
            <button class="btn btn-secondary" type="button" @click="showDetailTab('notes')">
              Open Decision Tab
            </button>
            <button
              class="btn btn-danger"
              type="button"
              :disabled="verificationReadOnly || submittingReject"
              @click="rejectTeacher"
            >
              {{ rejectButtonLabel }}
            </button>
            <button
              class="btn btn-primary"
              type="button"
              :disabled="verificationReadOnly || submittingApprove"
              @click="approveTeacher"
            >
              {{ approveButtonLabel }}
            </button>
          </div>
        </template>

        <StatePanel
          v-else
          icon="verification"
          title="Choose a teacher"
          message="Select a pending application to inspect documents and complete the review."
        />
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { adminApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminTeacherDetail, adminTeachers, teacherApplicationsPending } from '@/demo/data'
import type { AdminTeacherDetailResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDate, formatDateTime, formatNumber } from '@/utils/format'

const pendingResource = useResource(() => adminApi.pendingTeachers(), teacherApplicationsPending)
const teachersResource = useResource(() => adminApi.teachers(), adminTeachers)
const detailResource = useResource(
  () => adminApi.teacherDetail(adminTeacherDetail.teacherId),
  adminTeacherDetail,
  false,
)

const pageError = computed(
  () => pendingResource.error.value || teachersResource.error.value || detailResource.error.value,
)
const detailUsingFallback = computed(() => detailResource.usingFallback.value)
const verificationReadOnly = computed(
  () =>
    pendingResource.usingFallback.value || teachersResource.usingFallback.value || detailResource.usingFallback.value,
)
const verificationDecisionReadOnly = computed(
  () => verificationReadOnly.value || submittingApprove.value || submittingReject.value,
)

const approvalNote = ref('')
const rejectionReason = ref('')
const submittingApprove = ref(false)
const submittingReject = ref(false)
const downloadingDocumentId = ref<number | null>(null)
const actionError = ref<string | null>(null)
const selectedTeacherId = ref(0)
const searchTerm = ref('')
const currentPage = ref(1)
const pageSize = 5

type ApplicantTone = 'success' | 'warning'
type AvatarKind = 'female' | 'male' | 'hijab' | 'student'
type DetailTab = 'overview' | 'documents' | 'notes'
type SortMode = 'newest' | 'name'

const avatarKinds = ['female', 'male', 'hijab', 'student'] as const
const activeDetailTab = ref<DetailTab>('overview')
const sortMode = ref<SortMode>('newest')

interface ApplicantAudit {
  id: string
  title: string
  copy: string
  time: string
}

interface ApplicantDocument {
  id: number
  title: string
  fileName: string
  status: string
  tone: ApplicantTone
  meta: string
}

interface ApplicantSummary {
  teacherId: number
  userId: number
  fullName: string
  email: string
  teacherStatus: string
  appliedAt: string
  appliedLabel: string
  summary: string
  documentCount: number
  documentCountLabel: string
  categoriesLabel: string
  shortIntroductionLabel: string
  bioLabel: string
  rejectionReasonLabel: string
  avatarKind: AvatarKind
}

const pendingApplicants = computed<ApplicantSummary[]>(() =>
  (pendingResource.data.value ?? []).map((item, index) => ({
    teacherId: item.teacherId,
    userId: item.userId,
    fullName: item.fullName,
    email: item.email,
    teacherStatus: item.teacherStatus,
    appliedAt: item.latestSubmissionTime || item.createdAt,
    appliedLabel: formatDate(item.latestSubmissionTime || item.createdAt),
    summary: item.bioSummary?.trim() || 'No teacher summary provided yet.',
    documentCount: item.verificationDocumentCount,
    documentCountLabel: `${formatNumber(item.verificationDocumentCount)} document(s) submitted`,
    categoriesLabel: 'Available in teacher detail',
    shortIntroductionLabel: item.bioSummary?.trim() || 'No short introduction provided.',
    bioLabel: 'Open the teacher detail to review the full profile.',
    rejectionReasonLabel: 'No rejection reason recorded.',
    avatarKind: avatarKinds[index % avatarKinds.length],
  })),
)

const filteredApplicants = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  const result = pendingApplicants.value.filter((item) => {
    if (!keyword) return true
    return item.fullName.toLowerCase().includes(keyword) || item.email.toLowerCase().includes(keyword)
  })

  result.sort((left, right) => {
    if (sortMode.value === 'name') return left.fullName.localeCompare(right.fullName)
    return new Date(right.appliedAt).getTime() - new Date(left.appliedAt).getTime()
  })

  return result
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredApplicants.value.length / pageSize)))
const pagedApplicants = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredApplicants.value.slice(start, start + pageSize)
})

watch(
  filteredApplicants,
  () => {
    if (currentPage.value > totalPages.value) currentPage.value = 1
  },
  { deep: true },
)

watch(
  filteredApplicants,
  (items) => {
    if (!items.some((item) => item.teacherId === selectedTeacherId.value)) {
      selectedTeacherId.value = items[0]?.teacherId ?? 0
    }
  },
  { immediate: true, deep: true },
)

const selectedBaseApplicant = computed(
  () =>
    filteredApplicants.value.find((item) => item.teacherId === selectedTeacherId.value) ??
    filteredApplicants.value[0] ??
    null,
)

const selectedDetail = computed<AdminTeacherDetailResponse | null>(() => {
  const detail = detailResource.data.value
  return detail && detail.teacherId === selectedTeacherId.value ? detail : null
})

const selectedApplicant = computed(() => {
  if (!selectedBaseApplicant.value) return null
  if (!selectedDetail.value) return selectedBaseApplicant.value

  return {
    ...selectedBaseApplicant.value,
    teacherStatus: selectedDetail.value.teacherStatus,
    categoriesLabel: selectedDetail.value.teachingCategories.length
      ? selectedDetail.value.teachingCategories.join(', ')
      : 'No categories configured',
    shortIntroductionLabel: selectedDetail.value.shortIntroduction?.trim() || 'No short introduction provided.',
    bioLabel: selectedDetail.value.bio?.trim() || 'No detailed bio provided.',
    documentCount: selectedDetail.value.verificationDocuments.length,
    documentCountLabel: `${formatNumber(selectedDetail.value.verificationDocuments.length)} document(s) available`,
    rejectionReasonLabel: selectedDetail.value.rejectionReason?.trim() || 'No rejection reason recorded.',
  }
})

const reviewDocuments = computed<ApplicantDocument[]>(() =>
  (selectedDetail.value?.verificationDocuments ?? []).map((document) => ({
    id: document.id,
    title: document.description || document.documentType,
    fileName: document.originalFileName,
    status: document.currentVersion ? 'Current Version' : 'Older Version',
    tone: document.currentVersion ? 'success' : 'warning',
    meta: `${document.contentType} • ${formatNumber(document.fileSize)} bytes • ${formatDateTime(document.submittedAt)}`,
  })),
)

const reviewHistory = computed<ApplicantAudit[]>(() =>
  (selectedDetail.value?.verificationReviewHistory ?? []).map((entry) => ({
    id: `review-${entry.id}`,
    title: formatStatus(entry.decisionStatus),
    copy: entry.note?.trim() || 'No reviewer note recorded.',
    time: formatDateTime(entry.reviewedAt),
  })),
)

const auditTrail = computed<ApplicantAudit[]>(() =>
  (selectedDetail.value?.recentAuditLogs ?? []).map((entry) => ({
    id: `audit-${entry.auditLogId}`,
    title: formatStatus(entry.actionType),
    copy: `${entry.targetType}: ${entry.description}`,
    time: formatDateTime(entry.createdAt),
  })),
)

const statCards = computed(() => [
  {
    label: 'Pending Review',
    value: formatNumber(filteredApplicants.value.length),
    copy: 'Current pending teacher queue',
    icon: 'verification',
    tone: 'gold',
  },
  {
    label: 'Approved Teachers',
    value: formatNumber(
      (teachersResource.data.value?.items ?? []).filter((item) => item.teacherStatus === 'APPROVED').length,
    ),
    copy: 'Teacher directory count',
    icon: 'status',
    tone: 'green',
  },
  {
    label: 'Rejected Teachers',
    value: formatNumber(
      (teachersResource.data.value?.items ?? []).filter((item) => item.teacherStatus === 'REJECTED').length,
    ),
    copy: 'Teacher directory count',
    icon: 'close',
    tone: 'red',
  },
  {
    label: 'Documents In Queue',
    value: formatNumber(filteredApplicants.value.reduce((sum, item) => sum + item.documentCount, 0)),
    copy: 'Pending application document total',
    icon: 'file',
    tone: 'purple',
  },
])

const todayLabel = computed(() =>
  new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)
const sortModeLabel = computed(() => (sortMode.value === 'newest' ? 'Newest First' : 'A-Z'))
const noticeError = computed(() => actionError.value || pageError.value)
const approveButtonLabel = computed(() => (submittingApprove.value ? 'Approving...' : 'Approve'))
const rejectButtonLabel = computed(() => (submittingReject.value ? 'Rejecting...' : 'Reject'))
const paginationCountLabel = computed(() => {
  if (!filteredApplicants.value.length) {
    return '0-0'
  }

  const start = (currentPage.value - 1) * pageSize + 1
  const end = Math.min(filteredApplicants.value.length, currentPage.value * pageSize)
  return `${start}-${end}`
})
const actionSuccess = ref<string | null>(null)

function cycleSortMode(): void {
  sortMode.value = sortMode.value === 'newest' ? 'name' : 'newest'
}

function goToPage(page: number): void {
  currentPage.value = page
}

function showDetailTab(tab: DetailTab): void {
  activeDetailTab.value = tab
}

function getDocumentDownloadLabel(documentId: number): string {
  return downloadingDocumentId.value === documentId ? 'Downloading...' : 'Download'
}

function previewTeacherDetail(id: number): AdminTeacherDetailResponse | null {
  const applicant = pendingApplicants.value.find((item) => item.teacherId === id)
  if (!applicant) return null

  return {
    ...adminTeacherDetail,
    teacherId: applicant.teacherId,
    userId: applicant.userId,
    fullName: applicant.fullName,
    email: applicant.email,
    bio: applicant.bioLabel,
    shortIntroduction: applicant.shortIntroductionLabel,
    teacherStatus: applicant.teacherStatus as AdminTeacherDetailResponse['teacherStatus'],
  }
}

async function selectTeacher(id: number): Promise<void> {
  actionError.value = null
  actionSuccess.value = null
  downloadingDocumentId.value = null
  approvalNote.value = ''
  rejectionReason.value = ''
  selectedTeacherId.value = id
  activeDetailTab.value = 'overview'

  if (verificationReadOnly.value) {
    detailResource.data.value = previewTeacherDetail(id)
    return
  }

  try {
    detailResource.data.value = await adminApi.teacherDetail(id)
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to load this teacher detail.')
    detailResource.data.value = null
  }
}

async function downloadTeacherDocument(documentId: number, fileName: string): Promise<void> {
  if (downloadingDocumentId.value) return
  if (detailUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = 'Teacher document downloads are unavailable while using preview data.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  downloadingDocumentId.value = documentId

  try {
    const download = await adminApi.downloadTeacherDocument(documentId)
    saveBlobDownload(download, fileName || `teacher-document-${documentId}`)
    actionSuccess.value = 'Teacher verification document downloaded.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to download this teacher document.')
  } finally {
    downloadingDocumentId.value = null
  }
}

async function approveTeacher(): Promise<void> {
  if (submittingApprove.value) return
  if (!selectedApplicant.value) return
  if (verificationReadOnly.value) {
    actionSuccess.value = null
    actionError.value = 'Teacher applications cannot be approved while preview data is active.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingApprove.value = true

  try {
    await adminApi.approveTeacher(selectedApplicant.value.teacherId, approvalNote.value.trim() || undefined)
    await pendingResource.reload()
    await teachersResource.reload()
    detailResource.data.value = null
    approvalNote.value = ''
    rejectionReason.value = ''
    actionSuccess.value = 'Teacher application approved and queue refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to approve this teacher.')
  } finally {
    submittingApprove.value = false
  }
}

async function rejectTeacher(): Promise<void> {
  if (submittingReject.value) return
  if (!selectedApplicant.value) return
  if (verificationReadOnly.value) {
    actionSuccess.value = null
    actionError.value = 'Teacher applications cannot be rejected while preview data is active.'
    return
  }

  const normalizedReason = rejectionReason.value.trim()
  if (!normalizedReason) {
    actionSuccess.value = null
    actionError.value = 'Enter a rejection reason before rejecting this teacher application.'
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingReject.value = true

  try {
    await adminApi.rejectTeacher(selectedApplicant.value.teacherId, normalizedReason)
    await pendingResource.reload()
    await teachersResource.reload()
    detailResource.data.value = null
    approvalNote.value = ''
    rejectionReason.value = ''
    actionSuccess.value = 'Teacher application rejected and queue refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to reject this teacher.')
  } finally {
    submittingReject.value = false
  }
}

function formatStatus(status: string): string {
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}
</script>

<style scoped>
.admin-verification-page {
  position: relative;
}

.admin-verification-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.admin-verification-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.admin-verification-tool-button {
  min-height: 44px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.admin-verification-tool-button.is-date {
  min-width: 190px;
}

.admin-verification-search {
  min-width: 260px;
  min-height: 44px;
  padding: 0 14px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  border-radius: 16px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #8a95b0;
}

.admin-verification-search-input {
  min-height: 36px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
}

.admin-verification-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.admin-verification-stat,
.admin-verification-list-panel,
.admin-verification-detail-panel,
.admin-verification-info-card,
.admin-verification-doc-card,
.admin-verification-notes-card {
  border-radius: 24px;
}

.admin-verification-stat {
  min-height: 112px;
  padding: 20px 18px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid rgba(232, 236, 245, 0.94);
  box-shadow: 0 12px 24px rgba(49, 64, 94, 0.06);
  background: rgba(255, 255, 255, 0.98);
}

.admin-verification-stat-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 16px;
}

.admin-verification-stat-icon.is-gold {
  background: rgba(255, 181, 68, 0.14);
  color: #ee9618;
}

.admin-verification-stat-icon.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-verification-stat-icon.is-red {
  background: rgba(255, 121, 121, 0.14);
  color: #e05c52;
}

.admin-verification-stat-icon.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-verification-stat-label,
.admin-verification-stat-copy,
.admin-verification-row-copy span,
.admin-verification-row-copy small,
.admin-verification-row-side span,
.admin-verification-row-side small,
.admin-verification-basic-info span,
.admin-verification-document-row span,
.admin-verification-document-row small,
.admin-verification-audit-item span,
.admin-verification-audit-item small,
.admin-verification-note-preview p,
.admin-verification-pagination span,
.admin-verification-empty-copy {
  color: #72809d;
}

.admin-verification-stat-value {
  font-size: 1.9rem;
  line-height: 1;
}

.admin-verification-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: 18px;
  align-items: start;
}

.admin-verification-list-head,
.admin-verification-section-head,
.admin-verification-detail-top,
.admin-verification-detail-profile,
.admin-verification-detail-top-side {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.admin-verification-detail-profile {
  min-width: 0;
  justify-content: flex-start;
}

.admin-verification-detail-top-side {
  min-width: 0;
  flex-direction: column;
  align-items: flex-end;
}

.admin-verification-detail-profile .stack {
  min-width: 0;
  max-width: 100%;
}

.admin-verification-list,
.admin-verification-document-list,
.admin-verification-audit-list {
  display: grid;
  gap: 12px;
}

.admin-verification-row {
  width: 100%;
  padding: 14px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  border-radius: 18px;
  border: 1px solid rgba(234, 238, 247, 0.96);
  background: rgba(255, 255, 255, 0.96);
  color: var(--text);
  text-align: left;
}

.admin-verification-row.is-active {
  border-color: rgba(125, 88, 255, 0.3);
  box-shadow: 0 10px 20px rgba(106, 73, 255, 0.06);
}

.admin-verification-row-copy,
.admin-verification-row-side {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.admin-verification-row-copy strong,
.admin-verification-row-copy span,
.admin-verification-row-copy small {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.admin-verification-row-side {
  justify-items: end;
}

.admin-verification-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 8px;
}

.admin-verification-pagination-pages {
  gap: 8px;
}

.admin-verification-page-button {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  border: 1px solid rgba(228, 232, 244, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #7d89a4;
  font-weight: 700;
}

.admin-verification-page-button.is-active {
  background: linear-gradient(135deg, #7d58ff, #6d49f8);
  color: white;
  border-color: transparent;
}

.admin-verification-detail-panel,
.admin-verification-info-card,
.admin-verification-doc-card,
.admin-verification-notes-card {
  display: grid;
  gap: 16px;
}

.admin-verification-detail-top h2 {
  margin: 0;
  font-size: 1.8rem;
  line-height: 1;
}

.admin-verification-detail-top h2,
.admin-verification-detail-top span,
.admin-verification-detail-top small {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.admin-verification-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.92fr);
  gap: 16px;
}

.admin-verification-basic-info {
  display: grid;
  gap: 12px;
}

.admin-verification-basic-info div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.admin-verification-block-row {
  align-items: flex-start;
}

.admin-verification-document-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-verification-document-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.admin-verification-document-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-verification-document-actions {
  align-items: flex-end;
}

.admin-verification-audit-head {
  padding-top: 8px;
  border-top: 1px solid rgba(236, 239, 247, 0.96);
}

.admin-verification-audit-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
}

.admin-verification-audit-dot {
  width: 10px;
  height: 10px;
  margin-top: 7px;
  border-radius: 50%;
  background: #7a56ff;
}

.admin-verification-doc-card--solo {
  min-height: 320px;
}

.admin-verification-note-preview {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(246, 248, 252, 0.96);
}

.admin-verification-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 1480px) {
  .admin-verification-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-verification-layout,
  .admin-verification-detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1100px) {
  .admin-verification-header {
    display: grid;
  }

  .admin-verification-row,
  .admin-verification-pagination {
    grid-template-columns: 1fr;
  }

  .admin-verification-row {
    justify-items: start;
  }

  .admin-verification-row-side,
  .admin-verification-detail-top-side {
    justify-items: start;
    align-items: flex-start;
  }

  .admin-verification-detail-top,
  .admin-verification-detail-profile {
    display: grid;
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .admin-verification-document-row {
    align-items: flex-start;
  }

  .admin-verification-document-actions {
    align-items: flex-start;
  }
}
</style>
