<template>
  <div class="page-stack admin-verification-page">
    <PageHeaderBlock
      :title="t('adminTeacherVerification.title')"
      :description="t('adminTeacherVerification.description')"
      icon="verification"
    >
      <template #meta>
        <span class="tag">
          <IconGlyph name="calendar" :size="14" aria-hidden="true" />
          {{ todayLabel }}
        </span>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="pendingResource.usingFallback.value" :error="noticeError" :success="actionSuccess" />

    <ResourceSection class="page-stack" :loading="queueLoading" :error="queueError" @retry="reloadQueue">
      <section class="admin-kpi-grid">
        <AdminKpiTile
          v-for="card in statCards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :hint="card.copy"
          :icon="card.icon"
          :tone="card.tone"
        />
      </section>

      <section class="admin-split admin-split--even">
        <article class="panel admin-panel">
          <h2 class="card-title">
            {{ t('adminTeacherVerification.pendingApplications', { count: filteredApplicants.length }) }}
          </h2>

          <div class="admin-toolbar">
            <div class="field admin-toolbar-search">
              <label for="admin-verification-search">{{ t('adminTeacherVerification.searchLabel') }}</label>
              <div class="input-with-icon">
                <IconGlyph class="input-icon" name="search" :size="16" />
                <input
                  id="admin-verification-search"
                  v-model="searchTerm"
                  class="input"
                  type="search"
                  :placeholder="t('adminTeacherVerification.search')"
                />
              </div>
            </div>
            <button class="btn btn-secondary" type="button" @click="cycleSortMode">
              <IconGlyph name="filter" :size="16" />
              <span>{{ t('adminTeacherVerification.sort', { mode: sortModeLabel }) }}</span>
            </button>
          </div>

          <ul v-if="pagedApplicants.length" class="admin-queue">
            <li v-for="item in pagedApplicants" :key="item.teacherId">
              <button
                type="button"
                class="admin-queue-item"
                :class="{ 'is-selected': item.teacherId === selectedTeacherId }"
                :aria-pressed="item.teacherId === selectedTeacherId"
                @click="selectTeacher(item.teacherId)"
              >
                <PortraitAvatar :size="40" :name="item.fullName" :tone="item.avatarTone" />
                <span class="admin-queue-copy">
                  <strong>{{ item.fullName }}</strong>
                  <span class="admin-feed-text">{{ item.email }}</span>
                  <span class="admin-feed-meta">{{ item.summary }}</span>
                </span>
                <span class="admin-queue-aside">
                  <span class="admin-feed-meta">{{ item.appliedLabel }}</span>
                  <span class="tag">{{ item.documentCountLabel }}</span>
                </span>
              </button>
            </li>
          </ul>
          <StatePanel
            v-else
            compact
            icon="verification"
            :title="t('adminTeacherVerification.noPending')"
            :message="t('adminTeacherVerification.noPendingHint')"
          />

          <PaginationBar
            v-if="totalPages > 1"
            :info="
              t('adminTeacherVerification.showing', { shown: paginationCountLabel, total: filteredApplicants.length })
            "
            :page-count="totalPages"
            :current-page="currentPage"
            :page-label="(page) => t('adminTeacherVerification.pageAria', { page })"
            @select="goToPage"
          />
        </article>

        <article class="panel admin-panel">
          <template v-if="selectedApplicant">
            <div class="admin-profile">
              <PortraitAvatar :size="56" :name="selectedApplicant.fullName" :tone="selectedApplicant.avatarTone" />
              <div class="admin-profile-copy">
                <h2 class="admin-profile-name">{{ selectedApplicant.fullName }}</h2>
                <span class="admin-profile-meta">{{ selectedApplicant.email }}</span>
                <span class="admin-feed-meta">
                  {{ t('adminTeacherVerification.submittedAt', { date: selectedApplicant.appliedLabel }) }}
                </span>
              </div>
              <div class="admin-queue-aside">
                <StatusPill :label="t('adminTeacherVerification.pending')" tone="warning" />
                <span class="admin-feed-meta">{{ selectedApplicant.documentCountLabel }}</span>
              </div>
            </div>

            <div class="tabs tabs--underline" role="tablist" :aria-label="t('adminTeacherVerification.detailTabsAria')">
              <button
                v-for="tab in detailTabs"
                :id="`admin-verification-tab-${tab.value}`"
                :key="tab.value"
                class="tab-button"
                type="button"
                role="tab"
                :aria-selected="activeDetailTab === tab.value"
                aria-controls="admin-verification-tabpanel"
                :tabindex="activeDetailTab === tab.value ? 0 : -1"
                @click="showDetailTab(tab.value)"
                @keydown.right.prevent="moveDetailTab(1)"
                @keydown.left.prevent="moveDetailTab(-1)"
              >
                {{ tab.label }}
              </button>
            </div>

            <div
              id="admin-verification-tabpanel"
              class="admin-verification-tabpanel"
              role="tabpanel"
              :aria-labelledby="`admin-verification-tab-${activeDetailTab}`"
            >
              <template v-if="activeDetailTab === 'overview'">
                <section class="admin-verification-section">
                  <h3 class="card-title">{{ t('adminTeacherVerification.applicationSummary') }}</h3>
                  <dl class="kv-list">
                    <dt>{{ t('adminTeacherVerification.teacherId') }}</dt>
                    <dd>#{{ selectedApplicant.teacherId }}</dd>
                    <dt>{{ t('adminTeacherVerification.userId') }}</dt>
                    <dd>#{{ selectedApplicant.userId }}</dd>
                    <dt>{{ t('adminTeacherVerification.status') }}</dt>
                    <dd>{{ formatStatus(selectedApplicant.teacherStatus) }}</dd>
                    <dt>{{ t('adminTeacherVerification.submitted') }}</dt>
                    <dd>{{ selectedApplicant.appliedLabel }}</dd>
                    <dt>{{ t('adminTeacherVerification.teachingCategories') }}</dt>
                    <dd>{{ selectedApplicant.categoriesLabel }}</dd>
                  </dl>
                  <div class="admin-note-block">
                    <span>{{ t('adminTeacherVerification.shortIntroduction') }}</span>
                    <p>{{ selectedApplicant.shortIntroductionLabel }}</p>
                    <p>{{ selectedApplicant.bioLabel }}</p>
                  </div>
                </section>

                <section class="admin-verification-section">
                  <h3 class="card-title">{{ t('adminTeacherVerification.reviewHistory') }}</h3>
                  <ul v-if="reviewHistory.length" class="list-divided admin-feed">
                    <li v-for="entry in reviewHistory" :key="entry.id" class="admin-feed-item">
                      <div class="admin-feed-copy">
                        <span class="admin-feed-title">{{ entry.title }}</span>
                        <span class="admin-feed-text">{{ entry.copy }}</span>
                        <span class="admin-feed-meta">{{ entry.time }}</span>
                      </div>
                    </li>
                  </ul>
                  <p v-else class="field-hint">{{ t('adminTeacherVerification.noHistory') }}</p>
                </section>

                <section class="admin-verification-section">
                  <h3 class="card-title">{{ t('adminTeacherVerification.recentAudit') }}</h3>
                  <ul v-if="auditTrail.length" class="list-divided admin-feed">
                    <li v-for="entry in auditTrail" :key="entry.title + entry.time" class="admin-feed-item">
                      <div class="admin-feed-copy">
                        <span class="admin-feed-title">{{ entry.title }}</span>
                        <span class="admin-feed-text">{{ entry.copy }}</span>
                        <span class="admin-feed-meta">{{ entry.time }}</span>
                      </div>
                    </li>
                  </ul>
                  <p v-else class="field-hint">{{ t('adminTeacherVerification.noAudit') }}</p>
                </section>
              </template>

              <section v-else-if="activeDetailTab === 'documents'" class="admin-verification-section">
                <h3 class="card-title">{{ t('adminTeacherVerification.verificationDocuments') }}</h3>
                <ul v-if="reviewDocuments.length" class="list-divided admin-feed">
                  <li v-for="document in reviewDocuments" :key="document.id" class="admin-feed-item">
                    <span class="admin-icon-badge is-sm admin-tone-info" aria-hidden="true">
                      <IconGlyph name="file" :size="16" />
                    </span>
                    <div class="admin-feed-copy">
                      <span class="admin-feed-title">{{ document.title }}</span>
                      <span class="admin-feed-text">{{ document.fileName }}</span>
                      <span class="admin-feed-meta">{{ document.meta }}</span>
                    </div>
                    <div class="admin-feed-aside">
                      <StatusPill :label="document.status" :tone="document.tone" />
                      <button
                        class="btn btn-secondary btn-sm"
                        type="button"
                        :disabled="detailUsingFallback || downloadingDocumentId === document.id"
                        :aria-busy="downloadingDocumentId === document.id"
                        @click="downloadTeacherDocument(document.id, document.fileName)"
                      >
                        <IconGlyph name="download" :size="14" />
                        <span>{{ getDocumentDownloadLabel(document.id) }}</span>
                      </button>
                    </div>
                  </li>
                </ul>
                <StatePanel
                  v-else
                  compact
                  icon="file"
                  :title="t('adminTeacherVerification.noDocuments')"
                  :message="t('adminTeacherVerification.noDocumentsHint')"
                />
              </section>

              <section v-else class="admin-verification-section">
                <h3 class="card-title">{{ t('adminTeacherVerification.decisionNotes') }}</h3>
                <div class="admin-note-block">
                  <span>{{ t('adminTeacherVerification.currentRejection') }}</span>
                  <p>{{ selectedApplicant.rejectionReasonLabel }}</p>
                </div>

                <div class="field">
                  <label for="admin-verification-approval-note">{{ t('adminTeacherVerification.approvalNote') }}</label>
                  <textarea
                    id="admin-verification-approval-note"
                    v-model="approvalNote"
                    class="textarea"
                    maxlength="1000"
                    :placeholder="t('adminTeacherVerification.approvalPlaceholder')"
                    :disabled="verificationDecisionReadOnly"
                  />
                </div>

                <div class="field">
                  <label for="admin-verification-rejection-reason">
                    {{ t('adminTeacherVerification.rejectionReason') }}
                  </label>
                  <textarea
                    id="admin-verification-rejection-reason"
                    v-model="rejectionReason"
                    class="textarea"
                    maxlength="1000"
                    aria-describedby="admin-verification-rejection-hint"
                    :placeholder="t('adminTeacherVerification.rejectionPlaceholder')"
                    :disabled="verificationDecisionReadOnly"
                  />
                  <p id="admin-verification-rejection-hint" class="field-hint">
                    {{ t('adminTeacherVerification.rejectionRequired') }}
                  </p>
                </div>
              </section>
            </div>

            <div class="card-footer">
              <button
                v-if="activeDetailTab !== 'notes'"
                class="btn btn-ghost"
                type="button"
                @click="showDetailTab('notes')"
              >
                {{ t('adminTeacherVerification.openDecision') }}
              </button>
              <button
                class="btn btn-danger"
                type="button"
                :disabled="!canDecide"
                :aria-busy="submittingReject"
                @click="rejectTeacher"
              >
                {{ rejectButtonLabel }}
              </button>
              <button
                class="btn btn-primary"
                type="button"
                :disabled="!canDecide"
                :aria-busy="submittingApprove"
                @click="approveTeacher"
              >
                {{ approveButtonLabel }}
              </button>
            </div>
          </template>

          <StatePanel
            v-else
            compact
            icon="verification"
            :title="t('adminTeacherVerification.chooseTeacher')"
            :message="t('adminTeacherVerification.chooseTeacherHint')"
          />
        </article>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminApi } from '@/api/services'
