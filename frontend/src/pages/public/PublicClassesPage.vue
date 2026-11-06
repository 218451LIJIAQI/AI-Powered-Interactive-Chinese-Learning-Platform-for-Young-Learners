<template>
  <div class="public-page public-doc">
    <PageHeaderBlock :title="t('classCatalogue.title')" />

    <StatePanel v-if="resource.loading.value" tone="loading" :title="t('classes.loading')" message="" />
    <StatePanel
      v-else-if="resource.error.value"
      tone="error"
      icon="alert"
      :title="t('classCatalogue.loadErrorTitle')"
      :message="resource.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="!allCards.length"
      tone="empty"
      icon="classes"
      spacious
      :title="t('classCatalogue.noClassesTitle')"
      :message="t('classCatalogue.noClassesMessage')"
    />
    <section v-else class="pub-catalogue-results" aria-labelledby="pub-catalogue-matched">
      <ClassCatalogueFilters
        :model-value="theme"
        :themes="themes"
        :total="allCards.length"
        @update:model-value="selectTheme"
      />
      <!-- The chips show the numbers; the heading names the result for screen readers when a chip changes it. -->
      <h2 id="pub-catalogue-matched" class="sr-only" aria-live="polite">
        {{ t('classCatalogue.matched', totalItems) }}
      </h2>
      <ul class="pub-catalogue-grid">
        <li v-for="card in items" :key="card.id">
          <ClassCatalogueCard :card="card" :to="{ name: 'class-detail', params: { classId: card.id } }" />
        </li>
      </ul>
      <PaginationBar
        v-if="totalPages > 1"
        :current-page="page + 1"
        :total-pages="totalPages"
        :label="t('classCatalogue.pagination')"
        @update:current-page="goToPage($event - 1)"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { publicClassesApi } from '@/api/classes'
import ClassCatalogueCard from '@/components/classes/ClassCatalogueCard.vue'
import ClassCatalogueFilters from '@/components/classes/ClassCatalogueFilters.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useClassCatalogue } from '@/composables/useClassCatalogue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })

const { resource, allCards, themes, theme, selectTheme, page, items, totalItems, totalPages, goToPage } =
  useClassCatalogue((query) => publicClassesApi.search(query))
</script>
