<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'
import type { PaymentRecord } from '../../types/commerce'
import { extractApiError } from '../../utils/apiError'
import { formatMoney } from '../../utils/money'

const route = useRoute()
const { t } = useI18n()

const loading = ref(true)
const payment = ref<PaymentRecord | null>(null)

const paymentId = computed(() => {
  const raw = route.query.paymentId
  const numeric = Number(typeof raw === 'string' ? raw : 0)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : 0
})
const cancelled = computed(() => route.name === 'payment-cancel' || route.query.cancelled === 'true')

async function loadPayment() {
  if (!paymentId.value) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    payment.value = await commerceService.verifyPaymentStatus(paymentId.value)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPayment()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero :eyebrow="t('commerce.resultEyebrow')" :title="t('commerce.paymentResultTitle')" :description="t('commerce.paymentResultDescription')" />

    <el-card class="dashboard-panel" shadow="hover">
      <el-skeleton v-if="loading" animated :rows="5" />
      <template v-else>
        <el-empty
          v-if="!payment"
          :description="cancelled ? t('commerce.paymentCancelledMessage') : t('commerce.noPaymentSelected')"
        />
        <div v-else class="profile-summary">
          <p><strong>{{ t('commerce.order') }}:</strong> {{ payment.orderNumber }}</p>
          <p><strong>{{ t('commerce.status') }}:</strong> {{ payment.paymentStatus }}</p>
          <p><strong>{{ t('commerce.amount') }}:</strong> {{ formatMoney(payment.amount) }}</p>
          <p><strong>{{ t('commerce.provider') }}:</strong> {{ payment.providerName }}</p>
          <p><strong>{{ t('commerce.providerStatus') }}:</strong> {{ payment.providerStatus || '-' }}</p>
        </div>
      </template>
    </el-card>
  </div>
</template>
