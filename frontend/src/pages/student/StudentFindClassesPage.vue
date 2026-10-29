<template>
  <div class="student-find-page page-stack">
    <PageHeaderBlock :title="t('classCatalogue.find.title')" :description="t('classCatalogue.find.notice')" />
    <StudentClassesTabs />

    <ClassCatalogueFilters v-model="filters" :summary="summary.data.value" />

    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      :title="t('classes.loading')"
      :message="t('classCatalogue.find.notice')"
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
    <section v-else-if="items.length" class="student-find-results" aria-labelledby="find-matched">
      <h2 id="find-matched" class="section-title" aria-live="polite">{{ t('classCatalogue.matched', totalItems) }}</h2>
      <ul class="student-find-grid">
        <li v-for="card in items" :key="card.id">
          <ClassCatalogueCard :card="card" :to="{ name: 'student-class', params: { classId: card.id } }">
            <template #badge>
              <span v-if="planSteps.get(card.id)" class="plan-badge" data-testid="plan-badge">
                <IconGlyph name="route" :size="14" />{{ t('plan.cards.inYourPlan', { step: planSteps.get(card.id) }) }}
              </span>
            </template>
            <template #actions>
              <RouterLink
                class="btn btn-link"
                :to="{ name: 'student-class', params: { classId: card.id } }"
                :aria-label="t('classCatalogue.find.viewClassNamed', { title: card.title })"
              >
                {{ t('classCatalogue.find.viewClass') }}<IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </template>
          </ClassCatalogueCard>
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
    <StatePanel
      v-else-if="filtered"
      tone="empty"
      icon="search"
      :title="t('classCatalogue.emptyTitle')"
      :message="t('classCatalogue.emptyMessage')"
    >
      <template #actions>
        <button class="btn btn-secondary" type="button" @click="clearFilters">
          {{ t('classCatalogue.filters.clear') }}
        </button>
      </template>
    </StatePanel>
    <StatePanel
      v-else
      tone="empty"
      icon="classes"
      :title="t('classCatalogue.find.emptyTitle')"
      :message="t('classCatalogue.find.emptyMessage')"
    >
      <template #actions>
        <RouterLink class="btn btn-secondary" :to="{ name: 'student-classes' }">
          {{ t('classCatalogue.tabs.mine') }}
        </RouterLink>
      </template>
    </StatePanel>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { publicClassesApi, studentClassesApi } from '@/api/classes'
import { planApi } from '@/api/plan'
import ClassCatalogueCard from '@/components/classes/ClassCatalogueCard.vue'
import ClassCatalogueFilters from '@/components/classes/ClassCatalogueFilters.vue'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useClassCatalogue } from '@/composables/useClassCatalogue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { planMessages } from '@/plugins/planMessages'
import '@/styles/classes.css'
import '@/styles/plan.css'

const { t } = useLocalMessages({
  classCatalogue: classCatalogueMessages,
  classes: classesMessages,
  plan: planMessages,
})

const summary = useResource(() => publicClassesApi.summary())
// The plan only adds the "In your plan" badges, so the catalogue never waits for it or fails with it.
const overview = useResource(() => planApi.overview())
const planSteps = computed(
  () => new Map((overview.data.value?.plan?.classSteps ?? []).map((step) => [step.classId, step.position])),
)
const { filters, page, resource, items, totalItems, totalPages, goToPage, filtered, clearFilters } = useClassCatalogue(
  (query) => studentClassesApi.catalogue(query),
)
</script>
