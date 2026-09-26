<template>
  <div class="page-stack admin-activity-page">
    <PageHeaderBlock
      :title="t('adminActivityLibrary.title')"
      :description="t('adminActivityLibrary.description')"
      icon="practice"
    >
      <template #actions>
        <button
          class="teacher-outline-button admin-activity-header-button"
          type="button"
          @click="reloadFeed('keep')"
          :disabled="pageBusy || previewReadOnly"
        >
          <IconGlyph name="refresh" :size="16" />
          <span>{{ refreshFeedLabel }}</span>
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :error="pageError" :success="actionSuccess" />

    <section class="panel admin-activity-toolbar">
      <div class="admin-activity-filter-grid">
        <label class="field">
          <span>{{ t('adminActivityLibrary.activityType') }}</span>
          <select v-model="filters.type" class="select">
            <option value="">{{ t('adminActivityLibrary.allTypes') }}</option>
            <option v-for="item in activityTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="field">
          <span>{{ t('adminActivityLibrary.status') }}</span>
          <select v-model="filters.status" class="select">
            <option value="">{{ t('adminActivityLibrary.allStatuses') }}</option>
            <option value="DRAFT">{{ t('adminActivityLibrary.draft') }}</option>
            <option value="PUBLISHED">{{ t('adminActivityLibrary.published') }}</option>
            <option value="ARCHIVED">{{ t('adminActivityLibrary.archived') }}</option>
          </select>
        </label>

        <label class="field">
          <span>{{ t('adminActivityLibrary.ageBand') }}</span>
          <select v-model="filters.ageBand" class="select">
            <option value="">{{ t('adminActivityLibrary.allAges') }}</option>
            <option v-for="item in ageBandOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="field">
          <span>{{ t('adminActivityLibrary.difficulty') }}</span>
          <select v-model="filters.difficulty" class="select">
            <option value="">{{ t('adminActivityLibrary.allLevels') }}</option>
            <option v-for="item in difficultyOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="field admin-activity-filter-wide">
          <span>{{ t('adminActivityLibrary.keyword') }}</span>
          <input v-model="filters.keyword" class="input" :placeholder="t('adminActivityLibrary.keywordPlaceholder')" />
        </label>

        <label class="field">
          <span>{{ t('adminActivityLibrary.topic') }}</span>
          <input v-model="filters.topic" class="input" :placeholder="t('adminActivityLibrary.topicPlaceholder')" />
        </label>
      </div>

      <div class="admin-activity-toolbar-actions">
        <button class="btn btn-secondary" type="button" @click="resetFilters" :disabled="pageBusy">
          {{ t('adminActivityLibrary.reset') }}
        </button>
        <button class="btn btn-primary" type="button" @click="applyFilters" :disabled="pageBusy">
          {{ pageBusy ? t('adminActivityLibrary.loading') : t('adminActivityLibrary.applyFilters') }}
        </button>
      </div>
    </section>

    <section class="admin-activity-stats">
      <article v-for="item in statCards" :key="item.label" class="admin-activity-stat">
        <div class="admin-activity-stat-icon" :class="`is-${item.tone}`">
          <IconGlyph :name="item.icon" :size="20" />
        </div>
        <div class="stack u-gap-4">
          <span class="admin-activity-stat-label">{{ item.label }}</span>
          <strong class="admin-activity-stat-value">{{ item.value }}</strong>
          <small class="admin-activity-stat-copy">{{ item.copy }}</small>
        </div>
      </article>
    </section>

    <section class="admin-activity-layout">
      <article class="panel admin-activity-list-panel">
        <div class="row-between admin-activity-panel-head">
          <div class="student-section-heading">
            <strong>{{ t('adminActivityLibrary.unifiedFeed') }}</strong>
            <span>{{ t('adminActivityLibrary.totalItems', { count: formatNumber(feed.totalItems) }) }}</span>
          </div>
          <button class="btn btn-secondary" type="button" @click="startNewDraft()" :disabled="previewReadOnly">
            {{ t('adminActivityLibrary.newDraft') }}
          </button>
        </div>

        <StatePanel
          v-if="previewReadOnly"
          icon="practice"
          :title="t('adminActivityLibrary.liveAdminRequired')"
          :message="t('adminActivityLibrary.liveAdminMessage')"
        />

        <template v-else>
          <div v-if="feed.items.length" class="admin-activity-list">
            <button
              v-for="item in feed.items"
              :key="`${item.type}-${item.id}`"
              type="button"
              class="admin-activity-row"
              :class="{ 'is-active': item.id === selectedId && item.type === selectedType }"
              @click="selectActivity(item.type, item.id)"
            >
              <div class="admin-activity-row-copy">
                <div class="row admin-activity-row-top">
                  <strong>{{ item.title || fallbackActivityTitle(item.type) }}</strong>
                  <StatusPill :label="activityStatusLabel(item.status)" :tone="statusTone(item.status)" />
                </div>
                <span>{{ activityTypeLabel(item.type) }}</span>
                <small>{{ activitySummaryMeta(item) }}</small>
              </div>
            </button>
          </div>

          <StatePanel
            v-else-if="!feedResource.loading.value"
            icon="practice"
            :title="t('adminActivityLibrary.noMatches')"
            :message="t('adminActivityLibrary.noMatchesMessage')"
          />

          <div v-if="feed.totalPages > 1" class="admin-activity-pagination">
            <button
              class="admin-activity-page-button"
              type="button"
              :disabled="currentPage <= 1 || pageBusy"
              @click="goToPage(currentPage - 1)"
            >
              {{ t('adminActivityLibrary.previous') }}
            </button>
            <span>{{ t('adminActivityLibrary.pageOf', { page: currentPage, total: feed.totalPages }) }}</span>
            <button
              class="admin-activity-page-button"
              type="button"
              :disabled="currentPage >= feed.totalPages || pageBusy"
              @click="goToPage(currentPage + 1)"
            >
              {{ t('adminActivityLibrary.next') }}
            </button>
          </div>
        </template>
      </article>

      <article class="panel admin-activity-editor-panel">
        <div class="row-between admin-activity-panel-head">
          <div class="student-section-heading">
            <strong>{{ editorTitle }}</strong>
            <span>{{ editorSubtitle }}</span>
          </div>
          <div class="admin-activity-panel-actions">
            <StatusPill
              v-if="hasSelectedRecord && selectedSummary"
              :label="activityStatusLabel(selectedSummary.status)"
              :tone="statusTone(selectedSummary.status)"
            />
            <button
              class="teacher-outline-button"
              type="button"
              @click="startNewDraft(form.type)"
              :disabled="previewReadOnly"
            >
              {{ t('adminActivityLibrary.newType', { type: activityTypeLabel(form.type) }) }}
            </button>
          </div>
        </div>

        <StatePanel
          v-if="previewReadOnly"
          icon="verification"
          :title="t('adminActivityLibrary.readOnlyPreview')"
          :message="t('adminActivityLibrary.readOnlyPreviewMessage')"
        />

        <template v-else>
          <div class="admin-activity-editor-grid">
            <label class="field">
              <span>{{ t('adminActivityLibrary.activityType') }}</span>
              <select
                v-model="form.type"
                class="select"
                :disabled="hasSelectedRecord || formBusy"
                @change="changeDraftType"
              >
                <option v-for="item in activityTypeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>{{ titleFieldLabel }}</span>
              <input v-model="form.title" class="input" :placeholder="titleFieldPlaceholder" />
            </label>

            <label class="field">
              <span>{{ t('adminActivityLibrary.hskLevel') }}</span>
              <select v-model="form.hskLevel" class="select">
                <option :value="null">{{ t('adminActivityLibrary.notSet') }}</option>
                <option v-for="level in hskLevels" :key="level" :value="level">HSK {{ level }}</option>
              </select>
            </label>

            <label class="field">
              <span>{{ t('adminActivityLibrary.ageBand') }}</span>
              <select v-model="form.ageBand" class="select">
                <option value="">{{ t('adminActivityLibrary.notSet') }}</option>
                <option v-for="item in ageBandOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>

            <label class="field">
              <span>{{ t('adminActivityLibrary.difficulty') }}</span>
              <select v-model="form.difficulty" class="select">
                <option value="">{{ t('adminActivityLibrary.notSet') }}</option>
                <option v-for="item in difficultyOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>{{ t('adminActivityLibrary.topic') }}</span>
              <input v-model="form.topic" class="input" :placeholder="t('adminActivityLibrary.topicPlaceholder')" />
            </label>

            <label class="field admin-activity-editor-grid-wide">
              <span>{{ t('adminActivityLibrary.tags') }}</span>
              <input v-model="form.tagsText" class="input" :placeholder="t('adminActivityLibrary.tagsPlaceholder')" />
            </label>

            <label class="field">
              <span>{{ t('adminActivityLibrary.xpReward') }}</span>
              <input v-model.number="form.xpReward" class="input" type="number" min="0" />
            </label>

            <label class="field">
              <span>{{ t('adminActivityLibrary.estimatedSeconds') }}</span>
              <input v-model.number="form.estimatedSeconds" class="input" type="number" min="0" />
            </label>
          </div>

          <section v-if="form.type === 'FLASHCARD'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>{{ t('adminActivityLibrary.flashcardContent') }}</strong>
              <span>{{ t('adminActivityLibrary.flashcardContentHint') }}</span>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field">
                <span>{{ t('adminActivityLibrary.frontText') }}</span>
                <textarea v-model="form.frontText" class="textarea" rows="3" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.backText') }}</span>
                <textarea v-model="form.backText" class="textarea" rows="3" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.pinyin') }}</span>
                <input v-model="form.pinyin" class="input" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.englishMeaning') }}</span>
                <input v-model="form.englishMeaning" class="input" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.malayMeaning') }}</span>
                <input v-model="form.malayMeaning" class="input" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.sortOrder') }}</span>
                <input v-model.number="form.sortOrder" class="input" type="number" min="0" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.imageUrl') }}</span>
                <input v-model="form.imageUrl" class="input" type="url" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.audioUrl') }}</span>
                <input v-model="form.audioUrl" class="input" type="url" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>{{ t('adminActivityLibrary.extraPayload') }}</span>
                <textarea v-model="form.extraPayload" class="textarea" rows="3" />
              </label>
            </div>
          </section>

          <section v-else-if="form.type === 'MATCHING_GAME'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>{{ t('adminActivityLibrary.matchingPairs') }}</strong>
              <button class="btn btn-secondary" type="button" @click="addMatchingItem">
                {{ t('adminActivityLibrary.addPair') }}
              </button>
            </div>

            <div class="admin-activity-repeat-list">
              <article
                v-for="(item, index) in form.matchingItems"
                :key="`matching-${index}`"
                class="admin-activity-repeat-card"
              >
                <div class="row-between">
                  <strong>{{ t('adminActivityLibrary.pairNumber', { number: index + 1 }) }}</strong>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    @click="removeMatchingItem(index)"
                    :disabled="form.matchingItems.length <= 1"
                  >
                    {{ t('adminActivityLibrary.remove') }}
                  </button>
                </div>
                <div class="admin-activity-editor-grid">
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.leftText') }}</span>
                    <input v-model="item.leftText" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.rightText') }}</span>
                    <input v-model="item.rightText" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.leftImageUrl') }}</span>
                    <input v-model="item.leftImageUrl" class="input" type="url" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.rightImageUrl') }}</span>
                    <input v-model="item.rightImageUrl" class="input" type="url" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.pinyin') }}</span>
                    <input v-model="item.pinyin" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.sortOrder') }}</span>
                    <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
                  </label>
                </div>
              </article>
            </div>
          </section>

          <section v-else-if="form.type === 'LISTENING_EXERCISE'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>{{ t('adminActivityLibrary.listeningContent') }}</strong>
              <span>{{ t('adminActivityLibrary.listeningContentHint') }}</span>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field">
                <span>{{ t('adminActivityLibrary.audioUrl') }}</span>
                <input v-model="form.audioUrl" class="input" type="url" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>{{ t('adminActivityLibrary.questionPayload') }}</span>
                <textarea
                  v-model="form.questionPayload"
                  class="textarea"
                  rows="5"
                  :placeholder="t('adminActivityLibrary.listeningPayloadPlaceholder')"
                />
              </label>
            </div>
          </section>

          <section v-else-if="form.type === 'TRACING_EXERCISE'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>{{ t('adminActivityLibrary.tracingContent') }}</strong>
              <span>{{ t('adminActivityLibrary.tracingContentHint') }}</span>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field">
                <span>{{ t('adminActivityLibrary.worksheetUrl') }}</span>
                <input v-model="form.worksheetUrl" class="input" type="url" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.targetCharacter') }}</span>
                <input v-model="form.targetCharacter" class="input" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.pinyin') }}</span>
                <input v-model="form.pinyin" class="input" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.meaning') }}</span>
                <input v-model="form.meaning" class="input" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>{{ t('adminActivityLibrary.instructions') }}</span>
                <textarea v-model="form.instructions" class="textarea" rows="4" />
              </label>
            </div>
          </section>

          <section v-else-if="form.type === 'DRAG_DROP_ACTIVITY'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>{{ t('adminActivityLibrary.dragDropActivity') }}</strong>
              <button class="btn btn-secondary" type="button" @click="addDragItem">
                {{ t('adminActivityLibrary.addDraggableItem') }}
              </button>
            </div>

            <label class="field">
              <span>{{ t('adminActivityLibrary.instructions') }}</span>
              <textarea v-model="form.instructions" class="textarea" rows="4" />
            </label>

            <div class="admin-activity-repeat-list">
              <article
                v-for="(item, index) in form.dragItems"
                :key="`drag-${index}`"
                class="admin-activity-repeat-card"
              >
                <div class="row-between">
                  <strong>{{ t('adminActivityLibrary.draggableItemNumber', { number: index + 1 }) }}</strong>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    @click="removeDragItem(index)"
                    :disabled="form.dragItems.length <= 1"
                  >
                    {{ t('adminActivityLibrary.remove') }}
                  </button>
                </div>
                <div class="admin-activity-editor-grid">
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.dragText') }}</span>
                    <input v-model="item.dragText" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.targetZone') }}</span>
                    <input v-model="item.targetZone" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.pinyin') }}</span>
                    <input v-model="item.pinyin" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.imageUrl') }}</span>
                    <input v-model="item.imageUrl" class="input" type="url" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.sortOrder') }}</span>
                    <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
                  </label>
                </div>
              </article>
            </div>
          </section>

          <section v-else class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>{{ t('adminActivityLibrary.storyActivity') }}</strong>
              <button class="btn btn-secondary" type="button" @click="addStoryQuestion">
                {{ t('adminActivityLibrary.addQuestion') }}
              </button>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field admin-activity-editor-grid-wide">
                <span>{{ t('adminActivityLibrary.instructions') }}</span>
                <textarea v-model="form.instructions" class="textarea" rows="3" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>{{ t('adminActivityLibrary.storyText') }}</span>
                <textarea v-model="form.storyText" class="textarea" rows="8" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.pinyin') }}</span>
                <textarea v-model="form.storyPinyin" class="textarea" rows="4" />
              </label>
              <label class="field">
                <span>{{ t('adminActivityLibrary.translation') }}</span>
                <textarea v-model="form.storyTranslation" class="textarea" rows="4" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>{{ t('adminActivityLibrary.imageUrl') }}</span>
                <input v-model="form.imageUrl" class="input" type="url" />
              </label>
            </div>

            <div class="admin-activity-repeat-list">
              <article
                v-for="(item, index) in form.storyQuestions"
                :key="`story-${index}`"
                class="admin-activity-repeat-card"
              >
                <div class="row-between">
                  <strong>{{ t('adminActivityLibrary.questionNumber', { number: index + 1 }) }}</strong>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    @click="removeStoryQuestion(index)"
                    :disabled="form.storyQuestions.length <= 1"
                  >
                    {{ t('adminActivityLibrary.remove') }}
                  </button>
                </div>
                <div class="admin-activity-editor-grid">
                  <label class="field admin-activity-editor-grid-wide">
                    <span>{{ t('adminActivityLibrary.questionText') }}</span>
                    <input v-model="item.questionText" class="input" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.optionsPerLine') }}</span>
                    <textarea v-model="item.optionsText" class="textarea" rows="5" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.explanation') }}</span>
                    <textarea v-model="item.explanation" class="textarea" rows="5" />
                  </label>
                  <label class="field">
                    <span>{{ t('adminActivityLibrary.sortOrder') }}</span>
                    <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
                  </label>
                </div>
              </article>
            </div>
          </section>

          <div class="admin-activity-editor-footer">
            <div class="admin-activity-editor-meta">
              <span v-if="hasSelectedRecord && selectedSummary">
                {{ t('adminActivityLibrary.createdAt', { date: formatDateTime(selectedSummary.createdAt) }) }}
              </span>
              <span v-if="hasSelectedRecord && selectedSummary">
                {{ t('adminActivityLibrary.updatedAt', { date: formatDateTime(selectedSummary.updatedAt) }) }}
              </span>
              <span v-if="selectedSummary?.createdByDisplayName">
                {{ t('adminActivityLibrary.createdBy', { name: selectedSummary.createdByDisplayName }) }}
              </span>
            </div>

            <div class="admin-activity-editor-actions">
              <button class="btn btn-secondary" type="button" @click="startNewDraft(form.type)" :disabled="formBusy">
                {{ t('adminActivityLibrary.clearDraft') }}
              </button>
              <button class="btn btn-primary" type="button" @click="saveActivity" :disabled="formBusy">
                {{ saveButtonLabel }}
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                @click="updateStatus('DRAFT')"
                :disabled="!hasSelectedRecord || formBusy"
              >
                {{ t('adminActivityLibrary.saveAsDraft') }}
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                @click="updateStatus('PUBLISHED')"
                :disabled="!hasSelectedRecord || formBusy"
              >
                {{ t('adminActivityLibrary.publish') }}
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                @click="updateStatus('ARCHIVED')"
                :disabled="!hasSelectedRecord || formBusy"
              >
                {{ t('adminActivityLibrary.archive') }}
              </button>
              <button
                class="btn btn-danger"
                type="button"
                @click="deleteActivity"
                :disabled="!hasSelectedRecord || formBusy"
              >
                {{ deleteButtonLabel }}
              </button>
            </div>
          </div>
        </template>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  adminApi,
  type AdminDragDropActivityPayload,
  type AdminDragDropItemPayload,
  type AdminFlashcardPayload,
  type AdminListeningExercisePayload,
  type AdminMatchingGameItemPayload,
  type AdminMatchingGamePayload,
  type AdminStoryActivityPayload,
  type AdminStoryQuestionPayload,
  type AdminTracingExercisePayload,
} from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminActivityLibraryMessages } from '@/plugins/adminActivityLibraryMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  ActivityDifficulty,
  AdminActivitySummaryResponse,
  AdminActivityType,
  AgeBand,
  ContentStatus,
  PageResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime, formatNumber } from '@/utils/format'

