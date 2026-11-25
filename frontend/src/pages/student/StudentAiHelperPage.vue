<template>
  <div class="page-stack ai-helper-page">
    <h1 class="sr-only">{{ t('nav.aiHelper') }}</h1>

    <RouteTabs :tabs="tabs" :label="t('aiHelper.tabsLabel')">
      <span class="route-tabs-note">{{ t('aiHelper.practiceNote') }}</span>
      <!-- Today's count matters only once it stops the AI: before that it would be a number to keep an eye on. -->
      <span v-if="status && limitReached" class="tag is-strong ai-helper-requests" data-testid="ai-requests-today">
        <IconGlyph name="ai" :size="14" />
        {{ t('aiHelper.requestsToday', { used: status.usedToday, limit: status.dailyLimit }) }}
      </span>
    </RouteTabs>

    <!-- The AI is off or today's requests are used up: one calm line, with the way that still works. -->
    <div v-if="blocked" class="callout is-warning" role="status" data-testid="ai-blocked">
      <IconGlyph name="alert" :size="16" />
      <div>
        <template v-if="tab === 'talk' && tavusDeploymentId">
          <strong>{{ t(blocked === 'limit' ? 'aiHelper.talkReplyLimit' : 'aiHelper.talkReplyUnavailable') }}</strong>
          {{ t('aiHelper.tryLiveVideo') }}
        </template>
        <template v-else-if="blocked === 'limit'">
          <strong>{{ t('aiHelper.limitTitle', { limit: status?.dailyLimit }) }}</strong>
          {{ t('aiHelper.limitMessage') }}
        </template>
        <template v-else>
          <strong>{{
            t(tab === 'voice' && status?.available ? 'aiHelper.checksUnavailableTitle' : 'aiHelper.unavailableTitle')
          }}</strong>
          {{ t('aiHelper.unavailableMessage') }}
        </template>
      </div>
      <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-practice' }">
        <IconGlyph name="practice" :size="16" />
        {{ t('aiHelper.goToPractice') }}
      </RouterLink>
    </div>
    <DataNotice :error="statusResource.error.value" @retry="statusResource.reload" />

    <KeepAlive>
      <component :is="TABS[tab].component" :key="tab" v-bind="tabProps" v-on="tabListeners">
        <template #rail>
          <AiMyReports :revision="reportRevision" />
        </template>
      </component>
    </KeepAlive>

    <AppToast :message="toast" :action="toastAction" />
  </div>
</template>

<script setup lang="ts">
import '@/styles/ai.css'

import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { aiHelperApi } from '@/api/ai'
import AiAskTab from '@/components/ai/AiAskTab.vue'
import AiGeneratorTab from '@/components/ai/AiGeneratorTab.vue'
import AiMyReports from '@/components/ai/AiMyReports.vue'
import AiTalkTab from '@/components/ai/AiTalkTab.vue'
import AiVoiceTab from '@/components/ai/AiVoiceTab.vue'
import type { AiBlockedReason } from '@/components/ai/aiHelper'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { configuredTavusDeploymentId } from '@/composables/usePublicPlatformSettings'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { shownAiNotice } from '@/plugins/aiSafetyMessages'
import { firstQueryText } from '@/utils/routeQuery'

/**
 * The AI Helper: optional practice with Xiaomei and SparkBot. Each mode is its own route (/student/ai/talk, ask, quiz,
 * story, voice) and keeps its state while the learner switches. The tab row holds "Practice only · no XP", and today's
 * request count once the daily limit is reached; My reports is the rail of Ask SparkBot. When the AI is unavailable or
 * the limit is reached, one line says so (the limit is shared by every tool) and points to Practice, which works
 * without AI. The page's toast confirms what a mode did, with its Undo.
 */
const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const route = useRoute()
const { toast, toastAction, showToast } = useToast()

/** The five modes in tab order. */
const TABS = {
  talk: { component: AiTalkTab, label: 'aiHelper.talk', icon: 'mic' },
  ask: { component: AiAskTab, label: 'aiHelper.tabAsk', icon: 'chat' },
  quiz: { component: AiGeneratorTab, label: 'aiHelper.quickQuiz', icon: 'checklist' },
  story: { component: AiGeneratorTab, label: 'aiHelper.story', icon: 'learn' },
  voice: { component: AiVoiceTab, label: 'aiHelper.tabVoice', icon: 'audio' },
} as const

const statusResource = useResource(aiHelperApi.status)
const status = statusResource.data
const reportRevision = ref(0)
const tavusDeploymentId = configuredTavusDeploymentId()

type AiTab = keyof typeof TABS
// The route only matches these five tabs; /student/ai redirects to Talk with Xiaomei.
const tab = computed<AiTab>(() => {
  const value = route.params.tab
  return typeof value === 'string' && value in TABS ? (value as AiTab) : 'talk'
})

const limitReached = computed(() => Boolean(status.value && status.value.usedToday >= status.value.dailyLimit))
const blocked = computed<AiBlockedReason>(() => {
  const current = status.value
  if (!current) return null
  const available =
    tab.value === 'voice' ? current.speechCheckAvailable || current.handwritingCheckAvailable : current.available
  if (!available) return 'unavailable'
  return limitReached.value ? 'limit' : null
})
/**
 * Each mode gets the shared state it shows: what blocks AI requests, the AI notice (the admin's wording, or the
 * platform default in the student's language, as on every AI screen) and its own extras.
 */
const tabProps = computed(() => {
  const shared = { blocked: blocked.value, aiNotice: shownAiNotice(status.value?.aiNotice, t) }
  if (tab.value === 'talk') return { ...shared, tavusDeploymentId }
  if (tab.value === 'ask') return { ...shared, initialQuestion: firstQueryText(route.query.q) || null }
  if (tab.value === 'voice') return { ...shared, status: status.value }
  return { ...shared, kind: tab.value }
})

/**
 * A request refreshes today's count; a report refreshes My reports and says where the answer will appear; a mode's own
 * confirmation ("Check deleted.") goes to the toast with its Undo.
 */
const tabListeners = {
  used: statusResource.reload,
  reported: () => {
    reportRevision.value++
    showToast(tab.value === 'ask' ? t('aiHelper.reportSentHere') : t('aiSafety.reportSent'))
  },
  toast: showToast,
}

const tabs = computed<RouteTab[]>(() =>
  Object.entries(TABS).map(([key, info]) => ({
    label: t(info.label),
    to: { name: 'student-ai', params: { tab: key } },
    icon: info.icon,
  })),
)
</script>
