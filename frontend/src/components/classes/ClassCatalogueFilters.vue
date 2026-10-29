<template>
  <form class="class-filters" role="search" :aria-label="t('classCatalogue.filters.label')" @submit.prevent>
    <div class="field class-filters-search">
      <label class="field-label" :for="`${baseId}-search`">{{ t('classCatalogue.filters.search') }}</label>
      <div class="input-with-icon">
        <IconGlyph name="search" class="input-icon" :size="18" />
        <input
          :id="`${baseId}-search`"
          v-model="keyword"
          class="input"
          type="search"
          maxlength="200"
          :placeholder="t('classCatalogue.filters.searchPlaceholder')"
          data-testid="class-search"
        />
      </div>
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-topic`">{{ t('classCatalogue.filters.topic') }}</label>
      <select :id="`${baseId}-topic`" v-model="topicId" class="select">
        <option :value="null">{{ t('classCatalogue.filters.allTopics') }}</option>
        <option v-for="topic in summary?.topics ?? []" :key="topic.id" :value="topic.id">{{ topic.name }}</option>
      </select>
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-level`">{{ t('classCatalogue.filters.level') }}</label>
      <select :id="`${baseId}-level`" v-model="levelId" class="select">
        <option :value="null">{{ t('classCatalogue.filters.allLevels') }}</option>
        <option v-for="level in summary?.levels ?? []" :key="level.id" :value="level.id">{{ level.name }}</option>
      </select>
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-type`">{{ t('classCatalogue.filters.learningType') }}</label>
      <select :id="`${baseId}-type`" v-model="learningType" class="select">
        <option :value="null">{{ t('classCatalogue.filters.allLearningTypes') }}</option>
        <option v-for="type in LEARNING_TYPES" :key="type" :value="type">{{ t(LEARNING_TYPE_KEYS[type]) }}</option>
      </select>
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-sort`">{{ t('classCatalogue.filters.sort') }}</label>
      <select :id="`${baseId}-sort`" v-model="sort" class="select">
        <option value="RECENT">{{ t('classCatalogue.filters.sortRecent') }}</option>
        <option value="TITLE">{{ t('classCatalogue.filters.sortTitle') }}</option>
      </select>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { CatalogueSummary, LearningType } from '@/types/classes'
import { LEARNING_TYPES, LEARNING_TYPE_KEYS } from '@/utils/classes'

export interface CatalogueFilters {
  keyword: string
  topicId: number | null
  levelId: number | null
  learningType: LearningType | null
  sort: 'RECENT' | 'TITLE'
}

/**
 * The quiet filter bar of a class catalogue: search, then Topic, Difficulty, Learning type and sort in one row that
 * wraps in a narrow column. The parent loads the matching page.
 */
const props = defineProps<{ modelValue: CatalogueFilters; summary: CatalogueSummary | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: CatalogueFilters] }>()

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })
const baseId = useId()

function field<K extends keyof CatalogueFilters>(key: K) {
  return computed<CatalogueFilters[K]>({
    get: () => props.modelValue[key],
    set: (value) => emit('update:modelValue', { ...props.modelValue, [key]: value }),
  })
}

const keyword = field('keyword')
const topicId = field('topicId')
const levelId = field('levelId')
const learningType = field('learningType')
const sort = field('sort')
</script>
