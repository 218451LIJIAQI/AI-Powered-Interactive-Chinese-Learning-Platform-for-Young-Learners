<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import type { RefundRequest } from '../../types/commerce'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const requests = ref<RefundRequest[]>([])

async function loadRequests() {
  loading.value = true
  try {
    requests.value = await commerceService.getAdminRefundRequests()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    loading.value = false
  }
}

async function approveRequest(id: number) {
  try {
    await commerceService.approveRefund(id)
    ElMessage.success(t('commerce.messages.actionApplied'))
    await loadRequests()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  }
}

async function rejectRequest(id: number) {
  let reason: { value: string }
  try {
    reason = await ElMessageBox.prompt(
      t('commerce.rejectReasonPrompt'),
      t('commerce.reject'),
      { inputPattern: /.+/, inputErrorMessage: t('commerce.reasonRequired') },
    )
  } catch {
    // User cancelled the prompt.
    return
  }
  try {
    await commerceService.rejectRefund(id, { reason: reason.value })
    ElMessage.success(t('commerce.messages.actionApplied'))
    await loadRequests()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  }
}

async function markRefunded(id: number) {
  try {
    await commerceService.markRefunded(id)
    ElMessage.success(t('commerce.messages.actionApplied'))
    await loadRequests()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  }
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
        <el-table-column :label="t('commerce.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('refundStatus', row.refundStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="t('commerce.reason')" min-width="220" />
        <el-table-column min-width="280">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="success" plain @click="approveRequest(row.id)">{{ t('commerce.approve') }}</el-button>
              <el-button type="warning" plain @click="rejectRequest(row.id)">{{ t('commerce.reject') }}</el-button>
              <el-button type="primary" plain @click="markRefunded(row.id)">{{ t('commerce.markRefunded') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
