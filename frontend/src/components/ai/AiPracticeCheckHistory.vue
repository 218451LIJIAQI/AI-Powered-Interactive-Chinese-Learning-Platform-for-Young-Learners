<template>
  <div class="ai-voice-rail">
    <section class="panel ai-checks" :aria-labelledby="`${baseId}-recent`">
      <h2 :id="`${baseId}-recent`" class="card-title">{{ t('aiHelper.recentChecks') }}</h2>
      <StatePanel
        v-if="listLoading"
        tone="loading"
        :title="t('aiHelper.checksLoading')"
        :message="t('aiHelper.checksLoadingHint')"
        :skeleton="false"
      />
      <StatePanel
        v-else-if="checksResource.error.value"
        tone="error"
        icon="alert"
        :title="t('aiHelper.checksError')"
        :message="checksResource.error.value"
      >
        <template #actions>
          <button class="btn btn-secondary btn-sm" type="button" @click="checksResource.reload">
            {{ t('common.retry') }}
          </button>
        </template>
      </StatePanel>
      <ul v-else-if="checks.length" class="row-list">
        <li v-for="check in checks" :key="check.id" class="ai-check-item">
          <div class="row-list-copy">
            <span class="row-list-title">
              {{ kindLabel(check.type) }} · <span lang="zh-CN">{{ check.targetText }}</span>
            </span>
            <span class="row-list-meta">{{ whenLabel(check) }}</span>
          </div>
          <div class="row-list-actions">
            <span class="tag is-outline">{{ t('aiHelper.outOf100', { score: check.score }) }}</span>
            <span v-if="viewingCheckId === check.id" class="tag" data-testid="ai-check-viewing">
              {{ t('aiHelper.viewing') }}
            </span>
            <button
              v-else
              class="btn btn-secondary btn-sm"
              type="button"
              :aria-label="t('aiHelper.openCheck', { kind: kindLabel(check.type), target: check.targetText })"
              @click="openCheck = check"
            >
              {{ t('common.open') }}
            </button>
          </div>
        </li>
      </ul>
      <StatePanel
        v-else
        tone="empty"
        icon="mic"
        :title="t('aiHelper.noChecks')"
        :message="t('aiHelper.noChecksHint')"
      />
      <PaginationBar
        v-if="!listLoading && !checksResource.error.value"
        :page="displayedPage"
        :total-pages="totalPages"
        :label="t('aiHelper.recentChecks')"
        @update:page="goToPage"
      />
    </section>
  </div>

  <AppOverlay
    v-if="openCheck && !drawerSuppressed"
    variant="drawer"
    :busy="deleting"
    :title="t('aiHelper.checkDetailTitle', { kind: kindLabel(openCheck.type), target: openCheck.targetText })"
    :subtitle="checkMeta(openCheck, t('aiHelper.checkedOn', { time: formatDateTime(openCheck.checkedAt) }))"
    @close="openCheck = null"
  >
    <dl class="kv-list">
      <dt>{{ t('aiHelper.lesson') }}</dt>
      <dd>{{ openCheck.lessonTitle || t('aiHelper.noLesson') }}</dd>
      <dt>{{ t('aiHelper.checkType') }}</dt>
      <dd>{{ kindLabel(openCheck.type) }}</dd>
      <dt>{{ t('aiHelper.target') }}</dt>
      <dd lang="zh-CN">{{ openCheck.targetText }}</dd>
      <dt>{{ t('aiHelper.checked') }}</dt>
      <dd>{{ formatDateTime(openCheck.checkedAt) }}</dd>
      <dt>{{ t('aiHelper.score') }}</dt>
      <dd>
        <StatusPill :label="t('aiHelper.scorePill', { score: openCheck.score })" tone="outline" />
      </dd>
    </dl>
    <section class="ai-check-detail" :aria-labelledby="`${baseId}-detail`">
      <header class="ai-check-detail-head">
        <h3 :id="`${baseId}-detail`" class="card-title">
          {{ t(openCheck.pronunciation ? 'aiHelper.pronunciationResult' : 'aiHelper.sparkbotScore') }}
        </h3>
        <span class="tag">
          <IconGlyph name="ai" :size="14" />
          {{ t('aiHelper.practiceOnly') }}
        </span>
      </header>
      <AiCheckScore :check="openCheck" />
      <p class="field-hint">{{ t('aiHelper.practiceScoreNote', { notice: aiNotice }) }}</p>
      <button
        v-if="!reportedIds.has(openCheck.id)"
        class="btn btn-ghost btn-sm ai-check-report"
        type="button"
        :disabled="deleting"
        @click="emit('report', openCheck)"
      >
        <IconGlyph name="flag" :size="16" />
        {{ t('aiHelper.reportScore') }}
      </button>
      <span v-else class="ai-reported"><IconGlyph name="status" :size="14" />{{ t('aiSafety.reported') }}</span>
      <p v-if="detailError" class="callout is-compact is-danger" role="alert">{{ detailError }}</p>
    </section>
    <!-- Delete takes the check away at once: the toast's Undo brings it back, so nothing asks first. -->
    <template #footer>
      <button
        class="btn btn-danger-soft is-far-left"
        type="button"
        :disabled="deleting"
        :aria-busy="deleting"
        data-testid="ai-check-delete"
        @click="deleteCheck(openCheck)"
      >
        <IconGlyph name="trash" :size="16" />
        {{ t('aiHelper.delete') }}
      </button>
      <button class="btn btn-secondary" type="button" :disabled="deleting" @click="openCheck = null">
        {{ t('common.close') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { aiVoiceMessages } from '@/plugins/aiVoiceMessages'
import type { PracticeCheck } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'

import AiCheckScore from './AiCheckScore.vue'
import { formatPracticeCheckMeta, practiceCheckKindKey } from './aiHelper'

const PAGE_SIZE = 5

defineProps<{
  viewingCheckId: number | null
  reportedIds: ReadonlySet<number>
  aiNotice: string
  /** Hide the detail drawer while the parent displays the AI report modal. */
  drawerSuppressed: boolean
}>()
const emit = defineEmits<{
  report: [check: PracticeCheck]
  deleted: [checkId: number]
  toast: [message: string, action?: { label: string; run: () => void }]
}>()

const { t } = useLocalMessages({ aiHelper: aiVoiceMessages })
const baseId = useId()
const isUnmounted = useIsUnmounted()

const checksPage = ref(0)
const newestId = ref<number | null>(null)
const openCheck = ref<PracticeCheck | null>(null)
const deleting = ref(false)
const detailError = ref<string | null>(null)

const checksResource = useResource(() => aiHelperApi.practiceChecks(checksPage.value, PAGE_SIZE))
const listLoading = computed(
  () =>
    checksResource.loading.value ||
    (!checksResource.error.value && checksResource.data.value?.page !== checksPage.value),
)
const {
  items: checks,
  displayedPage,
  totalPages,
  goToPage,
} = useServerPage(checksResource, checksPage, { stepBackFromEmpty: true })

function kindLabel(kind: PracticeCheck['type']): string {
  return t(practiceCheckKindKey(kind))
}

function checkMeta(check: PracticeCheck, when: string): string {
  return formatPracticeCheckMeta(check, when)
}

function whenLabel(check: PracticeCheck): string {
  return check.id === newestId.value ? t('aiHelper.justNow') : formatDateTime(check.checkedAt)
}

function showNewest(checkId: number): void {
  newestId.value = checkId
  goToPage(0)
}

defineExpose({ showNewest })

async function deleteCheck(check: PracticeCheck): Promise<void> {
  if (deleting.value) return
  const sessionVersion = getAuthSessionVersion()
  deleting.value = true
  detailError.value = null
  try {
    await aiHelperApi.deletePracticeCheck(check.id)
    if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
    emit('deleted', check.id)
    openCheck.value = null
    goToPage(checks.value.length === 1 ? checksPage.value - 1 : checksPage.value)
    emit('toast', t('aiHelper.checkDeleted'), {
      label: t('common.undo'),
      run: () => void restoreCheck(check, sessionVersion),
    })
  } catch (reason) {
    if (!isUnmounted() && sessionVersion === getAuthSessionVersion()) {
      detailError.value = resolveApiError(reason, t('aiHelper.deleteError'))
    }
  } finally {
    if (!isUnmounted()) deleting.value = false
  }
}

async function restoreCheck(check: PracticeCheck, sessionVersion: number): Promise<void> {
  if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
  try {
    await aiHelperApi.restorePracticeCheck(check.id)
    if (isUnmounted() || sessionVersion !== getAuthSessionVersion()) return
    void checksResource.reload()
  } catch (reason) {
    if (!isUnmounted() && sessionVersion === getAuthSessionVersion()) {
      emit('toast', resolveApiError(reason, t('aiHelper.restoreCheckError')))
    }
  }
}

watch(openCheck, () => {
  detailError.value = null
})
</script>
