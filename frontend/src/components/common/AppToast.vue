<template>
  <Teleport to="body">
    <div class="toast-region" role="status" aria-live="polite" aria-atomic="true">
      <Transition name="toast">
        <p
          v-if="message"
          ref="toastEl"
          class="toast"
          :class="{ 'toast--action': action }"
          data-testid="toast"
          @mouseenter="onPointerEnter"
          @mouseleave="onPointerLeave"
          @focusin="onFocusIn"
          @focusout="onFocusOut"
        >
          <IconGlyph name="check" :size="16" />
          <span>{{ message }}</span>
          <button v-if="action" type="button" class="toast-action" @click="action.run()">{{ action.label }}</button>
        </p>
      </Transition>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import type { ToastAction } from '@/composables/useToast'

/**
 * The short confirmation of a same-page action ("Marked as read."); `useToast` shows and hides it. A reversible action
 * adds its Undo button (`action`), which keeps the toast open while it is hovered or focused.
 */
const props = defineProps<{ message: string | null; action?: ToastAction | null }>()

const toastEl = ref<HTMLElement | null>(null)
let hovered = false

function isFocused(): boolean {
  return Boolean(toastEl.value && toastEl.value.contains(document.activeElement))
}

function onPointerEnter(): void {
  hovered = true
  props.action?.hold()
}

function onFocusIn(): void {
  props.action?.hold()
}

function onPointerLeave(): void {
  hovered = false
  if (!isFocused()) props.action?.release()
}

function onFocusOut(event: FocusEvent): void {
  const toast = event.currentTarget as HTMLElement
  if (
    hovered ||
    toast.matches(':hover') ||
    (event.relatedTarget instanceof Node && toast.contains(event.relatedTarget))
  )
    return
  props.action?.release()
}

// A toast updated in place starts a fresh auto-hide timer in useToast; re-hold it if the user is still interacting.
watch(
  [() => props.message, () => props.action],
  ([message, action]) => {
    if (!message || !action) {
      hovered = false
      return
    }
    if (hovered || toastEl.value?.matches(':hover') || isFocused()) {
      action.hold()
    }
  },
  { flush: 'post' },
)
</script>
