<template>
  <div class="portrait-avatar" :class="avatarClass" :style="avatarStyle" aria-hidden="true">
    <span v-if="initials" class="portrait-initials" aria-hidden="true">{{ initials }}</span>
    <template v-else>
      <span class="portrait-backdrop" />
      <span class="portrait-shoulders" />
      <span class="portrait-head" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, type StyleValue } from 'vue'

import type { AvatarTone } from './avatarTone'

/**
 * Neutral user avatar (D21): initials when a name is given, otherwise a neutral silhouette.
 * It never infers gender, religion or appearance from a name or a list position.
 * `tone` picks a token pair (fill + text) whose initials meet WCAG AA contrast.
 * The avatar always sits next to the visible name, so it is decorative (hidden from assistive tech).
 */
type AvatarShape = 'circle' | 'soft'

interface Props {
  size?: number
  name?: string | null
  tone?: AvatarTone
  rounded?: AvatarShape
}

const props = withDefaults(defineProps<Props>(), {
  size: 56,
  name: null,
  tone: 'primary',
  rounded: 'circle',
})

const initials = computed(() => {
  const parts = (props.name ?? '').trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return ''
  const first = Array.from(parts[0] ?? '')[0] ?? ''
  const last = parts.length > 1 ? (Array.from(parts[parts.length - 1] ?? '')[0] ?? '') : ''
  return `${first}${last}`.toUpperCase()
})

const avatarClass = computed(() => [
  `is-tone-${props.tone}`,
  {
    'is-circle': props.rounded === 'circle',
    'is-soft': props.rounded === 'soft',
    'has-initials': Boolean(initials.value),
  },
])

const avatarStyle = computed<StyleValue>(() => ({
  '--portrait-size': `${props.size}px`,
}))
</script>

<style scoped>
.portrait-avatar {
  --portrait-fill: var(--color-primary-500);
  --portrait-ink: var(--text-on-primary);
  position: relative;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: var(--portrait-size);
  height: var(--portrait-size);
  overflow: hidden;
  contain: layout paint;
  background: linear-gradient(180deg, var(--color-primary-50) 0%, var(--surface-card) 100%);
  box-shadow: var(--shadow-xs);
}

.portrait-avatar.is-tone-info {
  --portrait-fill: var(--color-info-strong);
}

.portrait-avatar.is-tone-success {
  --portrait-fill: var(--color-success-strong);
}

.portrait-avatar.is-tone-warning {
  --portrait-fill: var(--color-warning-strong);
}

.portrait-avatar.is-tone-accent {
  --portrait-fill: var(--color-accent-500);
  --portrait-ink: var(--text-on-accent);
}

.portrait-avatar.is-circle {
  border-radius: var(--radius-round);
}

.portrait-avatar.is-soft {
  border-radius: calc(var(--portrait-size) * 0.28);
}

.portrait-avatar.has-initials {
  background: var(--portrait-fill);
}

.portrait-initials {
  color: var(--portrait-ink);
  font-size: calc(var(--portrait-size) * 0.38);
  font-weight: var(--font-weight-bold);
  line-height: 1;
  letter-spacing: 0.02em;
}

.portrait-backdrop,
.portrait-shoulders,
.portrait-head {
  position: absolute;
  display: block;
}

.portrait-backdrop {
  inset: 0;
  background: radial-gradient(circle at 28% 20%, var(--surface-card), transparent 24%);
}

.portrait-shoulders {
  left: 18%;
  right: 18%;
  bottom: -10%;
  height: 40%;
  border-radius: 46% 46% 0 0;
  background: var(--portrait-fill);
  opacity: 0.85;
}

.portrait-head {
  left: 32%;
  top: 18%;
  width: 36%;
  height: 36%;
  border-radius: 50%;
  background: var(--portrait-fill);
  opacity: 0.55;
}
</style>
