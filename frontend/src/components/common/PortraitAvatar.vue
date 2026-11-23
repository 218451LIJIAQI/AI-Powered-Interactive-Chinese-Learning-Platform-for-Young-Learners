<template>
  <div
    class="portrait-avatar"
    :class="`tint-${nameTint(name)}`"
    :style="{ '--portrait-size': `${size}px` }"
    aria-hidden="true"
  >
    <span class="portrait-initials">{{ initials }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { nameTint } from '@/utils/nameTint'

/**
 * Neutral user avatar: the initials of a name on a tinted circle. The tint only tells people apart in a list: it is
 * picked from the name's characters, so one person keeps one colour everywhere, and it never infers gender, religion
 * or appearance. The initials meet WCAG AA contrast on every tint.
 * The avatar always sits next to the visible name, so it is decorative (hidden from assistive tech).
 */
const props = defineProps<{ size: number; name: string }>()

const initials = computed(() => {
  const parts = props.name.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return ''
  const first = Array.from(parts[0])[0]
  const last = parts.length > 1 ? Array.from(parts[parts.length - 1])[0] : ''
  return `${first}${last}`.toUpperCase()
})
</script>

<style scoped>
.portrait-avatar {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  width: var(--portrait-size);
  height: var(--portrait-size);
  overflow: hidden;
  border-radius: var(--radius-round);
  contain: layout paint;
  background: var(--tint-bg);
  box-shadow: inset 0 0 0 1px var(--color-ink-a06);
}

.portrait-initials {
  color: var(--tint-fg);
  /* Never below the overline size, so the initials of the small inline avatars stay legible. */
  font-size: max(var(--font-size-2xs), calc(var(--portrait-size) * 0.38));
  font-weight: var(--font-weight-heading);
  line-height: 1;
  letter-spacing: 0.02em;
}
</style>
