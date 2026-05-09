<script setup lang="ts">
import AiVocabularyHelperPage from '../student/AiVocabularyHelperPage.vue'
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-alert
      title="Use AI suggestions as preparation notes only. Review everything before saving official lesson content."
      type="info"
      show-icon
      :closable="false"
    />
    <AiVocabularyHelperPage />
  </div>
</template>
