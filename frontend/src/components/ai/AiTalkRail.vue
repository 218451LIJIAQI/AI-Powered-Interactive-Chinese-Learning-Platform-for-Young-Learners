<template>
  <aside class="ai-talk-rail">
    <div class="ai-talk-scenarios">
      <h3 class="ai-talk-rail-title">{{ t('aiHelper.scenario') }}</h3>
      <p v-if="liveCall" class="ai-talk-current-scenario">{{ currentScenarioLabel }}</p>
      <FilterChips
        v-else
        :model-value="scenario"
        :options="scenarioOptions"
        :label="t('aiHelper.scenario')"
        :disabled="thinking || listening"
        @update:model-value="emit('chooseScenario', $event)"
      />
    </div>

    <div class="ai-talk-history">
      <h3 :id="`${baseId}-conversation`" class="ai-talk-rail-title">{{ t('aiHelper.conversation') }}</h3>
      <ol ref="transcript" class="ai-transcript" tabindex="0" :aria-labelledby="`${baseId}-conversation`">
        <li v-for="entry in entries" :key="entry.id" :class="{ 'is-student': entry.speaker === 'STUDENT' }">
          <span v-if="entry.speaker === 'XIAOMEI'" class="ai-transcript-who" lang="zh-CN">小美</span>
          <span v-else class="ai-transcript-who is-you">{{ t('aiHelper.you') }}</span>
          <span class="ai-transcript-line" lang="zh-CN">{{ entry.chinese }}</span>
          <span v-if="entry.natural" class="ai-transcript-tip">
            {{ t('aiHelper.tip') }}: <span lang="zh-CN">{{ entry.natural }}</span>
          </span>
        </li>
      </ol>
    </div>

    <div class="ai-talk-end">
      <button
        v-if="!liveCall"
        class="btn btn-secondary btn-block"
        type="button"
        :disabled="!canEnd || thinking || listening"
        :aria-describedby="`${baseId}-end`"
        data-testid="ai-talk-end"
        @click="emit('end')"
      >
        {{ t('aiHelper.endConversation') }}
      </button>
      <p v-if="!liveCall" :id="`${baseId}-end`" class="field-hint" data-testid="ai-talk-end-hint">
        {{ canEnd ? t('aiHelper.repliesDoneHint') : t('aiHelper.replyTwiceHint') }}
      </p>
      <AiNotice :text="aiNotice" :live-video="hasLiveVideo" />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, useId } from 'vue'

import FilterChips from '@/components/common/FilterChips.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiTalkMessages } from '@/plugins/aiTalkMessages'
import type { XiaomeiScenario } from '@/types/ai'

import AiNotice from './AiNotice.vue'
import type { TalkEntry } from './xiaomeiReport'

defineProps<{
  scenario: XiaomeiScenario
  currentScenarioLabel: string
  scenarioOptions: readonly { value: XiaomeiScenario; label: string }[]
  entries: readonly TalkEntry[]
  liveCall: boolean
  hasLiveVideo: boolean
  thinking: boolean
  listening: boolean
  canEnd: boolean
  aiNotice: string
}>()
const emit = defineEmits<{
  chooseScenario: [key: XiaomeiScenario]
  end: []
}>()

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiTalkMessages] })
const baseId = useId()
const transcript = ref<HTMLOListElement | null>(null)

function scrollToBottom(): void {
  if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
}

defineExpose({ scrollToBottom })
</script>
