<template>
  <nav class="practice-set-list" :aria-label="t('practice.setList')">
    <div class="input-with-icon">
      <IconGlyph class="input-icon" name="search" :size="16" />
      <input
        ref="searchField"
        v-model="query"
        class="input"
        type="search"
        :placeholder="t('practice.searchSets')"
        :aria-label="t('practice.searchSetsLabel')"
      />
    </div>

    <div v-if="loading" class="practice-set-skeleton" aria-hidden="true">
      <span v-for="row in 5" :key="row" class="skeleton practice-set-skeleton-row" />
    </div>

    <template v-else>
      <div v-if="!groups.length" class="practice-set-empty" role="status" data-testid="sets-no-match">
        <p class="practice-set-empty-title">{{ t('practice.noSetsMatch', { query: query.trim() }) }}</p>
        <p class="practice-set-empty-text">{{ t('practice.noSetsMatchHint') }}</p>
        <button class="btn btn-secondary btn-sm" type="button" @click="clearSearch">
          {{ t('common.clearSearch') }}
        </button>
      </div>

      <section v-for="group in groups" :key="group.key" class="practice-set-group">
        <h2 class="practice-set-group-title">
          <button
            class="practice-set-group-toggle"
            type="button"
            :aria-expanded="isOpen(group.key)"
            :aria-controls="`${baseId}-${group.key}`"
            @click="toggle(group.key)"
          >
            <span>{{ group.label }}</span>
            <span class="practice-set-group-count">
              {{ t('practice.setsInGroup', { count: group.sets.length }, group.sets.length) }}
              <IconGlyph :name="isOpen(group.key) ? 'chevronUp' : 'chevronDown'" :size="16" />
            </span>
          </button>
        </h2>
        <ul v-show="isOpen(group.key)" :id="`${baseId}-${group.key}`" class="practice-set-rows">
          <li v-for="set in group.sets" :key="set.id">
            <RouterLink
              class="practice-set-row"
              :to="practiceSetRoute(type, set.id)"
              :title="set.title"
              :aria-current="set.id === selectedId ? 'true' : undefined"
              :data-testid="`set-${set.id}`"
            >
              <span class="practice-set-row-copy">
                <span class="practice-set-row-title"><CjkText :text="set.title" /></span>
                <span class="practice-set-row-meta">{{ metaOf(set) }}</span>
              </span>
              <StatusPill v-if="set.done" :label="t('practice.statusCompleted')" tone="success" />
              <StatusPill v-else-if="set.started" :label="t('practice.statusInProgress')" tone="info" />
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { PRACTICE_ACTIVITIES, practiceSetRoute } from '@/config/practiceActivities'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeSetSummary, PracticeType } from '@/types/learn'
import { hskLevelName } from '@/utils/hsk'

/**
 * The sets of one activity, beside the stage: a search, then one group per level (HSK 1 Basic, HSK 2 Survival, HSK 3
 * Conversational) that folds open, with each set's size, XP and whether it is completed or in progress. The open
 * set's group starts open; a search opens every group with a match.
 */
const props = defineProps<{
  sets: readonly PracticeSetSummary[]
  selectedId: number | null
  loading: boolean
  type: PracticeType
}>()

const { t } = useLocalMessages({ practice: practiceMessages })
const baseId = useId()

const query = ref('')
const searchTerm = computed(() => query.value.trim().toLocaleLowerCase())
const opened = ref(new Set<string>())
const foldedMatches = ref(new Set<string>())
const searchField = ref<HTMLInputElement | null>(null)

/** Clear search takes the button away with the empty state, so the focus goes back to the search field. */
function clearSearch(): void {
  query.value = ''
  searchField.value?.focus()
}

function groupKey(set: PracticeSetSummary): string {
  return set.hskLevel ? `hsk${set.hskLevel}` : 'other'
}

function groupLabel(set: PracticeSetSummary): string {
  return set.hskLevel ? hskLevelName(t, set.hskLevel) : t('practice.levelOther')
}

const groups = computed(() => {
  const needle = searchTerm.value
  const byKey = new Map<string, { key: string; label: string; level: number; sets: PracticeSetSummary[] }>()
  for (const set of props.sets) {
    if (needle && !set.title.toLocaleLowerCase().includes(needle)) continue
    const key = groupKey(set)
    const group = byKey.get(key) ?? { key, label: groupLabel(set), level: set.hskLevel ?? 99, sets: [] }
    group.sets.push(set)
    byKey.set(key, group)
  }
  return [...byKey.values()].sort((left, right) => left.level - right.level)
})

// The open set's group opens with it.
watch(
  () => props.sets.find((set) => set.id === props.selectedId),
  (set) => {
    if (set) opened.value.add(groupKey(set))
  },
  { immediate: true },
)

watch(searchTerm, () => foldedMatches.value.clear())

function isOpen(key: string): boolean {
  return searchTerm.value ? !foldedMatches.value.has(key) : opened.value.has(key)
}

function toggle(key: string): void {
  const toggled = searchTerm.value ? foldedMatches.value : opened.value
  if (toggled.has(key)) toggled.delete(key)
  else toggled.add(key)
}

/** "6 questions · +18 XP"; tracing sets are measured in strokes (omitted on summaries), so only their XP shows. */
function metaOf(set: PracticeSetSummary): string {
  const xp = t('practice.xpTag', { xp: set.xpReward })
  if (props.type === 'TRACING') return xp
  return `${t(PRACTICE_ACTIVITIES[props.type].unitKey, { count: set.itemCount }, set.itemCount)} · ${xp}`
}
</script>
