<template>
  <span
    class="class-avatar"
    :class="`tint-${nameTint(title)}`"
    :style="{ '--class-avatar-size': `${size}px` }"
    aria-hidden="true"
  >
    {{ initials }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { classInitials } from '@/utils/classes'
import { nameTint } from '@/utils/nameTint'

/**
 * A class's avatar: its initials ("MC" for Mandarin for Campus Life) on a circle in the class's tint, the
 * same on every page. It always sits beside the class's visible title, so it is hidden from assistive technology.
 */
const props = defineProps<{ title: string; size: number }>()

const initials = computed(() => classInitials(props.title))
</script>

<style scoped>
.class-avatar {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: var(--class-avatar-size);
  height: var(--class-avatar-size);
  border-radius: var(--radius-round);
  background: var(--tint-bg);
  box-shadow: inset 0 0 0 1px var(--color-ink-a06);
  color: var(--tint-fg);
  font-size: max(var(--font-size-2xs), calc(var(--class-avatar-size) * 0.36));
  font-weight: var(--font-weight-heading);
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
}
</style>
