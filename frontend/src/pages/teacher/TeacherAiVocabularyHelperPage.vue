<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AiVocabularyHelperPage from '../student/AiVocabularyHelperPage.vue'

const { t } = useI18n()
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-alert
      :title="t('ai.teacherDraftNotice')"
      type="info"
      show-icon
      :closable="false"
    />
    <AiVocabularyHelperPage />
  </div>
</template>
