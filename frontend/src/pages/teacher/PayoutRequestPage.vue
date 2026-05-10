<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import type { PayoutRequest } from '../../types/commerce'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const requests = ref<PayoutRequest[]>([])
const form = reactive({
  amount: 0,
  requestedNote: '',
})

async function loadRequests() {
  loading.value = true
  try {
    requests.value = await commerceService.getTeacherPayoutRequests()
  } finally {
    loading.value = false
  }
}

async function submitRequest() {
  await commerceService.createPayoutRequest({
    amount: Number(form.amount),
    requestedNote: form.requestedNote || undefined,
  })
  ElMessage.success(t('commerce.messages.payoutRequested'))
  form.amount = 0
  form.requestedNote = ''
  await loadRequests()
}

onMounted(() => {
  loadRequests()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero :eyebrow="t('commerce.payoutEyebrow')" :title="t('commerce.payoutTitle')" :description="t('commerce.payoutDescription')" />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-input-number v-model="form.amount" :min="0.01" :step="1" />
        <el-input v-model="form.requestedNote" :placeholder="t('commerce.requestNote')" class="content-filter content-filter--wide" />
        <el-button type="primary" @click="submitRequest">{{ t('commerce.requestPayout') }}</el-button>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="requests" class="phase-table">
        <el-table-column prop="amount" :label="t('commerce.amount')" min-width="120" />
        <el-table-column :label="t('commerce.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('payoutStatus', row.payoutStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestedNote" :label="t('commerce.requestNote')" min-width="220" />
        <el-table-column prop="decisionReason" :label="t('commerce.decisionReason')" min-width="220" />
      </el-table>
    </el-card>
  </div>
</template>
