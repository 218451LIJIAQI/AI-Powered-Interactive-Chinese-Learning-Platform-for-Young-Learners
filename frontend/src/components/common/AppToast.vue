<template>
  <Teleport to="body">
    <div class="toast-region" role="status" aria-live="polite" aria-atomic="true">
      <Transition name="toast">
        <p v-if="message" class="toast" data-testid="toast">
          <IconGlyph name="check" :size="16" />
          <span>{{ message }}</span>
        </p>
      </Transition>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'

/** The short confirmation of a same-page action ("Marked as read."); `useToast` shows and hides it. */
defineProps<{ message: string | null }>()
</script>
