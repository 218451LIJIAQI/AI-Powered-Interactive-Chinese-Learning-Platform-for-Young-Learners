<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { tm } = useI18n()
const sections = computed(() => tm('legal.childSafety.sections') as Array<{ title: string; points: string[] }>)
</script>

<template>
  <div class="page-stack">
    <el-card class="simple-page-card legal-page-card" shadow="hover">
      <p class="section-label">{{ $t('legal.childSafety.eyebrow') }}</p>
      <h1>{{ $t('legal.childSafety.title') }}</h1>
      <p>{{ $t('legal.childSafety.description') }}</p>
    </el-card>

    <section class="legal-grid">
      <el-card v-for="section in sections" :key="section.title" class="dashboard-panel legal-section-card" shadow="hover">
        <h2>{{ section.title }}</h2>
        <ul class="legal-list">
          <li v-for="point in section.points" :key="point">{{ point }}</li>
        </ul>
      </el-card>
    </section>
  </div>
</template>
