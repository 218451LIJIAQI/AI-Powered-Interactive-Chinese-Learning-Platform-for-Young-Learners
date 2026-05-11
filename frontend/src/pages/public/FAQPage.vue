<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type FaqEntry = { question: string; answer: string }

const { t, tm } = useI18n()
const items = computed(() => (tm('legal.faqItems') as FaqEntry[]) ?? [])
</script>

<template>
  <div class="page-stack">
    <el-card class="simple-page-card legal-page-card" shadow="hover">
      <p class="section-label">{{ t('legal.faqEyebrow') }}</p>
      <h1>{{ t('legal.faqTitle') }}</h1>
      <p>{{ t('legal.faqDescription') }}</p>
    </el-card>

    <el-collapse class="faq-collapse">
      <el-collapse-item v-for="(item, index) in items" :key="item.question" :name="String(index)">
        <template #title>
          <strong>{{ item.question }}</strong>
        </template>
        <p class="faq-answer">{{ item.answer }}</p>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