type StatusMutation = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

interface MatchingItemDraft {
  leftText: string
  rightText: string
  leftImageUrl: string
  rightImageUrl: string
  pinyin: string
  sortOrder: number
}

interface DragItemDraft {
  dragText: string
  targetZone: string
  pinyin: string
  imageUrl: string
  sortOrder: number
}

interface StoryQuestionDraft {
  questionText: string
  optionsText: string
  explanation: string
  sortOrder: number
}

interface ActivityEditorForm {
  type: AdminActivityType
  title: string
  hskLevel: number | null
  ageBand: AgeBand | ''
  difficulty: ActivityDifficulty | ''
  topic: string
  tagsText: string
  xpReward: number | null
  estimatedSeconds: number | null
  frontText: string
  backText: string
  pinyin: string
  englishMeaning: string
  malayMeaning: string
  imageUrl: string
  audioUrl: string
  sortOrder: number | null
  extraPayload: string
  questionPayload: string
  worksheetUrl: string
  targetCharacter: string
  meaning: string
  instructions: string
  storyText: string
  storyPinyin: string
  storyTranslation: string
  matchingItems: MatchingItemDraft[]
  dragItems: DragItemDraft[]
  storyQuestions: StoryQuestionDraft[]
}

const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { adminActivityLibrary: adminActivityLibraryMessages.en },
    ms: { adminActivityLibrary: adminActivityLibraryMessages.ms },
    zh: { adminActivityLibrary: adminActivityLibraryMessages.zh },
  },
})

