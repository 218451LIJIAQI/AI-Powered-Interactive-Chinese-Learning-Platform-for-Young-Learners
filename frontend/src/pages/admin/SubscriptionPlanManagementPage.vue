<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { commerceService } from '../../services/commerceService'
import type { SubscriptionPlan } from '../../types/commerce'

const { t } = useI18n()
const loading = ref(true)
const plans = ref<SubscriptionPlan[]>([])
const form = reactive({
  name: '',
  price: 0,
  benefits: '',
  active: true,
})

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await commerceService.getAdminSubscriptionPlans()
  } finally {
    loading.value = false
  }
}

async function createPlan() {
  await commerceService.createSubscriptionPlan({ ...form })
  ElMessage.success(t('commerce.messages.planSaved'))
  form.name = ''
  form.price = 0
  form.benefits = ''
  form.active = true
  await loadPlans()
}

async function togglePlan(plan: SubscriptionPlan) {
  if (plan.active) {
    await commerceService.deactivateSubscriptionPlan(plan.id)
  } else {
    await commerceService.activateSubscriptionPlan(plan.id)
  }
  await loadPlans()
}

onMounted(() => {
  loadPlans()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('commerce.subscriptionManagementEyebrow')"
      :title="t('commerce.subscriptionManagementTitle')"
      :description="t('commerce.subscriptionManagementDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-input v-model="form.name" :placeholder="t('commerce.planName')" class="content-filter" />
        <el-input-number v-model="form.price" :min="0" />
        <el-input v-model="form.benefits" :placeholder="t('commerce.benefits')" class="content-filter content-filter--wide" />
        <el-switch v-model="form.active" />
        <el-button type="primary" @click="createPlan">{{ t('commerce.savePlan') }}</el-button>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="plans" class="phase-table">
        <el-table-column prop="name" :label="t('commerce.plan')" min-width="180" />
        <el-table-column prop="price" :label="t('commerce.price')" min-width="120" />
        <el-table-column prop="active" :label="t('commerce.status')" min-width="120">
          <template #default="{ row }">{{ row.active ? t('common.active') : t('common.inactive') }}</template>
        </el-table-column>
        <el-table-column prop="benefits" :label="t('commerce.benefits')" min-width="260" />
        <el-table-column min-width="140">
          <template #default="{ row }">
            <el-button plain @click="togglePlan(row)">
              {{ row.active ? t('commerce.deactivate') : t('commerce.activate') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
