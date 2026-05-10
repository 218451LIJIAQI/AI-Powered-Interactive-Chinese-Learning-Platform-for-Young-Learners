<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { supportTicketService } from '../../services/supportTicketService'
import type { SupportTicketItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const router = useRouter()
const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const tickets = ref<SupportTicketItem[]>([])

async function loadTickets() {
  loading.value = true
  try {
    tickets.value = await supportTicketService.getAdminTickets()
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
      :title="t('phase11.adminSupportTitle')"
      :description="t('phase11.adminSupportDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="tickets" class="phase-table">
        <el-table-column prop="subject" :label="t('phase11.ticketSubject')" min-width="220" />
        <el-table-column prop="requesterName" :label="t('phase11.ticketRequester')" min-width="180" />
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
        <el-table-column width="160">
          <template #default="{ row }">
            <el-button plain type="primary" @click="router.push(`/admin/support-tickets/${row.id}`)">
              {{ t('content.reviewDetail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !tickets.length" :description="t('phase11.emptySupportTickets')" />
    </el-card>
  </div>
</template>
