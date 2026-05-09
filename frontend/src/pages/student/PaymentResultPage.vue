<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'
import type { PaymentRecord } from '../../types/commerce'

const route = useRoute()
const { t } = useI18n()

const loading = ref(true)
const payment = ref<PaymentRecord | null>(null)

const paymentId = computed(() => Number(route.query.paymentId ?? 0))
const cancelled = computed(() => route.name === 'payment-cancel' || route.query.cancelled === 'true')

function formatMoney(amount: number) {
  return `RM ${Number(amount ?? 0).toFixed(2)}`
}

async function loadPayment() {
  if (!paymentId.value) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    payment.value = await commerceService.verifyPaymentStatus(paymentId.value)
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
