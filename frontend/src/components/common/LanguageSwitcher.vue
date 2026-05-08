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
    <el-option label="English" value="en" />
    <el-option label="Bahasa Melayu" value="ms" />
    <el-option label="中文" value="zh" />
  </el-select>
</template>