const emptyFeed: PageResponse<AdminActivitySummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
}

const activityTypeOptions = computed<Array<{ value: AdminActivityType; label: string }>>(() => [
  { value: 'FLASHCARD', label: t('adminActivityLibrary.flashcard') },
  { value: 'MATCHING_GAME', label: t('adminActivityLibrary.matchingGame') },
  { value: 'LISTENING_EXERCISE', label: t('adminActivityLibrary.listeningExercise') },
  { value: 'TRACING_EXERCISE', label: t('adminActivityLibrary.tracingExercise') },
  { value: 'DRAG_DROP_ACTIVITY', label: t('adminActivityLibrary.dragDropType') },
  { value: 'STORY_ACTIVITY', label: t('adminActivityLibrary.storyType') },
])

const ageBandOptions = computed<Array<{ value: AgeBand; label: string }>>(() => [
  { value: 'KINDERGARTEN', label: t('adminActivityLibrary.kindergarten') },
  { value: 'LOWER_PRIMARY', label: t('adminActivityLibrary.lowerPrimary') },
  { value: 'UPPER_PRIMARY', label: t('adminActivityLibrary.upperPrimary') },
  { value: 'ALL_AGES', label: t('adminActivityLibrary.allAgesValue') },
])

const difficultyOptions = computed<Array<{ value: ActivityDifficulty; label: string }>>(() => [
  { value: 'EASY', label: t('adminActivityLibrary.easy') },
  { value: 'MEDIUM', label: t('adminActivityLibrary.medium') },
  { value: 'HARD', label: t('adminActivityLibrary.hard') },
])

