<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'
import type { RefundRequest } from '../../types/commerce'

const { t } = useI18n()
const loading = ref(true)
const requests = ref<RefundRequest[]>([])

async function loadRequests() {
  loading.value = true
  try {
    requests.value = await commerceService.getAdminRefundRequests()
  } finally {
    loading.value = false
  }
}

async function rejectRequest(id: number) {
  const reason = await ElMessageBox.prompt(t('commerce.rejectReasonPrompt'), t('commerce.reject'), { inputPattern: /.+/, inputErrorMessage: t('commerce.reasonRequired') })
  await commerceService.rejectRefund(id, { reason: reason.value })
  await loadRequests()
}

onMounted(() => {
  loadRequests()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero :eyebrow="t('commerce.refundManagementEyebrow')" :title="t('commerce.refundManagementTitle')" :description="t('commerce.refundManagementDescription')" />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="requests" class="phase-table">
        <el-table-column prop="orderNumber" :label="t('commerce.order')" min-width="150" />
        <el-table-column prop="amount" :label="t('commerce.amount')" min-width="120" />
        <el-table-column prop="refundStatus" :label="t('commerce.status')" min-width="120" />
        <el-table-column prop="reason" :label="t('commerce.reason')" min-width="220" />
        <el-table-column min-width="280">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="success" plain @click="commerceService.approveRefund(row.id).then(loadRequests)">{{ t('commerce.approve') }}</el-button>
              <el-button type="warning" plain @click="rejectRequest(row.id)">{{ t('commerce.reject') }}</el-button>
              <el-button type="primary" plain @click="commerceService.markRefunded(row.id).then(loadRequests)">{{ t('commerce.markRefunded') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
