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
      <p class="admin-path-state">
        <StatusPill :label="t(setStatusKey(set.status))" :tone="setStatusTone(set.status)" />
        <span>{{ t(setStatusNoteKey(set.status)) }}</span>
      </p>

      <section class="admin-drawer-section" aria-labelledby="set-details-preview">
        <h3 id="set-details-preview" class="admin-reader-label">{{ t('adminLearning.preview') }}</h3>
        <div class="admin-learning-preview" data-testid="set-preview">
          <template v-if="set.type === 'STORY'">
            <p v-for="(line, index) in set.storyLines.slice(0, 3)" :key="index" class="cjk-text" lang="zh-CN">
              {{ line.hanzi }}
            </p>
          </template>
          <ul v-else>
            <li v-for="(item, index) in set.items.slice(0, 4)" :key="index">
              <span v-if="item.hanzi" class="admin-learning-preview-hanzi" lang="zh-CN">{{ item.hanzi }}</span>
              <span v-if="item.prompt">{{ item.prompt }}</span>
              <span v-if="item.audioText" class="cjk-text" lang="zh-CN">({{ item.audioText }})</span>
              <span v-if="item.meaning" class="admin-learning-preview-meaning">{{ item.meaning }}</span>
              <span
                v-if="item.correctIndex !== null && item.options[item.correctIndex]"
                class="admin-learning-preview-meaning"
              >
                → {{ item.options[item.correctIndex]!.text }}
              </span>
            </li>
          </ul>
          <p v-if="set.items.length > 4" class="type-caption">
            {{ t('adminLearning.andMore', { count: set.items.length - 4 }) }}
          </p>
        </div>
      </section>

      <dl class="kv-list">
        <dt>{{ t('adminLearning.stage') }}</dt>
        <dd>{{ t(stageKey(set.stage)) }}</dd>
        <dt>{{ t('adminLearning.level') }}</dt>
        <dd>{{ set.hskLevel ? `HSK ${set.hskLevel}` : '—' }}</dd>
        <dt>{{ t('adminLearning.items') }}</dt>
        <dd>
          {{
            set.type === 'TRACING' ? t('adminLearning.strokesValue', { count: set.strokeCount ?? 0 }) : set.items.length
          }}
        </dd>
        <dt>{{ t('adminLearning.xp') }}</dt>
        <dd>+{{ set.xpReward }} XP</dd>
        <dt>{{ t('adminLearning.createdBy') }}</dt>
        <dd>{{ set.createdBy ?? t('adminLearning.platformTeam') }}</dd>
        <dt>{{ t('adminLearning.playedBy') }}</dt>
        <dd>{{ t('adminLearning.studentsCount', { count: formatNumber(set.playedBy) }, set.playedBy) }}</dd>
      </dl>

      <section class="admin-drawer-section" aria-labelledby="set-details-history">
        <h3 id="set-details-history" class="admin-reader-label">{{ t('adminLearning.history') }}</h3>
        <ol class="admin-timeline">
          <li v-for="(entry, index) in set.history" :key="index">
            <span class="admin-timeline-mark" aria-hidden="true">
              <IconGlyph :name="historyIcon(entry.action)" :size="14" />
            </span>
            <span class="admin-timeline-copy">
              <strong>{{ t(historyKey(entry.action)) }}</strong>
              <span>{{ entry.actor ?? t('adminLearning.platformTeam') }} · {{ formatDateTime(entry.at) }}</span>
            </span>
          </li>
        </ol>
      </section>
    </template>
    <template v-if="set && !loading" #footer>
      <template v-if="set.status === 'PUBLISHED'">
        <button class="btn btn-outline" type="button" data-testid="set-archive" @click="emit('action', 'archive')">
          <IconGlyph name="archive" :size="16" />{{ t('adminLearning.archive') }}
        </button>
        <button class="btn btn-outline" type="button" data-testid="set-unpublish" @click="emit('action', 'unpublish')">
          {{ t('adminLearning.unpublish') }}
        </button>
      </template>
      <button
        v-else-if="set.status === 'ARCHIVED'"
        class="btn btn-outline"
        type="button"
        data-testid="set-restore"
        @click="emit('action', 'restore')"
      >
        {{ t('adminLearning.restore') }}
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
        <IconGlyph name="edit" :size="16" />{{ t('adminLearning.edit') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ContentStatus } from '@/types/api'
import type { AdminPracticeSetDetail, PracticeHistoryAction } from '@/types/learn'
import { formatDateTime, formatNumber } from '@/utils/format'
import { practiceTypeKey, setStatusKey, setStatusTone, stageKey } from '@/utils/learnLabels'

/** A Practice set's details: a preview, its facts, who played it, its history, and what can be done with it now. */
defineProps<{ open: boolean; set: AdminPracticeSetDetail | null; loading: boolean; error: string | null }>()
const emit = defineEmits<{
  close: []
  retry: []
  edit: []
  action: [action: 'publish' | 'unpublish' | 'archive' | 'restore']
}>()

const { t } = useLocalMessages({ adminLearning: adminLearningMessages, classes: classesMessages })

const HISTORY: Readonly<Record<PracticeHistoryAction, { key: string; icon: string }>> = {
  CREATED: { key: 'adminLearning.historyCreated', icon: 'plus' },
  UPDATED: { key: 'adminLearning.historyUpdated', icon: 'edit' },
  PUBLISHED: { key: 'adminLearning.historyPublished', icon: 'send' },
  UNPUBLISHED: { key: 'adminLearning.historyUnpublished', icon: 'eyeOff' },
  ARCHIVED: { key: 'adminLearning.historyArchived', icon: 'archive' },
  RESTORED: { key: 'adminLearning.historyRestored', icon: 'retry' },
}

function historyKey(action: PracticeHistoryAction): string {
  return HISTORY[action].key
}

function historyIcon(action: PracticeHistoryAction): string {
  return HISTORY[action].icon
}

/** What the status means for students, under the status pill. */
function setStatusNoteKey(status: ContentStatus): string {
  if (status === 'PUBLISHED') return 'adminLearning.setLive'
  return status === 'ARCHIVED' ? 'adminLearning.setArchived' : 'adminLearning.setHidden'
}
</script>
