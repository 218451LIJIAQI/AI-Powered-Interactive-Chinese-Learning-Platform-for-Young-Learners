<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description: string
  bullets: string[]
  accent?: 'student' | 'teacher' | 'admin'
}>()
</script>

<template>
  <div class="page-stack">
    <el-card class="workspace-placeholder" :class="accent ? `workspace-placeholder--${accent}` : ''" shadow="hover">
      <p class="section-label">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>

      <div class="workspace-placeholder__grid">
        <div class="workspace-placeholder__panel">
          <h2>Ready for later phases</h2>
          <el-empty description="This section is intentionally prepared with a polished placeholder state." />
        </div>

        <div class="workspace-placeholder__panel">
          <h2>Planned capabilities</h2>
          <ul class="workspace-placeholder__list">
            <li v-for="bullet in bullets" :key="bullet">{{ bullet }}</li>
          </ul>
        </div>
      </div>
    </el-card>
  </div>
</template>
