<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { commerceService } from '../../services/commerceService'
import type { TeacherEarning, TeacherEarningSummary } from '../../types/commerce'
import { extractApiError } from '../../utils/apiError'
import { formatMoney } from '../../utils/money'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const summary = ref<TeacherEarningSummary | null>(null)
const earnings = ref<TeacherEarning[]>([])

async function loadData() {
  loading.value = true
  try {
    const [summaryResponse, earningList] = await Promise.all([
      commerceService.getTeacherEarningSummary(),
      commerceService.getTeacherEarnings(),
    ])
    summary.value = summaryResponse
    earnings.value = earningList
  } catch (error) {
    ElMessage.error(extractApiError(error, t('commerce.messages.actionFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero :eyebrow="t('commerce.earningsEyebrow')" :title="t('commerce.earningsTitle')" :description="t('commerce.earningsDescription')" />

    <section class="dashboard-grid">
      <el-card class="dashboard-panel" shadow="hover">
        <p class="section-label">{{ t('commerce.availableBalance') }}</p>
        <h2>{{ formatMoney(summary?.availableBalance ?? 0) }}</h2>
      </el-card>
      <el-card class="dashboard-panel" shadow="hover">
        <p class="section-label">{{ t('commerce.pendingBalance') }}</p>
        <h2>{{ formatMoney(summary?.pendingBalance ?? 0) }}</h2>
      </el-card>
      <el-card class="dashboard-panel" shadow="hover">
        <p class="section-label">{{ t('commerce.totalNet') }}</p>
        <h2>{{ formatMoney(summary?.totalNetAmount ?? 0) }}</h2>
      </el-card>
    </section>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="earnings" class="phase-table">
        <el-table-column prop="itemTitle" :label="t('commerce.item')" min-width="220" />
        <el-table-column :label="t('commerce.status')" min-width="120">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('paymentStatus', row.earningStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('commerce.grossAmount')" min-width="120">
          <template #default="{ row }">{{ formatMoney(row.grossAmount) }}</template>
        </el-table-column>
        <el-table-column :label="t('commerce.commission')" min-width="120">
          <template #default="{ row }">{{ formatMoney(row.commissionAmount) }}</template>
        </el-table-column>
        <el-table-column :label="t('commerce.netAmount')" min-width="120">
          <template #default="{ row }">{{ formatMoney(row.netAmount) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
