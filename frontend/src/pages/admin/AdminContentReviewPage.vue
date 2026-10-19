<template>
  <div class="page-stack admin-content-review-page">
    <PageHeaderBlock
      :title="t('adminContentReview.title')"
      :description="t('adminContentReview.description')"
      icon="clip"
    />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <form class="panel admin-filter-bar" @submit.prevent="applyFilters">
      <div class="field">
        <label for="admin-review-filter-type">{{ t('adminContentReview.filterType') }}</label>
        <select id="admin-review-filter-type" v-model="filters.type" class="select" data-testid="review-filter-type">
          <option value="">{{ t('adminContentReview.allTypes') }}</option>
          <option v-for="type in REVIEW_TYPES" :key="type" :value="type">{{ reviewTypeLabel(type) }}</option>
        </select>
      </div>
      <div class="field">
        <label for="admin-review-filter-status">{{ t('adminContentReview.filterStatus') }}</label>
        <select
          id="admin-review-filter-status"
          v-model="filters.status"
          class="select"
          data-testid="review-filter-status"
        >
          <option v-for="status in STATUS_FILTERS" :key="status" :value="status">
            {{ statusFilterLabel(status) }}
          </option>
        </select>
      </div>
      <div class="field admin-content-review-keyword">
        <label for="admin-review-filter-keyword">{{ t('adminContentReview.keyword') }}</label>
        <div class="input-with-icon">
          <IconGlyph class="input-icon" name="search" :size="16" />
          <input
            id="admin-review-filter-keyword"
            v-model="filters.keyword"
            class="input"
            type="search"
            maxlength="100"
            :placeholder="t('adminContentReview.keywordPlaceholder')"
          />
        </div>
      </div>
      <div class="admin-filter-actions">
        <button class="btn btn-ghost" type="button" :disabled="queueResource.loading.value" @click="resetFilters">
          {{ t('adminContentReview.reset') }}
        </button>
        <button
          class="btn btn-primary"
          type="submit"
          :disabled="queueResource.loading.value"
          :aria-busy="queueResource.loading.value"
        >
          {{ queueResource.loading.value ? t('adminContentReview.loading') : t('adminContentReview.apply') }}
        </button>
      </div>
    </form>

    <section class="admin-split admin-split--even">
      <article class="panel admin-panel">
        <div class="card-header">
          <h2 class="card-title">{{ t('adminContentReview.queue') }}</h2>
          <span class="tag">{{ t('adminContentReview.queueTotal', { count: formatNumber(queue.totalItems) }) }}</span>
        </div>

        <ul v-if="queue.items.length" class="admin-queue">
          <li v-for="item in queue.items" :key="`${item.type}-${item.id}`">
            <button
              type="button"
              class="admin-queue-item"
              :class="{ 'is-selected': isSelected(item) }"
              :aria-pressed="isSelected(item)"
              data-testid="review-queue-row"
              @click="selectItem(item)"
            >
              <span class="admin-queue-copy">
                <span class="admin-queue-title">
                  <strong>{{ item.title }}</strong>
                  <ContentStatusBadge :status="item.contentStatus" :show-reason="false" />
                </span>
                <span class="admin-meta-row">
                  <span class="tag">{{ reviewTypeLabel(item.type) }}</span>
                  <LearningStageTag :stage="item.learningStage" />
                  <span class="admin-feed-text">{{ item.ownerName || t('adminContentReview.ownerUnknown') }}</span>
                </span>
                <span v-if="item.parentTitle" class="admin-feed-meta"
                  >{{ t('adminContentReview.parent') }}: {{ item.parentTitle }}</span
                >
                <span class="admin-feed-meta">{{
                  t('adminContentReview.submittedOn', { date: formatDateTime(item.updatedAt) })
                }}</span>
              </span>
            </button>
          </li>
        </ul>

        <StatePanel
          v-else-if="queueResource.loading.value"
          compact
          tone="loading"
          icon="progress"
          :title="t('adminContentReview.loading')"
          :message="t('adminContentReview.queue')"
        />
        <StatePanel
          v-else-if="queueResource.error.value"
          compact
          tone="error"
          icon="alert"
          :title="t('adminContentReview.loadErrorTitle')"
          :message="queueResource.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="queueResource.reload">
              {{ t('adminContentReview.retry') }}
            </button>
          </template>
        </StatePanel>
        <StatePanel
          v-else
          compact
          icon="status"
          :title="t('adminContentReview.emptyTitle')"
          :message="t('adminContentReview.emptyMessage')"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="resetFilters">
              {{ t('adminContentReview.reset') }}
            </button>
          </template>
        </StatePanel>

        <PaginationBar
          v-if="queue.totalPages > 1"
          :info="t('adminContentReview.pageOf', { page: currentPage, total: queue.totalPages })"
          :previous-label="t('adminContentReview.previous')"
          :next-label="t('adminContentReview.next')"
          :previous-disabled="currentPage <= 1"
          :next-disabled="currentPage >= queue.totalPages"
          @previous="goToPage(currentPage - 1)"
          @next="goToPage(currentPage + 1)"
        />
      </article>

      <article class="panel admin-panel">
        <h2 class="card-title">{{ t('adminContentReview.detailTitle') }}</h2>

        <StatePanel
          v-if="loadingDetail"
          compact
          tone="loading"
          icon="progress"
          :title="t('adminContentReview.loadingDetail')"
          :message="t('adminContentReview.chooseItemHint')"
        />

        <template v-else-if="detail">
          <div class="admin-content-review-head">
            <h3 class="admin-profile-name">{{ detail.item.title }}</h3>
            <div class="admin-meta-row">
              <span class="tag">{{ reviewTypeLabel(detail.item.type) }}</span>
              <ContentStatusBadge :status="detail.item.contentStatus" :rejection-reason="detail.item.rejectionReason" />
              <LearningStageTag :stage="detail.item.learningStage" show-unset />
            </div>
            <span class="admin-feed-text">
              {{ t('adminContentReview.owner') }}: {{ detail.item.ownerName || t('adminContentReview.ownerUnknown') }}
              <template v-if="detail.item.parentTitle">
                · {{ t('adminContentReview.parent') }}: {{ detail.item.parentTitle }}
              </template>
            </span>
            <p v-if="ownerNotApproved" class="alert alert-warning" role="note">
              <IconGlyph name="alert" :size="16" />
              <span>{{ t('adminContentReview.ownerNotApproved') }}</span>
            </p>
          </div>

          <ContentReviewDetailView :response="detail" />

          <section
            class="admin-content-review-decision"
            data-testid="review-decision"
            aria-labelledby="admin-review-decision-title"
          >
            <div class="admin-section-head">
              <h3 id="admin-review-decision-title" class="card-title">{{ t('adminContentReview.decision') }}</h3>
              <p class="card-subtitle">{{ t('adminContentReview.decisionHint') }}</p>
            </div>

            <p v-if="previewReadOnly" class="alert alert-warning" role="note">
              <IconGlyph name="alert" :size="16" />
              <span>{{ t('adminContentReview.previewReadOnly') }}</span>
            </p>

            <div v-if="canApprove" class="field">
              <label for="admin-review-approve-note">{{ t('adminContentReview.approveNote') }}</label>
              <input
                id="admin-review-approve-note"
                v-model="approveNote"
                class="input"
                maxlength="1000"
                :placeholder="t('adminContentReview.approveNotePlaceholder')"
              />
            </div>

            <div v-if="canReject" class="field">
              <label for="admin-review-reject-reason">{{ t('adminContentReview.rejectReason') }}</label>
              <textarea
                id="admin-review-reject-reason"
                v-model="rejectReason"
                class="textarea"
                maxlength="1000"
                required
                aria-describedby="admin-review-reject-hint"
                :placeholder="t('adminContentReview.rejectReasonPlaceholder')"
                data-testid="review-reject-reason"
              />
              <p id="admin-review-reject-hint" class="field-hint">
                {{ t('adminContentReview.rejectReasonRequired') }}
              </p>
            </div>

            <div v-if="canApprove || canReject || canArchive || canRestore" class="form-actions">
              <button
                v-if="canArchive"
                class="btn btn-secondary"
                type="button"
                :disabled="acting"
                @click="decide('archive')"
              >
                <IconGlyph name="archive" :size="16" />
                {{ t('adminContentReview.archive') }}
              </button>
              <button
                v-if="canRestore"
                class="btn btn-secondary"
                type="button"
                :disabled="acting"
                @click="decide('restore')"
              >
                {{ t('adminContentReview.restore') }}
              </button>
              <button
                v-if="canReject"
                class="btn btn-danger"
                type="button"
                :disabled="acting || !rejectReason.trim()"
                data-testid="review-reject"
                @click="decide('reject')"
              >
                {{ t('adminContentReview.reject') }}
              </button>
              <button
                v-if="canApprove"
                class="btn btn-primary"
                type="button"
                :disabled="acting"
                :aria-busy="acting"
                data-testid="review-approve"
                @click="decide('approve')"
              >
                {{ acting ? t('adminContentReview.working') : t('adminContentReview.approve') }}
              </button>
            </div>
            <p v-else class="field-hint">
              {{ t('adminContentReview.noActions') }}
            </p>
          </section>
        </template>

        <StatePanel
          v-else
          compact
          icon="clip"
          :title="t('adminContentReview.chooseItem')"
          :message="t('adminContentReview.chooseItemHint')"
        />
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { adminApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ContentReviewDetailView from '@/components/content/ContentReviewDetailView.vue'
import ContentStatusBadge from '@/components/content/ContentStatusBadge.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminContentReviewDetails, adminContentReviewQueue } from '@/demo/data'
import { adminContentReviewMessages } from '@/plugins/adminContentReviewMessages'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  ContentReviewDetailResponse,
  ContentReviewItemResponse,
  ContentStatus,
  PageResponse,
  ReviewContentType,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { contentStatusMessageKey } from '@/utils/contentStatus'
import { formatDateTime, formatNumber } from '@/utils/format'
import {
  REVIEW_CONTENT_TYPES,
  availableReviewActions,
  normalizeReviewContentType,
  reviewContentTypeMessageKey,
  type ReviewAction,
} from '@/utils/contentReview'

type StatusFilter = ContentStatus | 'ALL'

const PAGE_SIZE = 10
const REVIEW_TYPES = REVIEW_CONTENT_TYPES
const STATUS_FILTERS: StatusFilter[] = ['SUBMITTED', 'ALL', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'DRAFT']

const { t } = useI18n({
  messages: {
    en: { adminContentReview: adminContentReviewMessages.en, governance: contentGovernanceMessages.en },
    ms: { adminContentReview: adminContentReviewMessages.ms, governance: contentGovernanceMessages.ms },
    'zh-CN': {
      adminContentReview: adminContentReviewMessages['zh-CN'],
      governance: contentGovernanceMessages['zh-CN'],
    },
  },
})
const route = useRoute()
const auth = useAuthStore()

const initialType = normalizeReviewContentType(route.query.type)
const filters = reactive({
  type: (initialType ?? '') as ReviewContentType | '',
  status: 'SUBMITTED' as StatusFilter,
  keyword: '',
})
const currentPage = ref(1)
const detail = ref<ContentReviewDetailResponse | null>(null)
const loadingDetail = ref(false)
const acting = ref(false)
const approveNote = ref('')
const rejectReason = ref('')
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const emptyQueue: PageResponse<ContentReviewItemResponse> = {
  items: [],
  page: 0,
  size: PAGE_SIZE,
  totalItems: 0,
  totalPages: 0,
}

const queueResource = useResource(
  () =>
    adminApi.contentReviewQueue({
      status: filters.status,
      type: filters.type || undefined,
      keyword: filters.keyword.trim() || undefined,
      page: currentPage.value - 1,
      size: PAGE_SIZE,
    }),
  adminContentReviewQueue,
)

const queue = computed(() => queueResource.data.value ?? emptyQueue)
const usingFallback = computed(() => queueResource.usingFallback.value)
const previewReadOnly = computed(() => usingFallback.value || (auth.isPreviewSession && !auth.isAuthenticated))
// A queue load error with no rows is shown (with a retry) in the queue panel; keep it out of the notice then.
const noticeError = computed(() => actionError.value || (queue.value.items.length ? queueResource.error.value : null))
const actions = computed<ReviewAction[]>(() =>
  detail.value ? availableReviewActions(detail.value.item.contentStatus) : [],
)
const canApprove = computed(() => actions.value.includes('approve'))
const canReject = computed(() => actions.value.includes('reject'))
const canArchive = computed(() => actions.value.includes('archive'))
const canRestore = computed(() => actions.value.includes('restore'))
const ownerNotApproved = computed(
  () => Boolean(detail.value?.item.ownerTeacherStatus) && detail.value?.item.ownerTeacherStatus !== 'APPROVED',
)

watch(
  () => queueResource.loading.value,
  (loading, wasLoading) => {
    if (loading || !wasLoading || detail.value) return
    const requestedId = Number(route.query.id)
    const requestedType = normalizeReviewContentType(route.query.type)
    const match =
      requestedType && Number.isInteger(requestedId)
        ? queue.value.items.find((item) => item.type === requestedType && item.id === requestedId)
        : undefined
    if (match) {
      void selectItem(match)
    } else if (requestedType && Number.isInteger(requestedId) && requestedId > 0 && !previewReadOnly.value) {
      void loadDetail(requestedType, requestedId)
    }
  },
)

function reviewTypeLabel(type: ReviewContentType): string {
  return t(`adminContentReview.${reviewContentTypeMessageKey(type)}`)
}

function statusFilterLabel(status: StatusFilter): string {
  return status === 'ALL' ? t('adminContentReview.statusAll') : t(`governance.${contentStatusMessageKey(status)}`)
}

function isSelected(item: ContentReviewItemResponse): boolean {
  return detail.value?.item.type === item.type && detail.value.item.id === item.id
}

function clearFeedback(): void {
  actionError.value = null
  actionSuccess.value = null
}

function applyFilters(): void {
  currentPage.value = 1
  clearFeedback()
  void queueResource.reload()
}

function resetFilters(): void {
  filters.type = ''
  filters.status = 'SUBMITTED'
  filters.keyword = ''
  applyFilters()
}

function goToPage(page: number): void {
  currentPage.value = page
  void queueResource.reload()
}

async function selectItem(item: ContentReviewItemResponse): Promise<void> {
  clearFeedback()
  if (previewReadOnly.value) {
    detail.value = adminContentReviewDetails[`${item.type}-${item.id}`] ?? null
    if (!detail.value) actionError.value = t('adminContentReview.detailError')
    resetDecisionInputs()
    return
  }
  await loadDetail(item.type, item.id)
}

/** Only the latest detail request may update the panel (rapid clicks on different queue rows). */
let detailRequestSequence = 0

async function loadDetail(type: ReviewContentType, id: number): Promise<void> {
  const sequence = ++detailRequestSequence
  loadingDetail.value = true
  try {
    const loaded = await adminApi.contentReviewDetail(type, id)
    if (sequence !== detailRequestSequence) return
    detail.value = loaded
    resetDecisionInputs()
  } catch (reason) {
    if (sequence !== detailRequestSequence) return
    detail.value = null
    actionError.value = resolveApiError(reason, t('adminContentReview.detailError'))
  } finally {
    if (sequence === detailRequestSequence) loadingDetail.value = false
  }
}

function resetDecisionInputs(): void {
  approveNote.value = ''
  rejectReason.value = ''
}

async function decide(action: ReviewAction): Promise<void> {
  if (!detail.value || acting.value) return
  clearFeedback()
  const { type, id, title } = detail.value.item

  if (previewReadOnly.value) {
    actionError.value = t('adminContentReview.previewReadOnly')
    return
  }

  const reason = rejectReason.value.trim()
  if (action === 'reject' && !reason) {
    actionError.value = t('adminContentReview.rejectReasonRequired')
    return
  }

  const confirmMessage = {
    approve: t('adminContentReview.confirmApprove', { title }),
    reject: t('adminContentReview.confirmReject', { title, reason }),
    archive: t('adminContentReview.confirmArchive', { title }),
    restore: t('adminContentReview.confirmRestore', { title }),
  }[action]
  if (!window.confirm(confirmMessage)) return

  acting.value = true
  try {
    const note = approveNote.value.trim() || undefined
    let updated: ContentReviewItemResponse
    switch (action) {
      case 'approve':
        updated = await adminApi.approveContent(type, id, note)
        break
      case 'reject':
        updated = await adminApi.rejectContent(type, id, reason)
        break
      case 'archive':
        updated = await adminApi.archiveReviewedContent(type, id)
        break
      default:
        updated = await adminApi.restoreContent(type, id)
    }
    // The admin may have opened another item while the request ran; only merge into the decided item.
    if (detail.value?.item.type === type && detail.value.item.id === id) {
      detail.value = { ...detail.value, item: { ...detail.value.item, ...updated } }
    }
    const successKey = { approve: 'approved', reject: 'rejected', archive: 'archived', restore: 'restored' }[action]
    actionSuccess.value = t(`adminContentReview.${successKey}`, { title })
    resetDecisionInputs()
    await queueResource.reload()
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminContentReview.actionError'))
  } finally {
    acting.value = false
  }
}
</script>

<style scoped>
.admin-content-review-keyword {
  grid-column: span 2;
}

.admin-content-review-head {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.admin-content-review-head .alert {
  margin: 0;
}

.admin-content-review-decision {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  padding-top: var(--space-5);
  border-top: 1px solid var(--border-subtle);
}

.admin-content-review-decision .alert {
  margin: 0;
}

@media (max-width: 640px) {
  .admin-content-review-keyword {
    grid-column: 1 / -1;
  }
}
</style>