const hskLevels = [1, 2, 3, 4, 5, 6] as const

const filters = reactive({
  type: '' as AdminActivityType | '',
  status: '' as ContentStatus | '',
  ageBand: '' as AgeBand | '',
  difficulty: '' as ActivityDifficulty | '',
  keyword: '',
  topic: '',
})

const currentPage = ref(1)
const selectedType = ref<AdminActivityType>('FLASHCARD')
const selectedId = ref<number | null>(null)
const detailLoading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const form = reactive<ActivityEditorForm>(createEmptyForm('FLASHCARD'))

const previewReadOnly = computed(() => auth.isPreviewSession && !auth.isAuthenticated)

const feedResource = useResource(
  () =>
    adminApi.activityFeed({
      type: filters.type || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      topic: filters.topic.trim() || undefined,
      ageBand: filters.ageBand || undefined,
      difficulty: filters.difficulty || undefined,
      page: currentPage.value - 1,
      size: 12,
    }),
  undefined,
  false,
)

const feed = computed(() => feedResource.data.value ?? emptyFeed)
const selectedSummary = computed(
  () => feed.value.items.find((item) => item.id === selectedId.value && item.type === selectedType.value) ?? null,
)
const hasSelectedRecord = computed(() => selectedId.value !== null)
const pageBusy = computed(() => feedResource.loading.value || detailLoading.value)
const formBusy = computed(() => saving.value || deleting.value || detailLoading.value)
const pageError = computed(() => actionError.value || feedResource.error.value)
const statCards = computed(() => {
  const pageItems = feed.value.items
  const statusCounts = feed.value.items.reduce(
    (counts, item) => {
      if (item.status === 'PUBLISHED') counts.published += 1
      else if (item.status === 'DRAFT') counts.drafts += 1
      else if (item.status === 'ARCHIVED') counts.archived += 1
      return counts
    },
    { published: 0, drafts: 0, archived: 0 },
  )
  const typeCount = new Set(pageItems.map((item) => item.type)).size

  return [
    {
      label: t('adminActivityLibrary.filteredTotal'),
      value: formatNumber(feed.value.totalItems),
      copy: t('adminActivityLibrary.filteredTotalCopy'),
      icon: 'practice',
      tone: 'purple',
    },
    {
      label: t('adminActivityLibrary.published'),
      value: formatNumber(statusCounts.published),
      copy: t('adminActivityLibrary.publishedCopy'),
      icon: 'status',
      tone: 'green',
    },
    {
      label: t('adminActivityLibrary.drafts'),
      value: formatNumber(statusCounts.drafts),
      copy: t('adminActivityLibrary.draftsCopy'),
      icon: 'content',
      tone: 'blue',
    },
    {
      label: t('adminActivityLibrary.archived'),
      value: formatNumber(statusCounts.archived),
      copy: t('adminActivityLibrary.archivedCopy'),
      icon: 'history',
      tone: 'orange',
    },
    {
      label: t('adminActivityLibrary.visibleTypes'),
      value: formatNumber(typeCount),
      copy: t('adminActivityLibrary.visibleTypesCopy'),
      icon: 'analytics',
      tone: 'gold',
    },
  ]
})

const editorTitle = computed(() =>
  hasSelectedRecord.value
    ? t('adminActivityLibrary.details', { type: activityTypeLabel(selectedType.value) })
    : t('adminActivityLibrary.newActivityDraft'),
)
const editorSubtitle = computed(() => {
  if (hasSelectedRecord.value && selectedSummary.value) {
    return `${activityStatusLabel(selectedSummary.value.status)} · ${activitySummaryMeta(selectedSummary.value)}`
  }
  return t('adminActivityLibrary.editorNewHint', { type: activityTypeLabel(form.type) })
})
const saveButtonLabel = computed(() =>
  saving.value
    ? t('adminActivityLibrary.saving')
    : hasSelectedRecord.value
      ? t('adminActivityLibrary.saveChanges')
      : t('adminActivityLibrary.createActivity'),
)
const deleteButtonLabel = computed(() =>
  deleting.value ? t('adminActivityLibrary.deleting') : t('adminActivityLibrary.delete'),
)
const refreshFeedLabel = computed(() => {
  if (previewReadOnly.value) return t('adminActivityLibrary.previewFeed')
  return pageBusy.value ? t('adminActivityLibrary.refreshing') : t('adminActivityLibrary.refreshFeed')
})
const titleFieldLabel = computed(() =>
  form.type === 'FLASHCARD' ? t('adminActivityLibrary.optionalTitle') : t('adminActivityLibrary.titleLabel'),
)
const titleFieldPlaceholder = computed(() =>
  form.type === 'FLASHCARD'
    ? t('adminActivityLibrary.optionalFlashcardTitle')
    : t('adminActivityLibrary.activityTitle'),
)

onMounted(() => {
  if (previewReadOnly.value) {
    return
  }

  void reloadFeed('first')
})

