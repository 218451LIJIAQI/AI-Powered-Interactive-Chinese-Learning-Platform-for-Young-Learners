<template>
  <AppOverlay
    variant="drawer"
    :open="open"
    :title="set?.title ?? t('adminLearning.activity')"
    :subtitle="set ? t('adminLearning.practiceActivityOf', { type: t(practiceTypeKey(set.type)) }) : ''"
    test-id="set-details"
    @close="emit('close')"
  >
    <StatePanel
      v-if="loading"
      tone="loading"
      :title="t('adminLearning.activityLoading')"
      :message="t('adminLearning.activityLoadingHint')"
    />
    <StatePanel v-else-if="error" tone="error" icon="alert" :title="t('adminLearning.activityError')" :message="error">
      <template #actions>
        <button class="btn btn-primary" type="button" @click="emit('retry')">{{ t('adminLearning.retry') }}</button>
      </template>
    </StatePanel>
    <template v-else-if="set">
      <StatusPill :label="t(setStatusKey(set.status))" :tone="practiceStatusTone(set.status)" />

      <section class="admin-section" aria-labelledby="set-details-preview">
        <h3 id="set-details-preview" class="admin-section-title">{{ t('adminLearning.preview') }}</h3>
        <dl class="kv-list">
          <dt>{{ t('adminLearning.type') }}</dt>
          <dd>{{ t(practiceTypeKey(set.type)) }}</dd>
          <dt>{{ t('adminLearning.level') }}</dt>
          <dd>{{ set.hskLevel ? t('adminLearning.hskLevel', { level: set.hskLevel }) : '—' }}</dd>
          <dt>{{ t(ITEM_LABEL_KEYS[set.type]) }}</dt>
          <dd>{{ set.type === 'TRACING' ? (set.strokeCount ?? 0) : set.items.length }}</dd>
          <dt>{{ t('adminLearning.createdBy') }}</dt>
          <dd>{{ set.createdBy ?? t('adminLearning.platformTeam') }}</dd>
          <dt>{{ t('adminLearning.playedBy') }}</dt>
          <dd>{{ t('adminLearning.studentsCount', { count: formatNumber(set.playedBy) }, set.playedBy) }}</dd>
        </dl>
      </section>

      <section class="admin-section" aria-labelledby="set-details-history">
        <h3 id="set-details-history" class="admin-section-title">{{ t('adminLearning.history') }}</h3>
        <AdminEventList :events="historyRows" />
      </section>
    </template>
    <template v-if="set && !loading" #footer>
      <template v-if="set.status === 'PUBLISHED'">
        <button class="btn btn-secondary" type="button" data-testid="set-archive" @click="emit('action', 'archive')">
          <IconGlyph name="archive" :size="16" />
          <span>{{ t('adminLearning.archive') }}</span>
        </button>
        <button
          class="btn btn-secondary"
          type="button"
          data-testid="set-unpublish"
          @click="emit('action', 'unpublish')"
        >
          {{ t('adminLearning.unpublish') }}
        </button>
      </template>
      <button
        v-else-if="set.status === 'ARCHIVED'"
        class="btn btn-secondary"
        type="button"
        data-testid="set-restore"
        @click="emit('action', 'restore')"
      >
        <IconGlyph name="archive" :size="16" />
        <span>{{ t('adminLearning.restore') }}</span>
      </button>
      <button
        v-else
        class="btn btn-secondary"
        type="button"
        data-testid="set-publish"
        @click="emit('action', 'publish')"
      >
        {{ t('adminLearning.publish') }}
      </button>
      <button class="btn btn-primary" type="button" data-testid="set-edit" @click="emit('edit')">
        <IconGlyph name="edit" :size="16" />
        <span>{{ t('adminLearning.edit') }}</span>
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import AdminEventList from '@/components/admin/AdminEventList.vue'
import { practiceStatusTone } from '@/components/admin/adminDisplay'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type { AdminPracticeSetDetail, PracticeHistoryAction, PracticeType } from '@/types/learn'
import { formatDateTime, formatNumber } from '@/utils/format'
import { practiceTypeKey, setStatusKey } from '@/utils/learnLabels'

/** A Practice set's details: its facts, who played it, its history, and what can be done with it now. */
const props = defineProps<{
  open: boolean
  set: AdminPracticeSetDetail | null
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{
  close: []
  retry: []
  edit: []
  action: [action: 'publish' | 'unpublish' | 'archive' | 'restore']
}>()

const { t } = useLocalMessages({ adminLearning: adminLearningMessages })

const HISTORY_KEYS: Readonly<Record<PracticeHistoryAction, string>> = {
  CREATED: 'adminLearning.historyCreated',
  UPDATED: 'adminLearning.historyUpdated',
  PUBLISHED: 'adminLearning.historyPublished',
  UNPUBLISHED: 'adminLearning.historyUnpublished',
  ARCHIVED: 'adminLearning.historyArchived',
  RESTORED: 'adminLearning.historyRestored',
}

/** What a set's items are called in its facts. */
const ITEM_LABEL_KEYS: Readonly<Record<PracticeType, string>> = {
  FLASHCARDS: 'adminLearning.itemsCards',
  QUIZZES: 'adminLearning.itemsQuestions',
  LISTENING: 'adminLearning.itemsQuestions',
  TRACING: 'adminLearning.itemsStrokes',
  DRAG_DROP: 'adminLearning.itemsSentences',
  MATCHING: 'adminLearning.itemsPairs',
  STORY: 'adminLearning.itemsQuestions',
}

const historyRows = computed(() =>
  (props.set?.history ?? []).map((entry, index) => ({
    key: index,
    title: t(HISTORY_KEYS[entry.action]),
    meta: `${entry.actor ?? t('adminLearning.platformTeam')} · ${formatDateTime(entry.at)}`,
  })),
)
</script>
