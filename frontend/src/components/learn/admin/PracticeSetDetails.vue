<template>
  <AppOverlay
    variant="drawer"
    :title="set?.title ?? t('adminLearning.activity')"
    :subtitle="set ? t('adminLearning.practiceActivityOf', { type: t(`practiceType.${set.type}`) }) : ''"
    :busy="busy"
    test-id="set-details"
    @close="emit('close')"
  >
    <StatePanel
      v-if="loading"
      tone="loading"
      :title="t('adminLearning.activityLoading')"
      :message="t('adminLearning.activityLoadingHint')"
    />
    <StatePanel
      v-else-if="error"
      tone="error"
      :title="t('adminLearning.activityError')"
      :message="error"
      @retry="emit('retry')"
    />
    <template v-else-if="set">
      <StatusPill :label="t(setStatusKey(set.status))" :tone="contentStatusTone(set.status)" />

      <section class="admin-section" :aria-labelledby="`${baseId}-preview`">
        <h3 :id="`${baseId}-preview`" class="admin-section-title">{{ t('adminLearning.preview') }}</h3>
        <dl class="kv-list">
          <dt>{{ t('adminLearning.level') }}</dt>
          <dd>{{ set.hskLevel ? t('hsk.number', { level: set.hskLevel }) : '—' }}</dd>
          <dt>{{ t(ITEM_LABEL_KEYS[set.type]) }}</dt>
          <dd>{{ set.type === 'TRACING' ? (set.strokeCount ?? 0) : set.items.length }}</dd>
          <!-- Nobody plays a draft: it has no "Played by" (A4.17). -->
          <template v-if="set.status !== 'DRAFT'">
            <dt>{{ t('adminLearning.playedBy') }}</dt>
            <dd>{{ t('adminLearning.learnersCount', { count: formatNumber(set.playedBy) }, set.playedBy) }}</dd>
          </template>
        </dl>
      </section>

      <section class="admin-section" :aria-labelledby="`${baseId}-history`" data-testid="set-details-history">
        <h3 :id="`${baseId}-history`" class="admin-section-title">{{ t('adminLearning.history') }}</h3>
        <ol class="row-list is-flush">
          <li v-for="(entry, index) in set.history" :key="index">
            <span class="row-list-copy">
              <strong class="row-list-title">{{ t(HISTORY_KEYS[entry.action]) }}</strong>
              <span class="row-list-meta">
                {{ entry.actor ?? t('adminLearning.platformTeam') }} · {{ formatDateTime(entry.at) }}
              </span>
            </span>
          </li>
        </ol>
      </section>
    </template>
    <template v-if="set && !loading && !error" #footer>
      <!-- By the buttons, not on the page under the drawer's scrim, nor below a long history. -->
      <p v-if="actionError" class="field-error" role="alert">{{ actionError }}</p>
      <!-- A published set: Archive (rare, ghost), Unpublish, then Edit, the main action (A4.10). -->
      <template v-if="set.status === 'PUBLISHED'">
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="busy"
          :aria-busy="busy || undefined"
          data-testid="set-archive"
          @click="emit('action', 'archive')"
        >
          <IconGlyph name="archive" :size="16" />
          <span>{{ t('adminLearning.archive') }}</span>
        </button>
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="busy"
          :aria-busy="busy || undefined"
          data-testid="set-unpublish"
          @click="emit('action', 'unpublish')"
        >
          {{ t('adminLearning.unpublish') }}
        </button>
      </template>
      <button
        v-if="set.status !== 'ARCHIVED'"
        class="btn"
        :class="set.status === 'PUBLISHED' ? 'btn-primary' : 'btn-secondary'"
        type="button"
        :disabled="busy"
        data-testid="set-edit"
        @click="emit('edit')"
      >
        <IconGlyph name="edit" :size="16" />
        <span>{{ t('common.edit') }}</span>
      </button>
      <!-- A draft (A4.17) offers Edit then Publish; an archived set is read-only until restored. -->
      <template v-if="set.status !== 'PUBLISHED'">
        <button
          v-if="set.status === 'ARCHIVED'"
          class="btn btn-primary"
          type="button"
          :disabled="busy"
          :aria-busy="busy || undefined"
          data-testid="set-restore"
          @click="emit('action', 'restore')"
        >
          <IconGlyph name="archive" :size="16" />
          <span>{{ t('adminLearning.restore') }}</span>
        </button>
        <button
          v-else
          class="btn btn-primary"
          type="button"
          :disabled="busy"
          :aria-busy="busy || undefined"
          data-testid="set-publish"
          @click="emit('action', 'publish')"
        >
          {{ t('adminLearning.publish') }}
        </button>
      </template>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { useId } from 'vue'

import { contentStatusTone } from '@/components/admin/adminDisplay'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'
import type { AdminPracticeSetDetail, PracticeHistoryAction, PracticeSetAction, PracticeType } from '@/types/learn'
import { formatDateTime, formatNumber } from '@/utils/format'
import { setStatusKey } from '@/utils/learnLabels'

/**
 * A Practice set's details (A4.10, A4.17): its level and size, who played it once it was published, its history (what
 * happened, who and when), and what can be done with it now; an action on it that fails says so here.
 */
defineProps<{
  set: AdminPracticeSetDetail | null
  loading: boolean
  error: string | null
  /** An action on the set is on its way. */
  busy: boolean
  /** Why the last action on this set failed. */
  actionError: string | null
}>()
const emit = defineEmits<{
  close: []
  retry: []
  edit: []
  action: [action: PracticeSetAction]
}>()

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminPracticeLibraryMessages] })
const baseId = useId()

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
  LISTENING: 'adminLearning.itemsQuestions',
  TRACING: 'adminLearning.itemsStrokes',
  DRAG_DROP: 'adminLearning.itemsSentences',
  MATCHING: 'adminLearning.itemsPairs',
  TYPING: 'adminLearning.itemsWords',
}
</script>
