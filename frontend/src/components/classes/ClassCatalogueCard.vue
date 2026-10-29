<template>
  <article class="class-catalogue-card" :data-testid="`class-card-${card.id}`">
    <div class="class-catalogue-card-teacher">
      <PortraitAvatar :size="40" :name="card.teacher.name" />
      <span class="class-catalogue-card-by">
        <span class="class-catalogue-card-by-label">{{ t('classes.aClassBy') }}</span>
        <span class="class-catalogue-card-name">
          <strong>{{ card.teacher.name }}</strong>
          <span v-if="card.teacher.verified" class="verified-mark">
            <IconGlyph name="verified" :size="14" />{{ t('classes.verified') }}
          </span>
        </span>
      </span>
    </div>
    <h3 class="class-catalogue-card-title">
      <RouterLink :to="to" class="class-catalogue-card-link">{{ card.title }}</RouterLink>
    </h3>
    <p v-if="card.description" class="class-catalogue-card-about">{{ card.description }}</p>
    <div class="class-catalogue-card-foot">
      <div class="class-catalogue-card-labels">
        <span v-if="card.levelName" class="tag">{{ card.levelName }}</span>
        <span v-if="card.learningType" class="tag">{{ t(LEARNING_TYPE_KEYS[card.learningType]) }}</span>
        <slot name="badge" />
      </div>
      <p class="type-caption class-catalogue-card-facts">{{ facts }}</p>
      <div v-if="$slots.actions" class="class-catalogue-card-actions">
        <slot name="actions" />
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ClassCard } from '@/types/classes'
import { LEARNING_TYPE_KEYS } from '@/utils/classes'

/**
 * A class in a catalogue (public Classes, Find classes): the teacher leads ("A class by …"), then
 * the title (the whole card is its link), the teacher's short description, level and format as small labels, topic
 * and size. Slots add a badge (beside the labels) or actions (at the foot).
 */
const props = defineProps<{ card: ClassCard; to: RouteLocationRaw }>()

const { t } = useLocalMessages({ classes: classesMessages })

const facts = computed(() =>
  [props.card.topicName, t('classes.units', props.card.unitCount), t('classes.lessons', props.card.lessonCount)]
    .filter(Boolean)
    .join(' · '),
)
</script>
