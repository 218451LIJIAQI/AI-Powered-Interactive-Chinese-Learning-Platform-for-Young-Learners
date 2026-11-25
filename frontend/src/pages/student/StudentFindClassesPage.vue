<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('classCatalogue.tabs.find') }}</h1>
    <ClassCatalogueList :load="studentClassesApi.catalogue" :count-all="false">
      <template #header="{ catalogueCount }">
        <StudentClassesTabs :joined-count="joinedCount" :catalogue-count="catalogueCount" />
      </template>
      <template #card="{ card, query }">
        <ClassCatalogueCard
          :card="card"
          :to="classRoute(card.id, query)"
          :link-label="t('classCatalogue.find.viewClassNamed', { title: card.title })"
        >
          <template v-if="planClassIds.has(card.id)" #badge>
            <span class="tag is-strong" data-testid="in-your-plan">
              <IconGlyph name="route" :size="14" />{{ t('classCatalogue.find.inYourPlan') }}
            </span>
          </template>
          <template #actions>
            <span class="btn btn-secondary" aria-hidden="true">
              {{ t('classCatalogue.find.viewClass') }}
            </span>
          </template>
        </ClassCatalogueCard>
      </template>
      <template #empty>
        <StatePanel
          v-if="membershipLoading"
          heading-tag="h2"
          tone="loading"
          :title="t('common.loadingTitle')"
          :message="t('common.loadingMessage')"
        />
        <StatePanel
          v-else
          heading-tag="h2"
          tone="empty"
          icon="classes"
          :title="joinedCount ? t('classCatalogue.find.emptyTitle') : t('classCatalogue.noClassesTitle')"
          :message="t('classCatalogue.noClassesMessage')"
        >
          <template v-if="joinedCount" #actions>
            <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">
              {{ t('classCatalogue.tabs.mine') }}
            </RouterLink>
          </template>
        </StatePanel>
      </template>
    </ClassCatalogueList>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type LocationQueryRaw } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import ClassCatalogueCard from '@/components/classes/ClassCatalogueCard.vue'
import ClassCatalogueList from '@/components/classes/ClassCatalogueList.vue'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages })

// The catalogue remains browsable while account-specific counts and plan badges load.
const { data: membership, loading: membershipLoading } = useResource(studentClassesApi.catalogueMembership)
const joinedCount = computed(() => membership.value?.joinedCount)
const planClassIds = computed(() => new Set(membership.value?.plannedClassIds))

function classRoute(classId: number, query: LocationQueryRaw) {
  return { name: 'student-class', params: { classId }, query }
}
</script>
