<template>
  <div class="public-page pub-catalogue-page">
    <PageHeaderBlock
      :title="t('classCatalogue.title')"
      :description="t('classCatalogue.description')"
      :eyebrow="t('classCatalogue.eyebrow')"
    />

    <div class="pub-catalogue-layout">
      <div class="pub-catalogue-filters">
        <p class="pub-catalogue-filters-title">
          <IconGlyph name="filter" :size="16" />
          <span>{{ t('classCatalogue.filtersTitle') }}</span>
          <span v-if="filtered" class="chip">{{ t('classCatalogue.filtersOn') }}</span>
        </p>
        <ClassCatalogueFilters v-model="filters" :summary="summary.data.value" />
      </div>

      <section class="pub-catalogue-join" aria-labelledby="pub-catalogue-join-title">
        <h2 id="pub-catalogue-join-title" class="pub-catalogue-join-title">{{ t('classCatalogue.signInTitle') }}</h2>
        <template v-if="isStudent">
          <p>{{ t('classCatalogue.studentText') }}</p>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-classes-find' }">
            {{ t('classCatalogue.findClasses') }}
          </RouterLink>
        </template>
        <template v-else>
          <p>{{ t('classCatalogue.signInText') }}</p>
          <div class="pub-catalogue-join-actions">
            <RouterLink class="btn btn-primary" :to="{ path: '/auth', query: { tab: 'register', role: 'student' } }">
              {{ t('classCatalogue.createAccount') }}
            </RouterLink>
            <RouterLink class="btn btn-ghost" :to="{ path: '/auth', query: { tab: 'signin' } }">
              {{ t('classCatalogue.signIn') }}
            </RouterLink>
          </div>
        </template>
      </section>

      <div class="pub-catalogue-results">
        <StatePanel
          v-if="resource.loading.value"
          tone="loading"
          :title="t('classes.loading')"
          :message="t('classCatalogue.description')"
        />
        <StatePanel
          v-else-if="resource.error.value && !items.length"
          tone="error"
          icon="alert"
          :title="t('classCatalogue.loadErrorTitle')"
          :message="resource.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          </template>
        </StatePanel>
        <template v-else-if="items.length">
          <div class="pub-catalogue-count">
            <h2 class="section-title" aria-live="polite">{{ t('classCatalogue.matched', totalItems) }}</h2>
            <button v-if="filtered" class="btn btn-link" type="button" @click="clearFilters">
              {{ t('classCatalogue.filters.clear') }}
            </button>
          </div>
          <ul class="pub-classes-grid">
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
        </template>
        <StatePanel
          v-else
          tone="empty"
          icon="search"
          :title="t('classCatalogue.emptyTitle')"
          :message="t('classCatalogue.emptyMessage')"
        >
          <template v-if="filtered" #actions>
            <button class="btn btn-secondary" type="button" @click="clearFilters">
              {{ t('classCatalogue.filters.clear') }}
            </button>
          </template>
        </StatePanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { publicClassesApi } from '@/api/classes'
import ClassCatalogueCard from '@/components/classes/ClassCatalogueCard.vue'
import ClassCatalogueFilters from '@/components/classes/ClassCatalogueFilters.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useClassCatalogue } from '@/composables/useClassCatalogue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { useAuthStore } from '@/stores/auth'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })
const auth = useAuthStore()
const isStudent = computed(() => auth.isAuthenticated && auth.hasRole('STUDENT'))

const summary = useResource(() => publicClassesApi.summary())
const { filters, page, resource, items, totalItems, totalPages, goToPage, filtered, clearFilters } = useClassCatalogue(
  (query) => publicClassesApi.search(query),
)
</script>
