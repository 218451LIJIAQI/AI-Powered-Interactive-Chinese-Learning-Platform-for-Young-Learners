<template>
  <div class="page-stack admin-content-page">
    <PageHeaderBlock :title="t('adminContent.title')" :description="t('adminContent.description')" icon="content" />

    <DataNotice :using-fallback="contentResource.usingFallback.value" :error="pageError" :success="actionSuccess" />

    <section class="admin-ops-hero admin-ops-hero--content">
      <div>
        <span class="admin-ops-kicker">{{ t('adminContent.quality') }}</span>
        <h2>{{ t('adminContent.heroTitle') }}</h2>
        <p>{{ t('adminContent.heroHint') }}</p>
      </div>
      <div class="admin-ops-stat-grid">
        <article v-for="stat in contentStats" :key="stat.label">
          <span><IconGlyph :name="stat.icon" :size="20" /></span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.label }}</small>
        </article>
      </div>
    </section>

    <section class="soft-banner">
      {{ t('adminContent.postPublishNotice') }}
    </section>

    <section class="three-col admin-content-layout">
      <div class="stack admin-content-main">
        <InfoCard :title="t('adminContent.contentList')" :subtitle="t('adminContent.contentListHint')" icon="content">
          <div class="list">
            <ListInfoItem
              v-for="item in content.items"
              :key="`${item.contentType}-${item.id}`"
              :title="item.title"
              :description="item.description || t('adminContent.noDescription')"
              icon="content"
            >
              <template #meta>
                <span class="tag">{{ item.contentType }}</span>
                <span class="tag">{{ item.teacher.fullName }}</span>
                <StatusPill
                  :label="contentStatusLabel(item.contentStatus)"
                  :tone="contentStatusTone(item.contentStatus)"
                />
              </template>
              <template #aside>
                <div class="list-action-row">
                  <button class="btn btn-secondary" type="button" @click="loadDetail(item.contentType, item.id)">
                    {{ t('adminContent.review') }}
                  </button>
                  <button
                    class="btn btn-ghost"
                    type="button"
                    :disabled="contentReadOnly"
                    @click="archiveContent(item.contentType, item.id)"
                  >
                    {{ t('adminContent.archive') }}
                  </button>
                </div>
              </template>
            </ListInfoItem>
          </div>
        </InfoCard>
      </div>

      <div class="stack">
        <InfoCard
          :title="t('adminContent.selectedDetail')"
          :subtitle="t('adminContent.selectedDetailHint')"
          icon="content"
        >
          <div v-if="detail" class="stack">
            <strong>{{ detail.title }}</strong>
            <span class="muted">{{ detail.description || t('adminContent.noDescription') }}</span>
            <div class="tag-row">
              <span class="tag">{{ detail.contentType }}</span>
              <span class="tag">{{ detail.teacher.fullName }}</span>
              <span class="tag">{{ detail.level.name }}</span>
            </div>
            <p class="muted compact-copy">{{ detail.basicContent || t('adminContent.noLessonBody') }}</p>
          </div>
          <StatePanel
            v-else
            icon="content"
            :title="t('adminContent.chooseContent')"
            :message="t('adminContent.chooseContentHint')"
          />
        </InfoCard>

        <ActionFormCard
          :title="t('adminContent.moderationAction')"
          :subtitle="t('adminContent.moderationActionHint')"
          icon="moderation"
          :submit-label="t('adminContent.requestRevision')"
          :submitting-label="t('adminContent.submitting')"
          :submitting="submittingModeration"
          :disabled="contentReadOnly || detailReadOnly"
          @submit="moderate('request-revision')"
        >
          <div class="field">
            <label for="admin-content-moderation-reason">{{ t('adminContent.reason') }}</label>
            <textarea
              id="admin-content-moderation-reason"
              v-model="moderationReason"
              class="textarea"
              maxlength="1000"
              :placeholder="t('adminContent.reasonPlaceholder')"
              :disabled="contentReadOnly || detailReadOnly || submittingModeration"
            />
          </div>
          <div class="list-action-row list-action-row--start">
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('hide')"
            >
              {{ t('adminContent.hide') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('unpublish')"
            >
              {{ t('adminContent.unpublish') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('archive')"
            >
              {{ t('adminContent.archive') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('restore')"
            >
              {{ t('adminContent.restore') }}
            </button>
          </div>
        </ActionFormCard>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import InfoCard from '@/components/dashboard/InfoCard.vue'
import ListInfoItem from '@/components/dashboard/ListInfoItem.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminContentDetail, adminContentList } from '@/demo/data'
import { adminContentMessages } from '@/plugins/adminContentMessages'
import type { ContentStatus, ContentSubmissionDetailResponse, ManagedContentType } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

const { t } = useI18n({
  messages: {
    en: { adminContent: adminContentMessages.en },
    ms: { adminContent: adminContentMessages.ms },
    zh: { adminContent: adminContentMessages.zh },
  },
})

const contentResource = useResource(() => adminApi.content(), adminContentList)
const detailResource = useResource(
  () => adminApi.contentDetail(adminContentDetail.contentType, adminContentDetail.id),
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
const pageError = computed(() => actionError.value || contentResource.error.value)
const contentStats = computed(() => {
  const items = content.value.items
  const teachers = new Set(items.map((item) => item.teacher.fullName))
  const publishedCount = items.reduce((count, item) => (item.contentStatus === 'PUBLISHED' ? count + 1 : count), 0)

  return [
    { icon: 'content', label: t('adminContent.visibleContent'), value: String(items.length) },
    {
      icon: 'status',
      label: t('adminContent.published'),
      value: String(publishedCount),
    },
    {
      icon: 'teacher',
      label: t('adminContent.teachers'),
      value: String(teachers.size),
    },
  ]
})
const moderationReason = ref(t('adminContent.defaultReason'))
const submittingModeration = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

function contentStatusTone(status: ContentStatus): 'success' | 'warning' {
  return status === 'PUBLISHED' ? 'success' : 'warning'
}

function contentStatusLabel(status: ContentStatus): string {
  const keys: Record<ContentStatus, string> = {
    PUBLISHED: 'statusPublished',
    SUBMITTED: 'statusSubmitted',
    APPROVED: 'statusApproved',
    REJECTED: 'statusRejected',
    DRAFT: 'statusDraft',
    ARCHIVED: 'statusArchived',
  }
  return t(`adminContent.${keys[status]}`)
}

async function loadDetail(contentType: ManagedContentType, id: number): Promise<void> {
  actionError.value = null
  actionSuccess.value = null

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

  try {
    detailResource.data.value = await adminApi.contentDetail(contentType, id)
    actionSuccess.value = t('adminContent.detailLoaded')
  } catch (reason) {
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

async function archiveContent(contentType: string, id: number): Promise<void> {
  if (!ensureLiveContent(t('adminContent.archivePreviewError'))) return
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

async function moderate(action: 'hide' | 'unpublish' | 'archive' | 'request-revision' | 'restore'): Promise<void> {
  if (submittingModeration.value) return
  if (!detail.value) return
  if (!ensureLiveContent(t('adminContent.moderationPreviewError'))) return
  actionError.value = null
  actionSuccess.value = null
  submittingModeration.value = true
  try {
    await adminApi.moderationAction(action, {
      targetType: detail.value.contentType,
      targetId: detail.value.id,
      reason: moderationReason.value,
    })
    await contentResource.reload()
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
