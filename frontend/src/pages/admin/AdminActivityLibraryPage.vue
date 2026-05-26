<template>
  <div class="page-stack admin-activity-page">
    <PageHeaderBlock
      title="Activity Library"
      description="Create, publish, archive, and curate the platform-owned activity library across all six supported learning activity types."
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
          <span>{{ previewReadOnly ? 'Preview feed' : pageBusy ? 'Refreshing...' : 'Refresh feed' }}</span>
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :error="pageError" :success="actionSuccess" />

    <section class="panel admin-activity-toolbar">
      <div class="admin-activity-filter-grid">
        <label class="field">
          <span>Activity type</span>
          <select v-model="filters.type" class="select">
            <option value="">All types</option>
            <option v-for="item in activityTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="field">
          <span>Status</span>
          <select v-model="filters.status" class="select">
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </label>

        <label class="field">
          <span>Age band</span>
          <select v-model="filters.ageBand" class="select">
            <option value="">All ages</option>
            <option v-for="item in ageBandOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="field">
          <span>Difficulty</span>
          <select v-model="filters.difficulty" class="select">
            <option value="">All levels</option>
            <option v-for="item in difficultyOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>

        <label class="field admin-activity-filter-wide">
          <span>Keyword</span>
          <input v-model="filters.keyword" class="input" placeholder="Search titles, topics, or content" />
        </label>

        <label class="field">
          <span>Topic</span>
          <input v-model="filters.topic" class="input" placeholder="greetings" />
        </label>
      </div>

      <div class="admin-activity-toolbar-actions">
        <button class="btn btn-secondary" type="button" @click="resetFilters" :disabled="pageBusy">Reset</button>
        <button class="btn btn-primary" type="button" @click="applyFilters" :disabled="pageBusy">
          {{ pageBusy ? 'Loading...' : 'Apply filters' }}
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
            <strong>Unified Activity Feed</strong>
            <span>{{ feed.totalItems }} total item(s)</span>
          </div>
          <button class="btn btn-secondary" type="button" @click="startNewDraft()" :disabled="previewReadOnly">
            New Draft
          </button>
        </div>

        <StatePanel
          v-if="previewReadOnly"
          icon="practice"
          title="Live admin account required"
          message="Preview mode keeps the admin shell visible, but the activity library only edits real backend records after you sign in as an admin."
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
                  <StatusPill :label="humanizeEnum(item.status)" :tone="statusTone(item.status)" />
                </div>
                <span>{{ activityTypeLabel(item.type) }}</span>
                <small>{{ activitySummaryMeta(item) }}</small>
              </div>
            </button>
          </div>

          <StatePanel
            v-else-if="!feedResource.loading.value"
            icon="practice"
            title="No activities match these filters"
            message="Adjust the filters or create a new draft to start building an activity."
          />

          <div v-if="feed.totalPages > 1" class="admin-activity-pagination">
            <button
              class="admin-activity-page-button"
              type="button"
              :disabled="currentPage <= 1 || pageBusy"
              @click="goToPage(currentPage - 1)"
            >
              Previous
            </button>
            <span>Page {{ currentPage }} of {{ feed.totalPages }}</span>
            <button
              class="admin-activity-page-button"
              type="button"
              :disabled="currentPage >= feed.totalPages || pageBusy"
              @click="goToPage(currentPage + 1)"
            >
              Next
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
              :label="humanizeEnum(selectedSummary.status)"
              :tone="statusTone(selectedSummary.status)"
            />
            <button
              class="teacher-outline-button"
              type="button"
              @click="startNewDraft(form.type)"
              :disabled="previewReadOnly"
            >
              New {{ activityTypeLabel(form.type) }}
            </button>
          </div>
        </div>

        <StatePanel
          v-if="previewReadOnly"
          icon="verification"
          title="Read-only preview"
          message="Sign in with a live admin account to create, edit, publish, archive, and delete platform activities."
        />

        <template v-else>
          <div class="admin-activity-editor-grid">
            <label class="field">
              <span>Activity type</span>
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
              <span>{{ form.type === 'FLASHCARD' ? 'Title (optional)' : 'Title' }}</span>
              <input
                v-model="form.title"
                class="input"
                :placeholder="form.type === 'FLASHCARD' ? 'Optional flashcard title' : 'Activity title'"
              />
            </label>

            <label class="field">
              <span>HSK level</span>
              <select v-model="form.hskLevel" class="select">
                <option :value="null">Not set</option>
                <option v-for="level in [1, 2, 3, 4, 5, 6]" :key="level" :value="level">HSK {{ level }}</option>
              </select>
            </label>

            <label class="field">
              <span>Age band</span>
              <select v-model="form.ageBand" class="select">
                <option value="">Not set</option>
                <option v-for="item in ageBandOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>

            <label class="field">
              <span>Difficulty</span>
              <select v-model="form.difficulty" class="select">
                <option value="">Not set</option>
                <option v-for="item in difficultyOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>Topic</span>
              <input v-model="form.topic" class="input" placeholder="animals, tones, greetings" />
            </label>

            <label class="field admin-activity-editor-grid-wide">
              <span>Tags</span>
              <input v-model="form.tagsText" class="input" placeholder="comma-separated tags" />
            </label>

            <label class="field">
              <span>XP reward</span>
              <input v-model.number="form.xpReward" class="input" type="number" min="0" />
            </label>

            <label class="field">
              <span>Estimated seconds</span>
              <input v-model.number="form.estimatedSeconds" class="input" type="number" min="0" />
            </label>
          </div>

          <section v-if="form.type === 'FLASHCARD'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>Flashcard content</strong>
              <span>Curate language cards with multilingual support and optional media.</span>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field">
                <span>Front text</span>
                <textarea v-model="form.frontText" class="textarea" rows="3" />
              </label>
              <label class="field">
                <span>Back text</span>
                <textarea v-model="form.backText" class="textarea" rows="3" />
              </label>
              <label class="field">
                <span>Pinyin</span>
                <input v-model="form.pinyin" class="input" />
              </label>
              <label class="field">
                <span>English meaning</span>
                <input v-model="form.englishMeaning" class="input" />
              </label>
              <label class="field">
                <span>Malay meaning</span>
                <input v-model="form.malayMeaning" class="input" />
              </label>
              <label class="field">
                <span>Sort order</span>
                <input v-model.number="form.sortOrder" class="input" type="number" min="0" />
              </label>
              <label class="field">
                <span>Image URL</span>
                <input v-model="form.imageUrl" class="input" type="url" />
              </label>
              <label class="field">
                <span>Audio URL</span>
                <input v-model="form.audioUrl" class="input" type="url" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>Extra payload</span>
                <textarea v-model="form.extraPayload" class="textarea" rows="3" />
              </label>
            </div>
          </section>

          <section v-else-if="form.type === 'MATCHING_GAME'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>Matching pairs</strong>
              <button class="btn btn-secondary" type="button" @click="addMatchingItem">Add pair</button>
            </div>

            <div class="admin-activity-repeat-list">
              <article
                v-for="(item, index) in form.matchingItems"
                :key="`matching-${index}`"
                class="admin-activity-repeat-card"
              >
                <div class="row-between">
                  <strong>Pair {{ index + 1 }}</strong>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    @click="removeMatchingItem(index)"
                    :disabled="form.matchingItems.length <= 1"
                  >
                    Remove
                  </button>
                </div>
                <div class="admin-activity-editor-grid">
                  <label class="field">
                    <span>Left text</span>
                    <input v-model="item.leftText" class="input" />
                  </label>
                  <label class="field">
                    <span>Right text</span>
                    <input v-model="item.rightText" class="input" />
                  </label>
                  <label class="field">
                    <span>Left image URL</span>
                    <input v-model="item.leftImageUrl" class="input" type="url" />
                  </label>
                  <label class="field">
                    <span>Right image URL</span>
                    <input v-model="item.rightImageUrl" class="input" type="url" />
                  </label>
                  <label class="field">
                    <span>Pinyin</span>
                    <input v-model="item.pinyin" class="input" />
                  </label>
                  <label class="field">
                    <span>Sort order</span>
                    <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
                  </label>
                </div>
              </article>
            </div>
          </section>

          <section v-else-if="form.type === 'LISTENING_EXERCISE'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>Listening exercise content</strong>
              <span>Save the audio source plus the question payload your student view will consume.</span>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field">
                <span>Audio URL</span>
                <input v-model="form.audioUrl" class="input" type="url" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>Question payload</span>
                <textarea
                  v-model="form.questionPayload"
                  class="textarea"
                  rows="5"
                  placeholder='{"prompt":"Listen and identify the word"}'
                />
              </label>
            </div>
          </section>

          <section v-else-if="form.type === 'TRACING_EXERCISE'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>Tracing exercise content</strong>
              <span
                >Capture the worksheet, character, pronunciation, meaning, and teacher guidance for the activity.</span
              >
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field">
                <span>Worksheet URL</span>
                <input v-model="form.worksheetUrl" class="input" type="url" />
              </label>
              <label class="field">
                <span>Target character</span>
                <input v-model="form.targetCharacter" class="input" />
              </label>
              <label class="field">
                <span>Pinyin</span>
                <input v-model="form.pinyin" class="input" />
              </label>
              <label class="field">
                <span>Meaning</span>
                <input v-model="form.meaning" class="input" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>Instructions</span>
                <textarea v-model="form.instructions" class="textarea" rows="4" />
              </label>
            </div>
          </section>

          <section v-else-if="form.type === 'DRAG_DROP_ACTIVITY'" class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>Drag and drop activity</strong>
              <button class="btn btn-secondary" type="button" @click="addDragItem">Add draggable item</button>
            </div>

            <label class="field">
              <span>Instructions</span>
              <textarea v-model="form.instructions" class="textarea" rows="4" />
            </label>

            <div class="admin-activity-repeat-list">
              <article
                v-for="(item, index) in form.dragItems"
                :key="`drag-${index}`"
                class="admin-activity-repeat-card"
              >
                <div class="row-between">
                  <strong>Draggable item {{ index + 1 }}</strong>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    @click="removeDragItem(index)"
                    :disabled="form.dragItems.length <= 1"
                  >
                    Remove
                  </button>
                </div>
                <div class="admin-activity-editor-grid">
                  <label class="field">
                    <span>Drag text</span>
                    <input v-model="item.dragText" class="input" />
                  </label>
                  <label class="field">
                    <span>Target zone</span>
                    <input v-model="item.targetZone" class="input" />
                  </label>
                  <label class="field">
                    <span>Pinyin</span>
                    <input v-model="item.pinyin" class="input" />
                  </label>
                  <label class="field">
                    <span>Image URL</span>
                    <input v-model="item.imageUrl" class="input" type="url" />
                  </label>
                  <label class="field">
                    <span>Sort order</span>
                    <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
                  </label>
                </div>
              </article>
            </div>
          </section>

          <section v-else class="admin-activity-section">
            <div class="admin-activity-section-head">
              <strong>Story activity</strong>
              <button class="btn btn-secondary" type="button" @click="addStoryQuestion">Add question</button>
            </div>

            <div class="admin-activity-editor-grid">
              <label class="field admin-activity-editor-grid-wide">
                <span>Instructions</span>
                <textarea v-model="form.instructions" class="textarea" rows="3" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>Story text</span>
                <textarea v-model="form.storyText" class="textarea" rows="8" />
              </label>
              <label class="field">
                <span>Pinyin</span>
                <textarea v-model="form.storyPinyin" class="textarea" rows="4" />
              </label>
              <label class="field">
                <span>Translation</span>
                <textarea v-model="form.storyTranslation" class="textarea" rows="4" />
              </label>
              <label class="field admin-activity-editor-grid-wide">
                <span>Image URL</span>
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
                  <strong>Question {{ index + 1 }}</strong>
                  <button
                    class="teacher-outline-button"
                    type="button"
                    @click="removeStoryQuestion(index)"
                    :disabled="form.storyQuestions.length <= 1"
                  >
                    Remove
                  </button>
                </div>
                <div class="admin-activity-editor-grid">
                  <label class="field admin-activity-editor-grid-wide">
                    <span>Question text</span>
                    <input v-model="item.questionText" class="input" />
                  </label>
                  <label class="field">
                    <span>Options (one per line)</span>
                    <textarea v-model="item.optionsText" class="textarea" rows="5" />
                  </label>
                  <label class="field">
                    <span>Explanation</span>
                    <textarea v-model="item.explanation" class="textarea" rows="5" />
                  </label>
                  <label class="field">
                    <span>Sort order</span>
                    <input v-model.number="item.sortOrder" class="input" type="number" min="0" />
                  </label>
                </div>
              </article>
            </div>
          </section>

          <div class="admin-activity-editor-footer">
            <div class="admin-activity-editor-meta">
              <span v-if="hasSelectedRecord && selectedSummary"
                >Created {{ formatDateTime(selectedSummary.createdAt) }}</span
              >
              <span v-if="hasSelectedRecord && selectedSummary"
                >Updated {{ formatDateTime(selectedSummary.updatedAt) }}</span
              >
              <span v-if="selectedSummary?.createdByDisplayName">By {{ selectedSummary.createdByDisplayName }}</span>
            </div>

            <div class="admin-activity-editor-actions">
              <button class="btn btn-secondary" type="button" @click="startNewDraft(form.type)" :disabled="formBusy">
                Clear draft
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
                Save as Draft
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                @click="updateStatus('PUBLISHED')"
                :disabled="!hasSelectedRecord || formBusy"
              >
                Publish
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                @click="updateStatus('ARCHIVED')"
                :disabled="!hasSelectedRecord || formBusy"
              >
                Archive
              </button>
              <button
                class="btn btn-danger"
                type="button"
                @click="deleteActivity"
                :disabled="!hasSelectedRecord || formBusy"
              >
                {{ deleting ? 'Deleting...' : 'Delete' }}
              </button>
            </div>
          </div>
        </template>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Admin activity library editor — six interactive activity types
 * (flashcards, matching, listening, tracing, drag-drop, story)
 * curated centrally by admins.
 *
 * <h2>Why admin-curated</h2>
 * <p>The activity library is intentionally separated from teacher
 * lessons so the company can guarantee quality for the early
 * learner demographic. Teachers see activities from this library
 * surfaced in the student practice page based on age-band match.</p>
 *
 * <h2>Bulk upload</h2>
 * <p>The page supports CSV import for each activity type — the
 * upload is parsed client-side then validated server-side via the
 * activity ingest API.</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'

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