function createEmptyMatchingItem(index: number): MatchingItemDraft {
  return {
    leftText: '',
    rightText: '',
    leftImageUrl: '',
    rightImageUrl: '',
    pinyin: '',
    sortOrder: index,
  }
}

function createEmptyDragItem(index: number): DragItemDraft {
  return {
    dragText: '',
    targetZone: '',
    pinyin: '',
    imageUrl: '',
    sortOrder: index,
  }
}

function createEmptyStoryQuestion(index: number): StoryQuestionDraft {
  return {
    questionText: '',
    optionsText: '',
    explanation: '',
    sortOrder: index,
  }
}

function createEmptyForm(type: AdminActivityType): ActivityEditorForm {
  return {
    type,
    title: '',
    hskLevel: null,
    ageBand: '',
    difficulty: '',
    topic: '',
    tagsText: '',
    xpReward: null,
    estimatedSeconds: null,
    frontText: '',
    backText: '',
    pinyin: '',
    englishMeaning: '',
    malayMeaning: '',
    imageUrl: '',
    audioUrl: '',
    sortOrder: null,
    extraPayload: '',
    questionPayload: '',
    worksheetUrl: '',
    targetCharacter: '',
    meaning: '',
    instructions: '',
    storyText: '',
    storyPinyin: '',
    storyTranslation: '',
    matchingItems: [createEmptyMatchingItem(0)],
    dragItems: [createEmptyDragItem(0)],
    storyQuestions: [createEmptyStoryQuestion(0)],
  }
}

function applyFormState(next: ActivityEditorForm): void {
  Object.assign(form, next)
}

function optionalString(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function optionalNumber(value: number | null): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function parseTags(): string[] {
  return form.tagsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function activityTypeLabel(type: AdminActivityType): string {
  return activityTypeOptions.value.find((item) => item.value === type)?.label ?? humanizeEnum(type)
}

function fallbackActivityTitle(type: AdminActivityType): string {
  return t('adminActivityLibrary.draftFallback', { type: activityTypeLabel(type) })
}

function humanizeEnum(value: string): string {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function statusTone(status: ContentStatus): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'primary'
  if (status === 'REJECTED') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

function activityStatusLabel(status: ContentStatus | StatusMutation): string {
  const labels: Partial<Record<ContentStatus | StatusMutation, string>> = {
    DRAFT: t('adminActivityLibrary.draft'),
    PUBLISHED: t('adminActivityLibrary.published'),
    ARCHIVED: t('adminActivityLibrary.archived'),
    REJECTED: t('adminActivityLibrary.rejected'),
    SUBMITTED: t('adminActivityLibrary.submitted'),
  }
  return labels[status] ?? humanizeEnum(status)
}

function ageBandLabel(ageBand: AgeBand): string {
  return ageBandOptions.value.find((item) => item.value === ageBand)?.label ?? humanizeEnum(ageBand)
}

function difficultyLabel(difficulty: ActivityDifficulty): string {
  return difficultyOptions.value.find((item) => item.value === difficulty)?.label ?? humanizeEnum(difficulty)
}

function activitySummaryMeta(item: AdminActivitySummaryResponse): string {
  const parts = [item.topic?.trim() || t('adminActivityLibrary.noTopic')]
  if (item.hskLevel) parts.push(`HSK ${item.hskLevel}`)
  if (item.ageBand) parts.push(ageBandLabel(item.ageBand))
  if (item.difficulty) parts.push(difficultyLabel(item.difficulty))
  if (item.tags.length) parts.push(item.tags.slice(0, 2).join(', '))
  return parts.join(' · ')
}

function resetFilters(): void {
  filters.type = ''
  filters.status = ''
  filters.ageBand = ''
  filters.difficulty = ''
  filters.keyword = ''
  filters.topic = ''
  currentPage.value = 1
  actionError.value = null
  actionSuccess.value = null
  if (!previewReadOnly.value) {
    void reloadFeed('first')
  }
}

function applyFilters(): void {
  currentPage.value = 1
  actionError.value = null
  actionSuccess.value = null
  if (!previewReadOnly.value) {
    void reloadFeed('first')
  }
}

function goToPage(page: number): void {
  currentPage.value = page
  void reloadFeed('first')
}

function changeDraftType(): void {
  if (hasSelectedRecord.value) {
    return
  }
  startNewDraft(form.type)
}

function addMatchingItem(): void {
  form.matchingItems.push(createEmptyMatchingItem(form.matchingItems.length))
}

function removeMatchingItem(index: number): void {
  if (form.matchingItems.length <= 1) return
  form.matchingItems.splice(index, 1)
}

function addDragItem(): void {
  form.dragItems.push(createEmptyDragItem(form.dragItems.length))
}

function removeDragItem(index: number): void {
  if (form.dragItems.length <= 1) return
  form.dragItems.splice(index, 1)
}

function addStoryQuestion(): void {
  form.storyQuestions.push(createEmptyStoryQuestion(form.storyQuestions.length))
}

function removeStoryQuestion(index: number): void {
  if (form.storyQuestions.length <= 1) return
  form.storyQuestions.splice(index, 1)
}

function startNewDraft(type: AdminActivityType = 'FLASHCARD'): void {
  selectedId.value = null
  selectedType.value = type
  applyFormState(createEmptyForm(type))
  actionError.value = null
  actionSuccess.value = null
}

async function reloadFeed(
  selection: 'first' | 'keep' | { id: number; type: AdminActivityType } = 'keep',
): Promise<void> {
  if (previewReadOnly.value) {
    actionError.value = null
    actionSuccess.value = t('adminActivityLibrary.previewRefresh')
    return
  }

  await feedResource.reload()

  const items = feed.value.items
  if (!items.length) {
    if (selection !== 'keep') {
      startNewDraft(form.type)
    }
    return
  }

  if (selection === 'first') {
    await selectActivity(items[0].type, items[0].id)
    return
  }

  if (selection === 'keep') {
    if (hasSelectedRecord.value) {
      const match = items.find((item) => item.id === selectedId.value && item.type === selectedType.value)
      if (match) {
        return
      }
      await selectActivity(items[0].type, items[0].id)
      return
    }

    return
  }

  const match = items.find((item) => item.id === selection.id && item.type === selection.type)
  if (match) {
    await selectActivity(match.type, match.id)
    return
  }

  await selectActivity(items[0].type, items[0].id)
}

async function selectActivity(type: AdminActivityType, id: number): Promise<void> {
  detailLoading.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    selectedType.value = type
    selectedId.value = id

    switch (type) {
      case 'FLASHCARD': {
        const detail = await adminApi.flashcard(id)
        applyFormState({
          ...createEmptyForm(type),
          type,
          title: detail.title || '',
          hskLevel: detail.hskLevel ?? null,
          ageBand: detail.ageBand ?? '',
          difficulty: detail.difficulty ?? '',
          topic: detail.topic || '',
          tagsText: detail.tags.join(', '),
          xpReward: detail.xpReward ?? null,
          estimatedSeconds: detail.estimatedSeconds ?? null,
          frontText: detail.frontText,
          backText: detail.backText,
          pinyin: detail.pinyin || '',
          englishMeaning: detail.englishMeaning || '',
          malayMeaning: detail.malayMeaning || '',
          imageUrl: detail.imageUrl || '',
          audioUrl: detail.audioUrl || '',
          sortOrder: detail.sortOrder ?? null,
          extraPayload: detail.extraPayload || '',
        })
        break
      }
      case 'MATCHING_GAME': {
        const detail = await adminApi.matchingGame(id)
        applyFormState({
          ...createEmptyForm(type),
          type,
          title: detail.title,
          hskLevel: detail.hskLevel ?? null,
          ageBand: detail.ageBand ?? '',
          difficulty: detail.difficulty ?? '',
          topic: detail.topic || '',
          tagsText: detail.tags.join(', '),
          xpReward: detail.xpReward ?? null,
          estimatedSeconds: detail.estimatedSeconds ?? null,
          matchingItems: detail.items.length
            ? detail.items.map((item, index) => ({
                leftText: item.leftText,
                rightText: item.rightText,
                leftImageUrl: item.leftImageUrl || '',
                rightImageUrl: item.rightImageUrl || '',
                pinyin: item.pinyin || '',
                sortOrder: item.sortOrder ?? index,
              }))
            : [createEmptyMatchingItem(0)],
        })
        break
      }
      case 'LISTENING_EXERCISE': {
        const detail = await adminApi.listeningExercise(id)
        applyFormState({
          ...createEmptyForm(type),
          type,
          title: detail.title,
          hskLevel: detail.hskLevel ?? null,
          ageBand: detail.ageBand ?? '',
          difficulty: detail.difficulty ?? '',
          topic: detail.topic || '',
          tagsText: detail.tags.join(', '),
          xpReward: detail.xpReward ?? null,
          estimatedSeconds: detail.estimatedSeconds ?? null,
          audioUrl: detail.audioUrl || '',
          questionPayload: detail.questionPayload || '',
        })
        break
      }
      case 'TRACING_EXERCISE': {
        const detail = await adminApi.tracingExercise(id)
        applyFormState({
          ...createEmptyForm(type),
          type,
          title: detail.title,
          hskLevel: detail.hskLevel ?? null,
          ageBand: detail.ageBand ?? '',
          difficulty: detail.difficulty ?? '',
          topic: detail.topic || '',
          tagsText: detail.tags.join(', '),
          xpReward: detail.xpReward ?? null,
          estimatedSeconds: detail.estimatedSeconds ?? null,
          worksheetUrl: detail.worksheetUrl || '',
          targetCharacter: detail.targetCharacter || '',
          pinyin: detail.pinyin || '',
          meaning: detail.meaning || '',
          instructions: detail.instructions || '',
        })
        break
      }
      case 'DRAG_DROP_ACTIVITY': {
        const detail = await adminApi.dragDropActivity(id)
        applyFormState({
          ...createEmptyForm(type),
          type,
          title: detail.title,
          hskLevel: detail.hskLevel ?? null,
          ageBand: detail.ageBand ?? '',
          difficulty: detail.difficulty ?? '',
          topic: detail.topic || '',
          tagsText: detail.tags.join(', '),
          xpReward: detail.xpReward ?? null,
          estimatedSeconds: detail.estimatedSeconds ?? null,
          instructions: detail.instructions || '',
          dragItems: detail.items.length
            ? detail.items.map((item, index) => ({
                dragText: item.dragText,
                targetZone: item.targetZone,
                pinyin: item.pinyin || '',
                imageUrl: item.imageUrl || '',
                sortOrder: item.sortOrder ?? index,
              }))
            : [createEmptyDragItem(0)],
        })
        break
      }
      case 'STORY_ACTIVITY': {
        const detail = await adminApi.storyActivity(id)
        applyFormState({
          ...createEmptyForm(type),
          type,
          title: detail.title,
          hskLevel: detail.hskLevel ?? null,
          ageBand: detail.ageBand ?? '',
          difficulty: detail.difficulty ?? '',
          topic: detail.topic || '',
          tagsText: detail.tags.join(', '),
          xpReward: detail.xpReward ?? null,
          estimatedSeconds: detail.estimatedSeconds ?? null,
          instructions: detail.instructions || '',
          storyText: detail.storyText,
          storyPinyin: detail.pinyin || '',
          storyTranslation: detail.translation || '',
          imageUrl: detail.imageUrl || '',
          storyQuestions: detail.questions.length
            ? detail.questions.map((item, index) => ({
                questionText: item.questionText,
                optionsText: optionsPayloadToText(item.optionsPayload),
                explanation: item.explanation || '',
                sortOrder: item.sortOrder ?? index,
              }))
            : [createEmptyStoryQuestion(0)],
        })
        break
      }
    }
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.loadDetailError'))
  } finally {
    detailLoading.value = false
  }
}

function optionsPayloadToText(payload: string): string {
  try {
    const parsed = JSON.parse(payload) as unknown
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item)).join('\n')
    }
  } catch {}
  return payload
}

