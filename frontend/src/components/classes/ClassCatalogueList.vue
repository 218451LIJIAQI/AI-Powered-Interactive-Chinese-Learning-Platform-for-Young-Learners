<template>
  <slot name="header" :catalogue-count="resource.data.value?.length ?? null" />
  <ul v-if="resource.loading.value" class="class-catalogue-grid" aria-busy="true">
    <li class="sr-only" role="status">{{ t('classes.loading') }}</li>
    <li v-for="tile in 4" :key="tile" aria-hidden="true">
      <div class="class-catalogue-card is-skeleton">
        <span class="skeleton class-catalogue-skeleton-cover" />
        <span class="skeleton skeleton-line is-short" />
        <span class="skeleton skeleton-line" />
      </div>
    </li>
  </ul>
  <StatePanel
    v-else-if="resource.error.value"
    heading-tag="h2"
    tone="error"
    icon="alert"
    :title="t('classCatalogue.loadErrorTitle')"
    :message="resource.error.value"
    @retry="resource.reload"
  />
  <div v-else-if="!allCards.length" class="panel student-classes-empty">
    <slot name="empty">
      <StatePanel
        heading-tag="h2"
        tone="empty"
        icon="classes"
        :title="t('classCatalogue.noClassesTitle')"
        :message="t('classCatalogue.noClassesMessage')"
      />
    </slot>
  </div>
  <section v-else class="class-catalogue-results" :aria-labelledby="headingId">
    <FilterChips
      ref="themeChips"
      :model-value="theme"
      :options="themeOptions"
      :label="t('classCatalogue.filters.label')"
      @update:model-value="filterByTheme"
    />
    <h2 :id="headingId" class="sr-only" aria-live="polite">{{ t('classCatalogue.matched', totalItems) }}</h2>
    <ul class="class-catalogue-grid">
      <li v-for="card in items" :key="card.id">
        <slot name="card" :card="card" :query="listQuery" />
      </li>
    </ul>
    <PaginationBar
      :page="page"
      :total-pages="totalPages"
      :label="t('classCatalogue.pagination')"
      @update:page="changePage"
    />
    <div v-if="showAllLink && theme !== null" class="class-catalogue-more">
      <p>{{ t('classCatalogue.lookingElse') }}</p>
      <button class="btn btn-secondary" type="button" data-testid="catalogue-see-all" @click="seeAllClasses">
        {{ t('classCatalogue.seeAll') }}
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, useId, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import FilterChips from '@/components/common/FilterChips.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { pauseDuringNavigation } from '@/composables/usePageNavigation'
import { useClassCatalogue } from '@/composables/useClassCatalogue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { PageResponse } from '@/types/api'
import type { CatalogueQuery, ClassCard } from '@/types/classes'
import { pageFromQuery, pageQuery } from '@/utils/pageQuery'
import '@/styles/classes.css'

const props = withDefaults(
  defineProps<{
    load: (query: CatalogueQuery) => Promise<PageResponse<ClassCard>>
    showAllLink?: boolean
    /** "All · 8": off where a tab above already counts the classes (the learner's Find classes). */
    countAll?: boolean
  }>(),
  { showAllLink: false, countAll: true },
)
defineSlots<{
  header?(props: { catalogueCount: number | null }): unknown
  card(props: { card: ClassCard; query: { theme?: string; page?: string } }): unknown
  empty?(): unknown
}>()

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })
const { resource, allCards, themes, theme, selectTheme, page, items, totalItems, totalPages } = useClassCatalogue(
  (query) => props.load(query),
)
const headingId = useId()
const themeChips = useTemplateRef<{ focusSelected: () => void }>('themeChips')
const themeOptions = computed(() => [
  { value: null, label: t('classCatalogue.filters.all'), count: props.countAll ? allCards.value.length : undefined },
  ...themes.value.map(({ name, count }) => ({ value: name, label: name, count })),
])
const route = useRoute()
const router = useRouter()
const listPath = route.path
const listQuery = computed(() => ({ theme: theme.value ?? undefined, page: pageQuery(page.value) }))

function updateAddress(): void {
  void router.replace({ path: listPath, query: { ...route.query, ...listQuery.value }, hash: route.hash })
}

// Resolve old links against the loaded catalogue, including a page or theme that no longer exists.
const restoreCatalogueQuery = watch(
  [() => route.query, resource.data],
  () => {
    if (route.path !== listPath || !resource.data.value) return
    selectTheme(themes.value.find(({ name }) => name === route.query.theme)?.name ?? null)
    page.value = Math.min(pageFromQuery(route.query.page), Math.max(0, totalPages.value - 1))
    if (route.query.theme !== listQuery.value.theme || route.query.page !== listQuery.value.page) updateAddress()
  },
  { immediate: true },
)
pauseDuringNavigation(restoreCatalogueQuery)

function filterByTheme(next: string | null): void {
  selectTheme(next)
  updateAddress()
}

function changePage(next: number): void {
  page.value = next
  updateAddress()
}

/** The See all button leaves the page when the filter clears, so move focus to the selected chip. */
async function seeAllClasses(): Promise<void> {
  filterByTheme(null)
  await nextTick()
  themeChips.value?.focusSelected()
}
</script>
