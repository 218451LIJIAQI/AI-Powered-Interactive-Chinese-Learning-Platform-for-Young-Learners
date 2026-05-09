<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'
import type { PaymentRecord } from '../../types/commerce'

const { t } = useI18n()
const loading = ref(true)
const payments = ref<PaymentRecord[]>([])

function formatMoney(amount: number) {
  return `RM ${Number(amount ?? 0).toFixed(2)}`
}

async function loadPayments() {
  loading.value = true
  try {
    payments.value = await commerceService.getAdminPayments()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPayments()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('commerce.paymentManagementEyebrow')"
      :title="t('commerce.paymentManagementTitle')"
      :description="t('commerce.paymentManagementDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="payments" class="phase-table">
        <el-table-column prop="paymentId" :label="t('commerce.paymentId')" min-width="120" />
        <el-table-column prop="orderNumber" :label="t('commerce.order')" min-width="160" />
        <el-table-column prop="studentName" :label="t('commerce.student')" min-width="180" />
        <el-table-column prop="providerName" :label="t('commerce.provider')" min-width="120" />
        <el-table-column prop="paymentStatus" :label="t('commerce.status')" min-width="120" />
        <el-table-column :label="t('commerce.amount')" min-width="120">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="currencyCode" :label="t('commerce.currency')" min-width="100" />
        <el-table-column prop="createdAt" :label="t('commerce.createdAt')" min-width="180" />
        <el-table-column prop="paidAt" :label="t('commerce.paidAt')" min-width="180" />
        <el-table-column prop="providerStatus" :label="t('commerce.providerStatus')" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>
