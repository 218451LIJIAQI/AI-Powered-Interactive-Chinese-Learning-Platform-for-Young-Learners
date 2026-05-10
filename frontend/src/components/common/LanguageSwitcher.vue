<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useLanguageStore } from '../../stores/languageStore'

const languageStore = useLanguageStore()
const { t } = useI18n()

const currentLocale = computed({
  get: () => languageStore.locale,
  set: (value) => languageStore.setLocale(value),
})
</script>

<template>
  <el-select v-model="currentLocale" size="small" class="toolbar-select" :aria-label="t('common.language')">
    <el-option :label="t('ai.languages.en')" value="en" />
    <el-option :label="t('ai.languages.ms')" value="ms" />
    <el-option :label="t('ai.languages.zh')" value="zh" />
  </el-select>
</template>