import AdminKpiTile from '@/components/admin/AdminKpiTile.vue'
import type { AdminTone } from '@/components/admin/adminTone'
import IconGlyph from '@/components/common/IconGlyph.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import type { AvatarTone } from '@/components/common/avatarTone'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminTeacherDetail, adminTeachers, teacherApplicationsPending } from '@/demo/data'
import { adminTeacherVerificationMessages } from '@/plugins/adminTeacherVerificationMessages'
import type { AdminTeacherDetailResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDate, formatDateTime, formatNumber, getDisplayLocale, toFiniteNumber } from '@/utils/format'
import { canDecideVerification, nextSelectedTeacherId } from '@/utils/teacherVerification'

const { t, locale } = useI18n({
  messages: {
    en: { adminTeacherVerification: adminTeacherVerificationMessages.en },
    ms: { adminTeacherVerification: adminTeacherVerificationMessages.ms },
    'zh-CN': { adminTeacherVerification: adminTeacherVerificationMessages['zh-CN'] },
  },
})

/** The queue is searched, sorted and paged client-side, so load the largest page the API serves (100). */
const PENDING_QUEUE_SIZE = 100

const pendingResource = useResource(
  () => adminApi.pendingTeachers({ size: PENDING_QUEUE_SIZE }),
  teacherApplicationsPending,
)
/** Exact approved / rejected totals from the server (`totalItems`), not a count over one page of teachers. */
function teacherStatusCountResource(status: 'APPROVED' | 'REJECTED') {
  return useResource(
    () => adminApi.teachers({ status, size: 1 }),
    () => {
      const items = adminTeachers.items.filter((item) => item.teacherStatus === status)
      return { ...adminTeachers, items, totalItems: items.length, totalPages: 1 }
    },
  )
}
const approvedTeachersResource = teacherStatusCountResource('APPROVED')
const rejectedTeachersResource = teacherStatusCountResource('REJECTED')
// A5 #2: the detail always belongs to the selected applicant (no hard-coded demo id).
const detailResource = useResource(
  () => adminApi.teacherDetail(selectedTeacherId.value),
  // Preview: replaced by the selected applicant's preview detail in selectTeacher().
  adminTeacherDetail,
  false,
)
const detailLoading = ref(false)

