<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description: string
}>()
</script>

<template>
  <section class="page-hero">
    <p class="page-hero__eyebrow">{{ eyebrow }}</p>
    <h1>{{ title }}</h1>
    <p class="page-hero__description">{{ description }}</p>
  </section>
</template>
