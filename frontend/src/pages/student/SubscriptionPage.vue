<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'
import type { StudentSubscription, SubscriptionPlan } from '../../types/commerce'
import { extractApiError } from '../../utils/apiError'
import { formatMoney } from '../../utils/money'

const { t } = useI18n()

const loading = ref(true)
const plans = ref<SubscriptionPlan[]>([])
const currentSubscription = ref<StudentSubscription | null>(null)

async function loadData() {
  loading.value = true
  try {
    const [planList, current] = await Promise.all([
      commerceService.getSubscriptionPlans(),
      commerceService.getCurrentSubscription(),
    ])
    plans.value = planList
    currentSubscription.value = current
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    loading.value = false
  }
}

async function checkoutPlan(planId: number) {
  try {
    const result = await commerceService.checkoutSubscription({ itemType: 'SUBSCRIPTION', itemId: planId })
    if (result.checkoutUrl) {
      window.location.assign(result.checkoutUrl)
      return
    }
    ElMessage.warning(result.message)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('commerce.subscriptionEyebrow')"
      :title="t('commerce.subscriptionTitle')"
      :description="t('commerce.subscriptionDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-skeleton v-if="loading" animated :rows="5" />
      <div v-else class="dashboard-grid">
        <el-card class="dashboard-panel" shadow="never">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('commerce.activeSubscription') }}</p>
              <h2>{{ currentSubscription?.plan.name || t('commerce.noActiveSubscription') }}</h2>
            </div>
          </div>
          <p v-if="currentSubscription">{{ currentSubscription.subscriptionStatus }}</p>
          <p v-else>{{ t('commerce.noActiveSubscriptionDescription') }}</p>
        </el-card>

        <el-card v-for="plan in plans" :key="plan.id" class="dashboard-panel" shadow="never">
          <div class="panel-header">
            <div>
              <p class="section-label">{{ t('commerce.plan') }}</p>
              <h2>{{ plan.name }}</h2>
            </div>
            <el-tag :type="plan.active ? 'success' : 'info'" round>{{ plan.active ? t('common.active') : t('common.inactive') }}</el-tag>
          </div>
          <p>{{ plan.benefits }}</p>
          <strong>{{ formatMoney(plan.price) }}</strong>
          <div class="table-actions subscription-card__actions">
            <el-button type="primary" @click="checkoutPlan(plan.id)">{{ t('commerce.subscribeNow') }}</el-button>
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.subscription-card__actions {
  margin-top: 1rem;
}
</style>