const queueLoading = computed(
  () =>
    pendingResource.loading.value || approvedTeachersResource.loading.value || rejectedTeachersResource.loading.value,
)
const queueError = computed(
  () => pendingResource.error.value || approvedTeachersResource.error.value || rejectedTeachersResource.error.value,
)
const detailUsingFallback = computed(() => detailResource.usingFallback.value)
const verificationReadOnly = computed(
  () =>
    pendingResource.usingFallback.value ||
    approvedTeachersResource.usingFallback.value ||
    rejectedTeachersResource.usingFallback.value ||
    detailResource.usingFallback.value,
)
const canDecide = computed(() =>
  canDecideVerification({
    selectedTeacherId: selectedTeacherId.value,
    loadedDetailTeacherId: detailResource.data.value?.teacherId ?? null,
    detailLoading: detailLoading.value,
    readOnly: verificationReadOnly.value,
    submitting: submittingApprove.value || submittingReject.value,
  }),
)
const verificationDecisionReadOnly = computed(() => !canDecide.value)

const approvalNote = ref('')
const rejectionReason = ref('')
const submittingApprove = ref(false)
const submittingReject = ref(false)
const downloadingDocumentId = ref<number | null>(null)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const selectedTeacherId = ref(0)
const searchTerm = ref('')
const currentPage = ref(1)
const pageSize = 5