function optionsTextToPayload(text: string): string {
  const options = text
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  return JSON.stringify(options)
}

function validateForm(): string | null {
  if (form.type !== 'FLASHCARD' && !form.title.trim()) {
    return t('adminActivityLibrary.titleRequired')
  }

  if (form.type === 'FLASHCARD') {
    if (!form.frontText.trim()) return t('adminActivityLibrary.flashcardFrontRequired')
    if (!form.backText.trim()) return t('adminActivityLibrary.flashcardBackRequired')
  }

  if (form.type === 'MATCHING_GAME') {
    if (!form.matchingItems.some((item) => item.leftText.trim() && item.rightText.trim())) {
      return t('adminActivityLibrary.matchingPairRequired')
    }
  }

  if (form.type === 'LISTENING_EXERCISE' && !form.title.trim()) {
    return t('adminActivityLibrary.listeningTitleRequired')
  }

  if (form.type === 'DRAG_DROP_ACTIVITY') {
    if (!form.dragItems.some((item) => item.dragText.trim() && item.targetZone.trim())) {
      return t('adminActivityLibrary.dragItemRequired')
    }
  }

  if (form.type === 'STORY_ACTIVITY') {
    if (!form.storyText.trim()) return t('adminActivityLibrary.storyTextRequired')
    if (!form.storyQuestions.some((item) => item.questionText.trim() && item.optionsText.trim())) {
      return t('adminActivityLibrary.storyQuestionRequired')
    }
  }

  return null
}

