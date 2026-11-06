<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('classCatalogue.find.title')" hide-title />
    <StudentClassesTabs />

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
    <section v-else-if="allCards.length" class="student-find-results" aria-labelledby="find-matched">
      <ClassCatalogueFilters
        :model-value="theme"
        :themes="themes"
        :total="allCards.length"
        @update:model-value="selectTheme"
      />
      <!-- The chips show the numbers; the heading names the result for screen readers when a chip changes it. -->
      <h2 id="find-matched" class="sr-only" aria-live="polite">{{ t('classCatalogue.matched', totalItems) }}</h2>
      <ul class="student-find-grid">
        <li v-for="card in items" :key="card.id">
          <ClassCatalogueCard :card="card" detailed :to="{ name: 'student-class', params: { classId: card.id } }">
            <template v-if="planClassIds.has(card.id)" #badge>
              <span class="tag is-strong" data-testid="plan-badge">
                <IconGlyph name="route" :size="14" />{{ t('classCatalogue.find.inYourPlan') }}
              </span>
            </template>
            <template #actions>
              <RouterLink
                class="btn btn-secondary"
                :to="{ name: 'student-class', params: { classId: card.id } }"
                :aria-label="t('classCatalogue.find.viewClassNamed', { title: card.title })"
              >
                {{ t('classCatalogue.find.viewClass') }}
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
    <div v-else class="panel student-classes-empty">
      <StatePanel
        tone="empty"
        icon="classes"
        :title="t('classCatalogue.find.emptyTitle')"
        :message="t('classCatalogue.find.emptyMessage')"
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">
            {{ t('classCatalogue.tabs.mine') }}
          </RouterLink>
        </template>
      </StatePanel>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
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
import '@/styles/classes.css'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages, classes: classesMessages })

// The plan only adds the "In your plan" tags, so the catalogue never waits for it or fails with it.
const overview = useResource(() => planApi.overview())
const planClassIds = computed(() => new Set((overview.data.value?.plan?.classSteps ?? []).map((step) => step.classId)))
const { resource, allCards, themes, theme, selectTheme, page, items, totalItems, totalPages, goToPage } =
  useClassCatalogue((query) => studentClassesApi.catalogue(query))
</script>
