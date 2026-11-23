<template>
  <article class="class-catalogue-card">
    <!-- The class's cover: its tint on squared paper, with its initials. Decoration only; the title names the class. -->
    <div class="class-catalogue-heading" :class="`tint-${nameTint(card.title)}`">
      <div class="class-catalogue-cover" aria-hidden="true">
        <ClassAvatar :title="card.title" :size="40" />
      </div>
      <div v-if="card.topicName || $slots.badge" class="class-catalogue-card-tags">
        <span v-if="card.topicName" class="tag">{{ card.topicName }}</span>
        <slot name="badge" />
      </div>
    </div>
    <h3 class="class-catalogue-card-title">
      <RouterLink :to="to" class="class-catalogue-card-link"><CjkText :text="card.title" /></RouterLink>
    </h3>
    <p v-if="card.description" class="class-catalogue-card-description" data-testid="class-card-description">
      <CjkText :text="card.description" />
    </p>
    <p class="class-catalogue-card-meta"><IconGlyph name="learn" :size="15" />{{ meta }}</p>
    <div v-if="$slots.actions" class="class-catalogue-card-actions">
      <slot name="actions" />
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ClassCard } from '@/types/classes'
import { nameTint } from '@/utils/nameTint'

import ClassAvatar from './ClassAvatar.vue'

/**
 * A class in a catalogue, the same on the public pages and in Find classes, in the order of the UI rules: the class's
 * cover (its tint and initials), its theme tag, the title (the whole card is its link), what the class is about (two
 * lines at most) and "5 lessons · best after Unit 4". Slots add a tag beside the theme (`badge`, "In your plan") and an
 * action at the foot, on the card's right edge (`actions`).
 */
const props = defineProps<{ card: ClassCard; to: RouteLocationRaw }>()

const { t } = useLocalMessages({ classes: classesMessages })

const meta = computed(() => {
  const unit = props.card.bestAfterUnit
  return [t('classes.lessons', props.card.lessonCount), unit ? t('classes.bestAfterInline', { unit }) : null]
    .filter(Boolean)
    .join(' · ')
})
</script>
