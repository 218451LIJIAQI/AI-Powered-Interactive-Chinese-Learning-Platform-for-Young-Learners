<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <el-card shadow="hover" class="placeholder-card">
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
  </el-card>
</template>
