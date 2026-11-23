<template>
  <AppOverlay
    variant="drawer"
    :title="t('learn.guidebookTitle', { number: unit.number })"
    :subtitle="t('learn.guidebookSubtitle', { title: unit.title, titleZh: unit.titleZh })"
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
      @retry="guide.reload"
    />
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
      <button class="btn btn-primary" type="button" @click="emit('close')">
        {{ t('learn.gotIt') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { learnApi } from '@/api/learn'
import AppOverlay from '@/components/common/AppOverlay.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathUnit } from '@/types/learn'

import ListenButtons from './ListenButtons.vue'

/**
 * A unit's guidebook: its key phrases (each with Listen) and short tips. The page mounts it for one opening, so it
 * reads its unit's guidebook once.
 */
interface Props {
  unit: PathUnit
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useLocalMessages({ learn: learnMessages })

const guide = useResource(() => learnApi.guidebook(props.unit.id))
</script>
