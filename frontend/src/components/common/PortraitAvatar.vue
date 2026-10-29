<template>
  <div class="portrait-avatar" :class="avatarClass" :style="avatarStyle" aria-hidden="true">
    <span v-if="initials" class="portrait-initials">{{ initials }}</span>
    <template v-else>
      <span class="portrait-backdrop" />
      <span class="portrait-shoulders" />
      <span class="portrait-head" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, type StyleValue } from 'vue'

/**
 * Neutral user avatar: initials when a name is given, otherwise a neutral silhouette.
 * It never infers gender, religion or appearance from a name or a list position.
 * `tone` picks the fill (warning flags a student who needs support); the initials meet WCAG AA contrast on both.
 * The avatar always sits next to the visible name, so it is decorative (hidden from assistive tech).
 */
interface Props {
  size?: number
  name?: string | null
  tone?: 'primary' | 'warning'
}

const props = withDefaults(defineProps<Props>(), {
  size: 56,
  name: null,
  tone: 'primary',
})

const initials = computed(() => {
  const parts = (props.name ?? '').trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return ''
  const first = Array.from(parts[0])[0]
  const last = parts.length > 1 ? Array.from(parts[parts.length - 1])[0] : ''
  return `${first}${last}`.toUpperCase()
})

const avatarClass = computed(() => ({
  'is-tone-warning': props.tone === 'warning',
  'has-initials': Boolean(initials.value),
}))

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
  border-radius: var(--radius-round);
  contain: layout paint;
  background: linear-gradient(180deg, var(--color-primary-50) 0%, var(--surface-card) 100%);
  box-shadow: var(--shadow-xs);
}

.portrait-avatar.is-tone-warning {
  --portrait-fill: var(--color-warning-strong);
}

.portrait-avatar.has-initials {
  background: var(--portrait-fill);
}

.portrait-initials {
  color: var(--portrait-ink);
  /* Never below the overline size, so the initials of the small inline avatars stay legible. */
  font-size: max(var(--font-size-2xs), calc(var(--portrait-size) * 0.38));
  font-weight: var(--font-weight-bold);
  line-height: 1;
  letter-spacing: 0.02em;
}

.portrait-backdrop,
.portrait-shoulders,
.portrait-head {
  position: absolute;
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
