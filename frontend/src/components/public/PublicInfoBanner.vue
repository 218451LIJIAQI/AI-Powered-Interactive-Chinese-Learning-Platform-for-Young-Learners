<template>
  <article class="public-info-banner" :class="toneClass">
    <span class="public-info-banner-icon" aria-hidden="true">
      <IconGlyph :name="icon" :size="22" />
    </span>
    <div class="public-info-banner-copy">
      <div class="public-info-banner-head">
        <strong>{{ title }}</strong>
        <span v-if="showBadge" class="public-info-banner-badge">{{ badge }}</span>
      </div>
      <p>{{ description }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'

type BannerTone = 'green' | 'purple' | 'blue' | 'pearl'

interface Props {
  icon: string
  title: string
  description: string
  badge?: string
  tone?: BannerTone
}

const props = withDefaults(defineProps<Props>(), {
  badge: '',
  tone: 'green',
})

const toneClass = computed(() => `is-${props.tone}`)
const showBadge = computed(() => props.badge.trim().length > 0)
</script>

<style scoped>
.public-info-banner {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--banner-tone-soft), var(--surface-card) 70%);
  box-shadow: var(--shadow-sm);
}

.public-info-banner-icon {
  display: grid;
  place-items: center;
  width: var(--tap-target-min);
  height: var(--tap-target-min);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--banner-tone-text);
}

.public-info-banner-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.public-info-banner-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-2);
}

.public-info-banner-head strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.public-info-banner-badge {
  display: inline-flex;
  align-items: center;
  min-height: var(--space-6);
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  background: var(--banner-tone-soft);
  color: var(--banner-tone-text);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.public-info-banner-copy p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.is-green {
  --banner-tone-soft: var(--color-success-soft);
  --banner-tone-text: var(--color-success-text);
}

.is-purple {
  --banner-tone-soft: var(--color-primary-50);
  --banner-tone-text: var(--color-primary-700);
}

.is-blue {
  --banner-tone-soft: var(--color-info-soft);
  --banner-tone-text: var(--color-info-text);
}

.is-pearl {
  --banner-tone-soft: var(--surface-subtle);
  --banner-tone-text: var(--text-secondary);
}
</style>