function buildFlashcardPayload(): AdminFlashcardPayload {
  return {
    title: optionalString(form.title),
    frontText: form.frontText.trim(),
    backText: form.backText.trim(),
    pinyin: optionalString(form.pinyin),
    englishMeaning: optionalString(form.englishMeaning),
    malayMeaning: optionalString(form.malayMeaning),
    imageUrl: optionalString(form.imageUrl),
    audioUrl: optionalString(form.audioUrl),
    sortOrder: optionalNumber(form.sortOrder),
    extraPayload: optionalString(form.extraPayload),
    hskLevel: optionalNumber(form.hskLevel),
    ageBand: form.ageBand || null,
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
  }
}

function buildMatchingGamePayload(): AdminMatchingGamePayload {
  return {
    title: form.title.trim(),
    hskLevel: optionalNumber(form.hskLevel),
    ageBand: form.ageBand || null,
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
    items: form.matchingItems
      .filter((item) => item.leftText.trim() && item.rightText.trim())
      .map(
        (item): AdminMatchingGameItemPayload => ({
          leftText: item.leftText.trim(),
          rightText: item.rightText.trim(),
          leftImageUrl: optionalString(item.leftImageUrl),
          rightImageUrl: optionalString(item.rightImageUrl),
          pinyin: optionalString(item.pinyin),
          sortOrder: optionalNumber(item.sortOrder),
        }),
      ),
  }
}

function buildListeningExercisePayload(): AdminListeningExercisePayload {
  return {
    title: form.title.trim(),
    audioUrl: optionalString(form.audioUrl),
    questionPayload: optionalString(form.questionPayload),
    hskLevel: optionalNumber(form.hskLevel),
    ageBand: form.ageBand || null,
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
  }
}

function buildTracingExercisePayload(): AdminTracingExercisePayload {
  return {
    title: form.title.trim(),
    worksheetUrl: optionalString(form.worksheetUrl),
    targetCharacter: optionalString(form.targetCharacter),
    pinyin: optionalString(form.pinyin),
    meaning: optionalString(form.meaning),
    instructions: optionalString(form.instructions),
    hskLevel: optionalNumber(form.hskLevel),
    ageBand: form.ageBand || null,
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
  }
}

function buildDragDropPayload(): AdminDragDropActivityPayload {
  return {
    title: form.title.trim(),
    instructions: optionalString(form.instructions),
    hskLevel: optionalNumber(form.hskLevel),
    ageBand: form.ageBand || null,
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
    items: form.dragItems
      .filter((item) => item.dragText.trim() && item.targetZone.trim())
      .map(
        (item): AdminDragDropItemPayload => ({
          dragText: item.dragText.trim(),
          targetZone: item.targetZone.trim(),
          pinyin: optionalString(item.pinyin),
          imageUrl: optionalString(item.imageUrl),
          sortOrder: optionalNumber(item.sortOrder),
        }),
      ),
  }
}

function buildStoryPayload(): AdminStoryActivityPayload {
  return {
    title: form.title.trim(),
    instructions: optionalString(form.instructions),
    storyText: form.storyText.trim(),
    pinyin: optionalString(form.storyPinyin),
    translation: optionalString(form.storyTranslation),
    imageUrl: optionalString(form.imageUrl),
    hskLevel: optionalNumber(form.hskLevel),
    ageBand: form.ageBand || null,
    difficulty: form.difficulty || null,
    topic: optionalString(form.topic),
    tags: parseTags(),
    xpReward: optionalNumber(form.xpReward),
    estimatedSeconds: optionalNumber(form.estimatedSeconds),
    questions: form.storyQuestions
      .filter((item) => item.questionText.trim() && item.optionsText.trim())
      .map(
        (item): AdminStoryQuestionPayload => ({
          questionText: item.questionText.trim(),
          optionsPayload: optionsTextToPayload(item.optionsText),
          explanation: optionalString(item.explanation),
          sortOrder: optionalNumber(item.sortOrder),
        }),
      ),
  }
}

async function saveActivity(): Promise<void> {
  if (saving.value || deleting.value) return
  if (previewReadOnly.value) {
    actionError.value = t('adminActivityLibrary.previewChanges')
    actionSuccess.value = null
    return
  }

  const validationMessage = validateForm()
  if (validationMessage) {
    actionError.value = validationMessage
    actionSuccess.value = null
    return
  }

  saving.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    let recordId = selectedId.value
    const type = form.type

    switch (type) {
      case 'FLASHCARD': {
        const payload = buildFlashcardPayload()
        const response =
          hasSelectedRecord.value && recordId != null
            ? await adminApi.updateFlashcard(recordId, payload)
            : await adminApi.createFlashcard(payload)
        recordId = response.id
        break
      }
      case 'MATCHING_GAME': {
        const payload = buildMatchingGamePayload()
        const response =
          hasSelectedRecord.value && recordId != null
            ? await adminApi.updateMatchingGame(recordId, payload)
            : await adminApi.createMatchingGame(payload)
        recordId = response.id
        break
      }
      case 'LISTENING_EXERCISE': {
        const payload = buildListeningExercisePayload()
        const response =
          hasSelectedRecord.value && recordId != null
            ? await adminApi.updateListeningExercise(recordId, payload)
            : await adminApi.createListeningExercise(payload)
        recordId = response.id
        break
      }
      case 'TRACING_EXERCISE': {
        const payload = buildTracingExercisePayload()
        const response =
          hasSelectedRecord.value && recordId != null
            ? await adminApi.updateTracingExercise(recordId, payload)
            : await adminApi.createTracingExercise(payload)
        recordId = response.id
        break
      }
      case 'DRAG_DROP_ACTIVITY': {
        const payload = buildDragDropPayload()
        const response =
          hasSelectedRecord.value && recordId != null
            ? await adminApi.updateDragDropActivity(recordId, payload)
            : await adminApi.createDragDropActivity(payload)
        recordId = response.id
        break
      }
      case 'STORY_ACTIVITY': {
        const payload = buildStoryPayload()
        const response =
          hasSelectedRecord.value && recordId != null
            ? await adminApi.updateStoryActivity(recordId, payload)
            : await adminApi.createStoryActivity(payload)
        recordId = response.id
        break
      }
    }

    if (recordId == null) {
      throw new Error(t('adminActivityLibrary.recordIdError'))
    }

    actionSuccess.value = hasSelectedRecord.value
      ? t('adminActivityLibrary.updatedSuccess', { type: activityTypeLabel(type) })
      : t('adminActivityLibrary.createdSuccess', { type: activityTypeLabel(type) })

    await reloadFeed({ id: recordId, type })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.saveError'))
  } finally {
    saving.value = false
  }
}

