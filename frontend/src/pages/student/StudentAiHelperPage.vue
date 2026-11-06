<template>
  <div class="page-stack ai-helper-page">
    <PageHeaderBlock :title="t('aiHelper.pageTitle')" hide-title />

    <RouteTabs :tabs="tabs" :label="t('aiHelper.tabsLabel')">
      <StatusPill v-if="blocked === 'unavailable'" :label="t('aiHelper.unavailableBadge')" tone="outline" />
      <span v-else class="ai-helper-note">{{ t('aiHelper.practiceNote') }}</span>
      <span class="tag ai-helper-requests" :class="{ 'is-strong': limitReached }" data-testid="ai-requests-today">
        <IconGlyph name="ai" :size="14" />
        {{ requestsLabel }}
      </span>
    </RouteTabs>

    <!-- The AI is off or today's requests are used up: one calm line, with the way that still works. -->
    <div v-if="blocked" class="alert alert-warning ai-helper-notice" role="status" data-testid="ai-blocked">
      <IconGlyph name="alert" :size="16" />
      <p>
        <strong>{{
          blocked === 'limit'
            ? t('aiHelper.limitTitle', { limit: status?.dailyLimit ?? 0 })
            : t('aiHelper.unavailableTitle')
        }}</strong>
        {{ blocked === 'limit' ? t('aiHelper.limitMessage') : t('aiHelper.unavailableMessage') }}
      </p>
      <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-practice' }">
        <IconGlyph name="practice" :size="16" />
        {{ t('aiHelper.goToPractice') }}
      </RouterLink>
    </div>
    <DataNotice v-else-if="statusResource.error.value" :error="statusResource.error.value">
      <template #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="statusResource.reload">
          {{ t('aiHelper.retry') }}
        </button>
      </template>
    </DataNotice>

    <KeepAlive>
      <component :is="tabComponent" :key="tab" v-bind="tabProps" v-on="tabListeners">
        <template #rail>
          <AiMyReports
            :reports="reports"
            :loading="reportsResource.loading.value"
            :error="reportsResource.error.value"
            @retry="reportsResource.reload"
          />
        </template>
      </component>
    </KeepAlive>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { aiHelperApi } from '@/api/ai'
import AiAskTab from '@/components/ai/AiAskTab.vue'
import AiMyReports from '@/components/ai/AiMyReports.vue'
import AiQuizTab from '@/components/ai/AiQuizTab.vue'
import AiStoryTab from '@/components/ai/AiStoryTab.vue'
import AiTalkTab from '@/components/ai/AiTalkTab.vue'
import AiVoiceTab from '@/components/ai/AiVoiceTab.vue'
import type { AiBlockedReason, AiHelperTab } from '@/components/ai/aiHelper'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import '@/styles/ai.css'

/**
 * The AI Helper: optional practice with Xiaomei and SparkBot. Each mode is its own route (/student/ai/talk, ask, quiz,
 * story, voice) and keeps its state while the student switches. The tab row holds "Practice only · no XP" and today's
 * AI requests against the daily limit; My reports is the rail of Ask SparkBot. When SparkBot is unavailable or the limit
 * is reached, one line says so and points to Practice, which works without AI.
 */
const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()

const TAB_COMPONENTS = {
  talk: AiTalkTab,
  ask: AiAskTab,
  quiz: AiQuizTab,
  story: AiStoryTab,
  voice: AiVoiceTab,
} as const

const statusResource = useResource(() => aiHelperApi.status())
const reportsResource = useResource(() => aiHelperApi.reports())
const status = computed(() => statusResource.data.value)
const reports = computed(() => reportsResource.data.value?.items ?? [])

const tab = computed<AiHelperTab>(() => {
  const value = route.params.tab
  return typeof value === 'string' && value in TAB_COMPONENTS ? (value as AiHelperTab) : 'talk'
})
const tabComponent = computed(() => TAB_COMPONENTS[tab.value])
const initialQuestion = computed(() => (typeof route.query.q === 'string' ? route.query.q : null))

// /student/ai opens Talk with Xiaomei under its own tab path, so the tab strip marks it.
watch(
  () => route.params.tab,
  (value) => {
    if (!value) void router.replace({ name: 'student-ai', params: { tab: 'talk' }, query: route.query })
  },
  { immediate: true },
)

const limitReached = computed(() => Boolean(status.value && status.value.usedToday >= status.value.dailyLimit))
const blocked = computed<AiBlockedReason>(() => {
  if (!status.value) return null
  if (!status.value.available) return 'unavailable'
  return limitReached.value ? 'limit' : null
})
const requestsLabel = computed(() =>
  status.value
    ? t('aiHelper.requestsToday', { used: status.value.usedToday, limit: status.value.dailyLimit })
    : t('aiHelper.requestsLoading'),
)

/** Each mode gets the shared state it shows: what blocks AI requests, the AI notice and its own extras. */
const tabProps = computed(() => {
  const shared = { blocked: blocked.value, aiNotice: status.value?.aiNotice || t('aiHelper.aiCanMakeMistakes') }
  switch (tab.value) {
    case 'ask':
      return { ...shared, initialQuestion: initialQuestion.value }
    case 'voice':
      return { ...shared, status: status.value }
    default:
      return shared
  }
})

/** A request refreshes today's count; a report refreshes My reports and says where the answer will appear. */
const tabListeners = {
  used: statusResource.reload,
  reported: () => {
    void reportsResource.reload()
    showToast(tab.value === 'ask' ? t('aiHelper.reportSentHere') : t('aiHelper.reportSentElsewhere'))
  },
}

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('aiHelper.tabTalk'),
    to: { name: 'student-ai', params: { tab: 'talk' } },
    icon: 'mic',
    testId: 'ai-tab-talk',
  },
  {
    label: t('aiHelper.tabAsk'),
    to: { name: 'student-ai', params: { tab: 'ask' } },
    icon: 'chat',
    testId: 'ai-tab-ask',
  },
  {
    label: t('aiHelper.tabQuiz'),
    to: { name: 'student-ai', params: { tab: 'quiz' } },
    icon: 'checklist',
    testId: 'ai-tab-quiz',
  },
  {
    label: t('aiHelper.tabStory'),
    to: { name: 'student-ai', params: { tab: 'story' } },
    icon: 'learn',
    testId: 'ai-tab-story',
  },
  {
    label: t('aiHelper.tabVoice'),
    to: { name: 'student-ai', params: { tab: 'voice' } },
    icon: 'audio',
    testId: 'ai-tab-voice',
  },
])
</script>