type ApplicantTone = 'success' | 'warning'
type DetailTab = 'overview' | 'documents' | 'notes'
type SortMode = 'newest' | 'name'

const avatarTones: readonly AvatarTone[] = ['primary', 'info', 'success', 'warning']
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
  avatarTone: AvatarTone
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
    summary: item.bioSummary?.trim() || t('adminTeacherVerification.noSummary'),
    documentCount: item.verificationDocumentCount,
    documentCountLabel: t('adminTeacherVerification.documentCount', {
      count: formatNumber(item.verificationDocumentCount),
    }),
    categoriesLabel: t('adminTeacherVerification.detailCategories'),
    shortIntroductionLabel: item.bioSummary?.trim() || t('adminTeacherVerification.noIntroduction'),
    bioLabel: t('adminTeacherVerification.openFullProfile'),
    rejectionReasonLabel: t('adminTeacherVerification.noRejection'),
    avatarTone: avatarTones[index % avatarTones.length],
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
    const nextId = nextSelectedTeacherId(
      items.map((item) => item.teacherId),
      selectedTeacherId.value,
    )
    const detailId = detailResource.data.value?.teacherId ?? null
    if (nextId !== selectedTeacherId.value || (nextId > 0 && detailId !== nextId && !detailLoading.value)) {
      // Load the auto-selected applicant's documents and history before any decision is possible.
      void selectTeacher(nextId)
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
      : t('adminTeacherVerification.noCategories'),
    shortIntroductionLabel:
      selectedDetail.value.shortIntroduction?.trim() || t('adminTeacherVerification.noIntroduction'),
    bioLabel: selectedDetail.value.bio?.trim() || t('adminTeacherVerification.noBio'),
    documentCount: selectedDetail.value.verificationDocuments.length,
    documentCountLabel: t('adminTeacherVerification.documentCount', {
      count: formatNumber(selectedDetail.value.verificationDocuments.length),
    }),
    rejectionReasonLabel: selectedDetail.value.rejectionReason?.trim() || t('adminTeacherVerification.noRejection'),
  }
})

