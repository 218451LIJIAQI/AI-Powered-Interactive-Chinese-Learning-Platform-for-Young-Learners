<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import type { PaymentRecord } from '../../types/commerce'
import { extractApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/datetime'
import { formatMoney } from '../../utils/money'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const payments = ref<PaymentRecord[]>([])

async function loadPayments() {
  loading.value = true
  try {
    payments.value = await commerceService.getPayments()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPayments()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('commerce.paymentsEyebrow')"
      :title="t('commerce.paymentHistoryTitle')"
      :description="t('commerce.paymentHistoryDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="payments" class="phase-table">
        <el-table-column prop="orderNumber" :label="t('commerce.order')" min-width="160" />
        <el-table-column prop="providerName" :label="t('commerce.provider')" min-width="120" />
        <el-table-column :label="t('commerce.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('paymentStatus', row.paymentStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('commerce.amount')" min-width="120">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column :label="t('commerce.createdAt')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
