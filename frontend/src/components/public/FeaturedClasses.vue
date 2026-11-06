<template>
  <ul v-if="classes.length" class="featured-classes-list">
    <li v-for="card in classes" :key="card.id">
      <ClassCatalogueCard :card="card" :to="{ name: 'class-detail', params: { classId: card.id } }" />
    </li>
  </ul>
  <StatePanel
    v-else-if="resource.loading.value"
    tone="loading"
    :title="t('classes.loading')"
    :message="t('landing.featuredTitle')"
  />
  <StatePanel
    v-else-if="resource.error.value"
    tone="error"
    icon="alert"
    :title="t('landing.featuredLoadError')"
    :message="resource.error.value"
  >
    <template #actions>
      <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
    </template>
  </StatePanel>
  <StatePanel
    v-else
    tone="empty"
    icon="classes"
    :title="t('landing.featuredEmptyTitle')"
    :message="t('landing.featuredEmpty')"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { publicClassesApi } from '@/api/classes'
import ClassCatalogueCard from '@/components/classes/ClassCatalogueCard.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { landingMessages } from '@/plugins/landingMessages'
import '@/styles/classes.css'

/** Three published classes for the landing page (the classes with the most lessons), on the catalogue's own card. */
const { t } = useLocalMessages({ landing: landingMessages, classes: classesMessages })

const resource = useResource(() => publicClassesApi.featured())
const classes = computed(() => resource.data.value ?? [])
</script>
