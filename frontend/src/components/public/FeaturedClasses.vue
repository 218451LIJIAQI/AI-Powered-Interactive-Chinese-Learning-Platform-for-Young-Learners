<template>
  <div class="featured-classes">
    <ul v-if="classes.length" class="featured-classes-list" :aria-label="t('landing.featuredLabel')">
      <li v-for="card in classes" :key="card.id" class="featured-class">
        <PortraitAvatar :size="48" :name="card.teacher.name" />
        <div class="featured-class-copy">
          <span class="featured-class-teacher">
            {{ card.teacher.name }}
            <span v-if="card.teacher.verified" class="verified-mark">
              <IconGlyph name="verified" :size="14" />{{ t('classes.verified') }}
            </span>
          </span>
          <RouterLink class="featured-class-link" :to="{ name: 'class-detail', params: { classId: card.id } }">
            {{ card.title }}
          </RouterLink>
          <span class="type-caption">{{ facts(card) }}</span>
        </div>
        <IconGlyph class="featured-class-arrow" name="chevronRight" :size="18" />
      </li>
    </ul>
    <StatePanel
      v-else-if="resource.loading.value"
      tone="loading"
      :title="t('classes.loading')"
      :message="t('landing.featuredLabel')"
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
    <p v-else class="type-caption">{{ t('landing.featuredEmpty') }}</p>

    <RouterLink class="btn btn-link featured-classes-all" :to="{ name: 'classes' }">
      {{ t('landing.featuredViewAll') }}
      <IconGlyph name="chevronRight" :size="16" />
    </RouterLink>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { publicClassesApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { landingMessages } from '@/plugins/landingMessages'
import type { ClassCard } from '@/types/classes'

/**
 * Three published classes for the landing page (the classes with the most lessons), teacher first: a timetable-like
 * list of rows, each opening the class page, and a link to the whole catalogue.
 */
const { t } = useLocalMessages({ landing: landingMessages, classes: classesMessages })

const resource = useResource(() => publicClassesApi.featured())
const classes = computed(() => resource.data.value ?? [])

function facts(card: ClassCard): string {
  return [
    card.levelName,
    card.topicName,
    `${t('classes.units', card.unitCount)} · ${t('classes.lessons', card.lessonCount)}`,
    card.upcomingLiveSessionCount ? t('landing.featuredLive', card.upcomingLiveSessionCount) : null,
  ]
    .filter(Boolean)
    .join(' · ')
}
</script>