const reviewDocuments = computed<ApplicantDocument[]>(() =>
  (selectedDetail.value?.verificationDocuments ?? []).map((document) => ({
    id: document.id,
    title: document.description || document.documentType,
    fileName: document.originalFileName,
    status: document.currentVersion
      ? t('adminTeacherVerification.currentVersion')
      : t('adminTeacherVerification.olderVersion'),
    tone: document.currentVersion ? 'success' : 'warning',
    meta: `${document.contentType} • ${formatByteSize(document.fileSize)} • ${formatDateTime(document.submittedAt)}`,
  })),
)

const reviewHistory = computed<ApplicantAudit[]>(() =>
  (selectedDetail.value?.verificationReviewHistory ?? []).map((entry) => ({
    id: `review-${entry.id}`,
    title: formatStatus(entry.decisionStatus),
    copy: entry.note?.trim() || t('adminTeacherVerification.noReviewerNote'),
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

const statCards = computed<Array<{ label: string; value: string; copy: string; icon: string; tone: AdminTone }>>(() => [
  {
    label: t('adminTeacherVerification.pendingReview'),
    value: formatNumber(filteredApplicants.value.length),
    copy: t('adminTeacherVerification.pendingQueue'),
    icon: 'verification',
    tone: 'warning',
  },
  {
    label: t('adminTeacherVerification.approvedTeachers'),
    value: formatNumber(approvedTeachersResource.data.value?.totalItems ?? 0),
    copy: t('adminTeacherVerification.teacherCount'),
    icon: 'status',
    tone: 'success',
  },
  {
    label: t('adminTeacherVerification.rejectedTeachers'),
    value: formatNumber(rejectedTeachersResource.data.value?.totalItems ?? 0),
    copy: t('adminTeacherVerification.teacherCount'),
    icon: 'close',
    tone: 'danger',
  },
  {
    label: t('adminTeacherVerification.documentsQueue'),
    value: formatNumber(filteredApplicants.value.reduce((sum, item) => sum + item.documentCount, 0)),
    copy: t('adminTeacherVerification.pendingDocumentCount'),
    icon: 'file',
    tone: 'primary',
  },
])

const todayLabel = computed(() =>
  new Intl.DateTimeFormat(getDisplayLocale(locale.value), {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)
const sortModeLabel = computed(() =>
  sortMode.value === 'newest' ? t('adminTeacherVerification.newestFirst') : t('adminTeacherVerification.alphabetical'),
)
// Queue load errors are shown (with a retry) by the ResourceSection, so only detail and action errors reach the notice.
const noticeError = computed(() => actionError.value || detailResource.error.value)
const approveButtonLabel = computed(() =>
  submittingApprove.value ? t('adminTeacherVerification.approving') : t('adminTeacherVerification.approve'),
)
const rejectButtonLabel = computed(() =>
  submittingReject.value ? t('adminTeacherVerification.rejecting') : t('adminTeacherVerification.reject'),
)
const paginationCountLabel = computed(() => {
  if (!filteredApplicants.value.length) {
    return '0-0'
  }

  const start = (currentPage.value - 1) * pageSize + 1
  const end = Math.min(filteredApplicants.value.length, currentPage.value * pageSize)
  return `${start}-${end}`
})

function cycleSortMode(): void {
  sortMode.value = sortMode.value === 'newest' ? 'name' : 'newest'
}

function goToPage(page: number): void {
  currentPage.value = page
}

const detailTabs = computed<Array<{ value: DetailTab; label: string }>>(() => [
  { value: 'overview', label: t('adminTeacherVerification.overview') },
  { value: 'documents', label: t('adminTeacherVerification.documents') },
  { value: 'notes', label: t('adminTeacherVerification.decision') },
])

function showDetailTab(tab: DetailTab): void {
  activeDetailTab.value = tab
}

/** Arrow-key navigation between the detail tabs (WAI-ARIA tabs pattern): select the neighbour and focus it. */
function moveDetailTab(step: 1 | -1): void {
  const tabs = detailTabs.value
  const index = tabs.findIndex((tab) => tab.value === activeDetailTab.value)
  const next = tabs[(index + step + tabs.length) % tabs.length].value
  showDetailTab(next)
  void nextTick(() => document.getElementById(`admin-verification-tab-${next}`)?.focus())
}

function getDocumentDownloadLabel(documentId: number): string {
  return downloadingDocumentId.value === documentId
    ? t('adminTeacherVerification.downloading')
    : t('adminTeacherVerification.download')
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

function reloadQueue(): void {
  void pendingResource.reload()
  void approvedTeachersResource.reload()
  void rejectedTeachersResource.reload()
}

async function selectTeacher(id: number): Promise<void> {
  actionError.value = null
  actionSuccess.value = null
  downloadingDocumentId.value = null
  approvalNote.value = ''
  rejectionReason.value = ''
  selectedTeacherId.value = id
  activeDetailTab.value = 'overview'
  detailResource.data.value = null
  if (!id) return

  if (verificationReadOnly.value) {
    detailResource.data.value = previewTeacherDetail(id)
    return
  }

  detailLoading.value = true
  try {
    const detail = await adminApi.teacherDetail(id)
    if (selectedTeacherId.value === id) detailResource.data.value = detail
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminTeacherVerification.loadDetailError'))
    detailResource.data.value = null
  } finally {
    detailLoading.value = false
  }
}

async function downloadTeacherDocument(documentId: number, fileName: string): Promise<void> {
  if (downloadingDocumentId.value) return
  if (detailUsingFallback.value) {
    actionSuccess.value = null
    actionError.value = t('adminTeacherVerification.previewDownload')
    return
  }

  actionError.value = null
  actionSuccess.value = null
  downloadingDocumentId.value = documentId

  try {
    const download = await adminApi.downloadTeacherDocument(documentId)
    saveBlobDownload(download, fileName || `teacher-document-${documentId}`)
    actionSuccess.value = t('adminTeacherVerification.downloaded')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminTeacherVerification.downloadError'))
  } finally {
    downloadingDocumentId.value = null
  }
}

async function approveTeacher(): Promise<void> {
  if (submittingApprove.value) return
  if (!selectedApplicant.value) return
  if (verificationReadOnly.value) {
    actionSuccess.value = null
    actionError.value = t('adminTeacherVerification.previewApprove')
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingApprove.value = true

  try {
    await adminApi.approveTeacher(selectedApplicant.value.teacherId, approvalNote.value.trim() || undefined)
    await pendingResource.reload()
    await Promise.all([approvedTeachersResource.reload(), rejectedTeachersResource.reload()])
    detailResource.data.value = null
    approvalNote.value = ''
    rejectionReason.value = ''
    actionSuccess.value = t('adminTeacherVerification.approved')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminTeacherVerification.approveError'))
  } finally {
    submittingApprove.value = false
  }
}

async function rejectTeacher(): Promise<void> {
  if (submittingReject.value) return
  if (!selectedApplicant.value) return
  if (verificationReadOnly.value) {
    actionSuccess.value = null
    actionError.value = t('adminTeacherVerification.previewReject')
    return
  }

  const normalizedReason = rejectionReason.value.trim()
  if (!normalizedReason) {
    actionSuccess.value = null
    actionError.value = t('adminTeacherVerification.rejectionRequired')
    return
  }
  if (
    !window.confirm(
      t('adminTeacherVerification.confirmReject', {
        name: selectedApplicant.value.fullName,
        reason: normalizedReason,
      }),
    )
  ) {
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingReject.value = true

  try {
    await adminApi.rejectTeacher(selectedApplicant.value.teacherId, normalizedReason)
    await pendingResource.reload()
    await Promise.all([approvedTeachersResource.reload(), rejectedTeachersResource.reload()])
    detailResource.data.value = null
    approvalNote.value = ''
    rejectionReason.value = ''
    actionSuccess.value = t('adminTeacherVerification.rejected')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminTeacherVerification.rejectError'))
  } finally {
    submittingReject.value = false
  }
}

/** Localised "1,234 bytes" / "1,234 bait" / "1,234字节" without a hand-written unit string. */
function formatByteSize(size: number): string {
  return new Intl.NumberFormat(getDisplayLocale(locale.value), {
    style: 'unit',
    unit: 'byte',
    unitDisplay: 'long',
  }).format(toFiniteNumber(size))
}

function formatStatus(status: string): string {
  const keys: Record<string, string> = {
    PENDING: 'statusPending',
    APPROVED: 'statusApproved',
    REJECTED: 'statusRejected',
    UNDER_REVIEW: 'statusUnderReview',
  }
  const key = keys[status.toUpperCase()]
  if (key) return t(`adminTeacherVerification.${key}`)
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}
</script>

<style scoped>
.admin-verification-tabpanel {
  display: grid;
  gap: var(--space-6);
  min-width: 0;
}

.admin-verification-section {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}
</style>