const emptyFeed: PageResponse<AdminActivitySummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
}

const activityTypeOptions: Array<{ value: AdminActivityType; label: string }> = [
  { value: 'FLASHCARD', label: 'Flashcard' },
  { value: 'MATCHING_GAME', label: 'Matching Game' },
  { value: 'LISTENING_EXERCISE', label: 'Listening Exercise' },
  { value: 'TRACING_EXERCISE', label: 'Tracing Exercise' },
  { value: 'DRAG_DROP_ACTIVITY', label: 'Drag & Drop Activity' },
  { value: 'STORY_ACTIVITY', label: 'Story Activity' },
]

const ageBandOptions: Array<{ value: AgeBand; label: string }> = [
  { value: 'KINDERGARTEN', label: 'Kindergarten' },
  { value: 'LOWER_PRIMARY', label: 'Lower Primary' },
  { value: 'UPPER_PRIMARY', label: 'Upper Primary' },
  { value: 'ALL_AGES', label: 'All Ages' },
]

const difficultyOptions: Array<{ value: ActivityDifficulty; label: string }> = [
  { value: 'EASY', label: 'Easy' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HARD', label: 'Hard' },
]

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
  const published = pageItems.filter((item) => item.status === 'PUBLISHED').length
  const drafts = pageItems.filter((item) => item.status === 'DRAFT').length
  const archived = pageItems.filter((item) => item.status === 'ARCHIVED').length
  const typeCount = new Set(pageItems.map((item) => item.type)).size

  return [
    {
      label: 'Filtered Total',
      value: formatNumber(feed.value.totalItems),
      copy: 'Records matched by the current filters.',
      icon: 'practice',
      tone: 'purple',
    },
    {
      label: 'Published',
      value: formatNumber(published),
      copy: 'Published items on the current page.',
      icon: 'status',
      tone: 'green',
    },
    {
      label: 'Drafts',
      value: formatNumber(drafts),
      copy: 'Draft items ready for curation.',
      icon: 'content',
      tone: 'blue',
    },
    {
      label: 'Archived',
      value: formatNumber(archived),
      copy: 'Archived items still tracked for audit.',
      icon: 'history',
      tone: 'orange',
    },
    {
      label: 'Visible Types',
      value: formatNumber(typeCount),
      copy: 'Distinct activity types on this page.',
      icon: 'analytics',
      tone: 'gold',
    },
  ]
})

