<template>
  <RouterLink :to="to" class="brand">
    <!-- 火, "fire": the spark in the name, stamped like a seal. -->
    <span class="brand-seal seal" aria-hidden="true">火</span>
    <div class="brand-copy">
      <p class="brand-title">Mandarin<span class="accent">Spark</span></p>
      <p v-if="$slots.subtitle" class="brand-subtitle"><slot name="subtitle" /></p>
    </div>
  </RouterLink>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

/** The brand links home: the public landing page, or the role's home in the sidebar. */
defineProps<{ to: string }>()
</script>
