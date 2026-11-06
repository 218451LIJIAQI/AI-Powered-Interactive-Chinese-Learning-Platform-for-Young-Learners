<template>
  <article class="class-catalogue-card" :class="{ 'is-detailed': detailed }" :data-testid="`class-card-${card.id}`">
    <div class="class-catalogue-card-teacher">
      <PortraitAvatar :size="40" :name="card.teacher.name" />
      <strong class="class-catalogue-card-name">{{ card.teacher.name }}</strong>
    </div>
    <div v-if="card.topicName || $slots.badge" class="class-catalogue-card-tags">
      <span v-if="card.topicName" class="tag">{{ card.topicName }}</span>
      <slot name="badge" />
    </div>
    <h3 class="class-catalogue-card-title">
      <RouterLink :to="to" class="class-catalogue-card-link"><CjkText :text="card.title" /></RouterLink>
    </h3>
    <p v-if="meta" class="class-catalogue-card-meta">{{ meta }}</p>
    <p v-if="detailed && card.description" class="class-catalogue-card-about">{{ card.description }}</p>
    <div v-if="$slots.actions" class="class-catalogue-card-actions">
      <slot name="actions" />
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ClassCard } from '@/types/classes'

/**
 * A class in a catalogue, the same on public and student pages: teacher, theme tag, title (the whole card is its link),
 * "Best after Unit N". `detailed` (Find classes) adds the lesson count to the meta line and a two-line description.
 * Slots add a tag beside the theme (`badge`) and an action at the foot, on the card's right edge (`actions`).
 */
const props = withDefaults(defineProps<{ card: ClassCard; to: RouteLocationRaw; detailed?: boolean }>(), {
  detailed: false,
})

const { t } = useLocalMessages({ classes: classesMessages })

const meta = computed(() => {
  const unit = props.card.bestAfterUnit
  if (!props.detailed) return unit ? t('classes.bestAfter', { unit }) : ''
  return [t('classes.lessons', props.card.lessonCount), unit ? t('classes.bestAfterInline', { unit }) : null]
    .filter(Boolean)
    .join(' · ')
})
</script>
