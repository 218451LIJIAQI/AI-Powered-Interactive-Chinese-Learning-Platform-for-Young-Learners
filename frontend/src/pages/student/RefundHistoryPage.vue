<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import type { RefundRequest } from '../../types/commerce'

const { t } = useI18n()
const translateEnum = useEnumLabel()

const loading = ref(true)
const refunds = ref<RefundRequest[]>([])
const form = reactive({
  orderId: 0,
  reason: '',
})

async function loadRefunds() {
  loading.value = true
  try {
    refunds.value = await commerceService.getRefundRequests()
  } finally {
    loading.value = false
  }
}

async function submitRefundRequest() {
  await commerceService.createRefundRequest({ orderId: Number(form.orderId), reason: form.reason })
  ElMessage.success(t('commerce.messages.refundRequested'))
  form.orderId = 0
  form.reason = ''
  await loadRefunds()
}

onMounted(() => {
  loadRefunds()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero :eyebrow="t('commerce.refundEyebrow')" :title="t('commerce.refundTitle')" :description="t('commerce.refundDescription')" />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-input-number v-model="form.orderId" :min="1" :placeholder="t('commerce.orderId')" />
        <el-input v-model="form.reason" :placeholder="t('commerce.refundReason')" class="content-filter content-filter--wide" />
        <el-button type="primary" @click="submitRefundRequest">{{ t('commerce.submitRefundRequest') }}</el-button>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="refunds" class="phase-table">
        <el-table-column prop="orderNumber" :label="t('commerce.order')" min-width="160" />
        <el-table-column prop="amount" :label="t('commerce.amount')" min-width="120" />
        <el-table-column :label="t('commerce.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('refundStatus', row.refundStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="t('commerce.reason')" min-width="220" />
        <el-table-column prop="decisionReason" :label="t('commerce.decisionReason')" min-width="220" />
      </el-table>
    </el-card>
  </div>
</template>
