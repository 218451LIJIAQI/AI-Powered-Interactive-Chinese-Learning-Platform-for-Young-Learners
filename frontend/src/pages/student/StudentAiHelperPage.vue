<template>
  <div class="page-stack ai-helper-page">
    <div class="ai-helper-top">
      <!-- The module's own compact header (one h1): the rules stay visible but quiet, the tools stay small. -->
      <header class="ai-helper-head">
        <h1 class="ai-helper-title">{{ t('aiHelper.pageTitle') }}</h1>
        <p class="ai-helper-rules">
          {{ t('aiHelper.practiceNote') }}
          <RouterLink class="ai-helper-rules-link" :to="{ name: 'responsible-ai' }">{{
            t('aiHelper.responsibleAi')
          }}</RouterLink>
        </p>
        <span class="ai-helper-requests" :class="{ 'is-limit': limitReached }" data-testid="ai-requests-today">
          <IconGlyph :name="limitReached ? 'alert' : 'ai'" :size="16" />
          {{ requestsLabel }}
        </span>
        <button
          class="btn btn-secondary btn-sm ai-helper-reports"
          type="button"
          data-testid="ai-reports-open"
          @click="reportsOpen = true"
        >
          <IconGlyph name="flag" :size="16" />
          {{ t('aiHelper.myReports') }}
          <span v-if="reports.length" class="route-tab-count">{{ reports.length }}</span>
        </button>
      </header>

      <RouteTabs :tabs="tabs" :label="t('aiHelper.tabsLabel')" />
    </div>

    <div class="ai-helper-body">
      <div v-if="blocked" class="ai-helper-away" role="status" data-testid="ai-blocked">
        <IconGlyph class="ai-helper-away-icon" :name="blocked === 'limit' ? 'clock' : 'alert'" :size="20" />
        <p>
          <strong>{{
            blocked === 'limit'
              ? t('aiHelper.limitTitle', { limit: status?.dailyLimit ?? 0 })
              : t('aiHelper.unavailableTitle')
          }}</strong>
          {{ blocked === 'limit' ? t('aiHelper.limitMessage') : t('aiHelper.unavailableMessage') }}
        </p>
        <div class="ai-helper-away-actions">
          <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-practice' }">
            <IconGlyph name="practice" :size="16" />
            {{ t('aiHelper.goToPractice') }}
          </RouterLink>
          <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'student-learn' }">
            <IconGlyph name="learn" :size="16" />
            {{ t('aiHelper.goToLearn') }}
          </RouterLink>
        </div>
      </div>
      <DataNotice v-else-if="statusResource.error.value" :error="statusResource.error.value">
        <template #actions>
          <button class="btn btn-secondary btn-sm" type="button" @click="statusResource.reload">
            {{ t('aiHelper.retry') }}
          </button>
        </template>
      </DataNotice>

      <KeepAlive>
        <component :is="tabComponent" :key="tab" v-bind="tabProps" v-on="tabListeners" />
      </KeepAlive>
    </div>

    <AppOverlay v-if="reportsOpen" variant="drawer" :title="t('aiHelper.myReports')" @close="reportsOpen = false">
      <AiMyReports
        :reports="reports"
        :loading="reportsResource.loading.value"
        :error="reportsResource.error.value"
        @retry="reportsResource.reload"
      />
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { aiHelperApi } from '@/api/ai'
import AiAskTab from '@/components/ai/AiAskTab.vue'
import AiMyReports from '@/components/ai/AiMyReports.vue'
import AiQuizTab from '@/components/ai/AiQuizTab.vue'
import AiStoryTab from '@/components/ai/AiStoryTab.vue'
import AiTalkTab from '@/components/ai/AiTalkTab.vue'
import AiVoiceTab from '@/components/ai/AiVoiceTab.vue'
import type { AiBlockedReason, AiHelperTab } from '@/components/ai/aiHelper'
import AppOverlay from '@/components/common/AppOverlay.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import '@/styles/ai.css'

/**
 * The AI Helper: optional practice with Xiaomei and SparkBot (no XP, AI can make mistakes). Each mode is its own route
 * (/student/ai/talk, ask, quiz, story, voice) and keeps its state while the student switches. The header holds the
 * safety rules, today's AI requests against the daily limit and My reports (a drawer, reachable from every mode); when
 * SparkBot is unavailable or the limit is reached the page says so and points to Practice and Learn, which work
 * without AI.
 */
const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const route = useRoute()
const router = useRouter()

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
const reportsOpen = ref(false)

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

/** Each mode gets the shared state it shows: what blocks AI requests and its own extras. */
const tabProps = computed(() => {
  const aiNotice = status.value?.aiNotice ?? ''
  switch (tab.value) {
    case 'talk':
      return { blocked: blocked.value }
    case 'ask':
      return { blocked: blocked.value, aiNotice, initialQuestion: initialQuestion.value }
    case 'voice':
      return { blocked: blocked.value, aiNotice, status: status.value }
    default:
      return { blocked: blocked.value, aiNotice }
  }
})

/** A request refreshes today's count; a report refreshes My reports. */
const tabListeners = { used: statusResource.reload, reported: reportsResource.reload }

const tabs = computed<RouteTab[]>(() => [
  { label: t('aiHelper.tabTalk'), to: { name: 'student-ai', params: { tab: 'talk' } }, testId: 'ai-tab-talk' },
  { label: t('aiHelper.tabAsk'), to: { name: 'student-ai', params: { tab: 'ask' } }, testId: 'ai-tab-ask' },
  { label: t('aiHelper.tabQuiz'), to: { name: 'student-ai', params: { tab: 'quiz' } }, testId: 'ai-tab-quiz' },
  { label: t('aiHelper.tabStory'), to: { name: 'student-ai', params: { tab: 'story' } }, testId: 'ai-tab-story' },
  { label: t('aiHelper.tabVoice'), to: { name: 'student-ai', params: { tab: 'voice' } }, testId: 'ai-tab-voice' },
])
</script>
