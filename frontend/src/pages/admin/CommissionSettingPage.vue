<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'

const { t } = useI18n()
const loading = ref(true)
const form = reactive({
  commissionRate: 0,
})

async function loadSetting() {
  loading.value = true
  try {
    const setting = await commerceService.getCommissionSetting()
    form.commissionRate = setting.commissionRate
  } finally {
    loading.value = false
  }
}

async function saveSetting() {
  await commerceService.updateCommissionSetting({ commissionRate: Number(form.commissionRate) })
  ElMessage.success(t('commerce.messages.commissionSaved'))
}

onMounted(() => {
  loadSetting()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero :eyebrow="t('commerce.commissionEyebrow')" :title="t('commerce.commissionTitle')" :description="t('commerce.commissionDescription')" />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-input-number v-model="form.commissionRate" :min="0" :max="1" :step="0.01" />
        <el-button type="primary" :loading="loading" @click="saveSetting">{{ t('commerce.saveCommission') }}</el-button>
      </div>
    </el-card>
  </div>
</template>
