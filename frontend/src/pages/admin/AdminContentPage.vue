<template>
  <div class="page-stack admin-content-page">
    <PageHeaderBlock :title="t('adminContent.title')" :description="t('adminContent.description')" icon="content">
      <template #actions>
        <RouterLink class="btn btn-secondary" :to="{ name: 'admin-content-review' }">
          {{ t('adminContent.openReviewQueue') }}
        </RouterLink>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="contentResource.usingFallback.value" :error="pageError" :success="actionSuccess" />
    <DataNotice :info="t('adminContent.postPublishNotice')" />

    <section class="admin-stack" aria-labelledby="admin-content-quality-title">
      <div class="admin-section-head">
        <h2 id="admin-content-quality-title" class="section-title">{{ t('adminContent.quality') }}</h2>
        <p class="section-description">{{ t('adminContent.heroHint') }}</p>
      </div>
      <div class="admin-kpi-grid admin-kpi-grid--3">
        <AdminKpiTile
          v-for="stat in contentStats"
          :key="stat.label"
          :label="stat.label"
          :value="stat.value"
          :icon="stat.icon"
          :tone="stat.tone"
        />
      </div>
    </section>

    <section class="admin-split">
      <article class="panel admin-panel">
        <div class="card-header">
          <div>
            <h2 class="card-title">{{ t('adminContent.contentList') }}</h2>
            <p class="card-subtitle">{{ t('adminContent.contentListHint') }}</p>
          </div>
        </div>

        <StatePanel
          v-if="contentResource.loading.value && !content.items.length"
          compact
          tone="loading"
          icon="progress"
          :title="t('adminContent.loadingList')"
          :message="t('adminContent.contentListHint')"
        />
        <StatePanel
          v-else-if="contentResource.error.value && !content.items.length"
          compact
          tone="error"
          icon="alert"
          :title="t('adminContent.loadErrorTitle')"
          :message="contentResource.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="contentResource.reload">
              {{ t('adminContent.retry') }}
            </button>
          </template>
        </StatePanel>
        <ul v-else-if="content.items.length" class="admin-queue">
          <li
            v-for="item in content.items"
            :key="`${item.contentType}-${item.id}`"
            class="admin-content-row"
            :class="{ 'is-selected': isSelected(item.contentType, item.id) }"
          >
            <span class="admin-icon-badge is-sm admin-tone-primary" aria-hidden="true">
              <IconGlyph name="content" :size="16" />
            </span>
            <div class="admin-queue-copy">
              <strong>{{ item.title }}</strong>
              <span class="admin-feed-text">{{ item.description || t('adminContent.noDescription') }}</span>
              <div class="admin-meta-row">
                <span class="tag">{{ contentTypeLabel(item.contentType) }}</span>
                <span class="tag">{{ item.teacher.fullName }}</span>
                <ContentStatusBadge :status="item.contentStatus" :rejection-reason="item.rejectionReason" />
              </div>
            </div>
            <div class="admin-content-actions">
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                :aria-pressed="isSelected(item.contentType, item.id)"
                @click="loadDetail(item.contentType, item.id)"
              >
                {{ t('adminContent.review') }}
              </button>
              <RouterLink
                v-if="item.contentStatus === 'SUBMITTED'"
                class="btn btn-ghost btn-sm"
                :to="{ name: 'admin-content-review', query: { type: item.contentType, id: String(item.id) } }"
              >
                {{ t('adminContent.reviewSubmission') }}
              </RouterLink>
              <button
                v-else-if="item.contentStatus !== 'ARCHIVED'"
                class="btn btn-ghost btn-sm"
                type="button"
                :disabled="contentReadOnly"
                @click="archiveContent(item.contentType, item.id, item.title)"
              >
                <IconGlyph name="archive" :size="14" />
                {{ t('adminContent.archive') }}
              </button>
            </div>
          </li>
        </ul>
        <StatePanel
          v-else
          compact
          icon="content"
          :title="t('adminContent.emptyTitle')"
          :message="t('adminContent.emptyHint')"
        />
      </article>

      <div class="admin-stack">
        <article class="panel admin-panel">
          <div class="card-header">
            <div>
              <h2 class="card-title">{{ t('adminContent.selectedDetail') }}</h2>
              <p class="card-subtitle">{{ t('adminContent.selectedDetailHint') }}</p>
            </div>
          </div>

          <StatePanel
            v-if="detailResource.loading.value"
            compact
            tone="loading"
            icon="progress"
            :title="t('adminContent.loadingDetail')"
            :message="t('adminContent.selectedDetailHint')"
          />
          <div v-else-if="detail" class="admin-content-detail">
            <h3 class="admin-profile-name">{{ detail.title }}</h3>
            <p class="admin-feed-text">{{ detail.description || t('adminContent.noDescription') }}</p>
            <div class="admin-meta-row">
              <span class="tag">{{ contentTypeLabel(detail.contentType) }}</span>
              <span class="tag">{{ detail.teacher.fullName }}</span>
              <span class="tag">{{ detail.level.name }}</span>
              <ContentStatusBadge :status="detail.contentStatus" :rejection-reason="detail.rejectionReason" />
            </div>
            <div class="admin-note-block">
              <p>{{ detail.basicContent || t('adminContent.noLessonBody') }}</p>
            </div>
          </div>
          <StatePanel
            v-else
            compact
            icon="content"
            :title="t('adminContent.chooseContent')"
            :message="t('adminContent.chooseContentHint')"
          />
        </article>

        <form class="panel admin-panel admin-content-moderation" @submit.prevent="moderate('request-revision')">
          <div class="card-header">
            <div>
              <h2 class="card-title">{{ t('adminContent.moderationAction') }}</h2>
              <p class="card-subtitle">{{ t('adminContent.moderationActionHint') }}</p>
            </div>
          </div>

          <div class="field">
            <label for="admin-content-moderation-reason">{{ t('adminContent.reason') }}</label>
            <textarea
              id="admin-content-moderation-reason"
              v-model="moderationReason"
              class="textarea"
              maxlength="1000"
              aria-describedby="admin-content-moderation-hint"
              :placeholder="t('adminContent.reasonPlaceholder')"
              :disabled="contentReadOnly || detailReadOnly || submittingModeration"
            />
            <p id="admin-content-moderation-hint" class="field-hint">{{ t('adminContent.moderationStatesHint') }}</p>
          </div>

          <div class="form-actions">
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly || detail?.contentStatus === 'ARCHIVED'"
              @click="moderate('archive')"
            >
              {{ t('adminContent.archiveHidden') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly || detail?.contentStatus !== 'ARCHIVED'"
              @click="moderate('restore')"
            >
              {{ t('adminContent.restorePublished') }}
            </button>
            <button
              class="btn btn-primary"
              type="submit"
              :disabled="contentReadOnly || detailReadOnly || submittingModeration"
              :aria-busy="submittingModeration"
            >
              {{ submittingModeration ? t('adminContent.submitting') : t('adminContent.returnToTeacher') }}
            </button>
          </div>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { adminApi } from '@/api/services'
import AdminKpiTile from '@/components/admin/AdminKpiTile.vue'
import type { AdminTone } from '@/components/admin/adminTone'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ContentStatusBadge from '@/components/content/ContentStatusBadge.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminContentDetail, adminContentList } from '@/demo/data'
import { adminContentMessages } from '@/plugins/adminContentMessages'
import type { ContentSubmissionDetailResponse, ManagedContentType } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

const { t } = useI18n({
  messages: {
    en: { adminContent: adminContentMessages.en },
    ms: { adminContent: adminContentMessages.ms },
    'zh-CN': { adminContent: adminContentMessages['zh-CN'] },
  },
})

/** The list has no pager, so request the largest page the API serves instead of its default of 10. */
const CONTENT_LIST_SIZE = 100

const contentResource = useResource(() => adminApi.content({ size: CONTENT_LIST_SIZE }), adminContentList)
// A5 #3: the detail is loaded for the item the admin selects (loadDetail); no hard-coded demo id is requested.
const selectedContent = ref<{ contentType: ManagedContentType; id: number } | null>(null)
const detailResource = useResource<ContentSubmissionDetailResponse | null>(
  () =>
    selectedContent.value
      ? adminApi.contentDetail(selectedContent.value.contentType, selectedContent.value.id)
      : Promise.resolve(null),
  adminContentDetail,
  false,
)

const emptyContentList = {
  items: [],
  page: 0,
  size: 0,
  totalItems: 0,
  totalPages: 0,
}

const content = computed(
  () => contentResource.data.value ?? (contentResource.usingFallback.value ? adminContentList : emptyContentList),
)
const detail = computed(() => detailResource.data.value)
const contentReadOnly = computed(() => contentResource.usingFallback.value)
const detailReadOnly = computed(() => detailResource.usingFallback.value)
// A list load error with no rows is shown (with a retry) in the list panel; keep it out of the notice then.
const pageError = computed(() => actionError.value || (content.value.items.length ? contentResource.error.value : null))
const contentStats = computed<Array<{ icon: string; label: string; value: string; tone: AdminTone }>>(() => {
  const items = content.value.items
  const teachers = new Set(items.map((item) => item.teacher.fullName))
  const publishedCount = items.reduce((count, item) => (item.contentStatus === 'PUBLISHED' ? count + 1 : count), 0)

  return [
    { icon: 'content', label: t('adminContent.visibleContent'), value: String(items.length), tone: 'primary' },
    {
      icon: 'status',
      label: t('adminContent.published'),
      value: String(publishedCount),
      tone: 'success',
    },
    {
      icon: 'teacher',
      label: t('adminContent.teachers'),
      value: String(teachers.size),
      tone: 'info',
    },
  ]
})
const moderationReason = ref(t('adminContent.defaultReason'))
const submittingModeration = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

function isSelected(contentType: ManagedContentType, id: number): boolean {
  return selectedContent.value?.contentType === contentType && selectedContent.value.id === id
}

function contentTypeLabel(contentType: ManagedContentType): string {
  const keys: Record<ManagedContentType, string> = {
    PACKAGE: 'typeCourse',
    MODULE: 'typeModule',
    LESSON: 'typeLesson',
  }
  return t(`adminContent.${keys[contentType]}`)
}

async function loadDetail(contentType: ManagedContentType, id: number): Promise<void> {
  actionError.value = null
  actionSuccess.value = null
  selectedContent.value = { contentType, id }

  if (contentReadOnly.value) {
    const previewDetail = previewContentDetail(contentType, id)
    detailResource.data.value = previewDetail
    if (previewDetail) {
      actionSuccess.value = t('adminContent.previewLoaded')
    } else {
      actionError.value = t('adminContent.previewUnavailable')
    }
    return
  }

  const requested = selectedContent.value
  try {
    const loaded = await adminApi.contentDetail(contentType, id)
    // Ignore a slow response for an item the admin has already moved away from.
    if (selectedContent.value !== requested) return
    detailResource.data.value = loaded
    actionSuccess.value = t('adminContent.detailLoaded')
  } catch (reason) {
    if (selectedContent.value !== requested) return
    actionError.value = resolveApiError(reason, t('adminContent.detailError'))
    detailResource.data.value = null
  }
}

function previewContentDetail(contentType: ManagedContentType, id: number): ContentSubmissionDetailResponse | null {
  if (adminContentDetail.contentType === contentType && adminContentDetail.id === id) {
    return adminContentDetail
  }

  const listItem = adminContentList.items.find((item) => item.contentType === contentType && item.id === id)
  if (!listItem) {
    return null
  }

  return {
    ...listItem,
    basicContent: listItem.description ?? null,
    moduleId: null,
    moduleTitle: null,
    coverImageUrl: null,
    packageItems: [],
  }
}

async function archiveContent(contentType: string, id: number, title: string): Promise<void> {
  if (!ensureLiveContent(t('adminContent.archivePreviewError'))) return
  if (!window.confirm(t('adminContent.confirmArchive', { title }))) return
  actionError.value = null
  actionSuccess.value = null
  try {
    await adminApi.archiveContent(contentType, id, t('adminContent.archiveReason'))
    await contentResource.reload()
    actionSuccess.value = t('adminContent.archived')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminContent.archiveError'))
  }
}

async function moderate(action: 'archive' | 'request-revision' | 'restore'): Promise<void> {
  if (submittingModeration.value) return
  if (!detail.value) return
  if (!ensureLiveContent(t('adminContent.moderationPreviewError'))) return
  if (!moderationReason.value.trim()) {
    actionSuccess.value = null
    actionError.value = t('adminContent.reasonRequired')
    return
  }
  const confirmKey = {
    archive: 'confirmModerationArchive',
    'request-revision': 'confirmReturnToTeacher',
    restore: 'confirmRestore',
  }[action]
  if (!window.confirm(t(`adminContent.${confirmKey}`, { title: detail.value.title }))) return
  actionError.value = null
  actionSuccess.value = null
  submittingModeration.value = true
  try {
    await adminApi.moderationAction(action, {
      targetType: detail.value.contentType,
      targetId: detail.value.id,
      reason: moderationReason.value.trim(),
    })
    const moderatedType = detail.value.contentType
    const moderatedId = detail.value.id
    await contentResource.reload()
    detailResource.data.value = await adminApi.contentDetail(moderatedType, moderatedId)
    actionSuccess.value = t('adminContent.moderated')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminContent.moderationError'))
  } finally {
    submittingModeration.value = false
  }
}

function ensureLiveContent(message: string): boolean {
  if (!contentReadOnly.value && !detailReadOnly.value) return true
  actionSuccess.value = null
  actionError.value = message
  return false
}
</script>

<style scoped>
.admin-content-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard);
}

.admin-content-row.is-selected {
  border-color: var(--color-primary-500);
  background: var(--surface-hover);
  box-shadow: inset 3px 0 0 var(--color-primary-500);
}

.admin-content-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.admin-content-detail {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.admin-content-detail p {
  margin: 0;
}

.admin-content-moderation .form-actions > .btn {
  flex: 1 1 auto;
}

@media (max-width: 640px) {
  .admin-content-row {
    flex-wrap: wrap;
  }

  .admin-content-actions {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
