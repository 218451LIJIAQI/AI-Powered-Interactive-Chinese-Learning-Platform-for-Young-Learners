<template>
  <FilterChips v-model="selected" :options="options" :label="t('classCatalogue.filters.label')" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import FilterChips, { type FilterChipOption } from '@/components/common/FilterChips.vue'
import type { CatalogueTheme } from '@/composables/useClassCatalogue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'

/** The theme chips of a class catalogue ("All · 8", "Travel · 2", …); null is All. */
const props = defineProps<{ themes: readonly CatalogueTheme[]; total: number }>()
const selected = defineModel<string | null>({ required: true })

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages })

const options = computed<FilterChipOption<string | null>[]>(() => [
  { value: null, label: t('classCatalogue.filters.all'), count: props.total },
  ...props.themes.map((theme) => ({ value: theme.name, label: theme.name, count: theme.count })),
])
</script>
