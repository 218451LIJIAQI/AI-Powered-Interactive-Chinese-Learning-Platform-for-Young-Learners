<template>
  <div class="public-page public-doc">
    <PageHeaderBlock :title="t('common.exploreClasses')" />

    <ClassCatalogueList :load="publicClassesApi.search" show-all-link>
      <template #card="{ card, query }">
        <ClassCatalogueCard :card="card" :to="{ name: 'class-detail', params: { classId: card.id }, query }" />
      </template>
    </ClassCatalogueList>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import { publicClassesApi } from '@/api/classes'
import ClassCatalogueCard from '@/components/classes/ClassCatalogueCard.vue'
import ClassCatalogueList from '@/components/classes/ClassCatalogueList.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'

const { t } = useI18n({ useScope: 'global' })
</script>