async function updateStatus(status: StatusMutation): Promise<void> {
  if (saving.value || deleting.value) return
  if (!hasSelectedRecord.value || selectedId.value == null) {
    return
  }

  if (previewReadOnly.value) {
    actionError.value = t('adminActivityLibrary.previewStatus')
    actionSuccess.value = null
    return
  }

  if (
    status !== 'DRAFT' &&
    !confirmActivityMutation(
      t('adminActivityLibrary.confirmStatus', {
        type: activityTypeLabel(selectedType.value),
        status: activityStatusLabel(status),
      }),
    )
  ) {
    return
  }

  saving.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    switch (selectedType.value) {
      case 'FLASHCARD':
        await adminApi.updateFlashcardStatus(selectedId.value, status)
        break
      case 'MATCHING_GAME':
        await adminApi.updateMatchingGameStatus(selectedId.value, status)
        break
      case 'LISTENING_EXERCISE':
        await adminApi.updateListeningExerciseStatus(selectedId.value, status)
        break
      case 'TRACING_EXERCISE':
        await adminApi.updateTracingExerciseStatus(selectedId.value, status)
        break
      case 'DRAG_DROP_ACTIVITY':
        await adminApi.updateDragDropActivityStatus(selectedId.value, status)
        break
      case 'STORY_ACTIVITY':
        await adminApi.updateStoryActivityStatus(selectedId.value, status)
        break
    }

    actionSuccess.value = t('adminActivityLibrary.statusUpdated', {
      type: activityTypeLabel(selectedType.value),
      status: activityStatusLabel(status),
    })
    await reloadFeed({ id: selectedId.value, type: selectedType.value })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.statusError'))
  } finally {
    saving.value = false
  }
}

async function deleteActivity(): Promise<void> {
  if (deleting.value || saving.value) return
  if (!hasSelectedRecord.value || selectedId.value == null) {
    return
  }

  if (previewReadOnly.value) {
    actionError.value = t('adminActivityLibrary.previewDelete')
    actionSuccess.value = null
    return
  }

  if (
    !confirmActivityMutation(t('adminActivityLibrary.confirmDelete', { type: activityTypeLabel(selectedType.value) }))
  ) {
    return
  }

  deleting.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    switch (selectedType.value) {
      case 'FLASHCARD':
        await adminApi.deleteFlashcard(selectedId.value)
        break
      case 'MATCHING_GAME':
        await adminApi.deleteMatchingGame(selectedId.value)
        break
      case 'LISTENING_EXERCISE':
        await adminApi.deleteListeningExercise(selectedId.value)
        break
      case 'TRACING_EXERCISE':
        await adminApi.deleteTracingExercise(selectedId.value)
        break
      case 'DRAG_DROP_ACTIVITY':
        await adminApi.deleteDragDropActivity(selectedId.value)
        break
      case 'STORY_ACTIVITY':
        await adminApi.deleteStoryActivity(selectedId.value)
        break
    }

    actionSuccess.value = t('adminActivityLibrary.deletedSuccess', {
      type: activityTypeLabel(selectedType.value),
    })
    startNewDraft(selectedType.value)
    await reloadFeed('first')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.deleteError'))
  } finally {
    deleting.value = false
  }
}

function confirmActivityMutation(message: string): boolean {
  const confirmed = window.confirm(`${message}\n\n${t('adminActivityLibrary.liveChangeWarning')}`)
  if (!confirmed) {
    actionSuccess.value = null
    actionError.value = null
  }

  return confirmed
}
</script>

<style scoped>
.admin-activity-page {
  position: relative;
}

.admin-activity-header-button {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.admin-activity-toolbar,
.admin-activity-list-panel,
.admin-activity-editor-panel,
.admin-activity-stat,
.admin-activity-repeat-card {
  border-radius: 24px;
}

.admin-activity-toolbar {
  display: grid;
  gap: 16px;
}

.admin-activity-filter-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.admin-activity-filter-wide,
.admin-activity-editor-grid-wide {
  grid-column: span 2;
}

.admin-activity-toolbar-actions,
.admin-activity-panel-actions,
.admin-activity-editor-actions,
.admin-activity-editor-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.admin-activity-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.admin-activity-stat {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 18px;
  border: 1px solid rgba(232, 236, 245, 0.94);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 24px rgba(49, 64, 94, 0.06);
}

.admin-activity-stat-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 16px;
}

.admin-activity-stat-icon.is-purple {
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
}

.admin-activity-stat-icon.is-green {
  background: rgba(111, 210, 104, 0.12);
  color: #4aa746;
}

.admin-activity-stat-icon.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.admin-activity-stat-icon.is-orange {
  background: rgba(255, 181, 68, 0.14);
  color: #ee9618;
}

.admin-activity-stat-icon.is-gold {
  background: rgba(191, 159, 255, 0.14);
  color: #6d49f8;
}

.admin-activity-stat-label,
.admin-activity-stat-copy,
.admin-activity-row-copy span,
.admin-activity-row-copy small,
.admin-activity-editor-meta span {
  color: #72809d;
}

.admin-activity-stat-value {
  font-size: 1.7rem;
  line-height: 1;
}

.admin-activity-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 18px;
  align-items: start;
}

.admin-activity-panel-head {
  align-items: center;
}

.admin-activity-list,
.admin-activity-repeat-list {
  display: grid;
  gap: 12px;
}

.admin-activity-row {
  width: 100%;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(234, 238, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
  text-align: left;
  color: var(--text);
}

.admin-activity-row.is-active {
  border-color: rgba(109, 73, 248, 0.32);
  box-shadow: 0 12px 24px rgba(106, 73, 255, 0.08);
}

.admin-activity-row-copy {
  display: grid;
  gap: 6px;
}

.admin-activity-row-top {
  align-items: center;
  justify-content: space-between;
}

.admin-activity-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 6px;
  color: #72809d;
}

.admin-activity-page-button {
  min-height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid rgba(228, 232, 244, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #5e6d8f;
  font-weight: 700;
}

.admin-activity-editor-panel,
.admin-activity-section {
  display: grid;
  gap: 16px;
}

.admin-activity-editor-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.admin-activity-section {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(232, 236, 245, 0.94);
  background: rgba(248, 250, 255, 0.9);
}

.admin-activity-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-activity-section-head span {
  color: #72809d;
}

.admin-activity-repeat-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(229, 233, 245, 0.92);
  background: rgba(255, 255, 255, 0.98);
}

.admin-activity-editor-footer {
  display: grid;
  gap: 14px;
  padding-top: 4px;
}

@media (max-width: 1480px) {
  .admin-activity-filter-grid,
  .admin-activity-stats,
  .admin-activity-editor-grid,
  .admin-activity-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-activity-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 920px) {
  .admin-activity-filter-grid,
  .admin-activity-stats,
  .admin-activity-editor-grid {
    grid-template-columns: 1fr;
  }

  .admin-activity-filter-wide,
  .admin-activity-editor-grid-wide {
    grid-column: auto;
  }

  .admin-activity-pagination,
  .admin-activity-row-top {
    display: grid;
  }
}
</style>
