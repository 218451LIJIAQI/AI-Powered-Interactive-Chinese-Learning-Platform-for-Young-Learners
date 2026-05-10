<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { supportTicketService } from '../../services/supportTicketService'
import type { SupportTicketItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/datetime'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const tickets = ref<SupportTicketItem[]>([])


async function loadTickets() {
  loading.value = true
  try {
    tickets.value = await supportTicketService.getMyTickets()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTickets()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.supportEyebrow')"
      :title="t('phase11.supportTicketsTitle')"
      :description="t('phase11.supportTicketsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('nav.supportTickets') }}</p>
          <h2>{{ t('phase11.mySupportTickets') }}</h2>
        </div>
        <router-link :to="{ path: '/support-tickets/create' }">
          <el-button type="primary">{{ t('phase11.createSupportTicket') }}</el-button>
        </router-link>
      </div>

      <el-table v-loading="loading" :data="tickets" class="phase-table">
        <el-table-column prop="subject" :label="t('phase11.ticketSubject')" min-width="220" />
        <el-table-column :label="t('phase11.ticketStatus')" min-width="160">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('ticketStatus', row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('phase11.ticketPriority')" min-width="140">
          <template #default="{ row }">
            <el-tag round size="small" type="info">{{ translateEnum('ticketPriority', row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('commerce.createdAt')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column width="160">
          <template #default="{ row }">
            <router-link :to="{ path: `/support-tickets/${row.id}` }">
              <el-button plain type="primary">{{ t('content.reviewDetail') }}</el-button>
            </router-link>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !tickets.length" :description="t('phase11.emptySupportTickets')" />
    </el-card>
  </div>
</template>