const editorTitle = computed(() =>
  hasSelectedRecord.value ? `${activityTypeLabel(selectedType.value)} details` : 'New activity draft',
)
const editorSubtitle = computed(() => {
  if (hasSelectedRecord.value && selectedSummary.value) {
    return `${humanizeEnum(selectedSummary.value.status)} · ${activitySummaryMeta(selectedSummary.value)}`
  }
  return `Create a new ${activityTypeLabel(form.type).toLowerCase()} and save it directly to the admin activity library.`
})
const saveButtonLabel = computed(() =>
  saving.value ? 'Saving...' : hasSelectedRecord.value ? 'Save changes' : 'Create activity',
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

function applyFormState(next: ActivityEditorForm) {
  Object.assign(form, next)
}

function optionalString(value: string) {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function optionalNumber(value: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function parseTags() {
  return form.tagsText
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function activityTypeLabel(type: AdminActivityType) {
  return activityTypeOptions.find((item) => item.value === type)?.label ?? humanizeEnum(type)
}

function fallbackActivityTitle(type: AdminActivityType) {
  return `${activityTypeLabel(type)} draft`
}

function humanizeEnum(value: string) {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function statusTone(status: ContentStatus) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'primary'
  if (status === 'REJECTED') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

function activitySummaryMeta(item: AdminActivitySummaryResponse) {
  const parts = [item.topic?.trim() || 'No topic']
  if (item.hskLevel) parts.push(`HSK ${item.hskLevel}`)
  if (item.ageBand) parts.push(humanizeEnum(item.ageBand))
  if (item.difficulty) parts.push(humanizeEnum(item.difficulty))
  if (item.tags.length) parts.push(item.tags.slice(0, 2).join(', '))
  return parts.join(' · ')
}

function resetFilters() {
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

function applyFilters() {
  currentPage.value = 1
  actionError.value = null
  actionSuccess.value = null
  if (!previewReadOnly.value) {
    void reloadFeed('first')
  }
}

function goToPage(page: number) {
  currentPage.value = page
  void reloadFeed('first')
}

function changeDraftType() {
  if (hasSelectedRecord.value) {
    return
  }
  startNewDraft(form.type)
}

function addMatchingItem() {
  form.matchingItems.push(createEmptyMatchingItem(form.matchingItems.length))
}

function removeMatchingItem(index: number) {
  if (form.matchingItems.length <= 1) return
  form.matchingItems.splice(index, 1)
}

function addDragItem() {
  form.dragItems.push(createEmptyDragItem(form.dragItems.length))
}

function removeDragItem(index: number) {
  if (form.dragItems.length <= 1) return
  form.dragItems.splice(index, 1)
}

function addStoryQuestion() {
  form.storyQuestions.push(createEmptyStoryQuestion(form.storyQuestions.length))
}

function removeStoryQuestion(index: number) {
  if (form.storyQuestions.length <= 1) return
  form.storyQuestions.splice(index, 1)
}

function startNewDraft(type: AdminActivityType = 'FLASHCARD') {
  selectedId.value = null
  selectedType.value = type
  applyFormState(createEmptyForm(type))
  actionError.value = null
  actionSuccess.value = null
}

async function reloadFeed(selection: 'first' | 'keep' | { id: number; type: AdminActivityType } = 'keep') {
  if (previewReadOnly.value) {
    actionError.value = null
    actionSuccess.value = 'Preview mode is read-only. Sign in as an admin to refresh the live activity feed.'
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

async function selectActivity(type: AdminActivityType, id: number) {
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
    actionError.value = resolveApiError(reason, 'Unable to load this activity detail.')
  } finally {
    detailLoading.value = false
  }
}

function optionsPayloadToText(payload: string) {
  try {
    const parsed = JSON.parse(payload) as unknown
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item)).join('\n')
    }
  } catch {
    // Preserve the raw payload when it is not JSON.
  }
  return payload
}

function optionsTextToPayload(text: string) {
  const options = text
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  return JSON.stringify(options)
}

function validateForm() {
  if (form.type !== 'FLASHCARD' && !form.title.trim()) {
    return 'Activity title is required.'
  }

  if (form.type === 'FLASHCARD') {
    if (!form.frontText.trim()) return 'Front text is required for flashcards.'
    if (!form.backText.trim()) return 'Back text is required for flashcards.'
  }

  if (form.type === 'MATCHING_GAME') {
    if (!form.matchingItems.some((item) => item.leftText.trim() && item.rightText.trim())) {
      return 'Add at least one complete matching pair.'
    }
  }

  if (form.type === 'LISTENING_EXERCISE' && !form.title.trim()) {
    return 'Listening exercise title is required.'
  }

  if (form.type === 'DRAG_DROP_ACTIVITY') {
    if (!form.dragItems.some((item) => item.dragText.trim() && item.targetZone.trim())) {
      return 'Add at least one drag and drop item.'
    }
  }

  if (form.type === 'STORY_ACTIVITY') {
    if (!form.storyText.trim()) return 'Story text is required.'
    if (!form.storyQuestions.some((item) => item.questionText.trim() && item.optionsText.trim())) {
      return 'Add at least one story comprehension question with answer options.'
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

async function saveActivity() {
  if (saving.value || deleting.value) return
  if (previewReadOnly.value) {
    actionError.value = 'Activity library changes are read-only while preview data is active.'
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
      throw new Error('Unable to determine the saved activity record ID.')
    }

    actionSuccess.value = hasSelectedRecord.value
      ? `${activityTypeLabel(type)} updated successfully.`
      : `${activityTypeLabel(type)} created successfully.`

    await reloadFeed({ id: recordId, type })
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this activity.')
  } finally {
    saving.value = false
  }
}

async function updateStatus(status: StatusMutation) {
  if (saving.value || deleting.value) return
  if (!hasSelectedRecord.value || selectedId.value == null) {
    return
  }

  if (previewReadOnly.value) {
    actionError.value = 'Activity status changes are read-only while preview data is active.'
    actionSuccess.value = null
    return
  }

  if (
    status !== 'DRAFT' &&
    !confirmActivityMutation(
      `Move this ${activityTypeLabel(selectedType.value).toLowerCase()} to ${humanizeEnum(status)}?`,
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

    actionSuccess.value = `${activityTypeLabel(selectedType.value)} moved to ${humanizeEnum(status)}.`
    await reloadFeed({ id: selectedId.value, type: selectedType.value })
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to update activity status.')
  } finally {
    saving.value = false
  }
}

async function deleteActivity() {
  if (deleting.value || saving.value) return
  if (!hasSelectedRecord.value || selectedId.value == null) {
    return
  }

  if (previewReadOnly.value) {
    actionError.value = 'Activity deletion is read-only while preview data is active.'
    actionSuccess.value = null
    return
  }

  if (
    !confirmActivityMutation(
      `Delete this ${activityTypeLabel(selectedType.value).toLowerCase()}? This action cannot be undone.`,
    )
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

    actionSuccess.value = `${activityTypeLabel(selectedType.value)} deleted successfully.`
    startNewDraft(selectedType.value)
    await reloadFeed('first')
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to delete this activity.')
  } finally {
    deleting.value = false
  }
}

function confirmActivityMutation(message: string) {
  const confirmed = window.confirm(`${message}\n\nThis changes the live activity library for students.`)
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
