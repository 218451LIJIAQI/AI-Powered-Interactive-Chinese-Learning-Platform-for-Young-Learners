<template>
  <div class="page-stack admin-content-page">
    <PageHeaderBlock
      title="Content Management"
      description="Review published teacher content, inspect details, and archive or moderate content after publication."
      icon="content"
    />

    <DataNotice
      :using-fallback="contentResource.usingFallback.value"
      :error="actionError || contentResource.error.value"
      :success="actionSuccess"
    />

    <section class="admin-ops-hero admin-ops-hero--content">
      <div>
        <span class="admin-ops-kicker">Content Quality</span>
        <h2>Keep published learning materials aligned, safe, and ready for families.</h2>
        <p>
          Teachers publish directly while admins monitor quality, archive outdated items, and take moderation action
          after publication.
        </p>
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
      Admin content management here is intentionally post-publication. Teachers publish directly; admins oversee and
      moderate after publication.
    </section>

    <section class="three-col admin-content-layout">
      <div class="stack admin-content-main">
        <InfoCard title="Content list" subtitle="Published and submitted content visible to admins." icon="content">
          <div class="list">
            <ListInfoItem
              v-for="item in content.items"
              :key="`${item.contentType}-${item.id}`"
              :title="item.title"
              :description="item.description || 'No description.'"
              icon="content"
            >
              <template #meta>
                <span class="tag">{{ item.contentType }}</span>
                <span class="tag">{{ item.teacher.fullName }}</span>
                <StatusPill
                  :label="item.contentStatus"
                  :tone="item.contentStatus === 'PUBLISHED' ? 'success' : 'warning'"
                />
              </template>
              <template #aside>
                <div class="list-action-row">
                  <button class="btn btn-secondary" type="button" @click="loadDetail(item.contentType, item.id)">
                    Review
                  </button>
                  <button
                    class="btn btn-ghost"
                    type="button"
                    :disabled="contentReadOnly"
                    @click="archiveContent(item.contentType, item.id)"
                  >
                    Archive
                  </button>
                </div>
              </template>
            </ListInfoItem>
          </div>
        </InfoCard>
      </div>

      <div class="stack">
        <InfoCard
          title="Selected content detail"
          subtitle="Inspect teacher-owned content after publishing."
          icon="content"
        >
          <div v-if="detail" class="stack">
            <strong>{{ detail.title }}</strong>
            <span class="muted">{{ detail.description || 'No description.' }}</span>
            <div class="tag-row">
              <span class="tag">{{ detail.contentType }}</span>
              <span class="tag">{{ detail.teacher.fullName }}</span>
              <span class="tag">{{ detail.level.name }}</span>
            </div>
            <p class="muted compact-copy">{{ detail.basicContent || 'No lesson body available.' }}</p>
          </div>
          <StatePanel
            v-else
            icon="content"
            title="Choose content to review"
            message="Select a package or lesson to inspect moderation-relevant details."
          />
        </InfoCard>

        <ActionFormCard
          title="Moderation action"
          subtitle="Use only real backend moderation endpoints."
          icon="moderation"
          submit-label="Request revision"
          submitting-label="Submitting..."
          :submitting="submittingModeration"
          :disabled="contentReadOnly || detailReadOnly"
          @submit="moderate('request-revision')"
        >
          <div class="field">
            <label for="admin-content-moderation-reason">Reason</label>
            <textarea
              id="admin-content-moderation-reason"
              v-model="moderationReason"
              class="textarea"
              maxlength="1000"
              placeholder="Explain the moderation decision."
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
              Hide
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('unpublish')"
            >
              Unpublish
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('archive')"
            >
              Archive
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="contentReadOnly || detailReadOnly"
              @click="moderate('restore')"
            >
              Restore
            </button>
          </div>
        </ActionFormCard>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Admin content-review queue — approve / reject / request changes
 * on teacher-submitted courses, modules, lessons.
 *
 * <p>The page calls into {@code AdminContentReviewService} which
 * publishes the content only after admin approval. A rejection
 * sends the content back to draft state with a note visible to the
 * teacher in {@link TeacherContentPage}.</p>
 */
import { computed, ref } from 'vue'

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
import { resolveApiError } from '@/utils/apiError'

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
const contentStats = computed(() => [
  { icon: 'content', label: 'Visible content', value: String(content.value.items.length) },
  {
    icon: 'status',
    label: 'Published',
    value: String(content.value.items.filter((item) => item.contentStatus === 'PUBLISHED').length),
  },
  {
    icon: 'teacher',
    label: 'Teachers',
    value: String(new Set(content.value.items.map((item) => item.teacher.fullName)).size),
  },
])
const moderationReason = ref('Please revise this content for quality and consistency.')
const submittingModeration = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

async function loadDetail(contentType: string, id: number) {
  actionError.value = null
  actionSuccess.value = null
  await adminApi
    .contentDetail(contentType, id)
    .then((data) => {
      detailResource.data.value = data
      actionSuccess.value = 'Content detail loaded for review.'
    })
    .catch((reason) => {
      actionError.value = resolveApiError(reason, 'Unable to load this content detail.')
      detailResource.data.value = null
    })
}

const archivingContentId = ref<number | null>(null)

async function archiveContent(contentType: string, id: number) {
  if (archivingContentId.value) return
  if (!ensureLiveContent('Content cannot be archived while preview data is active.')) return
  actionError.value = null
  actionSuccess.value = null
  try {
    await adminApi.archiveContent(contentType, id, 'Archived from admin content management.')
    await contentResource.reload()
    actionSuccess.value = 'Content archived and list refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to archive this content.')
  }
}

async function moderate(action: 'hide' | 'unpublish' | 'archive' | 'request-revision' | 'restore') {
  if (submittingModeration.value) return
  if (!detail.value) return
  if (!ensureLiveContent('Moderation actions cannot be submitted while preview data is active.')) return
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
    actionSuccess.value = 'Moderation action submitted and content list refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to submit the moderation action.')
  } finally {
    submittingModeration.value = false
  }
}

function ensureLiveContent(message: string) {
  if (!contentReadOnly.value && !detailReadOnly.value) return true
  actionSuccess.value = null
  actionError.value = message
  return false
}
</script>
