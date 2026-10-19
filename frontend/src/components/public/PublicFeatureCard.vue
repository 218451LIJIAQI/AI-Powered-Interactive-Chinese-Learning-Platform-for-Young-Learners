<template>
  <article class="public-feature-card" :class="[toneClass, layoutClass]">
    <span class="public-feature-card-icon" aria-hidden="true">
      <IconGlyph :name="icon" :size="layout === 'inline' ? 18 : 22" />
    </span>
    <div class="public-feature-card-copy">
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'

type FeatureTone = 'purple' | 'green' | 'orange' | 'pink' | 'blue' | 'gold'
type FeatureLayout = 'stacked' | 'inline'

interface Props {
  icon: string
  title: string
  description: string
  tone?: FeatureTone
  layout?: FeatureLayout
}

const props = withDefaults(defineProps<Props>(), {
  tone: 'purple',
  layout: 'stacked',
})

const toneClass = computed(() => `is-${props.tone}`)
const layoutClass = computed(() => `is-${props.layout}`)
</script>

<style scoped>
.public-feature-card {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
}

.public-feature-card-icon {
  display: grid;
  place-items: center;
  width: var(--tap-target-min);
  height: var(--tap-target-min);
  border-radius: var(--radius-md);
  background: var(--feature-tone-soft);
  color: var(--feature-tone-text);
}

.public-feature-card-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.public-feature-card-copy strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.public-feature-card-copy p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.public-feature-card.is-inline {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  box-shadow: none;
}

.public-feature-card.is-inline .public-feature-card-icon {
  width: var(--control-height-sm);
  height: var(--control-height-sm);
  border-radius: var(--radius-sm);
}

.public-feature-card.is-inline .public-feature-card-copy strong {
  font-size: var(--font-size-md);
}

.is-purple {
  --feature-tone-soft: var(--color-primary-50);
  --feature-tone-text: var(--color-primary-600);
}

.is-green {
  --feature-tone-soft: var(--color-success-soft);
  --feature-tone-text: var(--color-success-text);
}

.is-orange {
  --feature-tone-soft: var(--color-warning-soft);
  --feature-tone-text: var(--color-warning-text);
}

.is-pink {
  --feature-tone-soft: var(--color-danger-soft);
  --feature-tone-text: var(--color-danger-text);
}

.is-blue {
  --feature-tone-soft: var(--color-info-soft);
  --feature-tone-text: var(--color-info-text);
}

.is-gold {
  --feature-tone-soft: var(--color-accent-100);
  --feature-tone-text: var(--color-accent-800);
}

@media (max-width: 640px) {
  .public-feature-card {
    grid-template-columns: auto minmax(0, 1fr);
    padding: var(--space-4);
  }
}
</style>
