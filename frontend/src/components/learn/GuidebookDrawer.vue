<template>
  <AppOverlay
    variant="drawer"
    :open="unit !== null"
    :title="unit ? t('learn.guidebookTitle', { number: unit.number }) : ''"
    :subtitle="unit ? t('learn.guidebookSubtitle', { title: unit.title, titleZh: unit.titleZh }) : ''"
    test-id="guidebook"
    @close="emit('close')"
  >
    <StatePanel
      v-if="guide.loading.value"
      tone="loading"
      :title="t('learn.guidebookLoading')"
      :message="t('learn.guidebookLoadingHint')"
    />
    <StatePanel
      v-else-if="guide.error.value"
      tone="error"
      icon="alert"
      :title="t('learn.guidebookError')"
      :message="guide.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="guide.reload">{{ t('learn.retry') }}</button>
      </template>
    </StatePanel>
    <template v-else-if="guide.data.value">
      <template v-if="guide.data.value.phrases.length">
        <h3 class="learn-guide-heading">{{ t('learn.keyPhrases') }}</h3>
        <ul class="row-list learn-guide-phrases">
          <li v-for="phrase in guide.data.value.phrases" :key="phrase.hanzi">
            <span class="row-list-copy">
              <strong class="learn-guide-phrase" lang="zh-CN">{{ phrase.hanzi }}</strong>
              <span class="row-list-meta">{{ phrase.pinyin }} · {{ phrase.meaning }}</span>
            </span>
            <ListenButtons :text="phrase.hanzi" />
          </li>
        </ul>
      </template>
      <template v-if="guide.data.value.tips.length">
        <h3 class="learn-guide-heading">{{ t('learn.tips') }}</h3>
        <ul class="learn-guide-tips">
          <li v-for="tip in guide.data.value.tips" :key="tip">{{ tip }}</li>
        </ul>
      </template>
    </template>
    <template #footer>
      <button class="btn btn-primary" type="button" data-testid="guidebook-done" @click="emit('close')">
        {{ t('learn.gotIt') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { watch } from 'vue'

import { learnApi } from '@/api/learn'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathUnit } from '@/types/learn'

import AppOverlay from '@/components/common/AppOverlay.vue'
import ListenButtons from './ListenButtons.vue'

/** A unit's guidebook: its key phrases (each with Listen) and short tips. */
interface Props {
  unit: PathUnit | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useLocalMessages({ learn: learnMessages })

const guide = useResource(() => (props.unit ? learnApi.guidebook(props.unit.id) : Promise.resolve(null)), false)

watch(
  () => props.unit?.id,
  (id) => {
    if (id === undefined) return
    // Another unit: never show the previous unit's phrases while this one loads.
    guide.data.value = null
    void guide.reload()
  },
)
</script>
